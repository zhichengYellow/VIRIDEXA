"""Read-only adapters for the protected course, literature and USDA lipid sources.

Every input row is emitted, including rows that cannot safely become observations.
"""
from __future__ import annotations

import csv
import hashlib
from decimal import Decimal, InvalidOperation
import json
import re
import zipfile
from pathlib import Path

KINDS = {"OIL_CONTENT", "OIL_FA_COMPOSITION", "FAME_COMPOSITION", "FOOD_NUTRIENT"}


def _number(value):
    """Return (number, state): missing is distinct from malformed input."""
    if value is None or str(value).strip().casefold() in {"", "null", "na", "n/a", "-"}:
        return None, "missing"
    try:
        result = Decimal(str(value).strip().replace(",", ""))
        if not result.is_finite():
            return None, "invalid"
        return float(result), "ok"
    except (TypeError, InvalidOperation, ValueError):
        return None, "invalid"


def _item(path, locator, raw, kind=None, value=None, unit=None, basis=None, analyte=None, reason=None, study=None, sample=None, paper=None, raw_value=None, source_location=None):
    norm = None
    if kind:
        assert kind in KINDS
        norm = {"study_id": study, "sample_id": sample, "kind": kind,
                "value": value, "unit": unit, "basis": basis, "analyte": analyte,
                "raw_value": raw_value, "source_location": source_location, "paper": paper}
    return {"pipeline": "LIPID", "source_path": path, "locator": locator,
            "raw": raw, "normalized": norm, "reason": reason}


def _csv(path):
    with path.open(encoding="utf-8-sig", newline="") as f:
        yield from csv.DictReader(f)


