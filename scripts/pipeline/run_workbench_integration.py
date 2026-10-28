#!/usr/bin/env python3
"""Run workbench MySQL integration tests against the prepared isolated schema only."""
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PRIVATE = ROOT / ".env.governance-test"

if not PRIVATE.is_file():
    raise SystemExit("Prepare the isolated governance test environment first; production is forbidden.")

values = dict(line.split("=", 1) for line in PRIVATE.read_text().splitlines() if "=" in line)
schema = values.get("GOVERNANCE_TEST_SCHEMA", "")
if not schema.startswith("viridexa_governance_test_") or not schema.replace("_", "").isalnum():
    raise SystemExit("Refusing non-test schema")
for name in ("GOVERNANCE_TEST_DB_URL", "GOVERNANCE_TEST_RO", "GOVERNANCE_TEST_WRITER", "GOVERNANCE_TEST_MIGRATOR", "GOVERNANCE_TEST_PASSWORD", "GOVERNANCE_FIXTURE_PASSWORD", "VIRIDEXA_PROTECTED_ROOT"):
    if not values.get(name):
        raise SystemExit(f"Isolated environment is missing {name}")

environment = os.environ.copy()
environment.update(values)
raise SystemExit(subprocess.call([
    str(ROOT / "apps/api/mvnw"), "-f", str(ROOT / "apps/api/pom.xml"),
    "-s", str(ROOT / ".mvn/settings.xml"), "-Dtest=SourceReleaseMySqlIT,WorkbenchMySqlIT,GraphMySqlIT,GovernanceMySqlIT,AgentMySqlIT,AccountMySqlIT,ProtectionMySqlIT", "test",
], cwd=ROOT, env=environment))
