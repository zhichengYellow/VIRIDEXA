#!/usr/bin/env python3
"""Stage scientific rows and historical review/audit events from a protected snapshot.

The snapshot is restored to a temporary, isolated MySQL container as an ingestion source.
Only SELECT statements run against it. The VIRIDEXA target receives source-record JSON and
event-ledger rows; its primary keys are generated independently.
"""

from __future__ import annotations

import argparse
import datetime as dt
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
SNAPSHOT_RELATIVE = "backups/viridexa-live-snapshot-20261024.sql.gz"
DATABASE = "nonfood_oil_plants"
EXCLUDED_TABLES = {
    "auth_email_challenges", "auth_events", "auth_login_attempts", "users",
    "favorites", "plant_daily_views", "user_plant_history", "user_search_history",
    "schema_migrations",
}
REVIEW_TABLES = {"knowledge_review_events", "external_import_reviews"}
AUDIT_TABLES = {"audit_logs"}


def configured_data_dir() -> Path:
    configured = os.environ.get("VIRIDEXA_DATA_DIR")
    if not configured:
        env_file = ROOT / ".env"
        if env_file.exists():
            for line in env_file.read_text().splitlines():
                if line.startswith("VIRIDEXA_DATA_DIR="):
                    configured = line.split("=", 1)[1].strip()
                    break
    path = Path(configured) if configured else ROOT.parent / "VIRIDEXA-protected" / "data"
    return (ROOT / path).resolve() if not path.is_absolute() else path.resolve()


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def stable_key(*parts: object) -> str:
    return sha256("\x1f".join(str(part) for part in parts).encode("utf-8"))


def sql_text(value: object | None) -> str:
    if value is None or value == "":
        return "NULL"
    encoded = str(value).encode("utf-8").hex()
    return f"CONVERT(UNHEX('{encoded}') USING utf8mb4)"


def sql_raw_json(value: str) -> str:
    encoded = value.encode("utf-8").hex()
    return f"CAST(CONVERT(UNHEX('{encoded}') USING utf8mb4) AS JSON)"


def insert(lines: list[str], table: str, columns: list[str], values: list[str]) -> None:
    lines.append(f"INSERT IGNORE INTO {table} ({', '.join(columns)}) VALUES ({', '.join(values)});")


def mysql_in_snapshot(sql: str) -> str:
    command = ["docker", "compose", "exec", "-T", "snapshot-source", "sh", "-c",
               'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --protocol=tcp --default-character-set=utf8mb4 --batch --raw --skip-column-names -h127.0.0.1 -uroot ' + DATABASE]
    result = subprocess.run(command, input=sql, text=True, capture_output=True, cwd=ROOT)
    if result.returncode != 0:
        raise RuntimeError("Read-only snapshot query failed: " + result.stderr[-1500:])
    return result.stdout


