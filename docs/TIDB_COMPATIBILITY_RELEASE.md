# TiDB full-data compatibility release · 2026-10-31

VIRIDEXA uses Cloudflare for the Vue frontend, with an optional Render Free Java service and TiDB Cloud Starter for the existing full dataset. This is an incremental compatibility release, not a new backend or database model. This source release excludes credentials, private snapshots and raw scientific files. No paid-tier upgrade, model request, scientific approval or protected raw-file edit is part of this work.

## Actual cloud findings

The supplied endpoint connected successfully with `sslMode=VERIFY_IDENTITY`. Its actual server reports `8.0.11-TiDB-v8.5.3-serverless`. Version alone was insufficient to determine feature behavior, so bounded disposable-schema tests were executed:

| Capability | Actual result |
|---|---|
| Verified TLS and authentication | Passed |
| Foreign-key checks | Enabled; invalid child rejected, error1452 |
| CHECK | Initially OFF; `SET GLOBAL tidb_enable_check_constraint=ON` succeeded; invalid value rejected, error3819 |
| Column grants | GRANT syntax and SHOW GRANTS accept them, but the attempted permitted column INSERT failed with1142; not accepted as usable |
| Table grants | Provisioned and exercised using separate technical identities |
| JDBC session READ ONLY | Unsupported; do not enable no-op SQL globally to hide this |

Only the integrity-enabling CHECK switch was changed. No integrity flag was disabled. Probe schemas and their successfully created SQL identities were removed after their tests. An initial unprefixed CREATE USER and an overlong technical username failed; the final provisioner uses the required tenant prefix and short names.

The original CLI's8.5.6 column-grant requirement remains the default permission strategy. For this tested Starter target, explicitly selecting `TIDB_PERMISSION_MODE=table-scoped` permits8.5+ with enforced CHECK/FKs. This is a documented permission change, not a claim that8.5.3 implements column restrictions. See `scripts/tidb/provision-grants.starter.example.sql`.

### Final target and migration

- Project database: `viridexa_tidb_20261031_v2`.
- Four dedicated target Flyway baseline migrations **successfully applied on actual TiDB**.
- The earlier `viridexa_tidb_20261031` attempt failed during V3 because a JSON text/ASCII key comparison encountered incompatible collations. It contains no imported business data and is retained for diagnosis; no automatic clean/repair or schema deletion was performed.
- The existing `sys` database was used only as a connection/bootstrap context. No project tables or business records were inserted into it.
- Full import and subsequent API results are recorded in the acceptance section below; baseline migration success alone is not data-release acceptance.

## Compatibility implementation

### Registration and permissions

MySQL keeps its original fixed-MEMBER registration procedure. `application-tidb.yml` chooses portable enrollment: account insertion and `public_member_account` insertion are one Spring JDBC transaction. V26 adds only that enrollment table to the normal MySQL migration chain. Existing MySQL V1–V25 remain unchanged.

`AccountRoles` adds only a fixed MEMBER membership for portable registrations. It never accepts a client-selected role. Existing real administrator roles remain in `user_role`; no human administrator was created here.

Starter uses three separate SQL identities, with generated credentials stored only in ignored `.runtime/render-tidb.env` (0600, parent0700):

- Public datasource: SELECT-only, no grant delegation or inherited writer role.
- Member writer: SELECT on four identity tables and INSERT only on `app_user` and `public_member_account`. No UPDATE/DELETE, `user_role` writes, role writes, science writes or DDL.
- Review writer: SELECT and narrowly enumerated business-table INSERT/UPDATE privileges from the checked template. No raw-candidate writes, role writes, schema-wide writes, DELETE, DDL or delegation.

**Tradeoff:** table-level INSERT/UPDATE privileges cover all columns of the explicitly listed writable tables, rather than the original column allowlist. Spring's fixed prepared SQL, authentication, CSRF, validation, optimistic locks and review audit remain mandatory. This is suitable for the bounded course-demo deployment; it is weaker against compromise of a writer credential than true column privileges. Do not claim identical database isolation. Root/migrator credentials are never passed to the running API.

