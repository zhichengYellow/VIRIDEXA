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
assert payload['total'] > 0, 'VIRIDEXA research-preview plant total is empty'
assert len(payload['items']) == 1, 'expected one real plant from limit=1'
plant = payload['items'][0]
assert plant['id'] > 0 and plant['nameZh'], 'plant identity fields are empty'
assert plant.get('scientificNameStatus'), 'scientific-name review status is missing'
assert plant['recordStatus'] in ('EXTRACTED', 'REVIEW_PENDING', 'APPROVED'), 'endpoint returned an unknown record state'
with urllib.request.urlopen(api + '/api/v1/plants?query=%E8%8B%8F%E9%93%81&limit=5', timeout=15) as response:
    search = json.load(response)
assert search['total'] == 1 and search['items'][0]['nameZh'] == '苏铁', 'name search did not return the source-backed course record'
with urllib.request.urlopen(api + f"/api/v1/plants/{plant['id']}", timeout=15) as response:
    detail = json.load(response)
assert detail['id'] == plant['id'], 'plant detail endpoint did not return the searched VIRIDEXA record'
with urllib.request.urlopen(web + '/api/v1/plants?limit=1', timeout=15) as response:
    proxied = json.load(response)
assert proxied['items'][0]['id'] == plant['id'], 'Vue dev proxy did not reach the same API record'
with urllib.request.urlopen(web, timeout=15) as response:
    html = response.read().decode('utf-8')
assert 'VIRIDEXA' in html, 'brand shell was not served'
print(f"API: VIRIDEXA id={plant['id']}; record status={plant['recordStatus']}; scientific-name status={plant['scientificNameStatus']}; count={payload['total']}")
print(f"API search/detail: Chinese-name search and /{plant['id']} detail passed")
print(f"Vue proxy: returned the same database record id={proxied['items'][0]['id']}")
print('Web: VIRIDEXA app shell served successfully')
PY
