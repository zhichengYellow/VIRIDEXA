#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ENV_FILE="$ROOT/.env"

if [ -e "$ENV_FILE" ]; then
  printf '%s\n' '.env already exists; left unchanged.'
  exit 0
fi

ROOT_PASSWORD=$(openssl rand -hex 24)
READONLY_PASSWORD=$(openssl rand -hex 24)
umask 077
cat > "$ENV_FILE" <<EOF
MYSQL_ROOT_PASSWORD=$ROOT_PASSWORD
MYSQL_READONLY_PASSWORD=$READONLY_PASSWORD
LEGACY_SNAPSHOT_PATH=/tmp/viridexa-phase1-live-snapshot.sql.gz
EOF
printf '%s\n' 'Created a local-only .env with separate random database passwords.'
