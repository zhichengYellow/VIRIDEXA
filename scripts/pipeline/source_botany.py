"""Read-only adapters for the local botanical taxonomy and trait snapshots."""
from __future__ import annotations

import csv
import re
import json
from pathlib import Path
from typing import Iterable, Iterator
from zipfile import ZipFile


def _root(root: str | Path) -> Path:
    return Path(root).resolve()


def _row(source: str, pipeline: str, locator: dict, raw: dict,
         normalized: dict | None, reason: str | None = None) -> dict:
    return {"pipeline": pipeline, "source_path": source, "locator": locator,
            "raw": raw, "normalized": normalized, "reason": reason}


def _taxonomy(name: str | None, status: str | None, **fields) -> tuple[dict | None, str | None]:
    name = (name or "").strip()
    if not name:
        return None, "missing scientific name"
    return {"scientific_name": name, "source_taxonomic_status": status,
            "review_status": "PENDING_HUMAN_REVIEW", **fields}, None


def _gbif(path: Path, rel: str) -> Iterator[dict]:
    with path.open(encoding="utf-8") as stream:
        for ordinal, line in enumerate(stream, 1):
            try:
                raw = json.loads(line)
            except json.JSONDecodeError:
                yield _row(rel, "TAXONOMY", {"record_ordinal": ordinal}, {"line": line.rstrip()}, None, "invalid JSON")
                continue
            if raw.get("record_kind") != "TAXONOMY":
                continue
            gbif = raw.get("raw_gbif_response") or {}
            usage = gbif.get("usage") or {}
            classification = gbif.get("classification") or []
            norm, reason = _taxonomy(raw.get("accepted_name") or usage.get("canonicalName") or raw.get("scientific_name"),
                usage.get("status"), source_namespace="GBIF", source_taxon_id=raw.get("external_taxon_id") or usage.get("key"),
                accepted_name=raw.get("accepted_name"), rank=raw.get("taxonomic_rank") or usage.get("rank"),
                name_zh=raw.get("name_zh"), classification=classification,
                match_resolution=raw.get("resolution"), source_url=raw.get("source_url"))
            yield _row(rel, "TAXONOMY", {"record_ordinal": ordinal}, raw, norm, reason)


def _traits(path: Path, rel: str, member: str) -> Iterator[dict]:
    with ZipFile(path) as archive, archive.open(member) as binary:
        text = (line.decode("utf-8-sig") for line in binary)
        reader = csv.DictReader(text)
        for ordinal, raw in enumerate(reader, 2):
            taxon = (raw.get("taxon") or "").strip()
            traits = {k: v for k, v in raw.items() if k not in {"_id", "taxon", "wfo", "source", "detected"} and v and v.strip()}
            lexical = {"leaf shape": "leaf_shape", "leaf margin": "leaf_margin",
                       "flower colour": "flower_colour", "habit": "growth_form",
                       "fruit type": "fruit_type", "fruit shape": "fruit_shape"}
            normalized_traits = {target: [part.strip().casefold() for part in traits[source].split(",") if part.strip()]
                                 for source, target in lexical.items() if traits.get(source)}
            norm = ({"scientific_name": taxon, "source_namespace": "NHM", "source_taxon_id": raw.get("_id"),
                     "wfo_reference": raw.get("wfo"), "traits": traits,
                     "normalized_traits": normalized_traits, "normalization_rule": "lexical-v1; comma split, trim, casefold; no semantic validation",
                     "review_status": "PENDING_HUMAN_REVIEW"} if taxon and traits else None)
            yield _row(rel, "MORPHOLOGY", {"archive_member": member, "record_ordinal": ordinal}, raw, norm,
                       None if norm else ("missing taxon name" if not taxon else "no populated trait fields"))


def _candidate_name(value: str | None) -> str:
    """Conservative candidate key: genus + epithet, explicitly not a name assertion."""
    words = re.findall(r"[A-Za-zÀ-ž][A-Za-zÀ-ž-]*", value or "")
    return " ".join(words[:2]).casefold() if len(words) >= 2 else ""


