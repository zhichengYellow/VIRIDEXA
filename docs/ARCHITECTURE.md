# Architecture

VIRIDEXA is an independent plant knowledge and research platform. It has a Vue client, an Nginx static and reverse-proxy layer, a Spring Boot API, and a MySQL target database managed by Flyway. Python tools handle source extraction, normalization, indexing, and controlled imports. See the detailed [database design](DATABASE_VIRIDEXA_DESIGN.md), [import pipeline](IMPORT_PIPELINE.md), and [data layout](DATA_LAYOUT.md) for implementation specifics.

```text
Browser → Nginx → Spring API → MySQL (Flyway-managed VIRIDEXA schema)
                       ↑
          controlled Python import tools
                       ↑
     optional local protected inputs (data/)
```

The API reads through a restricted database identity. Separate service identities support account registration and governed review operations; ingestion uses a distinct importer identity and explicit apply steps. Human review is represented as named, auditable decisions. Candidate or source-reported material is not silently promoted to verified scientific fact.

## Current scope

The prior local production baseline was 60,031 plant rows, 60,028 public catalog entries, and 60,031 distinct exact canonical names. Subsequent release work has added 74 iFlora Chinese-name entries and is importing exact accepted name matches from the provided 2022 China Species List workbook. Media now includes 10 locally persisted iNaturalist photos across 6 plants plus 3 static fallback assets; the combined set currently covers 7 plants. Formal scientific facts remain 0. Catalog row counts and source-name matches do not mean independently verified species. The current detailed counts are pending the release audit.

The Agent interface currently uses domain retrieval and a deterministic fallback. A real LLM provider and credentials are not configured. Login-gated research tools and administrator-gated review workflows remain distinct from public catalog browsing.

V24 Chinese-name provenance and V25 local botanical media persistence have been applied. The iFlora source version/date remains unspecified; the 2022 China Species List import is in progress and uses exact accepted scientific-name matching. Object storage such as R2 or OSS is an optional future deployment choice; no integration is implemented. Final values and acceptance evidence will be recorded in [VIRIDEXA_V0.1.0_RELEASE.md](VIRIDEXA_V0.1.0_RELEASE.md).

## Runtime and data boundary

`compose.yaml` runs the local stack. `.env` supplies local secrets and is excluded from Git. `VIRIDEXA_DATA_DIR` defaults to `./data`; Compose mounts that directory read-only in the API container. The MySQL target database is a separate Docker volume. A clean checkout may therefore start with an empty local input directory and a newly migrated database. Historical SQL backups are not Flyway migrations and are not required to launch the showcase.

Production deployment adds HTTPS-facing proxy configuration and deployment-specific controls. Consult [PHASE7S_DEPLOYMENT.md](PHASE7S_DEPLOYMENT.md) and [FINAL_PERFORMANCE_SECURITY_AUDIT.md](FINAL_PERFORMANCE_SECURITY_AUDIT.md); local checks do not establish public-host readiness.
