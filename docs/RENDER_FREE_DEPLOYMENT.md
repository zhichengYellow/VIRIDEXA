# Render Free + external database trial · 2026-10-31

## Scope and current status

The TiDB full-data compatibility release has completed:41 business tables /849,690 rows,60,028 public plants and94,798 public source records. Real TiDB-backed and original MySQL-backed HTTP checks each passed16/16. Formal scientific facts remain0. See [the detailed TiDB acceptance report](TIDB_COMPATIBILITY_RELEASE.md).

The Vue frontend remains on Cloudflare. Render deployment files are ready for source-based deployment; **a Render backend has not yet been deployed**. Local Docker Hub image pulls timed out, so Linux512MiB container acceptance and real Cloudflare-to-Render session/CSRF checks remain pending. The existing local Compose deployment and static-only Cloudflare configuration remain available.

Use the repository-root `render.yaml` for Blueprint setup, or the manual Web Service fields below. The Blueprint selects **Free**, Docker, Singapore, `render,tidb`, and `/actuator/health`. Supply database credentials and Turnstile settings only through Render server-side environment settings. Never upload `.runtime/`, private data/backups, or a root/migrator credential to GitHub. Remote model calls remain disabled on Render Free until their quota ledger has durable storage.

### Database selection context

TiDB was selected because the full local MySQL allocation was approximately1,136.08MiB, exceeding Aiven Free's advertised1GB disk allowance before operational headroom. A curated Aiven subset was considered but not exported. TiDB's stored-procedure, JSON_TABLE, charset, permission and resource differences were then adapted and tested; the compatibility details are in the linked report. No records or evidence were deleted to fit a smaller database, and no paid capacity was enabled.

## Packaging delivered

- `apps/api/Dockerfile.render`: source-based Maven build and `verify`; runtime uses a Java 21 JRE rather than shipping the JDK/Maven/source. Non-root UID 10001. Existing Compose Dockerfile is retained.
- `Dockerfile.render.dockerignore`: source-only build context; excludes local target artifacts, environment files and scientific inputs. Global API `.dockerignore` also correctly admits the actual release JAR for the existing Compose build.
- `deploy/render-start.sh`: explicit 224 MiB heap, 128 MiB metaspace, 32 MiB code cache, 16 MiB direct memory, 512 KiB thread stacks, Serial GC and exit-on-OOM. Requires verified external MySQL TLS and HTTPS frontend origin; refuses database administrator identity and remote-model credentials.
- `application-render.yml`: Render `$PORT`/0.0.0.0; readonly pool maximum 3/minimum idle 0; Tomcat max16/min2 threads; Flyway disabled in the running public service; secure session cookies; public health only, API docs disabled.
- `render.yaml`: optional Blueprint, `plan: free`, source Docker build context limited to `apps/api`, manual deployments, no database/disk/paid resources declared, secrets requested from the operator.

The account/review pools retain their existing separate identities, max2/max3 connections and lazy minimum idle0. Maximum application pool connections are therefore 8, excluding any separately operated migration process. Do not replace these identities with an administrator to bypass cloud permissions.

## Render console

For Blueprint deployment, select repository `zhichengYellow/VIRIDEXA` and the repository-root `render.yaml`. Its Dockerfile/context paths are explicitly repository-relative.

For manual Web Service creation:

| Field | Value |
| --- | --- |
| Runtime | Docker |
| Plan | Free |
| Root Directory | `apps/api` |
| Dockerfile Path | `./Dockerfile.render` |
| Docker Build Context | `.` within that root |
| Health Check | `/actuator/health` |
| Auto deploy | Off for the trial |

Docker handles Maven compilation and startup. Do not use a Node build command for the API. Render provides `PORT`; no public MySQL port is created by this configuration.

Set these in server-side settings, never in Vue or Git:

- `WEB_ORIGIN`: exact HTTPS Cloudflare frontend origin, no path.
- `DB_URL`: `jdbc:mysql://<external-host>:<port>/viridexa?sslMode=VERIFY_IDENTITY&serverTimezone=UTC` plus provider-required certificate trust settings. Never use `useSSL=false` for the external database. If a private CA is required, provision a Java truststore via a Render secret file and configure its truststore location/password server-side; verification failure must be fixed by supplying the correct CA, not by disabling verification.
- `DB_USERNAME` / `DB_PASSWORD`: read-only public query identity, not root/avnadmin.
- `DB_ACCOUNT_USERNAME` / `DB_ACCOUNT_PASSWORD`, `DB_REVIEW_USERNAME` / `DB_REVIEW_PASSWORD`: separate least-privilege identities. Verify permitted procedure execution and review grants on the selected provider.
- Real production Turnstile site key, secret and exact frontend hostname are required by the Blueprint. Register the workers.dev hostname if it is the frontend entry point. Do not substitute official test credentials for production.
- Use `SPRING_PROFILES_ACTIVE=render,tidb` for the selected TiDB path; keep the default MySQL profile separate. Keep `AGENT_MODEL_ENABLED=false`; do not provide an OpenRouter key.

Database schema/data preparation and dedicated migration authorization must precede API deployment. Automatic startup migrations are deliberately disabled: a successful health check does not prove that a new empty database has the required schema/data. The dedicated `scripts/tidb/` tools support the tested TiDB path; the original local-Docker provisioning scripts must not be assumed to create remote users.

## Cloudflare connection (optional configuration)

Static-only deployment still uses `apps/web/wrangler.jsonc`. After the actual Render service and database are ready, the optional bridge is:

```sh
cd apps/web
npm ci
npm run build
npx wrangler deploy --config wrangler.render.jsonc --dry-run
# Actual publishing is a separate step after configuring API_ORIGIN.
# npx wrangler deploy --config wrangler.render.jsonc
```

Set Worker server-side `API_ORIGIN` to the exact HTTPS `https://<service>.onrender.com` origin, no path. No database credentials are sent to Cloudflare/Vue. The optional Worker forwards only `/api/*` to the fixed origin; static SPA assets still use the asset binding. Missing origin gives **503 JSON**, rather than pretending an HTML SPA fallback is an API.

The bridge preserves browser session cookies, Origin and CSRF tokens; strips client-supplied forwarding headers; refuses redirects, arbitrary upstreams and unsupported methods; limits request body to 64 KiB and upstream wait to 25s; API responses use `no-store`. It contains no model/database logic. Proxy tests are deterministic mocks, not a live cloud login/CSRF integration result. Once deployed, verify actual login, register, logout, CSRF rejection and review authorization through the frontend origin before declaring the connection complete.

Do not configure wildcard trusted proxy CIDRs or blindly trust forwarded client IPs. This bridge does not yet establish an authenticated true-client-IP chain to Render, so backend IP limits can group visitors behind a proxy. Account quotas still apply; shared-IP false positives need verification during live acceptance. Do not relax those checks to make the demo appear successful.

Dynamic `/media/*` files and protected evidence files are **not** uploaded into this API image and are not proxied as though present. Existing curated frontend botanical assets still display; dynamic photos need a separately reviewed persistent-media plan. No raw PDF, database snapshot or protected data is packed into either image/static assets.

## Free-tier persistence and availability

Render Free sleeps after inactivity and its local filesystem is ephemeral. Sessions, Agent run state and local quotas can reset on wake/redeploy. For that reason **real remote model calls are explicitly disabled** in this deployment, even though the original durable-volume Compose deployment retains that capability. A reset budget must never permit extra model spending. The six read-only domain tools remain usable in retrieval mode once the actual database and authenticated session work.

The Worker times out after25s; a sleeping Render backend may take longer to wake, producing a truthful 504 and requiring retry. No artificial traffic generator is included to defeat free-service sleep policy. The public API may have larger first-query delays than the static frontend. This is a free demonstration trial, not a promise of always-on production availability.

## Initial packaging measurements (historical; current results below)

