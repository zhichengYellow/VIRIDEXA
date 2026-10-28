# Clean-slate rebuild checkpoint

**Checkpoint date:** 2026-10-24 (Asia/Shanghai)  
**Strategy:** Clean-Slate Rebuild. PlantScope code, pages, APIs, and schema are not runtime or design dependencies.

## Data safety gate

| Check | Result |
|---|---|
| Pre-move inventory | PASS — 292 files had matching sizes and SHA-256 values; the course PDF source and protected copy matched. |
| Existing backups | PASS — all 3 archived SQL gzip backups passed validation. |
| Current snapshot | PASS — SHA-256 `367f37bb5c77399141786f0b47d153e70a232c3cce4d9c3073c4bad150ec6063`; gzip valid and restored successfully in a temporary isolated MySQL service. |
| Extracted evidence and media | PASS — 231 images, 140 extracted PDF evidence files, and 45 mapped crop references existed and resolved. |
| Cleanup boundary | PASS — `git clean -ndx` demonstrated the original in-repository paths were at risk; no destructive clean ran. Protected `data/` and archived `database/` trees were moved outside Git after the safety gate, and the verified current snapshot was copied into the protected archive. |
| Current protected inventory | PASS — `docs/ASSET_MANIFEST.md` lists 292 source/archive files totaling 222,484,424 bytes. Pipeline outputs are under `data/processed/` and are not source inputs. |

Research inputs are managed inside this project at `data/` (`VIRIDEXA_DATA_DIR=./data`). Original archives remain read-only inputs; extracted results, image mappings and derived outputs retain their existing categories. Historical SQL archives are stored in `data/backups/legacy-database/` and must never be executed as current Flyway migrations. Compose mounts only `./data` read-only at `/science-inputs/data`, retaining the stored `data/...` source locators without mounting the repository or its secrets. Original documents remain subject to existing authorization and licensing controls. The external sibling backup is retained until a human decides it is no longer needed. The entire data tree is ignored by Git; ignored files are NOT protected from `git clean`, which must not be used on this tree.

## Clean-slate implementation

- PASS — new MySQL `viridexa` schema created through Flyway migrations V1 and V2.
- PASS — new Spring Boot/MyBatis plant API reads only VIRIDEXA tables with a `SELECT`-only DB user.
- PASS — Vue page receives an actual record through the new API and the same record through the Nginx proxy.
- PASS — course extractor imports real source rows: 33 plants, 89 research samples, 125 trait observations, 88 source-image-checked oil results, 1 explicit missing result, and 748 unresolved numeric fragments. All plants and samples remain review-pending; no missing value is set to zero.
- PASS — snapshot pipeline staged 6,554 rows from 27 scientific/source/review tables as raw source records, plus 2,791 historical review events and 6 operational audit events. Historical `actor_user_id` remains NULL; raw actor references are not converted to accounts.
- PASS — source database is restored from a protected snapshot into a temporary isolated MySQL instance; the API does not connect to it or to the former live database.
- PASS — MyBatis queries use VIRIDEXA tables; no JSP, Servlet, DAO, prior Service, CSS, API, or schema implementation was copied.

## Verification run

- Vue type-check and production build: PASS (`vue-tsc -b && vite build`).
- Spring API test/package: PASS (1 security test, 0 failures/errors; package build succeeded).
- Compose: PASS — MySQL 8.0.44 and API healthy; Flyway V1 and V2 successful; Nginx serves the app.
- API and web-proxy smoke: PASS — Chinese-name search, plant detail, and list returned VIRIDEXA id 1 with record state `REVIEW_PENDING`, scientific-name state `VERIFIED_FROM_PRINTED_HEADING`, and total 33; the Nginx proxy returned the same record.
- Database access check: PASS — API account has `SELECT` only; importer has `SELECT, INSERT` only.
- Snapshot lineage counts: PASS — 6,554 `source_record`, 2,791 `review_event`, 6 `operational_audit_event`; all 2,791 review events retain NULL `actor_user_id`.
- Re-import check: PASS — replaying both extract pipelines left target totals unchanged at 33 plants, 89 samples, 89 measurements, 6,554 source records, 2,791 review events, and 6 audit events.
- No GitHub push was made as part of this strategy change.

## Work still ahead

- Normalize snapshot-only plant/sample/source rows from the raw source-record ledger through evidence-aware extractors; the ledger is not presented as 6,554 normalized entities.
- Import and validate WFO, NHM, Flora of China, Global Spectrum, GBIF, USDA, and literature sources with source-specific rules.
- Implement evidence search, oil/fatty-acid comparison, named reviewer RBAC, review UI, graph projections, and evidence-constrained Agent.
- Add pipeline unit tests, database integration coverage, browser E2E, and Ubuntu deployment validation.