def ensure_snapshot_ready() -> None:
    subprocess.run(["docker", "compose", "--profile", "ingestion-source", "up", "-d", "snapshot-source"],
                   cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        result = subprocess.run(["docker", "inspect", "--format", "{{.State.Health.Status}}", "viridexa-snapshot-source"],
                                cwd=ROOT, text=True, capture_output=True)
        if result.returncode == 0 and result.stdout.strip() == "healthy":
            return
        state = result.stdout.strip() if result.returncode == 0 else "missing"
        if state == "unhealthy":
            raise RuntimeError("Temporary snapshot source did not become healthy")
        time.sleep(2)
    raise RuntimeError("Timed out waiting for the temporary snapshot restore")


def find_column(row: dict[str, object], names: tuple[str, ...]) -> object | None:
    for name in names:
        if name in row:
            return row[name]
    return None


def event_datetime(value: object | None) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None
    normalized = text.replace("Z", "+00:00")
    try:
        parsed = dt.datetime.fromisoformat(normalized)
        return parsed.replace(tzinfo=None).strftime("%Y-%m-%d %H:%M:%S.%f")
    except ValueError:
        try:
            parsed_date = dt.date.fromisoformat(text)
            return dt.datetime.combine(parsed_date, dt.time()).strftime("%Y-%m-%d %H:%M:%S.%f")
        except ValueError:
            return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", help="restore, read and report; default mode")
    mode.add_argument("--apply", action="store_true", help="write the snapshot source ledger to the local VIRIDEXA database")
    parser.add_argument("--data-dir", type=Path, help="external protected input directory")
    args = parser.parse_args()
    data = (args.data_dir or configured_data_dir()).resolve()
    snapshot = data / SNAPSHOT_RELATIVE
    if not snapshot.is_file():
        raise SystemExit(f"Protected source snapshot missing: {snapshot}")
    digest = hashlib.sha256()
    with snapshot.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    snapshot_sha = digest.hexdigest()
    try:
        with gzip.open(snapshot, "rb") as stream:
            for _ in iter(lambda: stream.read(1024 * 1024), b""):
                pass
    except OSError as exc:
        raise SystemExit(f"Protected snapshot gzip validation failed: {exc}")

    # Require the current asset manifest to certify the exact snapshot file.
    manifest = ROOT / "docs" / "ASSET_MANIFEST.md"
    match = None
    for line in manifest.read_text(encoding="utf-8").splitlines():
        if line.startswith(f"| data/{SNAPSHOT_RELATIVE} | "):
            cells = [cell.strip() for cell in line.strip("|").split("|")]
            if len(cells) >= 4:
                match = cells[3]
    if match != snapshot_sha:
        raise SystemExit("Snapshot SHA-256 does not match docs/ASSET_MANIFEST.md; regenerate and review the manifest first")

    ensure_snapshot_ready()
    try:
        table_names = [line for line in mysql_in_snapshot(
            "SELECT table_name FROM information_schema.tables WHERE table_schema='" + DATABASE + "' AND table_type='BASE TABLE' ORDER BY table_name;\n"
        ).splitlines() if line]
        selected = [name for name in table_names if name not in EXCLUDED_TABLES]
        if not selected:
            raise RuntimeError("No source tables selected from the protected snapshot")

        rows_by_table: dict[str, list[tuple[str, str, dict[str, object]]]] = {}
        source_counts: dict[str, int] = {}
        primary_columns: dict[str, list[str]] = {}
        for table in selected:
            if not re.fullmatch(r"[A-Za-z0-9_]+", table):
                raise RuntimeError(f"Unexpected snapshot table identifier: {table!r}")
            metadata = mysql_in_snapshot(
                "SELECT column_name, column_key FROM information_schema.columns "
                f"WHERE table_schema='{DATABASE}' AND table_name='{table}' ORDER BY ordinal_position;\n"
            )
            columns = []
            pks = []
            for line in metadata.splitlines():
                name, _, key = line.partition("\t")
                if not re.fullmatch(r"[A-Za-z0-9_]+", name):
                    raise RuntimeError(f"Unexpected snapshot column identifier: {name!r}")
                columns.append(name)
                if key == "PRI": pks.append(name)
            if not columns:
                raise RuntimeError(f"No columns reported for snapshot table {table}")
            projection = ", ".join(f"'{column}', `{column}`" for column in columns)
            order_by = " ORDER BY " + ", ".join(f"`{column}`" for column in pks) if pks else ""
            output = mysql_in_snapshot(f"SELECT JSON_OBJECT({projection}) FROM `{table}`{order_by};\n")
            parsed_rows: list[tuple[str, str, dict[str, object]]] = []
            duplicate_counts: dict[str, int] = {}
            for raw_json in output.splitlines():
                if not raw_json:
                    continue
                row = json.loads(raw_json)
                row_hash = sha256(raw_json.encode("utf-8"))
                if pks:
                    primary_key_json = json.dumps({key: row.get(key) for key in pks}, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
                    source_row_key = primary_key_json if len(primary_key_json) <= 180 else "pk-sha256:" + sha256(primary_key_json.encode("utf-8"))
                else:
                    occurrence = duplicate_counts.get(row_hash, 0)
                    duplicate_counts[row_hash] = occurrence + 1
                    source_row_key = f"content:{row_hash}:{occurrence}"
                parsed_rows.append((source_row_key, raw_json, row))
            rows_by_table[table] = parsed_rows
            source_counts[table] = len(parsed_rows)
            primary_columns[table] = pks

        run_id = dt.datetime.now().strftime("%Y%m%dT%H%M%S%f") + "-" + stable_key(snapshot_sha)[:10]
        run_dir = data / "processed" / "snapshot-runs" / run_id
        run_dir.mkdir(parents=True, exist_ok=False)
        sql: list[str] = ["SET NAMES utf8mb4;", "START TRANSACTION;"]
        snapshot_asset_key = stable_key("asset", SNAPSHOT_RELATIVE, snapshot_sha)
        insert(sql, "source_asset", ["asset_key", "relative_path", "sha256", "size_bytes", "media_type", "asset_kind", "ingest_state", "metadata"],
               [f"'{snapshot_asset_key}'", "'" + SNAPSHOT_RELATIVE + "'", f"'{snapshot_sha}'", str(snapshot.stat().st_size), "'application/gzip'", "'DATABASE_SNAPSHOT'", "'INDEXED'", "CAST('{\"source_schema\":\"snapshot-only\"}' AS JSON)"])
        snapshot_source_key = stable_key("snapshot-source", snapshot_sha)
        insert(sql, "research_source", ["source_key", "source_asset_id", "source_type", "title", "citation", "license_note", "metadata"],
               [f"'{snapshot_source_key}'", f"(SELECT id FROM source_asset WHERE asset_key='{snapshot_asset_key}')", "'DATABASE_SNAPSHOT'", "'Protected scientific database snapshot (2026-10-24)'", "NULL", "'Protected local input; not exposed as application content.'", f"CAST('{json.dumps({'sha256': snapshot_sha}, separators=(',', ':'))}' AS JSON)"])

        record_index: dict[tuple[str, str], tuple[str, str, dict[str, object], str]] = {}
        for table in selected:
            for source_row_key, raw_json, row in rows_by_table[table]:
                row_hash = sha256(raw_json.encode("utf-8"))
                source_record_key = stable_key("snapshot-record", snapshot_sha, table, source_row_key, row_hash)
                record_index[(table, source_row_key)] = (source_record_key, raw_json, row, row_hash)
                insert(sql, "source_record", ["source_record_key", "source_asset_id", "source_table_name", "source_row_key", "source_row_sha256", "transform_state", "payload"],
                       [f"'{source_record_key}'", f"(SELECT id FROM source_asset WHERE asset_key='{snapshot_asset_key}')", f"'{table}'", sql_text(source_row_key), f"'{row_hash}'", "'INDEXED'", sql_raw_json(raw_json)])

        event_counts = {"review_events": 0, "operational_audit_events": 0, "null_historical_actors": 0}
        for table in selected:
            for source_row_key, raw_json, row in rows_by_table[table]:
                source_record_key = record_index[(table, source_row_key)][0]
                actor = find_column(row, ("actor_user_id", "reviewed_by", "actor_id", "reviewer_id", "user_id"))
                actor_ref = None if actor is None else f"source-user:{actor}"
                happened_at = event_datetime(find_column(row, ("created_at", "reviewed_at", "occurred_at", "event_at", "event_time")))
                if table in REVIEW_TABLES:
                    event_key = stable_key("review-event", snapshot_sha, table, source_row_key, sha256(raw_json.encode("utf-8")))
                    entity_type = str(find_column(row, ("entity_type", "target_type", "subject_type")) or table)
                    entity_id = find_column(row, ("entity_id", "candidate_id", "target_id", "subject_id", "plant_id"))
                    entity_key = f"snapshot:{table}:{entity_id}" if entity_id is not None else f"snapshot:{table}:{source_row_key}"
                    action = str(find_column(row, ("action", "decision", "event_type", "review_action")) or "HISTORICAL_REVIEW_EVENT")
                    prior_state = find_column(row, ("prior_state", "previous_status", "old_status", "from_status"))
                    resulting_state = find_column(row, ("resulting_state", "new_status", "to_status", "status"))
                    note = find_column(row, ("reason", "review_reason", "note", "comment"))
                    insert(sql, "review_event", ["event_key", "research_source_id", "entity_type", "entity_key", "action", "prior_state", "resulting_state", "actor_user_id", "historical_actor_ref", "happened_at", "note", "raw_payload"],
                           [f"'{event_key}'", f"(SELECT id FROM research_source WHERE source_key='{snapshot_source_key}')", sql_text(entity_type), sql_text(entity_key), sql_text(action), sql_text(prior_state), sql_text(resulting_state), "NULL", sql_text(actor_ref), sql_text(happened_at), sql_text(note), sql_raw_json(raw_json)])
                    event_counts["review_events"] += 1
                    if actor is None: event_counts["null_historical_actors"] += 1
                elif table in AUDIT_TABLES:
                    event_key = stable_key("operational-audit", snapshot_sha, table, source_row_key, sha256(raw_json.encode("utf-8")))
                    action = str(find_column(row, ("action", "event_type")) or "HISTORICAL_AUDIT_EVENT")
                    insert(sql, "operational_audit_event", ["event_key", "source_asset_id", "source_record_key", "action", "actor_user_id", "historical_actor_ref", "happened_at", "payload"],
                           [f"'{event_key}'", f"(SELECT id FROM source_asset WHERE asset_key='{snapshot_asset_key}')", f"'{source_record_key}'", sql_text(action), "NULL", sql_text(actor_ref), sql_text(happened_at), sql_raw_json(raw_json)])
                    event_counts["operational_audit_events"] += 1
                    if actor is None: event_counts["null_historical_actors"] += 1
        sql.append("COMMIT;")
        sql_content = "\n".join(sql) + "\n"
        (run_dir / "snapshot-records.jsonl").write_text(
            "".join(json.dumps({"table": table, "source_row_key": row_key, "source_row_sha256": sha256(raw_json.encode("utf-8")), "payload": json.loads(raw_json)}, ensure_ascii=False, separators=(",", ":")) + "\n"
                    for table in selected for row_key, raw_json, _ in rows_by_table[table]),
            encoding="utf-8")
        (run_dir / "import.sql").write_text(sql_content, encoding="utf-8")
        report = {
            "run_id": run_id,
            "mode": "APPLY" if args.apply else "DRY_RUN",
            "snapshot": {"path": SNAPSHOT_RELATIVE, "sha256": snapshot_sha, "bytes": snapshot.stat().st_size},
            "source_schema": DATABASE,
            "selected_tables": selected,
            "excluded_tables": sorted(EXCLUDED_TABLES.intersection(table_names)),
            "source_rows_by_table": source_counts,
            "source_record_count": sum(source_counts.values()),
            "event_counts": event_counts,
            "target_apply": None,
            "rules": [
                "Historical source primary keys appear only as source-row provenance; VIRIDEXA table ids are newly generated.",
                "Snapshot facts remain in a source-record ledger until an evidence-aware extractor normalizes them.",
                "Historical scientific review events and operational audit events are stored in separate ledgers.",
                "Historical actor ids are references only; actor_user_id remains NULL unless a future named-user mapping is explicitly approved.",
                "No update, delete, DDL, or application request is sent to the snapshot source.",
                "Authentication telemetry and user activity tables are not imported into the scientific target.",
            ],
        }
        if args.apply:
            command = ["docker", "compose", "exec", "-T", "db", "sh", "-c",
                       'export MYSQL_PWD="$MYSQL_IMPORTER_PASSWORD"; exec mysql --protocol=tcp -h127.0.0.1 -uviridexa_importer viridexa']
            result = subprocess.run(command, input=sql_content, text=True, capture_output=True, cwd=ROOT)
            if result.returncode != 0:
                (run_dir / "apply.stderr.txt").write_text(result.stderr, encoding="utf-8")
                raise RuntimeError(f"VIRIDEXA snapshot ledger insert failed; see protected run folder {run_dir}")
            query = ("SELECT (SELECT COUNT(*) FROM source_record sr JOIN source_asset sa ON sa.id=sr.source_asset_id WHERE sa.asset_key='" + snapshot_asset_key + "'), "
                     "(SELECT COUNT(*) FROM review_event re JOIN research_source rs ON rs.id=re.research_source_id WHERE rs.source_key='" + snapshot_source_key + "'), "
                     "(SELECT COUNT(*) FROM operational_audit_event oa JOIN source_asset sa ON sa.id=oa.source_asset_id WHERE sa.asset_key='" + snapshot_asset_key + "')")
            count_command = ["docker", "compose", "exec", "-T", "db", "sh", "-c",
                             'export MYSQL_PWD="$MYSQL_IMPORTER_PASSWORD"; exec mysql --protocol=tcp -h127.0.0.1 -uviridexa_importer -Nse "' + query + '" viridexa']
            counts = subprocess.check_output(count_command, text=True, cwd=ROOT).strip().split()
            report["target_apply"] = {"status": "APPLIED", "source_records": int(counts[0]), "review_events": int(counts[1]), "operational_audit_events": int(counts[2])}
        (run_dir / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"mode={report['mode']} run_id={run_id} source_rows={report['source_record_count']}")
        print("source_tables=" + json.dumps(source_counts, ensure_ascii=False, sort_keys=True))
        print("events=" + json.dumps(event_counts, ensure_ascii=False, sort_keys=True))
        if report["target_apply"]:
            print("target=" + json.dumps(report["target_apply"], ensure_ascii=False, sort_keys=True))
        print(f"report={run_dir / 'report.json'}")
        return 0
    finally:
        subprocess.run(["docker", "compose", "--profile", "ingestion-source", "stop", "snapshot-source"], cwd=ROOT,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, json.JSONDecodeError, KeyError, TypeError, ValueError, RuntimeError, subprocess.CalledProcessError) as exc:
        print(f"snapshot pipeline failed: {exc}", file=sys.stderr)
        raise SystemExit(2)
