#!/usr/bin/env python3
"""Deterministically stage the course-PDF extraction into the VIRIDEXA v1 model.

The default mode is a dry run. Raw inputs are read only. Reports and generated SQL are
written under the external protected data directory's processed/ folder.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import mimetypes
import os
from pathlib import Path
import re
import subprocess
import sys
from decimal import Decimal, InvalidOperation

ROOT = Path(__file__).resolve().parents[2]
COURSE_PDF = "raw/course/课程设计资料.pdf"
PLANTS_JSON = "plants.json"
SAMPLES_JSON = "oil_samples.json"
TRAITS = {
    "morphology": "形态描述",
    "distribution": "分布",
    "cultivation": "栽培",
    "uses": "用途",
}


def data_root() -> Path:
    configured = os.environ.get("VIRIDEXA_DATA_DIR")
    if configured:
        path = Path(configured)
        return (ROOT / path).resolve() if not path.is_absolute() else path.resolve()
    env_file = ROOT / ".env"
    if env_file.exists():
        for line in env_file.read_text().splitlines():
            if line.startswith("VIRIDEXA_DATA_DIR="):
                value = line.split("=", 1)[1].strip()
                path = Path(value)
                return (ROOT / path).resolve() if not path.is_absolute() else path.resolve()
    return (ROOT.parent / "VIRIDEXA-protected" / "data").resolve()


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def file_hash(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def canonical(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False)


def stable_key(*parts: object) -> str:
    return sha256("\x1f".join(str(part) for part in parts).encode("utf-8"))


def sql_text(value: object | None) -> str:
    if value is None:
        return "NULL"
    encoded = str(value).encode("utf-8").hex()
    return f"CONVERT(UNHEX('{encoded}') USING utf8mb4)"


def sql_json(value: object | None) -> str:
    if value is None:
        return "NULL"
    return f"CAST({sql_text(canonical(value))} AS JSON)"


def sql_number(value: object | None) -> str:
    if value is None or value == "":
        return "NULL"
    try:
        number = Decimal(str(value))
    except InvalidOperation as exc:
        raise ValueError(f"Not a decimal number: {value!r}") from exc
    if not number.is_finite():
        raise ValueError(f"Not a finite decimal number: {value!r}")
    return format(number, "f")


def add_insert(lines: list[str], table: str, columns: list[str], values: list[str]) -> None:
    lines.append(
        f"INSERT IGNORE INTO {table} ({', '.join(columns)}) VALUES ({', '.join(values)});"
    )


def asset_kind(path: Path) -> str:
    suffix = path.suffix.lower()
    if suffix in {".pdf"}: return "PUBLICATION_OR_DOCUMENT"
    if suffix in {".zip"}: return "DATASET_ARCHIVE"
    if suffix in {".xlsx", ".xls"}: return "SPREADSHEET"
    if suffix in {".csv", ".tsv"}: return "TABULAR_DATA"
    if suffix in {".json", ".jsonl"}: return "STRUCTURED_DATA"
    if suffix in {".sql", ".gz"}: return "DATABASE_SNAPSHOT"
    if suffix in {".jpg", ".jpeg", ".png", ".webp", ".tif", ".tiff"}: return "IMAGE_EVIDENCE"
    if suffix in {".txt", ".md"}: return "TEXT_DOCUMENT"
    return "OTHER"


def media_type(path: Path) -> str | None:
    guessed, _ = mimetypes.guess_type(path.name)
    return guessed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", help="validate and write a report; this is the default")
    mode.add_argument("--apply", action="store_true", help="insert the prepared slice into the local VIRIDEXA Compose database")
    parser.add_argument("--data-dir", type=Path, help="external protected input directory")
    args = parser.parse_args()

    data = (args.data_dir or data_root()).resolve()
    pdf_path = data / COURSE_PDF
    plants_path = data / PLANTS_JSON
    samples_path = data / SAMPLES_JSON
    for required in (pdf_path, plants_path, samples_path):
        if not required.is_file():
            raise SystemExit(f"Required input is missing: {required}")

    pdf_hash = file_hash(pdf_path)
    plants_hash = file_hash(plants_path)
    samples_hash = file_hash(samples_path)
    plants_document = json.loads(plants_path.read_text(encoding="utf-8"))
    samples_document = json.loads(samples_path.read_text(encoding="utf-8"))
    if plants_document.get("source_pdf_sha256") != pdf_hash:
        raise SystemExit("plants.json source PDF SHA-256 does not match the protected course PDF")
    if samples_document.get("source_pdf_sha256") != pdf_hash:
        raise SystemExit("oil_samples.json source PDF SHA-256 does not match the protected course PDF")

    plants = plants_document.get("plants")
    samples = samples_document.get("samples")
    if not isinstance(plants, list) or not isinstance(samples, list):
        raise SystemExit("Expected plants[] and samples[] arrays in extracted source files")
    if any(not row.get("source_key") or not row.get("scientific_name") for row in plants):
        raise SystemExit("Every plant row must have a source key and scientific name")
    if len({row["source_key"] for row in plants}) != len(plants):
        raise SystemExit("Duplicate plant source keys found in plants.json")
    if any(not row.get("source_key") for row in samples):
        raise SystemExit("Every sample row must have a source key")
    if len({row["source_key"] for row in samples}) != len(samples):
        raise SystemExit("Duplicate sample source keys found in oil_samples.json")

    plant_keys = {row["source_key"] for row in plants}
    linked_samples = [row for row in samples if row.get("plant_id") in plant_keys]
    unlinked_samples = [row for row in samples if row.get("plant_id") not in plant_keys]
    source_checked_oil = [row for row in samples if (row.get("oil_content_verification") or {}).get("status") == "VERIFIED_FROM_SOURCE_IMAGE"]
    missing_oil = [row for row in samples if (row.get("oil_content_verification") or {}).get("status") == "MISSING_OR_UNREADABLE"]
    invalid_oil = []
    for row in source_checked_oil:
        value = row.get("oil_content_pct")
        number = Decimal(str(value))
        if not number.is_finite() or number < 0 or number > 100:
            invalid_oil.append(row["source_key"])
    if invalid_oil:
        raise SystemExit(f"Oil percentages outside [0, 100] for {len(invalid_oil)} source rows")

    run_id = dt.datetime.now().strftime("%Y%m%dT%H%M%S%f") + "-" + stable_key(pdf_hash, plants_hash, samples_hash)[:10]
    run_dir = data / "processed" / "pipeline-runs" / run_id
    run_dir.mkdir(parents=True, exist_ok=False)

    sql: list[str] = ["SET NAMES utf8mb4;", "START TRANSACTION;"]
    assets: list[dict[str, object]] = []
    for path in sorted(data.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(data).as_posix()
        if relative.startswith(("processed/", "migration/")) or path.name == ".DS_Store":
            continue
        digest = file_hash(path)
        key = stable_key("asset", relative, digest)
        row = {"key": key, "path": relative, "sha256": digest, "size": path.stat().st_size, "type": media_type(path), "kind": asset_kind(path)}
        assets.append(row)
        add_insert(sql, "source_asset",
                   ["asset_key", "relative_path", "sha256", "size_bytes", "media_type", "asset_kind", "ingest_state", "metadata"],
                   [sql_text(key), sql_text(relative), sql_text(digest), str(path.stat().st_size), sql_text(row["type"]), sql_text(row["kind"]), sql_text("INDEXED"), sql_json({"indexed_by": "viridexa-course-extract-pipeline", "path_root": "VIRIDEXA_DATA_DIR"})])

    pdf_asset_key = stable_key("asset", COURSE_PDF, pdf_hash)
    plants_asset_key = stable_key("asset", PLANTS_JSON, plants_hash)
    samples_asset_key = stable_key("asset", SAMPLES_JSON, samples_hash)
    research_source_key = stable_key("course-document", pdf_hash)
    add_insert(sql, "research_source",
               ["source_key", "source_asset_id", "source_type", "title", "citation", "license_note", "metadata"],
               [sql_text(research_source_key), f"(SELECT id FROM source_asset WHERE asset_key={sql_text(pdf_asset_key)})", sql_text("COURSE_DOCUMENT"), sql_text("课程设计资料"), sql_text("课程设计资料.pdf；植物与油样记录引用原始 PDF 页码及页面区域。"), sql_text("课程资料；部署前应按课程授权范围展示。"), sql_json({"file_sha256": pdf_hash, "source_pdf": COURSE_PDF})])

    evidence_rows: dict[str, dict[str, object]] = {}
    def add_evidence(source_key: str, page: int | None, printed_page: object | None, text: str | None, bbox: object | None, state: str, image_path: str | None = None) -> str:
        locator = {"pdf_page": page, "printed_page": printed_page, "bbox_normalized": bbox, "source_key": source_key}
        evidence_key = stable_key("evidence", pdf_hash, canonical(locator), text or "")
        if evidence_key in evidence_rows:
            return evidence_key
        image_hash = None
        if image_path:
            image_file = data / image_path
            if image_file.is_file():
                image_hash = file_hash(image_file)
        evidence_rows[evidence_key] = {"locator": locator, "excerpt": text, "state": state, "image_path": image_path, "image_hash": image_hash}
        add_insert(sql, "evidence",
                   ["evidence_key", "source_asset_id", "locator_type", "locator", "raw_excerpt", "evidence_image_path", "evidence_image_sha256", "evidence_state"],
                   [sql_text(evidence_key), f"(SELECT id FROM source_asset WHERE asset_key={sql_text(pdf_asset_key)})", sql_text("PDF_PAGE_BBOX" if bbox else "PDF_PAGE"), sql_json(locator), sql_text(text), sql_text(image_path), sql_text(image_hash), sql_text(state)])
        return evidence_key

    taxon_specs: dict[str, dict[str, object]] = {}
    plant_keys_by_source: dict[str, str] = {}
    plant_evidence: dict[str, str] = {}
    for row in plants:
        source_key = row["source_key"]
        page_values = row.get("source_pdf_pages") or []
        page = page_values[0] if page_values else None
        printed_values = row.get("source_printed_pages") or []
        printed = printed_values[0] if printed_values else None
        verify = row.get("taxonomy_verification") or {}
        identity_state = verify.get("scientific_name") or row.get("scientific_name_status") or "NEEDS_REVIEW"
        excerpt = " · ".join(str(value) for value in [row.get("name_zh"), row.get("scientific_name"), row.get("family_latin"), row.get("genus_latin")] if value)
        base_evidence = add_evidence(source_key, page, printed, excerpt, None,
                                     "SOURCE_CHECKED" if identity_state == "VERIFIED_FROM_PRINTED_HEADING" else "EXTRACTED",
                                     f"source_pages/page-{int(page):02d}.jpg" if page else None)
        plant_evidence[source_key] = base_evidence

        family = row.get("family_latin")
        genus = row.get("genus_latin")
        species = row.get("scientific_name")
        family_key = stable_key("taxon", pdf_hash, "FAMILY", family) if family else None
        genus_key = stable_key("taxon", pdf_hash, "GENUS", genus) if genus else None
        species_key = stable_key("taxon", pdf_hash, "SPECIES", species, genus_key or "")
        if family:
            taxon_specs[family_key] = {"rank": "FAMILY", "name": family, "parent": None, "state": verify.get("family_latin", "NEEDS_REVIEW"), "evidence": base_evidence}
        if genus:
            taxon_specs[genus_key] = {"rank": "GENUS", "name": genus, "parent": family_key, "state": verify.get("genus_latin", "NEEDS_REVIEW"), "evidence": base_evidence}
        taxon_specs[species_key] = {"rank": "SPECIES", "name": species, "parent": genus_key, "state": identity_state, "evidence": base_evidence}
        record_key = stable_key("plant", pdf_hash, source_key)
        plant_keys_by_source[source_key] = record_key

    for key, taxon in sorted(taxon_specs.items(), key=lambda item: {"FAMILY": 0, "GENUS": 1, "SPECIES": 2}[str(item[1]["rank"])]):
        if taxon["parent"] and taxon["parent"] not in taxon_specs:
            raise SystemExit(f"Taxon parent key missing from import set: {taxon['parent']}")
        sql.append("SET @taxon_parent_id = NULL;")
        if taxon["parent"]:
            sql.append(f"SELECT id INTO @taxon_parent_id FROM taxon WHERE taxon_key={sql_text(taxon['parent'])};")
        parent_expr = "@taxon_parent_id"
        add_insert(sql, "taxon", ["taxon_key", "parent_id", "taxon_rank", "canonical_name", "identity_state", "evidence_id"],
                   [sql_text(key), parent_expr, sql_text(taxon["rank"]), sql_text(taxon["name"]), sql_text(taxon["state"]), f"(SELECT id FROM evidence WHERE evidence_key={sql_text(taxon['evidence'])})"])

    for row in plants:
        source_key = row["source_key"]
        verify = row.get("taxonomy_verification") or {}
        evidence_key = plant_evidence[source_key]
        family_key = stable_key("taxon", pdf_hash, "FAMILY", row["family_latin"]) if row.get("family_latin") else None
        genus_key = stable_key("taxon", pdf_hash, "GENUS", row["genus_latin"]) if row.get("genus_latin") else None
        species_key = stable_key("taxon", pdf_hash, "SPECIES", row["scientific_name"], genus_key or "")
        taxon_lookup = lambda key: "NULL" if not key else f"(SELECT id FROM taxon WHERE taxon_key={sql_text(key)})"
        for rank, taxon_key, label, state_key in [
            ("FAMILY", family_key, row.get("family_zh"), "family_zh"),
            ("GENUS", genus_key, row.get("genus_zh"), "genus_zh"),
        ]:
            if taxon_key and label:
                name_key = stable_key("taxon-name", taxon_key, "zh", label)
                add_insert(sql, "taxon_name", ["name_key", "taxon_id", "name_role", "language_tag", "name_text", "review_state", "evidence_id"],
                           [sql_text(name_key), taxon_lookup(taxon_key), sql_text("VERNACULAR"), sql_text("zh"), sql_text(label), sql_text(verify.get(state_key, "NEEDS_REVIEW")), f"(SELECT id FROM evidence WHERE evidence_key={sql_text(evidence_key)})"])

        record_key = plant_keys_by_source[source_key]
        add_insert(sql, "plant", ["record_key", "source_record_key", "taxon_id", "review_state", "visibility_state"],
                   [sql_text(record_key), sql_text(source_key), taxon_lookup(species_key), sql_text("REVIEW_PENDING"), sql_text("RESEARCH_PREVIEW")])

        name_specs = [
            ("COMMON", "zh", row.get("name_zh"), verify.get("name_zh", "NEEDS_REVIEW")),
            ("SCIENTIFIC", "la", row.get("scientific_name"), verify.get("scientific_name", "NEEDS_REVIEW")),
            ("ENGLISH", "en", row.get("name_en"), verify.get("name_en", "NEEDS_REVIEW")),
        ]
        for alias in row.get("aliases") or []:
            name_specs.append(("ALIAS", alias.get("language", "zh"), alias.get("value"), alias.get("source_status", "NEEDS_REVIEW")))
        for role, language, value, status in name_specs:
            if not value:
                continue
            name_key = stable_key("plant-name", record_key, role, language, value)
            add_insert(sql, "plant_name", ["name_key", "plant_id", "name_role", "language_tag", "name_text", "review_state", "evidence_id"],
                       [sql_text(name_key), f"(SELECT id FROM plant WHERE record_key={sql_text(record_key)})", sql_text(role), sql_text(language), sql_text(value), sql_text(status), f"(SELECT id FROM evidence WHERE evidence_key={sql_text(evidence_key)})"])

    for key, label in TRAITS.items():
        add_insert(sql, "trait_definition", ["trait_key", "label", "value_kind", "canonical_unit", "definition", "vocabulary_version"],
                   [sql_text(key), sql_text(label), sql_text("TEXT"), "NULL", sql_text(None), sql_text("course-extract-1")])

    for row in plants:
        source_key = row["source_key"]
        record_key = plant_keys_by_source[source_key]
        for trait_key, field in (row.get("fields") or {}).items():
            if trait_key not in TRAITS or not isinstance(field, dict) or not field.get("value"):
                continue
            snippets = field.get("raw_snippets") or []
            snippet = snippets[0] if snippets else {}
            page = snippet.get("pdf_page") or (row.get("source_pdf_pages") or [None])[0]
            printed_list = row.get("source_printed_pages") or []
            printed = printed_list[0] if printed_list else None
            evidence_key = add_evidence(source_key, page, printed, snippet.get("text") or field["value"], snippet.get("bbox_normalized"), "EXTRACTED",
                                        f"source_pages/page-{int(page):02d}.jpg" if page else None)
            observation_key = stable_key("trait-observation", record_key, trait_key, field["value"], evidence_key)
            add_insert(sql, "trait_observation", ["observation_key", "plant_id", "trait_definition_id", "raw_value", "normalized_text", "normalized_number", "unit", "qualifier", "review_state", "evidence_id"],
                       [sql_text(observation_key), f"(SELECT id FROM plant WHERE record_key={sql_text(record_key)})", f"(SELECT id FROM trait_definition WHERE trait_key={sql_text(trait_key)})", sql_text(field["value"]), "NULL", "NULL", "NULL", "NULL", sql_text("EXTRACTED"), f"(SELECT id FROM evidence WHERE evidence_key={sql_text(evidence_key)})"])

    study_key = stable_key("study", research_source_key, "course-plant-oil-tables")
    add_insert(sql, "research_study", ["study_key", "research_source_id", "study_title", "study_date", "methods_summary", "metadata"],
               [sql_text(study_key), f"(SELECT id FROM research_source WHERE source_key={sql_text(research_source_key)})", sql_text("课程资料中的植物形态与油样表格"), "NULL", "NULL", sql_json({"source_pdf_sha256": pdf_hash})])

    for row in samples:
        source_sample_key = row["source_key"]
        sample_key = stable_key("research-sample", pdf_hash, source_sample_key)
        referenced_plant = row.get("plant_id")
        plant_record_key = plant_keys_by_source.get(referenced_plant)
        page = row.get("source_pdf_page")
        printed = row.get("source_printed_page")
        verification = row.get("oil_content_verification") or {}
        oil_candidate = row.get("oil_content_candidate") or {}
        raw_excerpt = oil_candidate.get("raw_text") or oil_candidate.get("raw_text_candidate")
        bbox = oil_candidate.get("source_bbox_normalized") or oil_candidate.get("bbox_normalized")
        snippets = row.get("raw_snippets") or []
        if not raw_excerpt and snippets:
            raw_excerpt = "; ".join(str(item.get("text", "")) for item in snippets if item.get("text")) or None
        evidence_state = "SOURCE_CHECKED" if verification.get("status") == "VERIFIED_FROM_SOURCE_IMAGE" else "EXTRACTED"
        evidence_key = add_evidence(source_sample_key, page, printed, raw_excerpt, bbox, evidence_state,
                                    f"source_pages/page-{int(page):02d}.jpg" if page else None)
        linkage = "LINKED" if plant_record_key else "UNLINKED"
        add_insert(sql, "research_sample", ["sample_key", "source_sample_key", "research_study_id", "plant_id", "taxon_id", "identification_text", "sample_label", "plant_part", "collection_unit", "testing_unit", "locality", "linkage_state", "review_state", "evidence_id", "raw_payload"],
                   [sql_text(sample_key), sql_text(source_sample_key), f"(SELECT id FROM research_study WHERE study_key={sql_text(study_key)})",
                    "NULL" if not plant_record_key else f"(SELECT id FROM plant WHERE record_key={sql_text(plant_record_key)})",
                    "NULL", sql_text(referenced_plant), sql_text(row.get("sample_label")), sql_text(row.get("sample_part_candidate")),
                    sql_text(row.get("collection_unit_candidate")), sql_text(row.get("testing_unit_candidate")), sql_text(row.get("origin_candidate")),
                    sql_text(linkage), sql_text("REVIEW_PENDING"), f"(SELECT id FROM evidence WHERE evidence_key={sql_text(evidence_key)})", sql_json(row)])

        if verification.get("status") == "MISSING_OR_UNREADABLE":
            value_raw = None
            value_numeric = None
            measurement_state = "MISSING"
        else:
            value_raw = oil_candidate.get("raw_text") or (str(row["oil_content_pct"]) if row.get("oil_content_pct") is not None else None)
            value_numeric = row.get("oil_content_pct")
            measurement_state = "SOURCE_CHECKED" if verification.get("status") == "VERIFIED_FROM_SOURCE_IMAGE" else "REVIEW_PENDING"
        measurement_key = stable_key("measurement", sample_key, "OIL_CONTENT", value_raw, evidence_key)
        add_insert(sql, "measurement", ["measurement_key", "research_sample_id", "measurement_kind", "analyte", "value_raw", "value_numeric", "unit", "denominator", "analytical_basis", "method", "measurement_state", "evidence_id", "raw_payload"],
                   [sql_text(measurement_key), f"(SELECT id FROM research_sample WHERE sample_key={sql_text(sample_key)})", sql_text("OIL_CONTENT"), "NULL", sql_text(value_raw), sql_number(value_numeric), sql_text(oil_candidate.get("unit") or "%"), "NULL", "NULL", sql_text(verification.get("method")), sql_text(measurement_state), f"(SELECT id FROM evidence WHERE evidence_key={sql_text(evidence_key)})", sql_json({"verification": verification, "candidate": oil_candidate, "record_kind": row.get("record_kind")})])

        for index, fragment in enumerate(row.get("unparsed_numeric_fragments") or []):
            frag_evidence = add_evidence(source_sample_key, page, printed, fragment.get("text"), fragment.get("bbox_normalized"), "EXTRACTED",
                                         f"source_pages/page-{int(page):02d}.jpg" if page else None)
            candidate_key = stable_key("candidate", samples_hash, source_sample_key, "UNPARSED_NUMERIC_FRAGMENT", index, canonical(fragment))
            add_insert(sql, "candidate_record", ["candidate_key", "source_asset_id", "evidence_id", "candidate_type", "review_state", "proposed_entity_key", "raw_payload"],
                       [sql_text(candidate_key), f"(SELECT id FROM source_asset WHERE asset_key={sql_text(samples_asset_key)})", f"(SELECT id FROM evidence WHERE evidence_key={sql_text(frag_evidence)})", sql_text("UNPARSED_NUMERIC_FRAGMENT"), sql_text("REVIEW_PENDING"), sql_text(sample_key), sql_json(fragment)])

    sql.append("COMMIT;")
    sql_text_file = "\n".join(sql) + "\n"
    (run_dir / "import.sql").write_text(sql_text_file, encoding="utf-8")

    report = {
        "run_id": run_id,
        "mode": "APPLY" if args.apply else "DRY_RUN",
        "inputs": {
            "data_root": str(data),
            "course_pdf": {"path": COURSE_PDF, "sha256": pdf_hash},
            "plant_extract": {"path": PLANTS_JSON, "sha256": plants_hash, "rows": len(plants)},
            "oil_sample_extract": {"path": SAMPLES_JSON, "sha256": samples_hash, "rows": len(samples)},
        },
        "asset_catalog_count": len(assets),
        "evidence_count": len(evidence_rows),
        "target_records": {
            "plants": len(plants),
            "taxa": len(taxon_specs),
            "trait_observations": sum(1 for row in plants for key, field in (row.get("fields") or {}).items() if key in TRAITS and isinstance(field, dict) and field.get("value")),
            "research_samples": len(samples),
            "linked_samples": len(linked_samples),
            "unlinked_samples": len(unlinked_samples),
            "oil_measurements": len(samples),
            "source_checked_oil_values": len(source_checked_oil),
            "missing_oil_values": len(missing_oil),
            "unparsed_fragment_candidates": sum(len(row.get("unparsed_numeric_fragments") or []) for row in samples),
        },
        "rules": [
            "VIRIDEXA ids are newly generated; no historical numeric ids are copied.",
            "Plant/sample links use only exact source keys present in the same protected extraction; unresolved links remain UNLINKED.",
            "Plant records remain REVIEW_PENDING and RESEARCH_PREVIEW; field-level source-check states are retained separately.",
            "Missing/unreadable oil results have NULL numeric values and MISSING state, never zero.",
            "Each oil measurement belongs to one sample and its source evidence; no study profiles are combined.",
            "The original PDF and extraction files are read-only inputs; output files are written only under data/processed/.",
        ],
        "database_apply": None,
    }

    if args.apply:
        command = ["docker", "compose", "exec", "-T", "db", "sh", "-c",
                   'export MYSQL_PWD="$MYSQL_IMPORTER_PASSWORD"; exec mysql --protocol=tcp -h 127.0.0.1 -u viridexa_importer viridexa']
        result = subprocess.run(command, input=sql_text_file, text=True, capture_output=True, cwd=ROOT)
        if result.returncode != 0:
            (run_dir / "apply.stderr.txt").write_text(result.stderr, encoding="utf-8")
            raise SystemExit(f"Target import failed; see protected run report at {run_dir}")
        count_command = ["docker", "compose", "exec", "-T", "db", "sh", "-c",
                         'export MYSQL_PWD="$MYSQL_IMPORTER_PASSWORD"; exec mysql --protocol=tcp -h 127.0.0.1 -u viridexa_importer -Nse "SELECT (SELECT COUNT(*) FROM plant), (SELECT COUNT(*) FROM research_sample), (SELECT COUNT(*) FROM measurement), (SELECT COUNT(*) FROM source_asset)" viridexa']
        counts = subprocess.check_output(count_command, text=True, cwd=ROOT).strip().split()
        report["database_apply"] = {"status": "APPLIED", "plants_total": int(counts[0]), "samples_total": int(counts[1]), "measurements_total": int(counts[2]), "source_assets_total": int(counts[3])}

    (run_dir / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"mode={report['mode']} run_id={run_id}")
    print(f"source assets indexed={len(assets)}; evidence rows={len(evidence_rows)}")
    print("target counts=" + json.dumps(report["target_records"], ensure_ascii=False, sort_keys=True))
    if report["database_apply"]:
        print("database=" + json.dumps(report["database_apply"], ensure_ascii=False, sort_keys=True))
    print(f"report={run_dir / 'report.json'}")
    print(f"prepared_sql={run_dir / 'import.sql'}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, json.JSONDecodeError, KeyError, TypeError, ValueError) as exc:
        print(f"pipeline validation failed: {exc}", file=sys.stderr)
        raise SystemExit(2)
