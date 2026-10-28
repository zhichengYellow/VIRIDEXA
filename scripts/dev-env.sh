#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ENV_FILE="$ROOT/.env"

python3 - "$ENV_FILE" "$ROOT" <<'PY'
from pathlib import Path
import os
import subprocess
import sys

env_file = Path(sys.argv[1])
root = Path(sys.argv[2])
values = {}
if env_file.exists():
    for line in env_file.read_text().splitlines():
        if not line or line.lstrip().startswith('#') or '=' not in line:
            continue
        key, value = line.split('=', 1)
        values[key.strip()] = value.strip()

for key in ('MYSQL_ROOT_PASSWORD', 'MYSQL_APP_PASSWORD', 'MYSQL_MIGRATOR_PASSWORD', 'MYSQL_IMPORTER_PASSWORD'):
    if not values.get(key):
        values[key] = subprocess.check_output(['openssl', 'rand', '-hex', '32'], text=True).strip()

data_dir = values.get('VIRIDEXA_DATA_DIR', './data')
values['VIRIDEXA_DATA_DIR'] = data_dir

for obsolete in ('MYSQL_READONLY_PASSWORD', 'LEGACY_SNAPSHOT_PATH'):
    values.pop(obsolete, None)

env_file.parent.mkdir(parents=True, exist_ok=True)
tmp = env_file.with_suffix('.env.tmp')
tmp.write_text('\n'.join(f'{key}={value}' for key, value in sorted(values.items())) + '\n')
os.chmod(tmp, 0o600)
tmp.replace(env_file)
print(f'Updated local-only environment file: {env_file.name}; secrets were preserved or generated and not displayed.')
PY
