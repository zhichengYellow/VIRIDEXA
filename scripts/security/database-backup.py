#!/usr/bin/env python3
"""Create a private, checksummed logical backup from the local Viridexa MySQL container."""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
BACKUPS = ROOT / "data/backups/managed"
CONTAINER = "viridexa-db-v1"
SCHEMA_RE = re.compile(r"(?:viridexa|viridexa_[a-z0-9_]{1,48})\Z")
SET_START = re.compile(rb"^\s*(?:/\*!\s*[0-9]+\s+)?SET\b", re.I)
SET_LINE = re.compile(rb"^\s*(?:/\*!\s*[0-9]+\s+)?SET\s+(.+?)\s*(?:\*/)?\s*;\s*$", re.I)
CHECK_NAME = re.compile(rb"\b(?:FOREIGN_KEY_CHECKS|UNIQUE_CHECKS)\b", re.I)
KNOWN_DISABLE = re.compile(
    rb"^SET\s+@OLD_(UNIQUE|FOREIGN_KEY)_CHECKS\s*=\s*@@(?:SESSION\.)?\1_CHECKS\s*,\s*\1_CHECKS\s*=\s*0$", re.I)
KNOWN_RESTORE = re.compile(
    rb"^SET\s+(?:(?:SESSION\s+)?)(UNIQUE|FOREIGN_KEY)_CHECKS\s*=\s*@OLD_\1_CHECKS$", re.I)


def classify_check_control(line: bytes) -> str | None:
    """Classify a standalone mysqldump session control line without scanning INSERT text."""
    candidate = line.strip()
    if not SET_START.match(candidate) or not CHECK_NAME.search(candidate):
        return None
    match = SET_LINE.fullmatch(candidate)
    if not match:
        return "unsupported"
    statement = match.group(1).strip()
    if KNOWN_DISABLE.fullmatch(b"SET " + statement) or KNOWN_RESTORE.fullmatch(b"SET " + statement):
        return "known"
    return "unsupported"


def checked_schema(value: str) -> str:
    if not SCHEMA_RE.fullmatch(value):
        raise ValueError("schema must match viridexa or viridexa_<lowercase letters/digits/underscore>")
    return value


def docker_client(binary: str, args: list[str], *, database: str | None = None) -> list[str]:
    inner = [binary, "-uroot", "--default-character-set=utf8mb4"]
    if binary == "mysql":
        inner += ["--batch", "--raw", "--skip-column-names"]
    if database:
        inner += ["--database", database]
    inner += args
    return ["docker", "exec", "-i", CONTAINER, "sh", "-c",
            'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec "$@"', "sh", *inner]


def query(sql: str, timeout: int = 60) -> list[str]:
    p = subprocess.run(docker_client("mysql", ["-e", sql]), input="", text=True,
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout, check=False)
    if p.returncode:
        raise RuntimeError(f"mysql query failed (exit {p.returncode}); stderr withheld")
    return p.stdout.rstrip("\n").splitlines() if p.stdout else []


def exact_table_counts(schema: str, tables: list[str]) -> dict[str, int]:
    statements = [f"SELECT '{name}', COUNT(*) FROM `{schema}`.`{name}`" for name in tables]
    rows = query(" UNION ALL ".join(statements) + ";", timeout=120) if statements else []
    return {name: int(count) for name, count in (r.split("\t", 1) for r in rows)}


