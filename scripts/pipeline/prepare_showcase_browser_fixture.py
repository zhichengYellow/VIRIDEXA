#!/usr/bin/env python3
"""Prepare an existing isolated real-source clone for browser checks; never production."""
import json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
e=dict(x.split('=',1) for x in (ROOT/'.env.governance-test').read_text().splitlines() if '=' in x)
s=e['GOVERNANCE_TEST_SCHEMA']
if not s.startswith('viridexa_governance_test_') or not s.replace('_','').isalnum():raise SystemExit('Isolated schema required')
def db(q):
 r=subprocess.run(['docker','exec','-i','viridexa-db-v1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 --batch --skip-column-names'],input=q,text=True,capture_output=True)
 if r.returncode:raise RuntimeError(r.stderr[:1000])
 return r.stdout.strip()
assert 1<=int(db(f'SELECT COUNT(*) FROM {s}.scientific_candidate;'))<=10
q=f'''USE {s};START TRANSACTION;
DELETE FROM scientific_decision;DELETE FROM scientific_review_state;
UPDATE scientific_source_version SET license_state='UNKNOWN';
INSERT INTO source_record_access(candidate_id,visibility,knowledge_confidence,content_sha256,source_sha256,authorization_reference,display_basis)
SELECT c.id,'PUBLIC',IF(c.pipeline='MORPHOLOGY','MACHINE_EXTRACTED','SOURCE_REPORTED'),c.content_sha256,v.sha256,'ISOLATED_BROWSER_TECHNICAL_FIXTURE','ISOLATED_TEST_FIXTURE'
FROM scientific_candidate c JOIN scientific_source_version v ON v.id=c.source_id WHERE c.normalization_state='CANDIDATE'
ON DUPLICATE KEY UPDATE visibility='PUBLIC';
INSERT IGNORE INTO evidence SELECT e.* FROM viridexa.evidence e WHERE e.id IN(SELECT n.evidence_id FROM viridexa.plant_name n JOIN plant p ON p.id=n.plant_id);
INSERT IGNORE INTO plant_name SELECT n.* FROM viridexa.plant_name n JOIN plant p ON p.id=n.plant_id;
UPDATE plant SET visibility_state='PUBLIC',review_state='REVIEW_PENDING';
INSERT IGNORE INTO review_event(event_key,entity_type,entity_key,action,resulting_state,raw_payload)
SELECT SHA2(CONCAT('ISOLATED_BROWSER_METADATA:',p.record_key),256),'PLANT',p.record_key,'CATALOG_METADATA_RELEASE','METADATA_ONLY',JSON_OBJECT('scope','CATALOG_METADATA_ONLY','scientific_name',t.canonical_name,'source_sha256',a.sha256)
FROM plant p JOIN taxon t ON t.id=p.taxon_id JOIN evidence e ON e.id=t.evidence_id JOIN source_asset a ON a.id=e.source_asset_id;
COMMIT;
'''
db(q)
ids=db(f'SELECT id FROM {s}.scientific_candidate ORDER BY id;').splitlines()
report={'schema':s,'candidateIds':ids,'catalogMetadataOnly':True,'humanDecisions':0,'productionWrites':False}
(ROOT/'reports/showcase/browser-fixture-scope.json').write_text(json.dumps(report,indent=2))
print('Prepared guarded isolated browser fixture; no production changes')
