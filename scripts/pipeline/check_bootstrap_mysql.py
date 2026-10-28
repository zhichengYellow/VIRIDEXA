#!/usr/bin/env python3
"""Run bootstrap SQL behavior checks inside a disposable child of the governance test schema."""
import concurrent.futures
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ENV_FILE = ROOT / ".env.governance-test"
REPORT = ROOT / "reports/phase7/bootstrap-mysql.json"
CONTAINER = "viridexa-db-v1"
sys.path.insert(0, str(Path(__file__).resolve().parent))
import bootstrap_admin as bootstrap


def env_schema():
    if not ENV_FILE.is_file():
        raise RuntimeError("governance test env file is missing")
    values = {}
    for line in ENV_FILE.read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip("\"'")
    schema = values.get("GOVERNANCE_TEST_SCHEMA", "")
    if not re.fullmatch(r"viridexa_governance_test_[a-zA-Z0-9_]+", schema):
        raise RuntimeError("test schema does not have the required isolated prefix")
    return schema


def mysql(sql, database=""):
    args = ["docker", "exec", "-i", CONTAINER, "sh", "-c",
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names']
    if database:
        args[-1] += " " + database
    result = subprocess.run(args, input=sql, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError("isolated MySQL check failed")
    return result.stdout.strip()


def check(condition, label):
    if not condition:
        raise RuntimeError("check failed: " + label)


def run():
    source = env_schema()
    child = source + "_bootstrap"
    if len(child) > 64:
        raise RuntimeError("derived child schema name is too long")
    checks = {}
    child_created = False
    try:
        tables = mysql("SELECT table_name FROM information_schema.tables WHERE table_schema='" + source + "' AND table_name IN ('app_user','app_role','user_role') ORDER BY table_name;")
        check(tables.splitlines() == ["app_role", "app_user", "user_role"], "source tables ready")
        exists = mysql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='" + child + "';")
        check(exists == "0", "child schema absent before run")
        mysql("CREATE DATABASE `" + child + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;")
        child_created = True
        for table in ("app_role", "app_user", "user_role"):
            ddl = mysql("SHOW CREATE TABLE `" + source + "`.`" + table + "`; ")
            parts = ddl.split("\t", 1)
            check(len(parts) == 2, "read table DDL " + table)
            mysql(parts[1] + ";", child)
        # Copy role definitions; the disposable child starts with empty account tables.
        role_count = mysql("SELECT COUNT(*) FROM `" + source + "`.app_role;")
        mysql("INSERT INTO app_role SELECT * FROM `" + source + "`.app_role;", child)
        check(mysql("SELECT COUNT(*) FROM app_user;", child) == "0", "child user table empty")
        check(mysql("SELECT COUNT(*) FROM user_role;", child) == "0", "child role links empty")
        checks["source_roles_copied"] = int(role_count)
        hashed = bootstrap.hash_password("Phase7 isolated technical fixture 2026")
        # Same SQL concurrently exercises GET_LOCK against the child schema.
        original = bootstrap.DATABASE
        bootstrap.DATABASE = child
        try:
            with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
                outcomes = list(pool.map(lambda _: bootstrap.execute_bootstrap("fixture-admin@example.invalid", "Fixture Administrator", hashed), range(2)))
            check(sum("CREATED" in outcome for outcome in outcomes) == 1, "one concurrent create")
            check(mysql("SELECT COUNT(*) FROM app_user u JOIN user_role ur ON ur.user_id=u.id JOIN app_role r ON r.id=ur.role_id WHERE r.role_key='ADMIN';", child) == "1", "exactly one concurrent admin")
            checks["concurrent_same_identity"] = "passed"
            before = mysql("SELECT CONCAT(COUNT(*), ':', MAX(password_hash)) FROM app_user;", child)
            rerun = bootstrap.execute_bootstrap("fixture-admin@example.invalid", "Fixture Administrator", "$2b$12$" + "b" * 53)
            after = mysql("SELECT CONCAT(COUNT(*), ':', MAX(password_hash)) FROM app_user;", child)
            check("ALREADY_INITIALIZED" in rerun and before == after, "same identity rerun preserves password")
            checks["same_identity_rerun"] = "passed"
            try:
                bootstrap.execute_bootstrap("other@example.invalid", "Other Fixture", hashed)
            except RuntimeError:
                checks["different_identity_refused"] = "passed"
            else:
                raise RuntimeError("check failed: different identity refused")
            # Remove fixtures, then prove a missing ADMIN role rolls back both inserts.
            mysql("DELETE ur FROM user_role ur; DELETE FROM app_user; DELETE FROM app_role WHERE role_key='ADMIN';", child)
            try:
                bootstrap.execute_bootstrap("rollback@example.invalid", "Rollback Fixture", hashed)
            except RuntimeError:
                pass
            check(mysql("SELECT CONCAT((SELECT COUNT(*) FROM app_user), ':', (SELECT COUNT(*) FROM user_role));", child) == "0:0", "missing role rolls back account and link")
            checks["missing_role_rollback"] = "passed"
            routines = mysql("SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema='" + child + "' AND routine_name LIKE 'bootstrap_admin_%';")
            check(routines == "0", "no bootstrap routines remain after failure")
            checks["routine_cleanup_on_failure"] = "passed"
        finally:
            bootstrap.DATABASE = original
    finally:
        if child_created:
            mysql("DROP DATABASE IF EXISTS `" + child + "`;")
    return {"status": "passed", "checks": checks, "child_schema": child,
            "source_schema": source, "child_dropped": True,
            "completed_at": datetime.now(timezone.utc).isoformat()}


if __name__ == "__main__":
    try:
        result = run()
        REPORT.parent.mkdir(parents=True, exist_ok=True)
        REPORT.write_text(json.dumps(result, indent=2) + "\n")
        print("Bootstrap MySQL checks passed; report written.")
    except Exception:
        REPORT.parent.mkdir(parents=True, exist_ok=True)
        REPORT.write_text(json.dumps({"status": "failed", "completed_at": datetime.now(timezone.utc).isoformat()}, indent=2) + "\n")
        raise
