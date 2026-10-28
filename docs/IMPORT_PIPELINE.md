# Scientific-data import pipeline

## Input boundary

Research inputs are managed inside this project at `data/` (`VIRIDEXA_DATA_DIR=./data`). Original archives remain read-only inputs; extracted results, image mappings and derived outputs retain their existing categories. Historical SQL archives are stored in `data/backups/legacy-database/` and must never be executed as current Flyway migrations. Compose mounts only `./data` read-only at `/science-inputs/data`, retaining the stored `data/...` source locators without mounting the repository or its secrets. Original documents remain subject to existing authorization and licensing controls. The external sibling backup is retained until a human decides it is no longer needed. The entire data tree is ignored by Git; ignored files are NOT protected from `git clean`, which must not be used on this tree.

Every discovered input can be catalogued as a `source_asset` with logical path, byte size, media type, and SHA-256. An extractor then creates source-specific evidence locators and normalized records. Processed outputs go to `VIRIDEXA_DATA_DIR/processed/pipeline-runs/<run-id>/`; they are rebuildable and excluded from Git and the original asset inventory.

## Current extractor

`scripts/pipeline/import_course_extracts.py` implements a deterministic first slice from the course PDF extraction:

- It checks the course PDF SHA-256 against both extracted JSON source declarations.
- It indexes the protected input files and creates source/evidence rows.
- It imports the 33 actual plant entries in `plants.json`, their scientific/common names, four available text traits, and the exact source-page locators.
- It imports the 89 actual sample rows in `oil_samples.json`. Eighty-eight values source-checked from page images remain in `SOURCE_CHECKED` state; the unreadable value remains `NULL` with `MISSING` state. All 89 sample records remain `REVIEW_PENDING` until review.
- It links samples only through exact source keys present in the same extracted dataset. Unmatched records remain unlinked.
- It stores unparsed numeric fragments as review-pending candidates. It does not combine fatty-acid values across samples.

This is an implemented vertical slice, not a claim that every protected archive has been normalized. WFO, NHM, Flora of China, Global Spectrum, GBIF, USDA, and literature samples remain separate source inputs for later extractors. Their raw files are already inventoried and protected.

`scripts/pipeline/import_snapshot_records.py` restores the checksum-verified database snapshot into a temporary MySQL container using a read-only bind mount and tmpfs. It reads 27 scientific/source/review tables (6,554 rows) into `source_record` as JSON source rows with source table, row key, row hash, and snapshot hash. It separately creates 2,791 append-only `review_event` rows and 6 `operational_audit_event` rows. The review stream keeps `actor_user_id` NULL and preserves a historical actor reference without creating accounts. Authentication telemetry and user activity tables are explicitly excluded from this scientific target.

The 6,554 rows are a source ledger, not 6,554 normalized or published VIRIDEXA entities. The current first-slice search contains the 33 course-extracted plant records. Snapshot-only plants, samples, and candidates remain traceable source rows until dedicated extractors can validate their evidence and relationships.

## Run protocol

```sh
python3 scripts/pipeline/import_course_extracts.py --dry-run
python3 scripts/pipeline/import_course_extracts.py --apply
python3 scripts/pipeline/import_snapshot_records.py --dry-run
python3 scripts/pipeline/import_snapshot_records.py --apply
```

Dry-run is the default. The course extractor validates hashes and extracted content without a database connection. The snapshot extractor starts an isolated, temporary restore from the protected snapshot and uses SELECT statements only. Both write `report.json` and prepared SQL under the `data/processed/` directory. `--apply` is explicit and writes only to the local Compose `viridexa` database using an insert-only account. Neither extractor connects to the old live service or writes to a source file.

Re-running an identical input is idempotent through deterministic content/source keys. A changed source file has a changed checksum and is a new version. Each report records input hashes, counts, link outcomes, missing-value handling, and the prepared/applied status. Compare counts and investigate exceptions before any release or review approval.

In the current local replay check, applying each pipeline twice kept the same totals: 33 plants, 89 samples, 89 measurements, 6,554 source rows, 2,791 review events, and 6 audit events.

## Historical review and snapshot ingestion

The new schema has `review_event.historical_actor_ref` and a nullable named-user relation. The first snapshot/history import is complete and its table counts are recorded in the external pipeline report. Normalization of snapshot-only source rows into the new plant/sample/measurement model is still pending; no old database identifiers are reused as target primary keys, and no source row is discarded while that work proceeds.

## Required invariants for every extractor

1. Verify the source file's current SHA-256 against the asset manifest before parsing.
2. Emit deterministic per-input and per-output counts, duplicate counts, link outcomes, and validation failures.
3. Preserve input payload and exact page/table/row/image locator for every candidate or formal measurement.
4. Never promote a candidate or source-checked value to `APPROVED` in an import.
5. Preserve sample-study boundaries, explicit units/bases, nullable values, rejected records, and unmatched sources.
6. Run dry-run first; apply only to the isolated VIRIDEXA target after inspecting the report.
7. Verify database counts and source/evidence relationships after apply; create and restore a target backup before any production use.