def metadata(schema: str) -> dict:
    schema = checked_schema(schema)
    raw_tables = query("SELECT TABLE_NAME FROM information_schema.TABLES "
                       f"WHERE TABLE_SCHEMA='{schema}' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME;")
    raw_views = query("SELECT TABLE_NAME FROM information_schema.VIEWS "
                      f"WHERE TABLE_SCHEMA='{schema}' ORDER BY TABLE_NAME;")
    if not raw_tables:
        raise RuntimeError("schema missing or has no base tables")
    tables, views = raw_tables, raw_views
    fkrows = query("SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME FROM information_schema.KEY_COLUMN_USAGE "
                   f"WHERE TABLE_SCHEMA='{schema}' AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION;")
    parents: dict[str, set[str]] = {t: set() for t in tables}
    self_columns: dict[str, set[str]] = {}
    for row in fkrows:
        child, column, parent = row.split("\t")
        if child == parent:
            self_columns.setdefault(child, set()).add(column)
        elif parent in parents:
            parents[child].add(parent)
    order: list[str] = []
    remaining = set(tables)
    while remaining:
        ready = sorted(t for t in remaining if not (parents[t] & remaining))
        if not ready:
            raise RuntimeError("foreign-key cycle across tables; fail-closed restore unsupported")
        order.extend(ready)
        remaining.difference_update(ready)
    if "taxon" in self_columns and "parent_id" in self_columns["taxon"]:
        bad = query(f"SELECT COUNT(*) FROM `{schema}`.`taxon` WHERE parent_id IS NOT NULL AND parent_id>=id;")
        if not bad or int(bad[0]) != 0:
            raise RuntimeError("taxon self-reference order check failed: parent_id must be less than id")
    # Generation expressions identify virtual/stored computed columns; DEFAULT_GENERATED
    # timestamps are ordinary insertable columns and must remain in the data stream.
    generated = query("SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS "
                      f"WHERE TABLE_SCHEMA='{schema}' AND GENERATION_EXPRESSION<>'' ORDER BY TABLE_NAME, ORDINAL_POSITION;")
    routines = query("SELECT ROUTINE_NAME, ROUTINE_TYPE FROM information_schema.ROUTINES "
                     f"WHERE ROUTINE_SCHEMA='{schema}' ORDER BY ROUTINE_NAME, ROUTINE_TYPE;")
    triggers = query("SELECT TRIGGER_NAME FROM information_schema.TRIGGERS "
                     f"WHERE TRIGGER_SCHEMA='{schema}' ORDER BY TRIGGER_NAME;")
    events = query("SELECT EVENT_NAME FROM information_schema.EVENTS "
                   f"WHERE EVENT_SCHEMA='{schema}' ORDER BY EVENT_NAME;")
    # Topologically order views from view_table_usage, which records direct view dependencies.
    usages = query("SELECT VIEW_NAME, TABLE_NAME FROM information_schema.VIEW_TABLE_USAGE "
                   f"WHERE VIEW_SCHEMA='{schema}' ORDER BY VIEW_NAME, TABLE_NAME;")
    vparents: dict[str, set[str]] = {v: set() for v in views}
    for row in usages:
        view, referenced = row.split("\t")
        if view in vparents and referenced in vparents:
            vparents[view].add(referenced)
    vorder: list[str] = []
    vremaining = set(views)
    while vremaining:
        ready = sorted(v for v in vremaining if not (vparents[v] & vremaining))
        if not ready:
            raise RuntimeError("view dependency cycle; fail-closed restore unsupported")
        vorder.extend(ready)
        vremaining.difference_update(ready)
    counts = exact_table_counts(schema, tables)
    version = query("SELECT VERSION();")
    coll = query("SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA "
                 f"WHERE SCHEMA_NAME='{schema}';")
    if not coll:
        raise RuntimeError("schema not found")
    return {"schema": schema, "tables": tables, "tableOrder": order, "views": views, "viewOrder": vorder,
            "foreignKeyOrder": {t: sorted(parents[t]) for t in tables if parents[t]},
            "generatedColumns": [r.split("\t") for r in generated], "tableCounts": counts,
            "routines": [r.split("\t") for r in routines], "triggers": triggers, "events": events,
            "mysqlVersion": version[0] if version else "unknown",
            "charset": coll[0].split("\t")[0], "collation": coll[0].split("\t")[1]}


def capture_dump(args: list[str], path: Path, timeout: int = 600) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(fd, "wb") as out:
            p = subprocess.run(docker_client("mysqldump", args), stdout=out,
                               stderr=subprocess.PIPE, timeout=timeout, check=False)
        if p.returncode:
            raise RuntimeError(f"mysqldump failed (exit {p.returncode}); stderr withheld")
    except Exception:
        path.unlink(missing_ok=True)
        raise
    path.chmod(0o600)


def filter_sql(raw: bytes, schema: str, *, ddl: bool) -> bytes:
    lines = []
    use_line = re.compile(rb"^\s*USE\s+`" + re.escape(schema.encode()) + rb"`\s*;\s*$", re.I)
    for line in raw.splitlines(keepends=True):
        if use_line.fullmatch(line.rstrip(b"\r\n")):
            if not ddl:
                continue
        control = classify_check_control(line.rstrip(b"\r\n"))
        if control == "known":
            continue
        if control == "unsupported":
            raise RuntimeError("unsupported FK/UNIQUE session control; refusing dump")
        lines.append(line)
    if any(classify_check_control(line.rstrip(b"\r\n")) for line in lines):
        raise RuntimeError("mysqldump FK/UNIQUE check control statement remains")
    return b"".join(lines)