Real permission checks confirmed that all three runtime identities are denied role writes and raw-candidate updates, and the public identity is denied account insertion. TiDB returns8121 for some permission failures; the checker accepts this only when its message explicitly identifies a privilege check.

### Views, charsets and integrity

TiDB does not implement the original JSON_TABLE view. Two explicit graph view replacements use a small indexed ordinal table (capacity1000). They retain source candidate IDs, raw terms, concepts, 1-based ordinality and the existing live visibility/publication rules. A CHECK rejects larger future arrays instead of silently truncating them. The actual source maximum is16.

TiDB also rejected a JSON-derived UTF-8 string comparison with an original ASCII binary hash column. Target-only table generation converts these ASCII keys to `utf8mb4_bin` and adds `CHAR_LENGTH(column)=OCTET_LENGTH(column)` CHECK guards, retaining the ASCII domain and case-sensitive comparisons. Source data bytes, source MySQL schema and historical migrations are unchanged. All referenced key columns undergo the same deterministic transformation. Old DEFINER names and source database qualifiers are removed from target view definitions; scientific release filters are retained.

The TiDB profile avoids JDBC's unsupported session READ ONLY syntax using `hikari.read-only=false` and `readOnlyPropagatesToServer=false`. The **database SELECT-only grants**, verified again by the startup safety bean, enforce public query read-only access. This does not authorize writes or disable transaction integrity. CHECK and FK startup gates remain enabled.

## Full sealed dataset

Use the latest compatible private bundle:

`data/backups/tidb-release-20261031-v4/`

- **41 business tables;849,690 source rows;15 views**.
- 60,031 total plants,60,045 taxa,83,817 plant-name rows.
- 95,238 scientific candidates,7,068 scientific samples,94,798 source-access rows,21 media registrations.
- 0 source application users and0 scientific decisions. No named scientific review was synthesized.
- Compressed SQL:89,029,068bytes;657 bounded INSERT statements;maximum statement1,046,211bytes.
- Five files sealed by SHA-256: four target Flyway migrations and the compressed data file.

V4 derives from the previously validated V2 snapshot; `derivedFromManifestSha256` records its provenance, and the compressed business-data file is unchanged. Earlier bundles remain private backups. A separately completed V3 export is retained but is **not the selected cloud release**; use V4 with uniquely named ASCII guards.

`export_release.py` can create new snapshots with the same transformations. `adapt_bundle.py` derives a fresh target-compatible bundle from an existing sealed snapshot without overwriting it. Both refuse existing output paths. Private bundles live under ignored `data/` and are excluded from frontend and API image builds.

Source Flyway history is archived in the manifest, not inserted as target migration history. The target has its own V1–V4 baseline. The exporter checks FK dependency order and rejects source account data without a separate privacy plan. It never disables constraints or uses REPLACE/INSERT IGNORE. The dump is UTC, and the importer sets session UTC.

## Operator commands

Credentials are server-only. The private `.runtime/tidb.env` contains the migrator connection, and `.runtime/render-tidb.env` contains the separate runtime connections. Neither belongs in Git, Vue, screenshots or public logs. The provisioner does not create an application administrator.

```sh
# Local/offline checks
python3 scripts/tidb/run_release.py validate-bundle --bundle data/backups/tidb-release-20261031-v4
python3 -m unittest discover -s scripts/tidb -p 'test_*.py'

# Load secure local migration settings
set -a
. .runtime/tidb.env
set +a
python3 scripts/tidb/run_release.py preflight
python3 scripts/tidb/run_release.py status

# Fresh targets only; CREATE DATABASE refuses an existing name
python3 scripts/tidb/run_release.py create-target --apply
python3 scripts/tidb/run_release.py migrate --bundle data/backups/tidb-release-20261031-v4 --apply
python3 scripts/tidb/provision_runtime.py --apply
python3 scripts/tidb/run_release.py import --bundle data/backups/tidb-release-20261031-v4 --apply
python3 scripts/tidb/run_release.py verify --bundle data/backups/tidb-release-20261031-v4
python3 scripts/tidb/cloud_smoke.py
```

