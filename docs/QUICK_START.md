# Quick start

This guide starts a clean VIRIDEXA checkout with a new local database. A fresh clone does not contain protected research inputs under `data/`; that is a valid empty-input state. The showcase application can run without restoring historical SQL or hidden source assets. Data layout and protected-input handling are described in [DATA_LAYOUT.md](DATA_LAYOUT.md).

## Requirements

- Docker with Compose
- Java 21
- Node.js 22.12 or later (the repository uses `nvm` in its examples)
- Python 3 and OpenSSL

## Start the local application

From the repository root:

```sh
./scripts/dev-env.sh
(cd apps/web && nvm use && npm ci && npm run build)
(cd apps/api && ./mvnw -s ../../.mvn/settings.xml package)
docker compose up -d --build
```

`dev-env.sh` creates or updates a mode-0600 `.env`, generating local database secrets without printing them. Do not commit `.env`. The application and database bind to localhost; open <http://localhost:5173>.

The Compose stack applies Flyway migrations to a new `viridexa` schema. The database volume persists between `docker compose down` and restart. For access to signup and review services, provision their least-privilege database identities after the first healthy start:

```sh
python3 scripts/pipeline/provision_public_accounts.py
python3 scripts/pipeline/provision_governance.py
docker compose up -d --no-deps --force-recreate api
```

Recreate the API so it receives the newly written `.env` values; a restart alone retains the container's original environment. These commands create service identities only. An administrator is a human account and must be explicitly initialized with `scripts/pipeline/bootstrap_admin.py`; see [PUBLIC_ACCOUNTS.md](PUBLIC_ACCOUNTS.md) and [PHASE7_ADMIN_INIT.md](PHASE7_ADMIN_INIT.md). No default human account is created.

## Optional protected data

The ignored `data/` directory stores local research inputs, media mappings, processed outputs, and backups. A clean clone can omit it; the local database starts empty, and no API key, raw research files, or production snapshot is required to run the application. These local datasets are not published with the source code. If authorized source files are available, consult [DATA_LAYOUT.md](DATA_LAYOUT.md) and [IMPORT_PIPELINE.md](IMPORT_PIPELINE.md) before placing or importing them. Import commands default to dry-run and require explicit `--apply` to write.

## Shutdown

```sh
docker compose down
```

This retains the local MySQL volume. `docker compose down -v` deletes the rebuildable local target database volume.

For system design, current catalog evidence, source boundaries, and release status, see [ARCHITECTURE.md](ARCHITECTURE.md), [DATA_SOURCES.md](DATA_SOURCES.md), and the forthcoming [VIRIDEXA v0.1.0 release record](VIRIDEXA_V0.1.0_RELEASE.md).