def sha(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def backup(schema: str, apply: bool) -> dict:
    info = metadata(schema)
    plan = {k: info[k] for k in ("schema", "mysqlVersion", "tables", "tableOrder", "views", "viewOrder", "tableCounts", "generatedColumns", "charset", "collation")}
    if not apply:
        return {"mode": "dry-run", "plan": plan, "backupCreated": False}
    BACKUPS.mkdir(parents=True, exist_ok=True, mode=0o700)
    BACKUPS.chmod(0o700)
    stamp = dt.datetime.now().strftime("%Y%m%dT%H%M%S")
    dest = BACKUPS / f"{schema}_{stamp}_{secrets.token_hex(4)}"
    dest.mkdir(mode=0o700)
    ddl_args = ["--no-data", "--no-create-db", "--skip-comments", "--skip-dump-date", "--single-transaction",
                "--column-statistics=0", "--set-gtid-purged=OFF", "--routines", "--events", "--triggers",
                schema, *info["tableOrder"]]
    data_args = ["--no-create-info", "--skip-triggers", "--skip-comments", "--skip-dump-date", "--skip-add-locks",
                 "--skip-disable-keys", "--complete-insert", "--skip-extended-insert", "--order-by-primary",
                 "--single-transaction", "--column-statistics=0", "--set-gtid-purged=OFF", schema, *info["tableOrder"]]
    try:
        raw_ddl, raw_data = dest / ".ddl.raw", dest / ".data.raw"
        capture_dump(ddl_args, raw_ddl)
        capture_dump(data_args, raw_data)
        ddl = filter_sql(raw_ddl.read_bytes(), schema, ddl=True)
        data = filter_sql(raw_data.read_bytes(), schema, ddl=False)
        # Save exact SHOW CREATE VIEW statements in dependency order, with schema qualification changed only here.
        view_sql = bytearray()
        for view in info["viewOrder"]:
            sql = f"SHOW CREATE VIEW `{schema}`.`{view}`;"
            rows = query(sql)
            if not rows:
                raise RuntimeError(f"SHOW CREATE VIEW returned no definition for {view}")
            columns = rows[0].split("\t")
            if len(columns) < 2 or not columns[1].lstrip().upper().startswith("CREATE "):
                raise RuntimeError(f"could not parse SHOW CREATE VIEW for {view}")
            statement = columns[1].encode("utf-8")
            statement = re.sub(re.escape(b"`" + schema.encode() + b"`."), b"`" + schema.encode() + b"`.", statement)
            view_sql.extend(statement + b";\n")
        # The mysqldump output contains table DDL and routines; topologically ordered views follow it.
        ddl += bytes(view_sql)
        raw_ddl.unlink(missing_ok=True); raw_data.unlink(missing_ok=True)
        for name, content in (("schema.sql", ddl), ("data.sql", data)):
            fd = os.open(dest / name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, "wb") as f: f.write(content)
        manifest = {"formatVersion": 1, "createdAt": dt.datetime.now().astimezone().isoformat(timespec="seconds"),
                    **info, "files": {"schema.sql": {"sha256": sha(dest / "schema.sql"), "bytes": (dest / "schema.sql").stat().st_size},
                                      "data.sql": {"sha256": sha(dest / "data.sql"), "bytes": (dest / "data.sql").stat().st_size}},
                    "dataSha256": sha(dest / "data.sql"), "foreignKeyChecksDisabled": False, "uniqueChecksDisabled": False}
        fd = os.open(dest / "manifest.json", os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w") as f: json.dump(manifest, f, indent=2, ensure_ascii=False); f.write("\n")
        (dest / "manifest.json").chmod(0o600)
        return {"mode": "apply", "backupCreated": True, "backupDirectory": str(dest.relative_to(ROOT)),
                "tableCount": len(info["tables"]), "viewCount": len(info["views"]), "rowCounts": info["tableCounts"],
                "schemaSha256": manifest["files"]["schema.sql"]["sha256"], "dataSha256": manifest["dataSha256"]}
    except Exception:
        # Remove only this invocation's incomplete backup directory.
        shutil.rmtree(dest, ignore_errors=True)
        raise


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--schema", default="viridexa")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--dry-run", action="store_true", help="show plan (default)")
    group.add_argument("--apply", action="store_true", help="create a private logical backup")
    args = parser.parse_args()
    try:
        print(json.dumps(backup(checked_schema(args.schema), args.apply), ensure_ascii=False, indent=2))
        return 0
    except Exception as exc:
        print(json.dumps({"status": "failed", "error": str(exc)}, ensure_ascii=False), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
