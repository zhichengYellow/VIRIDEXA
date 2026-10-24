# Phase 0 / Phase 1 acceptance

**Run date:** 2026-10-24 (Asia/Shanghai)
Status reflects work actually completed; open items are listed at the end.

## Phase 0 — heritage inventory and data protection

| Check | Result |
|---|---|
| Legacy workspace scan | PASS — current workspace started with `data/` and `database/` only; no JSP/Servlet source or separate PlantScope code was found in the targeted local search. No old code was copied. |
| Course source PDF | PASS — found in Downloads, matched recorded SHA-256, copied into protected `data/raw/course/` without replacing either copy. |
| File inventory | PASS — generated `docs/LEGACY_ASSET_MANIFEST.md` from the whole protected data and database trees. |
| Existing gzip backups | PASS — all 3 passed `gzip -t`; SHA-256 recorded in the asset manifest. |
| Old database access | PASS — 36-table MySQL 8.0.44 database statistics were read in `START TRANSACTION READ ONLY`, then rolled back. |
| Current consistent snapshot | PASS — `/tmp/viridexa-phase1-live-snapshot.sql.gz`, 256,553 bytes, SHA-256 `367f37bb5c77399141786f0b47d153e70a232c3cce4d9c3073c4bad150ec6063`; generated with single-transaction/no-lock options and validated as gzip. |
| Legacy database write boundary | PASS — no legacy DDL/DML, migration, restore, or volume changes were made. |

## Phase 1 — runnable application

| Check | Result |
|---|---|
| New application layout | PASS — `apps/api/`, `apps/web/`, `infra/`, `docs/`, `scripts/`, and `data/migration/` were created without clearing the existing asset tree. |
| Version checks | PASS — Spring Boot 4.1.2, Java 21, Maven Wrapper 3.10.0, MyBatis Starter 4.1.0, MySQL 8.0.44, Vue 3.5.44, Vite 8.3.5, Router 5.4.0, Pinia 4.1.0, Tailwind 4.3.3, ECharts 6.1.0, Cytoscape 3.34.4, Springdoc 3.1.1. Spring AI 2.0.2 compatibility was checked and is deferred. |
| Vue build | PASS — `npm run build` after locking TypeScript 5.9.3; Vue type-check and Vite production bundle completed. |
| API unit/security test | PASS — Maven Wrapper `test`: 1 test, 0 failures/errors; anonymous GET is permitted and write methods are denied. |
| Isolated MySQL restore | PASS — the current consistent snapshot restored into the separate `viridexa-mysql-phase1` container; 150 plant rows and 117 public `VERIFIED` rows observed. Read account has only `SELECT`. |
| MyBatis-backed real plant read | PASS — API returned source row 159 from the restored database (`Ziziphus jujuba` as stored), with record status `VERIFIED` and scientific-name status `NEEDS_REVIEW`. |
| Browser page data display | PASS — Vue fetches the same API record through its `/api` proxy; UI does not show the duplicated Latin value as a Chinese common name. |
| Compose smoke check | PASS — `scripts/check-phase1.sh` confirms API response, same record through the Vue proxy, and served app shell. |
| Full multi-service Compose stack | PASS — `docker compose build --pull=false api` built against the cached Java 21 image; `docker compose up -d --no-build` started MySQL, Spring API, and Nginx web. MySQL and API health checks are healthy; Nginx serves the app on 5173 and reverse-proxies `/api` to the API. Host API port is 18080 because a separate process already owns 8080. |

## Source database snapshot counts

Live read-only query on 2026-10-24 found 150 plants (117 verified, 33 needing review), 116 oil samples, 295 pending fatty-acid candidates, 1,180 trait values, 1,003 review-pending knowledge entries, and 2,791 knowledge-review events. See `DATA_PROTECTION_REPORT.md` for status breakdowns and backup hashes.

The 117 publicly exposed rows each have scientific-name status `NEEDS_REVIEW`, with `name_zh` identical to `scientific_name` and no per-row source document/hash. These remain surfaced with explicit status and missing-name/source labels; the API response preserves the original database values.

## Remaining gates

- Production deployment, Nginx hardening, and Ubuntu server rollout remain for the later deployment phase.
- No v2 data migration, full RBAC/user migration, review publication, ECharts chart, Cytoscape graph, Spring AI model connection, Nginx production route, Ubuntu deployment, or publication occurred in this phase.
