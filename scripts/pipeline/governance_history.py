#!/usr/bin/env python3
"""Conservatively map immutable legacy source_record rows to science candidates.

Dry-run is the default. Apply only inserts into the Phase 2B historical tables;
existing rows are compared and never updated.
"""
import argparse
import base64
import collections
from decimal import Decimal, InvalidOperation
import hashlib
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from run_science import mysql, fingerprint
from scientific import canonical, digest

REPO = Path(__file__).resolve().parents[2]
CLASSES = {
    "NORMALIZABLE_SOURCE_RELATION", "SOURCE_KNOWN_FACT_UNLINKED",
    "INCOMPLETE_SOURCE", "DUPLICATE_OR_REDUNDANT", "UNRECOGNIZED_OR_CONFLICT",
}


def json_value(value):
    return json.loads(value) if value else None


def fetch_records():
    q = ("SELECT r.source_record_key,r.source_table_name,r.source_row_key,r.source_row_sha256,"
         "a.relative_path,a.sha256,REPLACE(TO_BASE64(CAST(r.payload AS CHAR)),CHAR(10),'') FROM source_record r "
         "JOIN source_asset a ON a.id=r.source_asset_id ORDER BY r.id")
    result = []
    for line in mysql(q).splitlines():
        key, table, row_key, row_hash, path, asset_hash, payload = line.split("\t", 6)
        result.append(dict(source_record_key=key, source_table_name=table,
                           source_row_key=json_value(row_key) if row_key.startswith("{") else row_key,
                           source_row_sha256=row_hash, source_path=path,
                           source_asset_sha256=asset_hash, payload=json_value(base64.b64decode(payload).decode())))
    return result


def fetch_candidates():
    q = ("SELECT c.id,c.source_id,s.path,s.sha256,c.pipeline,CAST(c.locator AS CHAR),"
         "REPLACE(TO_BASE64(CAST(c.raw_payload AS CHAR)),CHAR(10),''),IF(c.normalized_payload IS NULL,'',REPLACE(TO_BASE64(CAST(c.normalized_payload AS CHAR)),CHAR(10),'')),c.content_sha256 "
         "FROM scientific_candidate c JOIN scientific_source_version s ON s.id=c.source_id ORDER BY c.id")
    result = []
    for line in mysql(q).splitlines():
        cid, sid, path, source_hash, pipeline, locator, raw, normalized, content_hash = line.split("\t", 8)
        result.append(dict(id=cid, source_id=sid, source_path=path, source_sha256=source_hash,
                           pipeline=pipeline, locator=json_value(locator),
                           raw=json_value(base64.b64decode(raw).decode()),
                           normalized=json_value(base64.b64decode(normalized).decode()) if normalized else None,
                           content_sha256=content_hash))
    return result


def _primary_source_keys(record):
    # Only keys on the source row itself count. Nested event snapshots can
    # repeat identifiers from unrelated entities and must not create links.
    payload = record["payload"] or {}
    return {str(payload[k]) for k in ("source_key", "source_record_key")
            if isinstance(payload.get(k), (str, int))}


def relation_for(record, candidate):
    payload = record["payload"] or {}
    if record["source_table_name"] == "plant_oil_samples":
        raw, norm = candidate["raw"] or {}, candidate["normalized"] or {}
        same_sample = payload.get("source_key") and payload.get("source_key") in {
            str(raw.get("sample_id", "")), str(raw.get("source_key", "")),
            str(norm.get("sample_id", "")), str(candidate["locator"].get("sample_id", ""))}
        try:
            legacy_value = Decimal(str(payload.get("oil_content_pct")))
            candidate_raw_value = Decimal(str(norm.get("raw_value")))
            candidate_value = Decimal(str(norm.get("value")))
            numeric_match = legacy_value.is_finite() and legacy_value == candidate_raw_value == candidate_value
        except (InvalidOperation, TypeError, ValueError):
            numeric_match = False
        value_exact = (same_sample and candidate["pipeline"] == "LIPID" and
                       norm.get("kind") == "OIL_CONTENT" and norm.get("unit") == "%" and numeric_match)
        return "OIL_CONTENT_VALUE_EXACT" if value_exact else "SAME_SAMPLE_SOURCE_EVIDENCE"
    return "EXACT_SOURCE_KEY_EVIDENCE"


