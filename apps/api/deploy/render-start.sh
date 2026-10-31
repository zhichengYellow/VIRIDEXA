#!/bin/sh
set -eu
# Fail closed: the Free service loses local quota ledgers across restarts.
if [ "${AGENT_MODEL_ENABLED:-false}" != false ]; then
  echo 'Render Free: remote model calls require durable quotas; refusing to start.' >&2
  exit 1
fi
if [ -n "${OPENROUTER_API_KEY:-}" ]; then
  echo 'Render Free: do not provide a model API key in this deployment.' >&2
  exit 1
fi
: "${DB_URL:?Set the external JDBC URL with verified TLS}"
# Do not print the URL: some JDBC URLs contain credentials.
case "$DB_URL" in jdbc:mysql://*) ;; *) echo 'Expected a MySQL JDBC URL.' >&2; exit 1 ;; esac
tls_modes="$(printf '%s' "$DB_URL" | tr '?&' '\n' | grep -i '^sslmode=' || true)"
if [ "$tls_modes" != 'sslMode=VERIFY_IDENTITY' ]; then
  echo 'External MySQL requires one unambiguous sslMode=VERIFY_IDENTITY.' >&2
  exit 1
fi
case "$(printf '%s' "$DB_URL" | tr '[:upper:]' '[:lower:]')" in
  *usessl=false*|*verifyservercertificate=false*)
    echo 'Conflicting or insecure JDBC TLS settings.' >&2; exit 1 ;;
esac
: "${DB_USERNAME:?Set a read-only database user}"
: "${DB_PASSWORD:?Set the read-only database password}"
case "$DB_USERNAME" in root|*.root|avnadmin) echo 'Do not run the public API with a database administrator.' >&2; exit 1 ;; esac
: "${WEB_ORIGIN:?Set the exact Cloudflare HTTPS frontend origin}"
case "$WEB_ORIGIN" in https://*) ;; *) echo 'WEB_ORIGIN must use HTTPS.' >&2; exit 1 ;; esac
# Explicit heap/native budgets for a 512 MiB trial. No unbounded JAVA_OPTS expansion.
exec java -Xms64m -Xmx224m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=32m \
  -XX:MaxDirectMemorySize=16m -Xss512k -XX:+UseSerialGC -XX:ActiveProcessorCount=2 \
  -XX:+ExitOnOutOfMemoryError -jar /app/app.jar
