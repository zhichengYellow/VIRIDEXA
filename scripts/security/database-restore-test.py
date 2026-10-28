#!/usr/bin/env python3
"""Restore a private logical backup into a new, randomly named test schema."""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys

import importlib.util

ROOT = Path(__file__).resolve().parents[2]
BACKUPS = (ROOT / "data/backups/managed").resolve()
REPORTS = ROOT / "reports/final-hardening"
spec = importlib.util.spec_from_file_location("database_backup", Path(__file__).with_name("database-backup.py"))
backup_lib = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(backup_lib)

TARGET_RE = re.compile(r"viridexa_restore_test_[0-9a-f]{24}\Z")


def checksum(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_backup(path: Path) -> tuple[Path, dict]:
    full = path.resolve()
    if full.parent != BACKUPS or not full.is_dir():
        raise ValueError("backup must be a direct child of the ignored data/backups/managed directory")
    manifest_file = full / "manifest.json"
    manifest = json.loads(manifest_file.read_text(encoding="utf-8"))
    if manifest.get("formatVersion") != 1:
        raise ValueError("unsupported backup format")
    source = backup_lib.checked_schema(manifest.get("schema", ""))
    for name in ("schema.sql", "data.sql"):
        item = manifest.get("files", {}).get(name, {})
        content = (full / name).read_bytes()
        if checksum(content) != item.get("sha256") or len(content) != item.get("bytes"):
            raise ValueError(f"backup integrity check failed for {name}")
        if any(backup_lib.classify_check_control(line) for line in content.splitlines()):
            raise ValueError(f"backup contains a disabled FK/UNIQUE control in {name}")
    if manifest.get("foreignKeyChecksDisabled") or manifest.get("uniqueChecksDisabled"):
        raise ValueError("backup manifest indicates FK or UNIQUE checks were disabled")
    return full, manifest


def mysql_stdin(sql: bytes, database: str | None = None, timeout: int = 600) -> None:
    cmd = backup_lib.docker_client("mysql", [], database=database)
    p = subprocess.run(cmd, input=sql, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
                       timeout=timeout, check=False)
    if p.returncode:
        # Preserve only an error code/line coordinate. MySQL may echo duplicate values or SQL text.
        first = p.stderr.decode("utf-8", "replace").splitlines()[:1]
        match = re.search(r"ERROR\s+(\d+)\s+\(([^)]+)\)(?:\s+at line\s+(\d+))?", first[0] if first else "")
        coordinate = f"; mysql error {match.group(1)} ({match.group(2)})" + (f" at line {match.group(3)}" if match.group(3) else "") if match else ""
        raise RuntimeError(f"mysql restore/query failed (exit {p.returncode}){coordinate}; stderr withheld")


def mysql_stream(sql: bytes, database: str, timeout: int = 600) -> list[str]:
    """Run schema, data, and check queries in one mysql session; return only its result rows."""
    cmd = backup_lib.docker_client("mysql", [], database=database)
    p = subprocess.run(cmd, input=sql, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                       timeout=timeout, check=False)
    if p.returncode:
        first = p.stderr.decode("utf-8", "replace").splitlines()[:1]
        match = re.search(r"ERROR\s+(\d+)\s+\(([^)]+)\)(?:\s+at line\s+(\d+))?", first[0] if first else "")
        coordinate = f"; mysql error {match.group(1)} ({match.group(2)})" + (f" at line {match.group(3)}" if match.group(3) else "") if match else ""
        raise RuntimeError(f"mysql restore/query failed (exit {p.returncode}){coordinate}; stderr withheld")
    return p.stdout.decode("utf-8", "replace").splitlines()


def run_query(sql: str) -> list[str]:
    return backup_lib.query(sql)


def transformed_ddl(content: bytes, source: str, target: str) -> bytes:
    # Only rewrite SQL identifiers in the DDL artifact. Never apply schema replacement to data.sql.
    source_b, target_b = source.encode(), target.encode()
    content = re.sub(rb"(?im)^\s*USE\s+`" + re.escape(source_b) + rb"`\s*;\s*$", b"", content)
    content = content.replace(b"`" + source_b + b"`.", b"`" + target_b + b"`.")
    # Dumped definers can refer to a user absent on the restore host. Bind objects to the
    # authenticated restore account; this rewrite is limited to DDL SQL definitions.
    return re.sub(rb"(?i)DEFINER\s*=\s*(?:`[^`]+`|[^@\s]+)@(?:`[^`]+`|[^\s]+)",
                  b"DEFINER=CURRENT_USER", content)


def verify(target: str, full: Path, manifest: dict, connection_checks: list[str]) -> dict:
    live = backup_lib.metadata(target)
    expected_tables = manifest["tables"]
    if live["tables"] != expected_tables or live["views"] != manifest["views"]:
        raise RuntimeError("restored table or view inventory differs from backup")
    if (live["routines"] != manifest["routines"] or live["triggers"] != manifest["triggers"]
            or live["events"] != manifest["events"]):
        raise RuntimeError("restored routine, trigger, or event inventory differs from backup")
    if live["tableCounts"] != manifest["tableCounts"]:
        raise RuntimeError("restored table counts differ from backup")
    if connection_checks != ["1\t1", "1\t1", "1\t1"]:
        raise RuntimeError("FK or UNIQUE checks were not enabled throughout the restore connection")
    generated = {table: set() for table in live["tables"]}
    for table, column in live["generatedColumns"]:
        if table in generated:
            generated[table].add(column)
    # Validate complete INSERT column lists against live generated columns. mysqldump must omit them.
    data = (full / "data.sql").read_bytes()
    for match in re.finditer(rb"(?m)^INSERT INTO `([^`]+)` \(([^\n]+)\) VALUES", data):
        table = match.group(1).decode("utf-8")
        cols = {part.strip().strip(b"`").decode("utf-8") for part in match.group(2).split(b",")}
        if cols & generated.get(table, set()):
            raise RuntimeError(f"data dump includes generated columns for {table}")
    temp = full / f".verify-{secrets.token_hex(8)}.raw"
    try:
        args = ["--no-create-info", "--skip-triggers", "--skip-comments", "--skip-dump-date", "--skip-add-locks",
                "--skip-disable-keys", "--complete-insert", "--skip-extended-insert", "--order-by-primary",
                "--single-transaction", "--column-statistics=0", "--set-gtid-purged=OFF", target,
                *manifest["tableOrder"]]
        backup_lib.capture_dump(args, temp)
        normalized = backup_lib.filter_sql(temp.read_bytes(), target, ddl=False)
    finally:
        temp.unlink(missing_ok=True)
    digest = checksum(normalized)
    if digest != manifest["dataSha256"]:
        raise RuntimeError("restored canonical row-data checksum differs from backup")
    return {"tableCount": len(live["tables"]), "viewCount": len(live["views"]),
            "rowCounts": live["tableCounts"], "dataSha256": digest,
            "constraintChecksOnSameConnection": connection_checks,
            "constraintCheckpoints": ["before-ddl", "between-ddl-and-data", "after-data"]}


def restore_test(path: Path, apply: bool) -> dict:
    full, manifest = read_backup(path)
    target = "viridexa_restore_test_" + secrets.token_hex(12)
    if not TARGET_RE.fullmatch(target):
        raise RuntimeError("internal target name generation failed")
    existing = run_query("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='" + target + "';")
    if existing != ["0"]:
        raise RuntimeError("generated restore schema unexpectedly already exists")
    report = {"formatVersion": 1, "checkedAt": dt.datetime.now().astimezone().isoformat(timespec="seconds"),
              "backupDirectory": str(full.relative_to(ROOT)), "sourceSchema": manifest["schema"],
              "targetSchema": target, "mode": "dry-run", "status": "planned", "cleanup": "not-needed"}
    if not apply:
        return report
    created = False
    stage = "create-schema"
    try:
        charset, collation = manifest["charset"], manifest["collation"]
        if not re.fullmatch(r"[A-Za-z0-9_]+", charset) or not re.fullmatch(r"[A-Za-z0-9_]+", collation):
            raise ValueError("backup contains invalid charset/collation identifiers")
        mysql_stdin(f"CREATE DATABASE `{target}` CHARACTER SET {charset} COLLATE {collation};\n".encode())
        created = True
        stage = "restore-ddl"
        ddl = transformed_ddl((full / "schema.sql").read_bytes(), manifest["schema"], target)
        data = (full / "data.sql").read_bytes()
        checks = b"SELECT @@FOREIGN_KEY_CHECKS, @@UNIQUE_CHECKS;\n"
        stage = "restore-schema-and-data"
        connection_checks = mysql_stream(checks + ddl + b"\n" + checks + data + b"\n" + checks,
                                         database=target)
        stage = "verify"
        proof = verify(target, full, manifest, connection_checks)
        report.update({"mode": "apply", "status": "passed", "verification": proof})
    except Exception as exc:
        report.update({"mode": "apply", "status": "failed", "stage": stage, "error": str(exc)})
    finally:
        if created:
            try:
                mysql_stdin(f"DROP DATABASE `{target}`;\n".encode())
                report["cleanup"] = "dropped-generated-test-schema"
            except Exception:
                report["cleanup"] = "failed-to-drop-generated-test-schema"
                report["status"] = "failed"
    if apply:
        REPORTS.mkdir(parents=True, exist_ok=True)
        stamp = dt.datetime.now().strftime("%Y%m%dT%H%M%S")
        out = REPORTS / f"backup-restore-drill-{stamp}-{target[-8:]}.json"
        fd = os.open(out, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(report, f, indent=2, ensure_ascii=False)
            f.write("\n")
        return {**report, "report": str(out.relative_to(ROOT))}
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backup", required=True, help="backup directory under data/backups/managed")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--dry-run", action="store_true", help="show restore plan (default)")
    group.add_argument("--apply", action="store_true", help="restore, verify, and drop a random test schema")
    args = parser.parse_args()
    try:
        result = restore_test(Path(args.backup), args.apply)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0 if result["status"] != "failed" else 1
    except Exception as exc:
        print(json.dumps({"status": "failed", "error": str(exc)}, ensure_ascii=False), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