def map_records(records, candidates):
    """Return auditable mapping rows and links; only exact source keys link."""
    key_index = collections.defaultdict(list)
    for c in candidates:
        for obj in (c["raw"], c["normalized"], c["locator"]):
            if not isinstance(obj, dict):
                continue
            for k in ("source_key", "source_record_key"):
                v = obj.get(k)
                if isinstance(v, (str, int)):
                    key_index[str(v)].append(c)
    hash_counts = collections.Counter((r["source_path"], r["source_table_name"], r["source_row_sha256"]) for r in records)
    mappings, links = [], []
    for r in records:
        payload = r["payload"] if isinstance(r["payload"], dict) else {}
        primary = _primary_source_keys(r)
        exact_source_hits = {c["id"]: c for value in primary for c in key_index.get(value, [])}
        hits = exact_source_hits
        # Historical actor references are deliberately not identity assertions.
        if hash_counts[(r["source_path"], r["source_table_name"], r["source_row_sha256"])] > 1:
            cls, reason = "DUPLICATE_OR_REDUNDANT", "Identical source row hash repeats within the same asset/table; no new fact asserted."
        elif not payload or not r["source_row_sha256"] or not r["source_path"]:
            cls, reason = "INCOMPLETE_SOURCE", "Source row lacks payload, row hash, or source asset path."
        elif exact_source_hits:
            cls, reason = "NORMALIZABLE_SOURCE_RELATION", "Exact source key appears in scientific candidate raw/normalized payload or locator; multiple analyte candidates may share a sample source key."
        elif len(hits) > 1:
            cls, reason = "NORMALIZABLE_SOURCE_RELATION", "Exact source row key matches several candidate records; links preserve source evidence only, with each relation qualified."
        elif r["source_table_name"] in {"published_scientific_fact", "plant_trait_values", "plant_oil_samples", "plant_knowledge_entries"}:
            cls, reason = "SOURCE_KNOWN_FACT_UNLINKED", "Legacy row contains a known scientific fact but no exact candidate source key was found."
        else:
            cls, reason = "UNRECOGNIZED_OR_CONFLICT", "No exact candidate source key or conservative source-record mapping rule matched."
        def link_relation(candidate):
            return relation_for(r, candidate)
        evidence = {
            "source_path": r["source_path"], "source_asset_sha256": r["source_asset_sha256"],
            "source_table_name": r["source_table_name"], "source_row_key": r["source_row_key"],
            "source_row_sha256": r["source_row_sha256"],
            "candidate_matches": [{"id": c["id"], "pipeline": c["pipeline"], "relation": link_relation(c),
                                   "source_path": c["source_path"], "source_sha256": c["source_sha256"],
                                   "locator": c["locator"], "content_sha256": c["content_sha256"]}
                                  for c in sorted(hits.values(), key=lambda x: x["id"])],
        }
        body = dict(source_record_key=r["source_record_key"], classification=cls, reason=reason,
                    mapping_payload=evidence)
        body["content_sha256"] = digest(canonical(body))
        mappings.append(body)
        for c in hits.values():
            relation=link_relation(c)
            links.append(dict(id=digest(r["source_record_key"] + ":" + c["id"] + ":" + relation),
                              source_record_key=r["source_record_key"], scientific_candidate_id=c["id"],
                              relation=relation))
    return mappings, links


def insert_sql(table, columns, values):
    def quote(v):
        if v is None: return "NULL"
        if isinstance(v, (int, float)): return str(v)
        return "CONVERT(UNHEX('" + str(v).encode("utf-8").hex() + "') USING utf8mb4)"
    col_sql = ",".join(columns)
    val_sql = ",".join(map(quote, values))
    return ("INSERT INTO " + table + " (" + col_sql + ") SELECT " + val_sql +
            " WHERE NOT EXISTS (SELECT 1 FROM " + table + " WHERE " + columns[0] + "=" + quote(values[0]) + ");")