Do not repeat `create-target` on an existing database. `migrate` never performs clean/repair. The CLI rejects system databases for migration/import, requires verified TLS and explicit write flags, and reports only sanitized SQL errors. `inspect-target` can inspect `sys` using query-only code.

The importer commits each sealed INSERT and its checkpoint in the **same transaction**. A connection failure or deliberate local process interruption can be resumed from committed hashes; it never upserts reviewed data. Completed checkpoint hashes are read once per run and compared against the sealed dump. A different manifest, altered checkpoint or invalid sequence is rejected. MySQL contract tests exercise rollback, resume, duplicate import and tampering.

The target advertises protocol compression; a real compressed connection was verified. `useCompression=true` is used for the migration connection. The initial uncompressed upload was interrupted after47 committed batches, then resumed with compression using the same sealed data. No claimed production throughput percentage is derived from this trial.

`verify` compares every source-table count plus candidate content/status, source-access relationship/content and ontology fingerprints. This goes beyond counts, but is not a cryptographic canonical checksum of every target JSON value or a scientific review. Keep initial target traffic/writes disabled until verification completes.

## Test evidence

- Protected-file rehash: **292/292 matched**,222,484,424bytes; source writes0.
- Latest Maven verify: **184 tests passed**,0 failures/errors/skips; log `/tmp/viridexa-tidb-cloud-maven.log`.
- Selected V4 bundle: **7 isolated MySQL portable-account tests passed**;3 real-source morphology candidates produced the same8 trait rows as the original view; null/scalar cases matched; overflow rejected. Actual resumable-importer transaction contracts passed. These are local MySQL tests, not substitutes for TiDB execution.
- **6 Python compatibility tests and6 Render packaging tests passed**.
- **14 offline target/TLS policy checks passed**, covering system-database write rejection and ambiguous/insecure TLS; they make no cloud calls.
- Current frontend rerun:59 tests and production build passed (44 files,2,631,488bytes). No frontend behavior change was required for the TiDB compatibility follow-up.
- Cloud permission and integrity results above are **real TiDB tests**. `run_cloud_account_contract.py --apply` additionally passed fixed-MEMBER enrollment, duplicate rejection, insertion/enrollment transaction rollback and concurrent uniqueness using the actual `AccountStore` on disposable TiDB tables. All cloud account fixtures and their SQL identity were removed; production application-user writes remained0.
- A low-memory local JVM connected to real TiDB: health/security/Agent metadata/public GET paths returned200; anonymous admin and protected sample requests returned401. This initial check occurred **during import**, so empty plant/graph results were intentionally not accepted as final data validation.

Raw local results stay in ignored `reports/tidb-cloud/` and `reports/tidb-compatibility/`. No fixture rows were added to the project cloud database.


## Final real-cloud acceptance

**Full TiDB import and verification passed.**657 statements completed:47 previously committed batches were resumed and610 newly applied. A second complete import applied **0** new batches and skipped657 matching checkpoints. Post-repeat counts and content/status fingerprints still matched; no scientific approval or production account was added.

| Actual target metric | Count |
|---|---:|
| Source business tables / matching rows |41 /849,690|
| Public plant directory |60,028|
| Public source records |94,798|
| Public plants with Chinese common names |23,701|
| Scientific samples / unlinked samples |7,068 /7,068|
| Formal scientific facts |0|
| Application users |0|
| Media registrations (not uploaded image bytes) |21|
| Unknown-license source versions |18|

Nine public/quality aggregates were independently compared with the current source MySQL and matched, including live public views and Chinese-name coverage. This is in addition to all41 base-table counts and candidate/access/projection fingerprints.

### Real HTTP regression and fixes

