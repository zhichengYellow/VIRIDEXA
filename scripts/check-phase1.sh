#!/bin/bash
set -euo pipefail

API_URL="${API_URL:-http://127.0.0.1:18080}"
WEB_URL="${WEB_URL:-http://127.0.0.1:5173}"

python3 - "$API_URL" "$WEB_URL" <<'PY'
import json
import sys
import urllib.request

api, web = sys.argv[1:]
with urllib.request.urlopen(api + '/api/v1/plants?limit=1', timeout=15) as response:
    payload = json.load(response)
assert payload['total'] > 0, 'verified plant total is empty'
assert len(payload['items']) == 1, 'expected one real plant from limit=1'
plant = payload['items'][0]
assert plant['id'] > 0 and plant['nameZh'], 'plant identity fields are empty'
assert plant['verificationStatus'] == 'VERIFIED', 'endpoint returned a non-verified plant'
assert plant.get('scientificNameStatus'), 'scientific-name review status is missing'
with urllib.request.urlopen(web + '/api/v1/plants?limit=1', timeout=15) as response:
    proxied = json.load(response)
assert proxied['items'][0]['id'] == plant['id'], 'Vue dev proxy did not reach the same API record'
with urllib.request.urlopen(web, timeout=15) as response:
    html = response.read().decode('utf-8')
assert 'VIRIDEXA' in html, 'brand shell was not served'
print(f"API: source row id={plant['id']}; record status={plant['verificationStatus']}; scientific-name status={plant['scientificNameStatus']}; count={payload['total']}")
print(f"Vue proxy: returned the same database record id={proxied['items'][0]['id']}")
print('Web: VIRIDEXA app shell served successfully')
PY