def apply_rows(mappings, links):
    # Insert-only and idempotent: exact primary-key replays select no row;
    # other constraint failures abort the transaction and close/roll it back.
    statements = ["START TRANSACTION;"]
    for m in mappings:
        statements.append(insert_sql("historical_mapping", ["source_record_key","classification","reason","mapping_payload","content_sha256"],
                                     [m["source_record_key"],m["classification"],m["reason"],canonical(m["mapping_payload"]),m["content_sha256"]]))
    for l in links:
        statements.append(insert_sql("historical_evidence_link", ["id","source_record_key","scientific_candidate_id","relation"],
                                     [l["id"],l["source_record_key"],l["scientific_candidate_id"],l["relation"]]))
    statements.append("COMMIT;")
    try:
        mysql("\n".join(statements))
    except Exception as exc:
        raise RuntimeError("historical mapping batch failed; transaction was rolled back by connection close: " + str(exc)) from exc
    found = {}
    for start in range(0, len(mappings), 500):
        subset = mappings[start:start+500]
        ids = ",".join("'"+m["source_record_key"]+"'" for m in subset)
        for line in mysql("SELECT source_record_key,content_sha256 FROM historical_mapping WHERE source_record_key IN ("+ids+");").splitlines():
            k, v = line.split("\t",1); found[k] = v
    for m in mappings:
        if found.get(m["source_record_key"]) != m["content_sha256"]:
            raise RuntimeError("Existing historical_mapping conflicts with dry-run content: " + m["source_record_key"])
    found_links = {}
    for start in range(0, len(links), 500):
        subset = links[start:start+500]
        ids = ",".join("'"+l["id"]+"'" for l in subset)
        q = "SELECT id,source_record_key,scientific_candidate_id,relation FROM historical_evidence_link WHERE id IN ("+ids+");"
        for line in mysql(q).splitlines():
            vals = line.split("\t"); found_links[vals[0]] = tuple(vals[1:])
    for l in links:
        if found_links.get(l["id"]) != (l["source_record_key"],l["scientific_candidate_id"],l["relation"]):
            raise RuntimeError("Existing historical_evidence_link conflicts with dry-run content: " + l["id"])


