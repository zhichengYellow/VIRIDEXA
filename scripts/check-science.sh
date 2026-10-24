#!/bin/sh
set -eu
python3 - <<'PY'
import json, urllib.request, urllib.error
base='http://127.0.0.1:18080'
for path in ['/api/v1/science/facts','/api/v1/science/plants','/api/v1/science/measurements','/api/v1/science/morphology?leafShape=ovate','/api/v1/science/relationships','/api/v1/science/coverage','/api/v1/science/lineage/absent/documents']:
 with urllib.request.urlopen(base+path) as response:
  assert response.status==200
  assert json.load(response)==[], 'No named scientific review has been performed; candidates must be isolated'
try:
 urllib.request.urlopen(base+'/api/v1/admin/science/candidates')
 raise AssertionError('Anonymous administrator access')
except urllib.error.HTTPError as e:
 assert e.code in (401,403)
print('PASS: seven public science endpoints, unpublished candidate isolation, anonymous admin denial')
PY