def _wfo(base: Path, stats: dict, prefix: str = "") -> Iterator[dict]:
    disk_rel = "raw/wfo/wfo_plantlist_2026-06.zip"
    rel = prefix + disk_rel
    path = base / disk_rel
    plants_file = base / "plants.json"
    if not path.is_file() or not plants_file.is_file():
        return
    plants = json.loads(plants_file.read_text(encoding="utf-8"))
    targets: dict[str, list[dict]] = {}
    for p in plants.get("plants", []):
        scientific = p.get("scientific_name")
        key = _candidate_name(scientific)
        if key:
            targets.setdefault(key, []).append(p)
    stats.setdefault("wfo", {n: {"scanned": 0, "passes": 0, "distinct_records_scanned": 0,
                                  "matched": 0, "excluded": 0} for n in ("name.tsv", "taxon.tsv", "synonym.tsv")})
    names: dict[str, dict] = {}
    target_name_ids: set[str] = set()
    taxon_name_to_id: dict[str, str] = {}
    taxon_rows: dict[str, dict] = {}
    with ZipFile(path) as archive:
        with archive.open("name.tsv") as f:
            stats["wfo"]["name.tsv"]["passes"] += 1
            for ordinal, row in enumerate(csv.DictReader((x.decode("utf-8-sig") for x in f), delimiter="\t"), 2):
                stats["wfo"]["name.tsv"]["scanned"] += 1
                stats["wfo"]["name.tsv"]["distinct_records_scanned"] += 1
                if _candidate_name(row.get("scientificName")) in targets:
                    names[row["ID"]] = row
                    target_name_ids.add(row["ID"])
                    stats["wfo"]["name.tsv"]["matched"] += 1
                else:
                    stats["wfo"]["name.tsv"]["excluded"] += 1
        # Retain all parent links as compact strings so target chains can be resolved in
        # bounded, repeated streaming passes without materializing the 1 GB expanded table.
        selected: dict[str, dict] = {}
        needed_names: set[str] = set()
        with archive.open("taxon.tsv") as f:
            stats["wfo"]["taxon.tsv"]["passes"] += 1
            for row in csv.DictReader((x.decode("utf-8-sig") for x in f), delimiter="\t"):
                stats["wfo"]["taxon.tsv"]["scanned"] += 1
                stats["wfo"]["taxon.tsv"]["distinct_records_scanned"] += 1
                if row.get("nameID") in target_name_ids:
                    selected[row["ID"]] = row
                    needed_names.add(row["nameID"])
        # Add every ancestor, one level per pass. Missing parent references remain explicit.
        frontier = {r.get("parentID") for r in selected.values()} - {"", None}
        while frontier:
            fresh = frontier - selected.keys()
            if not fresh:
                break
            found: dict[str, dict] = {}
            with archive.open("taxon.tsv") as f:
                stats["wfo"]["taxon.tsv"]["passes"] += 1
                for row in csv.DictReader((x.decode("utf-8-sig") for x in f), delimiter="\t"):
                    stats["wfo"]["taxon.tsv"]["scanned"] += 1
                    if row.get("ID") in fresh:
                        found[row["ID"]] = row
            selected.update(found)
            needed_names.update(r.get("nameID") for r in found.values() if r.get("nameID"))
            frontier = {r.get("parentID") for r in found.values()} - selected.keys() - {"", None}
        # Resolve names for ancestors with a second lightweight pass.
        with archive.open("name.tsv") as f:
            stats["wfo"]["name.tsv"]["passes"] += 1
            for row in csv.DictReader((x.decode("utf-8-sig") for x in f), delimiter="\t"):
                if row.get("ID") in needed_names:
                    names[row["ID"]] = row
        taxon_ids = set(selected)
        synonyms: list[dict] = []
        with archive.open("synonym.tsv") as f:
            stats["wfo"]["synonym.tsv"]["passes"] += 1
            for row in csv.DictReader((x.decode("utf-8-sig") for x in f), delimiter="\t"):
                stats["wfo"]["synonym.tsv"]["scanned"] += 1
                stats["wfo"]["synonym.tsv"]["distinct_records_scanned"] += 1
                if row.get("taxonID") in taxon_ids or row.get("nameID") in target_name_ids:
                    synonyms.append(row)
                    if row.get("nameID"):
                        needed_names.add(row["nameID"])
                    stats["wfo"]["synonym.tsv"]["matched"] += 1
                else:
                    stats["wfo"]["synonym.tsv"]["excluded"] += 1
        synonym_names = {r["nameID"] for r in synonyms if r.get("nameID")} - names.keys()
        if synonym_names:
            with archive.open("name.tsv") as f:
                for row in csv.DictReader((x.decode("utf-8-sig") for x in f), delimiter="\t"):
                    if row.get("ID") in synonym_names:
                        names[row["ID"]] = row
        for row in selected.values():
            n = names.get(row.get("nameID"), {})
            name = n.get("scientificName")
            key = _candidate_name(name)
            matching_targets = targets.get(key, [])
            target_ids = [p.get("source_key") or p.get("plant_id") for p in matching_targets]
            norm = {"scientific_name": name, "source_namespace": "WFO", "source_taxon_id": row.get("ID"),
                    "source_name_id": row.get("nameID"), "parent_taxon_id": row.get("parentID") or None,
                    "rank": n.get("rank"), "authorship": n.get("authorship"),
                    "candidate_match_rule": "casefolded first two alphabetic tokens (genus + epithet); authorship ignored",
                    "candidate_target_keys": target_ids, "candidate_ambiguous": len(matching_targets) != 1,
                    "source_taxonomic_status": "accepted taxon row", "review_status": "PENDING_HUMAN_REVIEW"}
            stats["wfo"]["taxon.tsv"]["matched"] += 1
            yield _row(rel, "TAXONOMY", {"archive_member": "taxon.tsv", "record_id": row["ID"]}, row, norm)
        stats["wfo"]["taxon.tsv"]["excluded"] = max(
            0, stats["wfo"]["taxon.tsv"]["distinct_records_scanned"] - len(selected))
        for name_id, row in names.items():
            if name_id not in target_name_ids and name_id not in synonym_names:
                continue
            yield _row(rel, "TAXONOMY", {"archive_member": "name.tsv", "record_id": name_id}, row,
                       {"scientific_name": row.get("scientificName"), "source_namespace": "WFO",
                        "source_name_id": name_id, "rank": row.get("rank"), "authorship": row.get("authorship"),
                        "candidate_match_rule": "casefolded first two alphabetic tokens (genus + epithet); authorship ignored",
                        "review_status": "PENDING_HUMAN_REVIEW"})
        for row in synonyms:
            yield _row(rel, "TAXONOMY", {"archive_member": "synonym.tsv", "record_id": row["ID"]}, row,
                       {"source_namespace": "WFO", "source_synonym_id": row.get("ID"),
                        "accepted_taxon_id": row.get("taxonID"), "source_name_id": row.get("nameID"),
                        "synonym_name": (names.get(row.get("nameID")) or {}).get("scientificName"),
                        "review_status": "PENDING_HUMAN_REVIEW"})