def build_trait_candidates(records, manifest=REPO / "docs/ASSET_MANIFEST.md",
                           protected_root=REPO):
    """Prepare six deterministic pending candidates from exact legacy rows/events."""
    records_by_table_id = collections.defaultdict(dict)
    for r in records:
        payload = r["payload"] or {}
        if isinstance(payload.get("id"), int):
            records_by_table_id[r["source_table_name"]][payload["id"]] = r
    expected = None
    logical = "data/" + "backups/viridexa-live-snapshot-20261024.sql.gz"
    for line in manifest.read_text().splitlines():
        cells = [c.strip() for c in line.split("|")]
        if len(cells) > 4 and cells[1] == logical:
            expected = cells[4]
            break
    disk_path = protected_root / logical
    if not expected or not disk_path.is_file():
        raise RuntimeError("Snapshot asset missing from manifest or protected root")
    actual = hashlib.sha256(disk_path.read_bytes()).hexdigest()
    if actual != expected:
        raise RuntimeError("Snapshot asset hash differs from ASSET_MANIFEST.md")
    asset_path = "backups/viridexa-live-snapshot-20261024.sql.gz"
    trait_labels = {r["payload"]["id"]: r["payload"] for r in records
                    if r["source_table_name"] == "plant_traits"}
    option_labels = {r["payload"]["id"]: r["payload"] for r in records
                     if r["source_table_name"] == "trait_options"}
    result = []
    target_entities = {229,230,231,232,233,235}
    for event in records:
        ep = event["payload"] or {}
        after = ep.get("after_data") or {}
        if (event["source_table_name"] != "knowledge_review_events" or ep.get("action") != "UPDATE" or
                ep.get("entity_id") not in target_entities or after.get("verification_status") != "CONFIRMED"):
            continue
        entity_id = ep["entity_id"]
        trait_record = records_by_table_id["plant_trait_values"].get(entity_id)
        if not trait_record: continue
        tr = trait_record["payload"]
        plant_record = records_by_table_id["plants"].get(tr.get("plant_id"))
        trait = trait_labels.get(tr.get("trait_id"), {})
        option = option_labels.get(tr.get("option_id"), {})
        source_value = option.get("label_zh")
        trait_code = trait.get("code")
        # Parse only the exact controlled vocabulary code recorded in the
        # snapshot. The species identity is carried as unverified raw context.
        norm = None
        reason = "Legacy controlled trait code/value has no approved lexical mapping."
        if trait_code and option.get("code") and option.get("trait_id") == tr.get("trait_id") and source_value:
            norm = {"trait": trait_code, "value": option["code"],
                    "normalization_rule": "legacy-controlled-option-code-v1",
                    "review_status": "PENDING_HUMAN_REVIEW",
                    "identity_state": "UNVERIFIED"}
            reason = None
        raw = {"legacy_trait_value": tr, "historical_confirmation_event": ep,
               "plant_identity_source": (plant_record["payload"] if plant_record else None),
               "trait_definition": trait, "trait_option": option,
               "historical_mapping_source_record_key": event["source_record_key"],
               "source_record_key": trait_record["source_record_key"],
               "source_record_row_sha256": trait_record["source_row_sha256"],
               "source_native_identity": (plant_record["payload"].get("scientific_name") if plant_record else None),
               "source_excerpt": tr.get("source_excerpt"), "source_page": tr.get("source_page"),
               "evidence_image_reference": tr.get("evidence_image_reference"),
               "historical_review_state": "REVIEW_PENDING_NAMED_REVIEW"}
        locator = {"legacy_trait_value_source_record_key": trait_record["source_record_key"],
                   "review_event_source_record_key": event["source_record_key"],
                   "legacy_trait_value_id": entity_id}
        row = {"pipeline": "MORPHOLOGY", "source_path": asset_path, "locator": locator,
               "raw": raw, "normalized": norm, "reason": reason}
        source_id = digest(asset_path + ":" + expected)
        cid = digest(source_id + ":MORPHOLOGY:" + canonical(locator))
        chash = digest(canonical(row))
        result.append({"id": cid, "source_id": source_id, "source_path": asset_path,
                       "source_sha256": expected, "pipeline": "MORPHOLOGY", "locator": locator,
                       "raw": raw, "normalized": norm, "reason": reason, "content_sha256": chash,
                       "classification": "CANDIDATE" if norm is not None else "HELD"})
    return sorted(result, key=lambda x: x["locator"]["legacy_trait_value_id"])


def apply_trait_candidates(rows):
    statements = ["START TRANSACTION;"]
    sources = {r["source_id"]: (r["source_path"], r["source_sha256"]) for r in rows}
    for sid, (path, sha) in sources.items():
        statements.append(insert_sql("scientific_source_version", ["id","dataset","version_label","path","sha256","license_state","integrity_state"],
                                     [sid,"LEGACY_SNAPSHOT","2026-10-24",path,sha,"UNKNOWN","VERIFIED"]))
    for r in rows:
        statements.append(insert_sql("scientific_candidate", ["id","source_id","pipeline","locator","raw_payload","normalized_payload","content_sha256","normalization_state","reason"],
                                     [r["id"],r["source_id"],r["pipeline"],canonical(r["locator"]),canonical(r["raw"]),
                                      canonical(r["normalized"]) if r["normalized"] is not None else None,
                                      r["content_sha256"],r["classification"],r["reason"]]))
    statements.append("COMMIT;")
    try:
        mysql("\n".join(statements))
    except Exception as exc:
        raise RuntimeError("candidate batch failed; transaction was rolled back by connection close: " + str(exc)) from exc
    source_ids = ",".join("'"+sid+"'" for sid in sources)
    actual_sources = {}
    for line in mysql("SELECT id,path,sha256 FROM scientific_source_version WHERE id IN ("+source_ids+");").splitlines():
        sid,path,sha=line.split("\t",2); actual_sources[sid]=(path,sha)
    for sid, expected in sources.items():
        if actual_sources.get(sid) != expected:
            raise RuntimeError("Existing scientific source conflicts with verified snapshot: "+sid)
    ids = ",".join("'"+r["id"]+"'" for r in rows)
    found = {}
    for line in mysql("SELECT id,content_sha256 FROM scientific_candidate WHERE id IN ("+ids+");").splitlines():
        k,v=line.split("\t",1); found[k]=v
    for r in rows:
        if found.get(r["id"]) != r["content_sha256"]:
            raise RuntimeError("Existing candidate conflicts with deterministic historical candidate: "+r["id"])


