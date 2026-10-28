#!/usr/bin/env python3
"""Collect Maven runtime, npm lockfile, and OSV dependency audit evidence."""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "reports/final-hardening/dependencies"
NODE22 = Path(os.environ.get("VIRIDEXA_NODE_BIN", str(Path.home()/".nvm/versions/node/v22.20.0/bin")))
OSV = "https://api.osv.dev/v1/querybatch"
TREE_CMD = ["./apps/api/mvnw", "-f", "apps/api/pom.xml", "-s", ".mvn/settings.xml", "dependency:tree"]
ARTIFACT = re.compile(r"([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+):[^:\s]+:([^:\s]+):(compile|runtime|test|provided|system)")


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False, sort_keys=True) + "\n")


def run_capture(args: list[str], cwd: Path, env: dict[str, str], timeout: int) -> tuple[int | None, str, str]:
    try:
        result = subprocess.run(args, cwd=cwd, env=env, text=True, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, timeout=timeout, check=False)
        return result.returncode, result.stdout, result.stderr
    except subprocess.TimeoutExpired as exc:
        return None, (exc.stdout or "").decode(errors="replace") if isinstance(exc.stdout, bytes) else (exc.stdout or ""), "timeout"
    except OSError as exc:
        return None, "", type(exc).__name__