def extract(root):
    """Yield normalized or held lipid rows under protected root (read-only)."""
    root = Path(root).resolve()
    # Course table: only explicitly image-verified oil percentages qualify.
    p = root / "data/oil_samples.json"
    if p.exists():
        doc = json.loads(p.read_text(encoding="utf-8"))
        course_pdf = root / "data/raw/course/课程设计资料.pdf"
        course_hash = hashlib.sha256(course_pdf.read_bytes()).hexdigest() if course_pdf.is_file() else None
        declared_hash = doc.get("source_pdf_sha256")
        course_verified = course_hash is not None and course_hash == declared_hash
        course_paper = {"path": "data/raw/course/课程设计资料.pdf", "sha256": declared_hash} if course_verified else None
        for i, row in enumerate(doc.get("samples", []), 1):
            status = (row.get("oil_content_verification") or {}).get("status")
            val, state = _number(row.get("oil_content_pct"))
            valid_range = val is None or 0 <= val <= 100
            ready = valid_range and state != "invalid" and course_verified
            yield _item("data/oil_samples.json", {"sample_id": row.get("sample_id"), "row": i, "field": "oil_content_pct"}, row,
                        "OIL_CONTENT" if ready and valid_range else None, val, "%", "as-reported oil content", "oil_content_pct",
                        "course paper hash unverified" if not course_verified else "course oil value is outside 0-100 percent" if not valid_range else "course oil value is invalid" if state == "invalid" else "course oil value is missing" if state == "missing" else None,
                        study="course-oil-pdf-sha256:" + (declared_hash or "unknown"), sample=row.get("sample_id"), paper=course_paper,
                        raw_value=(row.get("oil_content_candidate") or {}).get("raw_text"),
                        source_location={"pdf_page": row.get("source_pdf_page"), "printed_page": row.get("source_printed_page"), "bbox_normalized": ((row.get("oil_content_verification") or {}).get("source_bbox_normalized"))})

    # Historical course component candidates have no verified unit/basis.
    # Keep each raw cell as held evidence rather than invent percent composition.
    course_candidates = root / "data/fatty_acid_candidates.csv"
    if course_candidates.is_file():
        for i, row in enumerate(_csv(course_candidates), 2):
            yield _item("data/fatty_acid_candidates.csv", {"row": i, "candidate_id": row.get("candidate_id"),
                        "sample_id": row.get("sample_id"), "component": row.get("component_code")}, row,
                        reason="COURSE_COMPOSITION_UNIT_AND_BASIS_UNVERIFIED")

    extraction_path = root / "data/fatty_acid_literature_extraction.json"
    studies = {}
    if extraction_path.exists():
        extraction = json.loads(extraction_path.read_text(encoding="utf-8"))
        for s in extraction.get("source_files", []):
            pdf = root / s["file"]
            actual = hashlib.sha256(pdf.read_bytes()).hexdigest() if pdf.is_file() else None
            meta = dict(s)
            meta["paper"] = {"path": s["file"], "sha256": s["sha256"],
                              "composition_basis": s.get("composition_basis"), "method": s.get("method")}
            meta["hash_verified"] = actual == s.get("sha256")
            studies[s["source_key"]] = meta

    # Separate oil-content sample table and component-level literature table.
    p = root / "data/fatty_acid_literature_samples.csv"
    if p.exists():
        for i, row in enumerate(_csv(p), 2):
            v, state = _number(row.get("oil_content_pct"))
            ok = row.get("oil_content_unit") == "%"
            ok = ok and (v is None or 0 <= v <= 100) and state != "invalid"
            sid = row.get("source_key")
            study = sid.rsplit("-sample-", 1)[0] if sid else None
            meta = studies.get(study, {})
            valid_paper = meta.get("hash_verified") is True
            basis = meta.get("composition_basis")
            ok = ok and valid_paper
            yield _item("data/fatty_acid_literature_samples.csv", {"row": i, "sample_id": sid, "field": "oil_content_pct"}, row,
                        "OIL_CONTENT" if ok else None, v, "%" if ok else row.get("oil_content_unit"), "as-reported oil content", "oil_content_pct",
                        None if ok else "literature oil value invalid/out of range, unit unclear, or paper hash unverified",
                        study, sid, meta.get("paper") if valid_paper else None, row.get("oil_content_source_text"),
                        {"pdf_page": row.get("source_pdf_page"), "printed_page": row.get("source_printed_page"), "table": "oil content", "reference": row.get("source_note")})

    p = root / "data/fatty_acid_literature_candidates.csv"
    if p.exists():
        for i, row in enumerate(_csv(p), 2):
            study = row.get("source_key")
            meta = studies.get(study, {})
            basis = meta.get("composition_basis")
            kind = "FAME_COMPOSITION" if basis and "FAME" in basis.upper() else "OIL_FA_COMPOSITION" if basis else None
            if not meta.get("hash_verified"):
                kind = None
            v, state = _number(row.get("candidate_numeric_value"))
            code = row.get("component_code")
            match = re.match(r"^(C\d{1,2}:\d+)\b", code or "")
            analyte = match.group(1) if match else None
            accepted = kind is not None and row.get("unit_candidate") == "%" and analyte is not None and state != "invalid" and (v is None or 0 <= v <= 100)
            reason = "composition basis/unit/analyte is unresolved or value is invalid/out of range" if not accepted else None
            yield _item("data/fatty_acid_literature_candidates.csv", {"row": i, "candidate_id": row.get("candidate_id"), "sample_id": row.get("sample_id"), "component": code}, row,
                        kind if accepted else None, v, row.get("unit_candidate"), basis, analyte, reason, study, row.get("sample_id"),
                        meta.get("paper") if meta.get("hash_verified") else None, row.get("raw_value_text"),
                        {"pdf_page": row.get("source_pdf_page"), "printed_page": row.get("source_printed_page"), "reference": row.get("source_record_reference")})

    # Preserve every source extraction sample independently of curated/importable CSV rows.
    if extraction_path.exists():
        extraction = json.loads(extraction_path.read_text(encoding="utf-8"))
        for i, row in enumerate(extraction.get("all_extracted_samples", []), 1):
            study = row.get("paper")
            meta = studies.get(study, {})
            sample_no = row.get("sample_no")
            sample = f"{study}-sample-{int(sample_no):03d}" if sample_no is not None else None
            paper = meta.get("paper") if meta.get("hash_verified") else None
            basis = meta.get("composition_basis")
            composition_kind = "FAME_COMPOSITION" if basis and "FAME" in basis.upper() else "OIL_FA_COMPOSITION" if basis else None
            table_meta = meta

            oil = row.get("oil_content") or {}
            oil_value, oil_state = _number(oil.get("value"))
            oil_valid = meta.get("hash_verified") and oil.get("unit") == "%" and oil_state != "invalid" and (oil_value is None or 0 <= oil_value <= 100)
            oil_reason = None if oil_valid and oil_state == "ok" else "oil value missing" if oil_valid and oil_state == "missing" else "paper hash, unit, or oil value is invalid/out of range"
            yield _item("data/fatty_acid_literature_extraction.json",
                        {"all_extracted_samples_index": i, "field": "oil_content", "component_index": None},
                        {"sample": row, "field": oil}, "OIL_CONTENT" if oil_valid else None, oil_value,
                        oil.get("unit"), "as-reported oil content", oil.get("measure"), oil_reason,
                        study, sample, paper, oil.get("value"),
                        {"pdf_page": row.get("source_pdf_page"), "printed_page": row.get("printed_page"), "table": table_meta.get("table")})

            for ci, component in enumerate(row.get("fatty_acids", []), 1):
                value, state = _number(component.get("numeric_value"))
                valid_component = (meta.get("hash_verified") and composition_kind and component.get("unit") == "%"
                                   and value is not None and 0 <= value <= 100)
                reason = None if valid_component else "paper hash, composition basis, unit, or component value is invalid/missing/out of range"
                yield _item("data/fatty_acid_literature_extraction.json",
                            {"all_extracted_samples_index": i, "field": "fatty_acids", "component_index": ci},
                            {"sample": row, "field": component}, composition_kind if valid_component else None,
                            value, component.get("unit"), basis, component.get("component"), reason,
                            study, sample, paper, component.get("raw_value"),
                            {"pdf_page": row.get("source_pdf_page"), "printed_page": row.get("printed_page"), "table": table_meta.get("table")})

    # USDA Foundation data describes food nutrients, never oil composition.
    zips = sorted((root / "data/raw/fatty_acids/usda").glob("*.zip"))
    for zp in zips:
        with zipfile.ZipFile(zp) as zf:
            names = zf.namelist()
            food_name = next((n for n in names if n.endswith("/food.csv")), None)
            nutrient_name = next((n for n in names if n.endswith("/nutrient.csv")), None)
            values_name = next((n for n in names if n.endswith("/food_nutrient.csv")), None)
            if not (food_name and nutrient_name and values_name):
                continue
            foods = {r["fdc_id"]: r for r in _csv_from_zip(zf, food_name)}
            nutrients = {r["id"]: r for r in _csv_from_zip(zf, nutrient_name)}
            for i, row in enumerate(_csv_from_zip(zf, values_name), 2):
                nutrient = nutrients.get(row.get("nutrient_id"), {})
                name = nutrient.get("name", "")
                if not nutrient:
                    yield _item("data/raw/fatty_acids/usda/" + zp.name,
                                {"zip_member": values_name, "row": i, "id": row.get("id")},
                                {"food": foods.get(row.get("fdc_id"), {}), "food_nutrient": row, "nutrient": {}},
                                reason="USDA_MISSING_NUTRIENT_METADATA")
                    continue
                if not re.search(r"total lipid|fatty acids", name, re.I):
                    continue
                food = foods.get(row.get("fdc_id"), {})
                raw = {"food": food, "food_nutrient": row, "nutrient": nutrient}
                val, state = _number(row.get("amount"))
                yield _item("data/raw/fatty_acids/usda/" + zp.name, {"zip_member": values_name, "row": i, "id": row.get("id")}, raw,
                            "FOOD_NUTRIENT" if state != "invalid" else None, val, nutrient.get("unit_name"), "per 100 g food", name + " [nutrient_id=" + nutrient.get("id", "") + "]",
                            "USDA nutrient amount is invalid" if state == "invalid" else "USDA nutrient amount is missing" if state == "missing" else None, study="USDA_FDC", sample=row.get("fdc_id"))


def _csv_from_zip(zf, member):
    import io
    with zf.open(member) as stream:
        yield from csv.DictReader(io.TextIOWrapper(stream, encoding="utf-8-sig", newline=""))