def trait_evidence_links(rows):
    links=[]
    for row in rows:
        for key in ("historical_mapping_source_record_key", "source_record_key"):
            source_record_key = row["raw"].get(key)
            if source_record_key:
                relation = "EXACT_SOURCE_RECORD_EVIDENCE"
                links.append({"id":digest(source_record_key+":"+row["id"]+":"+relation),
                              "source_record_key":source_record_key,
                              "scientific_candidate_id":row["id"],"relation":relation})
    return links


def sample_evidence_links(records, candidates):
    """Weaker sample_id links, explicitly separate from source_key evidence."""
    index=collections.defaultdict(dict)
    for c in candidates:
        for obj in (c["raw"],c["normalized"],c["locator"]):
            if isinstance(obj,dict) and obj.get("sample_id") is not None:
                index[str(obj["sample_id"])][c["id"]]=c
    links=[]
    exact_pairs=set()
    source_index=collections.defaultdict(set)
    for c in candidates:
        for obj in (c["raw"],c["normalized"],c["locator"]):
            if isinstance(obj,dict):
                for k in ("source_key","source_record_key"):
                    if obj.get(k) is not None: source_index[str(obj[k])].add(c["id"])
    for r in records:
        p=r["payload"] or {}; sid=p.get("sample_id")
        if sid is None: continue
        key_ids={cid for key in _primary_source_keys(r) for cid in source_index.get(key,set())}
        for c in index.get(str(sid),{}).values():
            if c["id"] in key_ids: continue
            relation="SAME_SAMPLE_SOURCE_EVIDENCE"
            links.append({"id":digest(r["source_record_key"]+":"+c["id"]+":"+relation),
                          "source_record_key":r["source_record_key"],"scientific_candidate_id":c["id"],"relation":relation})
    return links


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--apply", action="store_true", help="Insert mappings and links; never update existing rows")
    ap.add_argument("--output", type=Path, default=REPO / "reports/phase2b/history")
    args = ap.parse_args()
    records, candidates = fetch_records(), fetch_candidates()
    trait_candidates = build_trait_candidates(records)
    # Keep the legacy mapping inventory stable after candidate creation; the
    # exact trait-record and review-event links are appended explicitly below.
    pending_ids = {r["id"] for r in trait_candidates}
    mapping_candidates = [c for c in candidates if c["id"] not in pending_ids]
    mappings, links = map_records(records, mapping_candidates)
    same_sample_links = sample_evidence_links(records,mapping_candidates)
    extra_trait_links = trait_evidence_links(trait_candidates)
    all_links = links + same_sample_links + extra_trait_links
    fp_before = fp_after = fp_replay = None
    if args.apply:
        fp_before = fingerprint()
        apply_rows(mappings, all_links)
        apply_trait_candidates(trait_candidates)
        fp_after = fingerprint()
        # Exact replay proves insert-only idempotence, including against any
        # concurrently added human decisions or source state.
        apply_rows(mappings, all_links)
        apply_trait_candidates(trait_candidates)
        fp_replay = fingerprint()
        if fp_after != fp_replay:
            raise RuntimeError("Replay fingerprint changed; insert-only replay was not idempotent")
        if any(fp_before[k] != fp_after[k] for k in fp_before if k not in ("candidates", "sources")):
            raise RuntimeError("Apply changed a protected science fingerprint outside candidate rows")
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "mappings.jsonl").write_text("".join(canonical(m)+"\n" for m in mappings))
    (args.output / "evidence_links.jsonl").write_text("".join(canonical(l)+"\n" for l in links))
    by_record = {r["source_record_key"]: r for r in records}
    by_map = {m["source_record_key"]: m for m in mappings}
    oil_audit = []
    trait_audit = []
    for r in records:
        p = r["payload"] or {}
        if r["source_table_name"] == "plant_oil_samples" and p.get("oil_content_status") == "VERIFIED":
            m = by_map[r["source_record_key"]]
            cm = m["mapping_payload"].get("candidate_matches", [])
            source_key = p.get("source_key")
            oil_audit.append({"source_record_key": r["source_record_key"], "source_row_sha256": r["source_row_sha256"],
                              "snapshot_asset": r["source_path"], "snapshot_asset_sha256": r["source_asset_sha256"],
                              "source_key": source_key, "legacy_sample_id": p.get("id"),
                              "plant_id": p.get("plant_id"), "source_pdf_page": p.get("source_pdf_page"),
                              "source_printed_page": p.get("source_printed_page"),
                              "oil_content_status": p.get("oil_content_status"),
                              "verification_status": p.get("verification_status"),
                              "oil_content_pct": p.get("oil_content_pct"),
                              "image_check": p.get("source_snippets", {}).get("oil_content_verification"),
                              "classification": m["classification"],
                              "candidate_matches": [x for x in cm if source_key in str(x.get("locator")) or source_key in str(x.get("id"))]})
        event_payload = p
        if r["source_table_name"] == "knowledge_review_events" and p.get("action") in ("UPDATE", "RECLASSIFY_PENDING_NAMED_REVIEW") and str(p.get("entity_id")) in {"229","230","231","232","233","235"} and ((p.get("after_data") or {}).get("verification_status") in ("CONFIRMED", "REVIEW_PENDING")):
            trait_audit.append({"source_record_key": r["source_record_key"], "source_row_sha256": r["source_row_sha256"],
                                "snapshot_asset": r["source_path"], "source_row_key": r["source_row_key"],
                                "entity_id": event_payload.get("entity_id"), "action": p.get("action"),
                                "actor_user_id": p.get("actor_user_id"), "historical_actor_ref": p.get("historical_actor_ref"),
                                "happened_at": p.get("happened_at"), "review_note": p.get("note"),
                                "event_payload": event_payload, "classification": by_map[r["source_record_key"]]["classification"],
                                "candidate_matches": by_map[r["source_record_key"]]["mapping_payload"].get("candidate_matches", [])})
    (args.output / "oil_88.jsonl").write_text("".join(canonical(x)+"\n" for x in oil_audit))
    (args.output / "trait_confirmed_6_history.jsonl").write_text("".join(canonical(x)+"\n" for x in trait_audit))
    (args.output / "trait_pending_candidates.jsonl").write_text("".join(canonical(x)+"\n" for x in trait_candidates))
    (args.output / "trait_candidate_evidence_links.jsonl").write_text("".join(canonical(x)+"\n" for x in extra_trait_links))
    (args.output / "same_sample_evidence_links.jsonl").write_text("".join(canonical(x)+"\n" for x in same_sample_links))
    stats = collections.Counter(m["classification"] for m in mappings)
    report = dict(mode="applied" if args.apply else "dry-run", source_records=len(records),
                  candidate_count=len(candidates), classifications={name: stats[name] for name in sorted(CLASSES)}, evidence_links=len(all_links),
                  exact_source_key_evidence_links=sum(1 for l in all_links if l["relation"] == "EXACT_SOURCE_KEY_EVIDENCE"),
                  source_checked_oil_records=len(oil_audit), historical_confirmed_trait_events=len(trait_audit),
                  oil_content_value_exact_links=sum(1 for l in all_links if l["relation"] == "OIL_CONTENT_VALUE_EXACT"),
                  same_sample_source_evidence_links=sum(1 for l in all_links if l["relation"] == "SAME_SAMPLE_SOURCE_EVIDENCE"),
                  prepared_trait_candidates=len(trait_candidates),
                  prepared_trait_normalized=sum(1 for c in trait_candidates if c["normalized"] is not None),
                  prepared_trait_held=sum(1 for c in trait_candidates if c["normalized"] is None),
                  trait_candidate_evidence_links=len(extra_trait_links),
                  fingerprints={"before": fp_before, "after": fp_after, "replay": fp_replay,
                                "replay_unchanged": fp_after == fp_replay if fp_after else None,
                                "non_candidate_state_unchanged": all(fp_before[k] == fp_after[k] for k in fp_before if k not in ("candidates", "sources")) if fp_after else None},
                  actor_identity="No named identity is inferred; historical anonymous reviews remain pending named review.")
    (args.output / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2)+"\n")
    print(canonical(report))

if __name__ == "__main__":
    main()
