# Data protection report

**Inventory date:** 2026-10-24 (Asia/Shanghai)
**Scope inspected:** `/Users/binyellow/VIRIDEXA/data`, `/Users/binyellow/VIRIDEXA/database`, the referenced course PDF in Downloads, and the currently running legacy MySQL container.
**Actions:** filesystem reads and SHA-256 hashing; MySQL `SELECT` queries in read-only transactions; one consistent `mysqldump` export; one byte-for-byte PDF copy into the protected project data tree. No legacy database DDL/DML was issued.

## Protected filesystem assets

`LEGACY_ASSET_MANIFEST.md` inventories each file under `data/` and `database/`, including ZIP, PDF, CSV, JSON/JSONL, XLSX, SQL dumps, extracted evidence, screenshots, crops, image mappings, and incidental `.DS_Store` metadata. The manifest records absolute inventory path, size, and SHA-256. `scripts/build_asset_manifest.py` recomputes it without changing source bytes.

The course source PDF was found at `/Users/binyellow/Downloads/课程设计资料.pdf` (9,298,489 bytes). Its SHA-256 is `669e9adbfbdfb855d196e5389b2b8aee763a226fee0ce7f0a7e83bc5bab73685`, matching the hash already recorded in `data/pdf_extract/pdf_inventory_and_crop_manifest.json`. A copy was created at `data/raw/course/课程设计资料.pdf` only after the hash matched. The Downloads original remains in place; the manifest script verifies both copies still match.

The inspected workspace had 291 files and 212,935,530 bytes before the protected PDF copy. The manifest after that copy records **292 files** and **222,234,019 bytes**. The existing `data/raw/` includes the WFO archive, NHM combined traits, Flora of China source data, Global Spectrum workbook, three fatty-acid papers, and USDA FoodData Central archive. `data/` also contains 224 JPGs and 7 PNGs across source scans, mapped plant-image crops, diagnostic/contact sheets, and extracted PDF evidence.

`.gitignore` excludes `data/` source and derived research files by default while keeping `data/migration/` available for migration code. No Git remote was configured and nothing was pushed.

## Legacy database read-only statistics

The legacy service was `nonfood-oilplants-pdf-mysql-20261009`, MySQL **8.0.44**, bound only to `127.0.0.1:3307` and attached to Docker volume `nonfood-oilplants-pdf-mysql-data-20261009`. The database account was not printed. All statistics queries ran in `START TRANSACTION READ ONLY` and ended in `ROLLBACK`; no old table or volume was altered.

| Measure | Observed count/status |
|---|---:|
| Tables | 36 |
| `plants` | 150: 117 `VERIFIED`, 33 `NEEDS_REVIEW` |
| Scientific-name status | 30 `VERIFIED_FROM_PRINTED_HEADING`, 120 `NEEDS_REVIEW` |
| `plant_oil_samples` | 116: 89 OCR candidates, 27 manual samples |
| Oil measurements | 88 `VERIFIED`, 27 `NEEDS_REVIEW`, 1 `MISSING_OR_UNREADABLE` |
| `fatty_acid_candidates` | 295, all `REVIEW_PENDING` |
| `plant_trait_values` | 1,180: 1,174 `EXTRACTED`, 6 `REVIEW_PENDING` |
| `plant_knowledge_entries` | 1,003, all `NEEDS_REVIEW` |
| `knowledge_review_events` | 2,791, covering 2026-10-10 to 2026-10-11 |
| `audit_logs` | 6; actors remain nullable |
| `external_import_candidates` | 3 |
| `users` | 0 |

These are live database query results from 2026-10-24, not the older `data/data_quality_report.json` snapshot. Counts in older CSV/JSON manifests are not substituted for database results.

## Backup and recoverability checks

All three existing SQL gzip backups passed `gzip -t`; each has a SHA-256 in `LEGACY_ASSET_MANIFEST.md`:

- `data/backups/nonfood_oil_plants_pre_fatty_literature_20261011.sql.gz`
- `data/backups/nonfood_oil_plants_post_fatty_literature_20261011.sql.gz`
- `data/backups/nonfood_oil_plants_post_fatty_literature_source_links_20261011.sql.gz`

The latest existing backup is dated 2026-10-11, so it is not the source used by the phase-one application. A separate current snapshot was exported read-only from the live database with `mysqldump --single-transaction --skip-lock-tables --quick --no-tablespaces`; all 36 source tables were InnoDB. The snapshot is `/tmp/viridexa-phase1-live-snapshot.sql.gz`, size **256,553 bytes**, SHA-256 `367f37bb5c77399141786f0b47d153e70a232c3cce4d9c3073c4bad150ec6063`, and it passes `gzip -t`.

The first persistent-volume startup hit Docker VM `ENOSPC` while MySQL wrote its new data directory. The old database and volume remained unaffected. The measured Docker VM has 8 GiB memory; a later filesystem check showed 3.2 GiB available in its 15 GiB root filesystem. Phase-one Compose reuses the locally available MySQL image and restores the snapshot in a dedicated container with a 1 GiB tmpfs; recreating that container re-imports the snapshot. The phase-one API user receives `SELECT` on the restored schema only; Flyway is disabled on this snapshot connection. This temporary restore is not a permanent deployment database.

The restored isolated copy contains 150 `plants` rows and 117 public rows marked `VERIFIED`, matching the source read-only counts. `viridexa_ro` was confirmed to have only `SELECT` on `nonfood_oil_plants.*`. A data-quality issue surfaced in the 117 public rows: all 117 have `scientific_name_status = NEEDS_REVIEW`, `name_zh` exactly duplicates `scientific_name`, and none has `source_document` or `source_pdf_sha256`. The phase-one UI therefore labels the Chinese name and citation as pending and keeps the taxonomic identity status visible. It does not claim a reviewed scientific identity or invent a citation.

## Unresolved boundaries

- The old JSP/Servlet application source was not in the VIRIDEXA workspace; the workspace initially contained only `data/` and `database/`. Targeted path searches did not find a separate PlantScope source directory. No old code was copied into the new app.
- Copyright-sensitive raw research files and extracted images remain outside Git. The project-local PDF copy is also ignored by Git.
- No historical review event with a `NULL` actor will be assigned a fabricated reviewer. The new model preserves actor nullability and source status.
- Candidate values, missing/unreadable values, and unlinked sources remain separate from verified evidence. A missing value is never converted to zero.