A local JVM using the Render memory flags and actual TiDB passed **16/16** checks, with real `银杏` results and plant ID2 (`Ginkgo biloba`). The same16 checks passed against the original MySQL with the new JAR. No database fixtures were inserted by these checks.

The checks include name search/suggestions, detail, source evidence, graph entry/expansion, workbench overview, showcase, health and model-disabled metadata. Anonymous protected samples/admin/account/metrics requests return401; registration without CSRF returns403. Advanced signed-in HTTP workflows and real human review are not claimed by these anonymous checks; real portable account transactions were tested separately in disposable TiDB tables.

Initial HTTP failures were retained in private reports and **fixed, not skipped**:

1. Source taxonomy used scalar subqueries in JOIN ON, rejected by TiDB1105. `PlantSourcesController` now uses three bounded PK joins with UNION, retaining the same species/parent/grandparent set.
2. Plant graph expansion exceeded the free instance memory limit8176. `GraphService` adds a redundant bound canonical-name predicate to let the existing name index narrow candidates before public-source view expansion. Original public filters and exact-name checks remain. TiDB-only sessions use executor/scan concurrency2; no instance-memory limit or paid capacity was increased.
3. Workbench overview exceeded the original5s statement timeout. The TiDB profile uses **10s per statement**, with a **20s workbench transaction**. MySQL retains its original5s/10s defaults. The response remains bounded; no timeout was disabled.
4. `DatabaseFailureHandler` logs only exception class, SQLState and vendor code for diagnosis, without SQL payloads or credentials.

Single final local-to-TiDB observations: startup12.72s; Chinese search1.83s; source detail1.98s; graph expansion8.67s; workbench overview11.87s; showcase5.81s. These are **single observations, not p95, not production benchmarks, and not percentage improvements**. Some aggregate/graph requests remain slow on this free cross-region setup. The optional Render Blueprint selects Singapore rather than default Oregon to place the backend in Asia, but no deployed latency benefit is asserted.

Current evidence: `reports/tidb-cloud/checks.json`, `verify.log`, `reimport.log`, `post-reimport-verify.log`, `view-parity.json`, `smoke.json`, `source-mysql/smoke.json`, and `protected-hashes.json` (all ignored private reports). The API test processes were stopped afterward; the existing Compose services and unrelated project containers were not replaced.

## Remaining deployment boundaries

Render has not yet been deployed and Cloudflare's optional API bridge has not been pointed at a real Render service. A fresh bounded image-build attempt again failed while fetching a Docker Hub authentication token (IPv6 connection timeout), before compiling the image; an actual512MiB Linux container run remains outstanding. The private log is `reports/tidb-cloud/render-image-build.log`. Local JVM tests are not cloud cold-start or container-memory benchmarks.

Render Free remains `AGENT_MODEL_ENABLED=false`: its ephemeral disk cannot safely preserve the existing paid/free remote-call quota ledger. No remote LLM key is packaged or required. Real model availability is not claimed.

Confirm spending limit0/free allowance in the provider console; SQL flags cannot establish billing configuration. No tier upgrade/top-up/purchase API is invoked. Real Render session/CSRF/Turnstile, proxy-IP behavior, cold starts and persistent dynamic media still require deployment acceptance. See [Render deployment guide](RENDER_FREE_DEPLOYMENT.md).

## Official references checked2026-10-31

- [Starter default kernel and release channels](https://docs.pingcap.com/releases/tidb-cloud/)
- [Column-level privileges from8.5.6](https://docs.pingcap.com/tidb/stable/column-privilege-management/)
- [CHECK and FK variables](https://docs.pingcap.com/tidb/stable/system-variables/)
- [Starter username prefixes](https://docs.pingcap.com/tidbcloud/select-cluster-tier/)
- [Unsupported JSON_TABLE](https://docs.pingcap.com/tidb/stable/json-functions/)
- [MySQL compatibility](https://docs.pingcap.com/tidb/stable/mysql-compatibility/)
