# VIRIDEXA · 植序

**Decode Nature. Connect Knowledge.**

VIRIDEXA is a new Java and Vue application for plant knowledge exploration and scientific research. The existing `data/` and `database/` trees are protected source material. They are not copied into the application source tree and are ignored by Git by default.

## Phase-one local run

This workspace is currently on macOS. Java 21, Node 22, npm, and Docker are available. Run the following from this directory:

```sh
./scripts/dev-env.sh
./scripts/export-legacy-readonly-snapshot.sh
nvm use
(cd apps/web && npm ci && npm run build)
(cd apps/api && ./mvnw -s ../../.mvn/settings.xml package)
docker compose up --build
```

The export reads the existing MySQL container in a consistent, read-only transaction and writes a new snapshot to `/tmp`; it does not overwrite existing snapshots. Compose restores that snapshot to its own MySQL 8.0.44 container in a 1 GiB tmpfs, creates a `SELECT`-only application account, then serves the API and built Vue app on loopback ports 18080 and 5173 through Nginx. Build the Vue assets on the host with `cd apps/web && npm run build`, then build the API jar with `cd apps/api && ./mvnw -s ../../.mvn/settings.xml package`; Compose uses the locally cached Java 21, MySQL 8.0, and Nginx images. The database snapshot is temporary: recreating the MySQL container imports the same checksum-verified snapshot again. The initial image pull/data-directory attempt returned `no space left on device`; current container storage is kept bounded with the tmpfs restore and existing local images. No production data volume is configured in this phase. For hot reload, run the Vite server on the host with Node 22.20 from `.nvmrc`.

Open <http://localhost:5173>. The search endpoint only returns public records whose `verification_status` is `VERIFIED`. The API source connection is read-only and Flyway is disabled for the restored legacy snapshot. V2 migrations will be applied to a separate target database after their dry run and recovery checks.

Run the initial checks with:

```sh
cd apps/api && ./mvnw -s ../../.mvn/settings.xml test
cd ../web && nvm use && npm ci && npm run build
cd ../..
./scripts/check-phase1.sh
```

To stop the local stack, use `docker compose down`. The phase-one MySQL data is temporary and will be restored from the snapshot when the container is recreated. It is separate from the old database container and volume.

## Phase-one artifacts

- `docs/LEGACY_ASSET_MANIFEST.md` — per-file sizes, SHA-256 hashes, and source paths.
- `docs/DATA_PROTECTION_REPORT.md` — legacy database counts, backup checks, and boundaries.
- `docs/VIRIDEXA_ARCHITECTURE.md` — phase-one architecture and verified dependency versions.
- `docs/DATABASE_V2_DESIGN.md` — proposed normalized target model.
- `docs/MIGRATION_MAPPING.md` — source-to-target mapping and dry-run rules.
- `docs/PHASE1_ACCEPTANCE.md` — execution evidence and remaining gates.

Application source and documentation are versioned in the configured GitHub repository. Raw scientific files, database contents, local credentials, and build outputs remain excluded from Git.