def main() -> int:
    global OUT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=OUT)
    OUT = parser.parse_args().output.resolve()
    OUT.mkdir(parents=True, exist_ok=True)
    scanned = dt.datetime.now().astimezone().isoformat(timespec="seconds")
    env = os.environ.copy()
    if NODE22.is_dir():
        env["PATH"] = str(NODE22) + os.pathsep + env.get("PATH", "")

    tree_rc, tree_out, tree_err = run_capture(TREE_CMD, ROOT, env, 300)
    (OUT / "maven-dependency-tree.txt").write_text(tree_out)
    deps: dict[tuple[str, str, str], set[str]] = {}
    for line in tree_out.splitlines():
        match = ARTIFACT.search(line)
        if match:
            group, artifact, version, scope = match.groups()
            if scope in {"compile", "runtime"}:
                deps.setdefault((group, artifact, version), set()).add(scope)
    runtime = [{"group": g, "artifact": a, "version": v, "scopes": sorted(scopes),
                "purl": f"pkg:maven/{g}/{a}@{v}"} for (g, a, v), scopes in sorted(deps.items())]
    write_json(OUT / "maven-runtime-dependencies.json", runtime)

    lock = ROOT / "apps/web/package-lock.json"
    npm_rc, npm_out, npm_err = run_capture(["npm", "audit", "--json", "--registry=https://registry.npmjs.org"], lock.parent, env, 180)
    try:
        npm_json = json.loads(npm_out)
        npm_state = "complete" if isinstance(npm_json, dict) and "metadata" in npm_json else "unknown"
    except json.JSONDecodeError:
        npm_json = {"status": "unknown", "reason": "npm audit returned invalid or empty JSON", "exitCode": npm_rc}
        npm_state = "unknown"
    write_json(OUT / "npm-audit.json", npm_json)

    queries = [{"package": {"name": f"{d['group']}:{d['artifact']}", "ecosystem": "Maven"}, "version": d["version"]} for d in runtime]
    write_json(OUT / "osv-querybatch-request.json", {"queries": queries})
    osv_items: list[dict] = []
    osv_state = "complete"
    for offset in range(0, len(queries), 100):
        batch = {"queries": queries[offset:offset + 100]}
        req = urllib.request.Request(OSV, data=json.dumps(batch).encode(), headers={"Content-Type": "application/json", "User-Agent": "VIRIDEXA-dependency-audit/1.0"}, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                result = json.loads(response.read())
            osv_items.extend(result.get("results", []))
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError) as exc:
            osv_state = "unknown"
            osv_items = []
            (OUT / "osv-querybatch-error.txt").write_text(f"OSV querybatch unavailable: {type(exc).__name__}\n")
            break
        time.sleep(0.1)
    write_json(OUT / "osv-querybatch-response.json", {"results": osv_items} if osv_state == "complete" else {"status": "unknown", "reason": "OSV querybatch request failed"})

    findings = []
    if osv_state == "complete":
        for dep, result in zip(runtime, osv_items):
            for vuln in result.get("vulns") or []:
                findings.append({"package": f"{dep['group']}:{dep['artifact']}", "version": dep["version"], "id": vuln.get("id"), "summary": vuln.get("summary"), "modified": vuln.get("modified")})
    npm_counts = npm_json.get("metadata", {}).get("vulnerabilities", {}) if npm_state == "complete" else {}
    mvn_version_rc, mvn_version, _ = run_capture(["./apps/api/mvnw", "-version"], ROOT, env, 60)
    node_version_rc, node_version, _ = run_capture(["node", "--version"], ROOT, env, 15)
    npm_version_rc, npm_version, _ = run_capture(["npm", "--version"], ROOT, env, 15)
    summary = {
        "scannedAt": scanned,
        "maven": {"command": " ".join(TREE_CMD), "mavenVersion": mvn_version.strip(), "mavenVersionExitCode": mvn_version_rc, "treeExitCode": tree_rc, "dependencyScope": "compile and runtime only (the runtime closure; test/provided/system excluded)", "runtimeDependencyCount": len(runtime), "osvStatus": osv_state, "osvEndpoint": OSV, "osvQueriedCount": len(queries), "osvFindingCount": len(findings), "findings": findings},
        "npm": {"command": "npm audit --json --registry=https://registry.npmjs.org", "nodeVersion": node_version.strip(), "nodeVersionExitCode": node_version_rc, "npmVersion": npm_version.strip(), "npmVersionExitCode": npm_version_rc, "lockfile": "apps/web/package-lock.json; complete lockfile audit including dev dependencies", "status": npm_state, "exitCode": npm_rc, "vulnerabilityCounts": npm_counts, "errorClass": None if npm_state == "complete" else type(npm_err).__name__},
        "tools": {"trivy": "not installed", "grype": "not installed", "dockerScout": "installed v1.18.1; image scan not run"},
        "containerImageScan": "not run; image coverage remains incomplete",
        "limits": ["Static dependency inventory and advisory lookup only; not source-code or container-image scanning.", "No dependency was upgraded."]
    }
    write_json(OUT / "summary.json", summary)
    human = [
        "# Dependency audit results", "", f"Scanned: {scanned}", "",
        f"- Maven runtime closure: {len(runtime)} compile/runtime coordinates; Maven exit {tree_rc}; official OSV querybatch status `{osv_state}` with {len(queries)} queries and {len(findings)} advisories.",
        f"- Frontend lockfile: npm audit against registry.npmjs.org status `{npm_state}`; {npm_counts.get('total', 'unknown')} advisories across the complete lockfile.",
        "- Scope: Maven compile/runtime dependency closure and all frontend lockfile dependencies, including development dependencies. Test-only Maven dependencies are excluded.",
        "- Tools: Docker Scout CLI v1.18.1 is installed, but no container image scan was run; image coverage remains incomplete. Trivy and Grype are not installed.",
        "- No dependency or lockfile was changed. This is a dependency advisory scan, not a source-code audit.",
        "", "Raw evidence: `maven-dependency-tree.txt`, `maven-runtime-dependencies.json`, `osv-querybatch-request.json`, `osv-querybatch-response.json`, and `npm-audit.json`.",
    ]
    (OUT / "README.md").write_text("\n".join(human) + "\n")
    print(json.dumps({"scannedAt": scanned, "mavenTreeExit": tree_rc, "runtimeCount": len(runtime), "osvStatus": osv_state, "osvFindings": len(findings), "npmStatus": npm_state, "npmExit": npm_rc}, ensure_ascii=False))
    return 0 if tree_rc == 0 and osv_state == "complete" and npm_state == "complete" else 1


if __name__ == "__main__":
    sys.exit(main())
