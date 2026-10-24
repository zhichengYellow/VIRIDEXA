#!/bin/bash
set -euo pipefail

CONTAINER="${LEGACY_MYSQL_CONTAINER:-nonfood-oilplants-pdf-mysql-20261009}"
OUTPUT="${SNAPSHOT_OUTPUT:-/tmp/viridexa-phase1-live-snapshot.sql.gz}"

if [[ -e "$OUTPUT" ]]; then
  gzip -t "$OUTPUT"
  printf 'Snapshot already exists; left unchanged: %s\n' "$OUTPUT"
  shasum -a 256 "$OUTPUT"
  exit 0
fi

TEMP_FILE=$(mktemp "${TMPDIR:-/tmp}/viridexa-snapshot.XXXXXX")
trap 'rm -f "$TEMP_FILE"' EXIT

docker exec "$CONTAINER" bash -euo pipefail -c '
  test -n "$MYSQL_DATABASE"
  test -n "$MYSQL_USER"
  test -n "$MYSQL_PASSWORD"
  MYSQL_PWD="$MYSQL_PASSWORD" mysqldump \
    --single-transaction --skip-lock-tables --quick --no-tablespaces \
    --set-gtid-purged=OFF "$MYSQL_DATABASE" | gzip -c
' > "$TEMP_FILE"

gzip -t "$TEMP_FILE"
mv -n "$TEMP_FILE" "$OUTPUT"
trap - EXIT
printf 'Read-only snapshot created: %s\n' "$OUTPUT"
shasum -a 256 "$OUTPUT"