- Maven `verify` offline against the existing local dependency cache: **177 passed, 0 failed/errors/skipped**; JAR approximately39 MiB. This does not execute the separately managed MySQL integration suites.
- Five isolated packaging/guard tests passed: memory flags, remote-model refusal, verified/ambiguous TLS handling, HTTPS frontend and source-only/non-root image configuration.
- Frontend tests: **59 passed, 0 failed/skipped**, including four proxy tests for fixed upstreams, cookies/CSRF preservation, spoofed forwarding headers, no-store, request size, methods, failure and redirect handling.
- Frontend production build and static-asset audit passed:44 files,2,631,488 bytes. Existing large ECharts lazy-chunk warning remains.
- Optional Cloudflare proxy Wrangler dry-run passed. Local Wrangler check: `/api/v1/plants` without API_ORIGIN returned503 JSON; `/technology` returned200 SPA HTML. No cloud upload occurred.
- Single local host JVM trial, same Render low-memory flags/profile against the existing local MySQL using localhost-only non-TLS connection: health ready5.6s; sampled peak RSS199.86 MiB; real `Ginkgo` search200/930bytes/5178.89ms; security metadata200/23.13ms. No model calls. Process stopped after checks. These are single observations, **not p95, not Linux512MiB cgroup proof, and not a claimed percentage improvement**. Raw local logs/results stay in ignored `reports/render-trial/`.
- Full Docker image build attempted but **not passed**: Docker Hub authorization/token fetch timed out before base images could be downloaded. No Docker global configuration was changed, and no volumes/images were pruned to mask the error. Consequently no runtime image size or actual Render cold start is claimed.
- Current source/render configurations do not publish new facts, alter source files, erase scientific history or replace the current database.

## Remaining deployment steps

1. TiDB migration, full import, integrity/permission checks and repeat-import verification have passed. Confirm the actual free-plan spending settings and remaining allowance in the provider console; SQL tests cannot establish billing configuration.
2. Deploy the Free Render service from this repository and provide only the separate runtime credentials plus the production Turnstile settings.
3. Complete a real image build and512MiB container/cloud acceptance; the local Docker Hub connection issue is still unresolved.
4. Configure Cloudflare's server-side API_ORIGIN and publish the optional bridge. Verify login/session/CSRF, Turnstile, query limits, first-wake retries, shared-proxy IP behavior and dynamic-media limitations end-to-end.

Official references (checked2026-10-31):

- https://render.com/docs/docker
- https://render.com/docs/web-services
- https://render.com/docs/free
- https://render.com/docs/blueprint-spec
- https://aiven.io/docs/products/mysql/concepts/mysql-free-tier
- https://docs.pingcap.com/tidbcloud/mysql-compatibility/

## TiDB actual compatibility update · 2026-10-31

Verified TLS, dedicated target Flyway V1–V4, table-scoped least-privilege runtime identities and isolated real TiDB registration transaction tests have now run successfully. The target is Starter8.5.3; CHECK was enabled and actual CHECK/FK rejection verified. The8.5.3 column-grant syntax did not provide usable column INSERT, so Starter uses the explicit table-scoped template. The public datasource remains SELECT-only. Target ASCII hash keys retain their ASCII domain through CHECK guards after charset conversion for JSON comparisons.

Use `SPRING_PROFILES_ACTIVE=render,tidb` and the server-only values in ignored `.runtime/render-tidb.env`; **do not deploy the migrator/root credentials**. Do not copy `.runtime` into the image or Git. The TiDB profile suppresses unsupported JDBC READ ONLY propagation; direct readonly database grants enforce the boundary. Full import/verification and current acceptance status are tracked in [the TiDB report](TIDB_COMPATIBILITY_RELEASE.md). This supersedes the earlier requirement to wait for8.5.6 before any compatibility trial.

Maven now passes184 tests, current frontend rerun59 tests/build/static audit passes, and6 Render packaging tests pass. A Render service, public API origin, real Turnstile deployment and512MiB Linux container acceptance are still separate pending steps; database compatibility success does not claim the website backend is online.

The full target import has now passed:41 business tables /849,690 rows;60,028 public plants;94,798 public source records;23,701 public Chinese-named plants;0 formal scientific facts. Reimport applied0 new batches and all content/state checks still matched. Real TiDB-backed and source MySQL-backed HTTP smoke checks both passed16/16 after JOIN-ON compatibility, graph-memory and bounded TiDB timeout fixes. These are local JVM-to-database checks, not a deployed Render service or live Cloudflare integration. See the TiDB report for actual one-shot timings and known free-instance limits. `region: singapore` is set in the optional Blueprint; no measured deployed latency gain is claimed.
