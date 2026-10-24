# VIRIDEXA · 植序

**Decode Nature. Connect Knowledge.**

VIRIDEXA is an independent plant knowledge and scientific research platform. It does not run, copy, or depend on the earlier PlantScope application or schema. Its database, APIs, review workflow, Vue interface, and import pipeline are designed for VIRIDEXA.

## Protected research inputs

Raw and extracted research materials live outside this Git worktree. The default location is `../VIRIDEXA-protected/data`; set `VIRIDEXA_DATA_DIR` in the local `.env` to use another protected directory. The archived SQL files are in the adjacent `../VIRIDEXA-protected/database/` directory and are inputs to explicit ingestion work only. Neither Git clean nor Docker volume operations can reach these sibling directories. The normal app stack never mounts the research tree; the opt-in snapshot importer mounts only the checksum-verified SQL file read-only.

The generated [protected asset manifest](docs/ASSET_MANIFEST.md) records logical paths, sizes, and SHA-256 values. Regenerate it with:

```sh
python3 scripts/build_asset_manifest.py
```

## Local run

Requirements: Docker Compose, Java 21, Node 22.12 or later, and Python 3.

```sh
./scripts/dev-env.sh
(cd apps/web && nvm use && npm ci && npm run build)
(cd apps/api && ./mvnw -s ../../.mvn/settings.xml package)
docker compose up -d --build
python3 scripts/pipeline/import_course_extracts.py --dry-run
python3 scripts/pipeline/import_course_extracts.py --apply
python3 scripts/pipeline/import_snapshot_records.py --dry-run
python3 scripts/pipeline/import_snapshot_records.py --apply
./scripts/check-app.sh
```

Open <http://localhost:5173>. The target MySQL schema is a new `viridexa` database with Flyway migrations. The API uses a `SELECT`-only account; the pipeline writes through a separate insert-only account after explicit `--apply`. Inspect each dry-run report under the external `processed/` directory before applying it. Snapshot ingestion temporarily restores the protected snapshot to an isolated MySQL tmpfs service; the API never connects to that source.

The Vue production assets are served by Nginx. Host-side hot reload is available from `apps/web` with Node 22.20. API OpenAPI is available at <http://localhost:18080/docs>.

## Checks

```sh
(cd apps/api && ./mvnw -s ../../.mvn/settings.xml test)
(cd apps/web && nvm use && npm ci && npm run build)
python3 scripts/pipeline/import_course_extracts.py --dry-run
python3 scripts/pipeline/import_snapshot_records.py --dry-run
./scripts/check-app.sh
```

`docker compose down` stops the application but keeps its rebuildable development database volume. `docker compose down -v` removes only the VIRIDEXA target database volume; scientific inputs remain outside the repository and outside Docker volumes.

## Design and data lineage

- [VIRIDEXA architecture](docs/VIRIDEXA_ARCHITECTURE.md)
- [VIRIDEXA database design](docs/DATABASE_VIRIDEXA_DESIGN.md)
- [Scientific-data import pipeline](docs/IMPORT_PIPELINE.md)
- [Data protection report](docs/DATA_PROTECTION_REPORT.md)
- [Clean-slate checkpoint](docs/CLEAN_SLATE_CHECKPOINT.md)

The first importer slice uses real course-PDF extractions: 33 plants, 89 samples, 88 source-image-checked oil values, one explicitly missing value, and 748 unresolved numeric fragments. The snapshot adapter stages 6,554 real source rows, 2,791 historical review events, and 6 operational audit events. Plants remain in review-pending preview state; no old application IDs are reused, no reviewers are invented, and study profiles are not merged.
