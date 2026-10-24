"""Disposable schema only; never imports fixtures into viridexa."""
import subprocess
from pathlib import Path
import uuid
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from bulk_sql import insert,bulk_sql

schema = 'viridexa_test_' + uuid.uuid4().hex[:12]
def sql(query, account='root'):
    password = {'root':'MYSQL_ROOT_PASSWORD','viridexa_ro':'MYSQL_APP_PASSWORD','viridexa_importer':'MYSQL_IMPORTER_PASSWORD'}[account]
    command = f'MYSQL_PWD="${password}" mysql -u{account} --batch --skip-column-names'
    return subprocess.run(['docker','exec','-i','viridexa-db-v1','sh','-c',command],input=query,text=True,capture_output=True)
def ok(query):
    r=sql(query)
    if r.returncode: raise RuntimeError(r.stderr)
    return r.stdout.strip()
try:
    ok(f'CREATE DATABASE {schema};')
    for p in sorted(Path('apps/api/src/main/resources/db/migration').glob('V*.sql')):
        ok(f'USE {schema};\n'+p.read_text())
    prefix=f'USE {schema};\n'
    ok(prefix+"INSERT INTO scientific_source_version VALUES (REPEAT('a',64),'test','1','fixture',REPEAT('a',64),'PERMITTED','VERIFIED');")
    ok(prefix+"INSERT INTO scientific_candidate VALUES(REPEAT('b',64),REPEAT('a',64),'TAXONOMY',JSON_OBJECT('row',1),JSON_OBJECT(),JSON_OBJECT(),REPEAT('c',64),'CANDIDATE',NULL);")
    assert ok(prefix+'SELECT COUNT(*) FROM published_scientific_fact;')=='0'
    assert sql(prefix+"INSERT INTO scientific_decision(candidate_id,reviewer_id,action,rationale) VALUES(REPEAT('b',64),999,'APPROVED','test');").returncode != 0
    ok(prefix+"INSERT INTO app_user(email,display_name,password_hash) VALUES('fixture@example.invalid','Isolated test','disabled');")
    ok(prefix+"INSERT INTO scientific_decision(candidate_id,reviewer_id,action,rationale) VALUES(REPEAT('b',64),1,'APPROVED','isolated test');")
    assert ok(prefix+'SELECT COUNT(*) FROM published_scientific_fact;')=='1'
    ok(prefix+"INSERT INTO scientific_decision(candidate_id,reviewer_id,action,rationale) VALUES(REPEAT('b',64),1,'REVOKED','isolated test');")
    assert ok(prefix+'SELECT COUNT(*) FROM published_scientific_fact;')=='0'
    assert sql('INSERT INTO viridexa.app_role(role_key,description) VALUES("FORBIDDEN_TEST","test");','viridexa_ro').returncode != 0
    # A failed batch must roll back the preceding valid source insertion.
    bad=sql(prefix+"START TRANSACTION; INSERT INTO scientific_source_version VALUES(REPEAT('d',64),'test','2','fixture-v2',REPEAT('d',64),'UNKNOWN','VERIFIED'); INSERT INTO scientific_candidate VALUES(REPEAT('e',64),REPEAT('f',64),'LIPID',JSON_OBJECT(),JSON_OBJECT(),NULL,REPEAT('e',64),'HELD','missing'); COMMIT;")
    assert bad.returncode != 0
    assert ok(prefix+"SELECT COUNT(*) FROM scientific_source_version WHERE id=REPEAT('d',64);")=='0'
    ok(prefix+"INSERT INTO scientific_source_version VALUES(REPEAT('d',64),'test','2','fixture-v2',REPEAT('d',64),'UNKNOWN','VERIFIED');")
    assert ok(prefix+'SELECT COUNT(*) FROM scientific_source_version;')=='2'
    # Latest decision must not bypass unknown source license.
    ok(prefix+"INSERT INTO scientific_decision(candidate_id,reviewer_id,action,rationale) VALUES(REPEAT('b',64),1,'APPROVED','isolated test'); UPDATE scientific_source_version SET license_state='UNKNOWN' WHERE id=REPEAT('a',64);")
    assert ok(prefix+'SELECT COUNT(*) FROM published_scientific_fact;')=='0'
    # INSERT and UPDATE of named decisions are denied to ingestion credentials.
    assert sql("INSERT INTO viridexa.scientific_decision(candidate_id,reviewer_id,action,rationale) VALUES(REPEAT('0',64),1,'APPROVED','denied');",'viridexa_importer').returncode != 0
    assert sql("UPDATE viridexa.scientific_candidate SET normalization_state='CANDIDATE' WHERE 1=0;",'viridexa_importer').returncode != 0
    batch=[insert('scientific_candidate',['8'*64,'a'*64,'MORPHOLOGY','{"row":2}','{"field":"ovate"}','{"traits":{"leaf shape":"ovate"}}','7'*64,'CANDIDATE',None],'8'*64)]
    batch.append(insert('scientific_candidate',['6'*64,'a'*64,'LIPID','{}','{}',None,'5'*64,'HELD','r'*180],'6'*64))
    query=prefix+'START TRANSACTION;'+bulk_sql(batch)+'COMMIT;'
    ok(query);ok(query)
    assert ok(prefix+"SELECT COUNT(*) FROM scientific_candidate WHERE id=REPEAT('8',64);")=='1'
    assert ok(prefix+"SELECT JSON_TYPE(raw_payload) FROM scientific_candidate WHERE id=REPEAT('8',64);")=='OBJECT'
    assert ok(prefix+"SELECT LENGTH(reason) FROM scientific_candidate WHERE id=REPEAT('6',64);")=='180'
    print('PASS: V1-V7 MySQL migrations and 15 checks: pending isolation, reviewer FK, approval/revocation, readonly API, batch rollback, version retention, unknown-license isolation, importer review/write restrictions, batched replay, JSON structure and long reason preservation.')
finally:
    ok(f'DROP DATABASE IF EXISTS {schema};')
