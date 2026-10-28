#!/usr/bin/env python3
"""Disposable UI catalog fixture. Never scientific approval; refuses production schema."""
from pathlib import Path
import subprocess,json
root=Path(__file__).resolve().parents[2]
e=dict(line.split('=',1) for line in (root/'.env.governance-test').read_text().splitlines() if '=' in line)
schema=e['GOVERNANCE_TEST_SCHEMA']
if not (schema.startswith('viridexa_governance_test_') and schema.replace('_','').isalnum()):
    raise SystemExit('Refusing non-isolated schema')
query=f"""USE {schema}; START TRANSACTION;
UPDATE plant SET visibility_state='PUBLIC',review_state='APPROVED' WHERE id=1;
UPDATE taxon t JOIN plant p ON p.taxon_id=t.id SET t.identity_state='VERIFIED' WHERE p.id=1;
INSERT INTO review_event(event_key,entity_type,entity_key,action,resulting_state,actor_user_id,happened_at,note)
SELECT SHA2('PHASE6_DISPOSABLE_CATALOG_UI_FIXTURE',256),'PLANT',record_key,'TECHNICAL_FIXTURE','APPROVED',(SELECT id FROM app_user WHERE email='automated-isolated-fixture@example.invalid'),CURRENT_TIMESTAMP(6),'ISOLATED UI FIXTURE ONLY; NOT HUMAN SCIENTIFIC REVIEW' FROM plant WHERE id=1;
COMMIT;"""
r=subprocess.run(['docker','exec','-i','viridexa-db-v1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'],input=query,text=True,capture_output=True)
if r.returncode:raise SystemExit(r.stderr)
(root/'reports/phase6/catalog-fixture.json').write_text(json.dumps({'schema':schema,'plantId':1,'catalog_ui_fixture_only':True,'human_review':False,'production_writes':False},indent=2))
print('Prepared isolated catalog UI fixture only. Scientific facts not published.')
