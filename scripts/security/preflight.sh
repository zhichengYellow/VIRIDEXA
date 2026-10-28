#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
fail() { printf 'FAIL: %s\n' "$1" >&2; exit 1; }
need() { [[ -n "${!1:-}" ]] || fail "required environment variable is unset: $1"; }

command -v docker >/dev/null 2>&1 || fail 'docker is required'
docker compose version >/dev/null 2>&1 || fail 'Docker Compose v2 is required'
for name in MYSQL_ROOT_PASSWORD MYSQL_APP_PASSWORD MYSQL_MIGRATOR_PASSWORD MYSQL_IMPORTER_PASSWORD MYSQL_ACCOUNT_PASSWORD MYSQL_REVIEW_PASSWORD WEB_ORIGIN TURNSTILE_SITE_KEY TURNSTILE_SECRET TURNSTILE_HOSTNAMES; do need "$name"; done
[[ "${SESSION_COOKIE_SECURE:-}" == true ]] || fail 'SESSION_COOKIE_SECURE must be true'
[[ "$WEB_ORIGIN" == https://* ]] || fail 'WEB_ORIGIN must use https'
python3 - "$TURNSTILE_HOSTNAMES" "$WEB_ORIGIN" <<'PY'
import sys
from urllib.parse import urlparse
hosts = [item.strip() for item in sys.argv[1].split(',')]
origin = urlparse(sys.argv[2]).hostname
if not hosts or any(not h or h != h.lower() or '/' in h or ':' in h for h in hosts):
    raise SystemExit('FAIL: TURNSTILE_HOSTNAMES must contain lowercase hostnames')
if len(hosts) != len(set(hosts)) or origin not in hosts:
    raise SystemExit('FAIL: hostnames must be unique and include WEB_ORIGIN hostname')
PY
compose_files=(-f compose.yaml -f compose.production.yaml)
if [[ "${1:-}" == --tunnel ]]; then
  need CLOUDFLARE_TUNNEL_TOKEN
  compose_files+=(-f compose.tunnel.yaml)
elif [[ -n "${1:-}" ]]; then
  fail 'supported option: --tunnel'
fi
# Parse resolved Compose internally; report only policy failures, never interpolated values.
config="$(docker compose "${compose_files[@]}" config --format json 2>/dev/null)" || fail 'production Compose configuration is invalid'
python3 -c 'import json,sys
c=json.load(sys.stdin); sv=c["services"]
for name in ("db","api","web"):
    if sv[name].get("ports"): raise SystemExit("FAIL: production service publishes a host port: "+name)
if sv["web"].get("read_only") is not True or sv["web"].get("user") != "101:101": raise SystemExit("FAIL: Nginx must run read-only as UID/GID 101")
if sv["api"].get("environment",{}).get("API_DOCS_ENABLED") != "false": raise SystemExit("FAIL: API docs must be disabled")
if sv["api"].get("environment",{}).get("SESSION_COOKIE_SECURE") != "true": raise SystemExit("FAIL: secure session cookies must be enabled")
if "api_egress" not in sv["api"].get("networks",{}): raise SystemExit("FAIL: API outbound egress network is missing")
if not c.get("networks",{}).get("database",{}).get("internal"): raise SystemExit("FAIL: database isolation network is missing")
if "no-new-privileges:true" not in sv["web"].get("security_opt",[]): raise SystemExit("FAIL: Nginx no-new-privileges is required")
if not sv["api"].get("read_only") or "ALL" not in sv["api"].get("cap_drop",[]): raise SystemExit("FAIL: API read-only filesystem and dropped capabilities are required")
for name in ("api","web","db"):
    options=sv[name].get("logging",{}).get("options",{})
    if options.get("max-size")!="10m" or options.get("max-file")!="3": raise SystemExit("FAIL: bounded container logs are required: "+name)
' <<<"$config" || exit 1
unset config
[[ -f infra/security/nginx-trusted-proxies.conf ]] || fail 'missing default trusted-proxy include'
[[ -f infra/nginx/web.conf.template ]] || fail 'missing Nginx deployment template'

printf 'Production preflight passed; no secrets were printed.\n'