def extract(root: str | Path, stats: dict | None = None) -> Iterable[dict]:
    """Yield actual local GBIF taxonomy and NHM/Flora of China morphology rows.

    ``root`` is the protected data directory. Inputs are opened read-only and archives
    are streamed. Pass a dict as ``stats`` to collect per-member scanned/matched/excluded
    counts for the WFO slice.
    """
    base = _root(root)
    if (base / "data").is_dir():
        base = base / "data"
    prefix = "data/"
    stats = stats if stats is not None else {}
    yield from _wfo(base, stats, prefix)
    course = base / "plants.json"
    if course.is_file():
        document = json.loads(course.read_text(encoding="utf-8"))
        for ordinal, plant in enumerate(document.get("plants", []), 1):
            name = plant.get("scientific_name")
            normalized, reason = _taxonomy(name, plant.get("scientific_name_status"),
                source_namespace="COURSE_PDF_EXTRACT", source_taxon_id=plant.get("source_key") or plant.get("plant_id"),
                name_zh=plant.get("name_zh"), source_pdf=plant.get("data_source"),
                source_pdf_sha256=plant.get("source_pdf_sha256"), source_pdf_pages=plant.get("source_pdf_pages"))
            yield _row(prefix + "plants.json", "TAXONOMY", {"record_ordinal": ordinal}, plant, normalized, reason)
    for filename in ("nhm_taxon_identity_review_20261011.csv", "nhm_taxon_import_manifest_20261011.csv"):
        mapping = base / filename
        if not mapping.is_file():
            continue
        with mapping.open(encoding="utf-8-sig", newline="") as stream:
            for ordinal, raw in enumerate(csv.DictReader(stream), 2):
                yield _row(prefix + filename, "TAXONOMY", {"record_ordinal": ordinal}, raw, None,
                           "mapping ledger preserved as source evidence; identity mapping requires review")
    gbif = base / "external/gbif/match_candidates_20261011.jsonl"
    if gbif.is_file():
        yield from _gbif(gbif, prefix + gbif.relative_to(base).as_posix())
    for rel, member in (("raw/nhm_traits/combined.zip", "resource.csv"),
                        ("raw/nhm_traits/efloras.flora_of_china.zip", "resource.csv")):
        archive = base / rel
        if archive.is_file():
            yield from _traits(archive, prefix + rel, member)
