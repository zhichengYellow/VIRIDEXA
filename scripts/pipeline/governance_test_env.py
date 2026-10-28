#!/usr/bin/env python3
"""Explicit isolated technical environment; never writes fixtures into viridexa."""
import argparse,json,os,secrets,subprocess,uuid
from pathlib import Path
from import_candidates import literal
ROOT=Path(__file__).resolve().parents[2]
PRIVATE=ROOT/'.env.governance-test'
def db(query):
 r=subprocess.run(['docker','exec','-i','viridexa-db-v1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names'],input=query,text=True,capture_output=True)
 if r.returncode:raise RuntimeError(r.stderr)
 return r.stdout.strip()
def read():return dict(line.split('=',1) for line in PRIVATE.read_text().splitlines() if '=' in line)
def checked(e):
 schema=e['GOVERNANCE_TEST_SCHEMA']
 if not schema.startswith('viridexa_governance_test_') or not schema.replace('_','').isalnum():raise RuntimeError('Refusing non-test schema')
 return schema
p=argparse.ArgumentParser(description=__doc__);p.add_argument('action',choices=['setup','seed','cleanup']);p.add_argument('--graph',action='store_true',help='Clone source metadata and selected ontology projections for isolated graph tests');p.add_argument('--report-directory',type=Path,default=ROOT/'reports/phase2b');a=p.parse_args()
if a.action=='setup':
 if PRIVATE.exists():raise SystemExit('Existing isolated environment; seed or cleanup it first')
 suffix=uuid.uuid4().hex[:10];schema='viridexa_governance_test_'+suffix;secret=secrets.token_hex(32)
 e={'GOVERNANCE_TEST_SCHEMA':schema,'GOVERNANCE_TEST_DB_URL':f'jdbc:mysql://127.0.0.1:3309/{schema}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC',
 'GOVERNANCE_TEST_RO':'vg_ro_'+suffix,'GOVERNANCE_TEST_WRITER':'vg_rw_'+suffix,'GOVERNANCE_TEST_MIGRATOR':'vg_mg_'+suffix,'GOVERNANCE_TEST_ACCOUNT':'vg_ac_'+suffix,'GOVERNANCE_TEST_PASSWORD':secret,
 'GOVERNANCE_FIXTURE_PASSWORD':secrets.token_hex(18),'VIRIDEXA_PROTECTED_ROOT':str(ROOT)}
 db('CREATE DATABASE '+schema+';'+''.join("CREATE USER '"+e[k]+"'@'%' IDENTIFIED BY '"+secret+"';" for k in ['GOVERNANCE_TEST_RO','GOVERNANCE_TEST_WRITER','GOVERNANCE_TEST_MIGRATOR','GOVERNANCE_TEST_ACCOUNT'])+
  f"GRANT SELECT ON {schema}.* TO '{e['GOVERNANCE_TEST_RO']}'@'%';GRANT SELECT ON {schema}.* TO '{e['GOVERNANCE_TEST_WRITER']}'@'%';GRANT ALL PRIVILEGES ON {schema}.* TO '{e['GOVERNANCE_TEST_MIGRATOR']}'@'%';")
 PRIVATE.write_text('\n'.join(k+'='+v for k,v in e.items())+'\n');os.chmod(PRIVATE,0o600)
 print('Created isolated test schema and private environment file; no production fixtures.')
elif a.action=='seed':
 e=read();schema=checked(e)
 required_version='12' if a.graph else '8'
 if db(f"SELECT COUNT(*) FROM {schema}.flyway_schema_history WHERE version='{required_version}' AND success=1;")!='1':raise SystemExit('Required Flyway migration has not completed; refusing fixture writes')
 # Seed only after the isolated application has run all Flyway migrations.
 if db(f'SELECT COUNT(*) FROM {schema}.app_user;')!='0':raise SystemExit('Test users already exist; not reseeding')
 ids=db("SELECT c.id FROM viridexa.scientific_candidate c JOIN viridexa.scientific_source_version s ON s.id=c.source_id WHERE c.pipeline='MORPHOLOGY' AND c.normalization_state='CANDIDATE' AND s.path LIKE '%combined.zip' ORDER BY c.id LIMIT 1;").splitlines()
 for kind in ['OIL_CONTENT','FAME_COMPOSITION']:
  ids+=db(f"SELECT c.id FROM viridexa.scientific_candidate c JOIN viridexa.scientific_lipid_observation o ON o.candidate_id=c.id WHERE o.kind='{kind}' AND o.value_numeric IS NOT NULL ORDER BY c.id LIMIT 1;").splitlines()
 ids+=db("SELECT id FROM viridexa.scientific_candidate WHERE normalization_state='HELD' AND pipeline='LIPID' ORDER BY id LIMIT 1;").splitlines()
 if len(ids)!=4 or any(len(x)!=64 for x in ids):raise RuntimeError('Required real examples missing')
 keys=','.join(literal(x) for x in ids)
 jar=ROOT/'apps/api/target/viridexa-api-0.1.0.jar'
 h=subprocess.run(['java','-Dloader.main=org.viridexa.api.governance.PasswordHash','-cp',str(jar),'org.springframework.boot.loader.launch.PropertiesLauncher'],input=e['GOVERNANCE_FIXTURE_PASSWORD']+'\n',text=True,capture_output=True)
 if h.returncode or not h.stdout.startswith('$2'):raise RuntimeError('Password helper failed')
 candidate_columns='id,source_id,pipeline,locator,raw_payload,normalized_payload,content_sha256,normalization_state,reason'
 query=f'USE {schema};START TRANSACTION;INSERT INTO scientific_source_version SELECT * FROM viridexa.scientific_source_version;INSERT INTO scientific_candidate({candidate_columns}) SELECT {candidate_columns} FROM viridexa.scientific_candidate WHERE id IN ({keys});'
 query+=f'INSERT INTO scientific_sample SELECT sm.* FROM viridexa.scientific_sample sm WHERE sm.id IN(SELECT o.sample_id FROM viridexa.scientific_lipid_observation o WHERE o.candidate_id IN ({keys}));'
 query+=f'INSERT INTO scientific_lipid_observation SELECT * FROM viridexa.scientific_lipid_observation WHERE candidate_id IN ({keys});INSERT INTO scientific_evidence_link SELECT * FROM viridexa.scientific_evidence_link WHERE candidate_id IN ({keys});'
 if a.graph:
  query+='INSERT INTO source_asset SELECT a.* FROM viridexa.source_asset a;'
  query+=f'INSERT INTO ontology_projection SELECT * FROM viridexa.ontology_projection WHERE candidate_id IN ({keys});'
  # One real course sample and its existing FK chain; never infer a link to a scientific_sample.
  course="(SELECT id FROM viridexa.research_sample ORDER BY id LIMIT 1)"
  taxon_ids=db(f"WITH RECURSIVE ancestors AS (SELECT t.id,t.parent_id,0 AS depth FROM viridexa.taxon t WHERE t.id IN(SELECT sm.taxon_id FROM viridexa.research_sample sm WHERE sm.id={course} UNION SELECT p.taxon_id FROM viridexa.research_sample sm JOIN viridexa.plant p ON p.id=sm.plant_id WHERE sm.id={course}) UNION ALL SELECT parent.id,parent.parent_id,a.depth+1 FROM ancestors a JOIN viridexa.taxon parent ON parent.id=a.parent_id WHERE a.depth<12) SELECT DISTINCT id FROM ancestors ORDER BY id;").splitlines()
  if not taxon_ids or len(taxon_ids)>26 or any(not x.isdigit() for x in taxon_ids):raise RuntimeError('Unbounded or unresolved isolated taxonomy fixture')
  taxon_keys=','.join(taxon_ids)
  query+=f'INSERT INTO evidence SELECT e.* FROM viridexa.evidence e WHERE e.id IN(SELECT evidence_id FROM viridexa.taxon WHERE id IN ({taxon_keys}) UNION SELECT evidence_id FROM viridexa.research_sample WHERE id={course} UNION SELECT evidence_id FROM viridexa.measurement WHERE research_sample_id={course});'
  query+=f'INSERT INTO taxon SELECT * FROM viridexa.taxon WHERE id IN ({taxon_keys}) ORDER BY id;'
  query+=f'INSERT INTO plant SELECT * FROM viridexa.plant WHERE id IN(SELECT plant_id FROM viridexa.research_sample WHERE id={course});'
  query+=f'INSERT INTO research_source SELECT * FROM viridexa.research_source WHERE id IN(SELECT research_source_id FROM viridexa.research_study WHERE id IN(SELECT research_study_id FROM viridexa.research_sample WHERE id={course}));'
  query+=f'INSERT INTO research_study SELECT * FROM viridexa.research_study WHERE id IN(SELECT research_study_id FROM viridexa.research_sample WHERE id={course});'
  query+=f'INSERT INTO research_sample SELECT * FROM viridexa.research_sample WHERE id={course};INSERT INTO measurement SELECT * FROM viridexa.measurement WHERE research_sample_id={course};'
 for email,role in [('automated-isolated-fixture@example.invalid','ADMIN'),('researcher-fixture@example.invalid','RESEARCHER')]:
  query+='INSERT INTO app_user(email,display_name,password_hash) VALUES('+','.join(literal(x) for x in [email,'ISOLATED TECHNICAL FIXTURE — NOT HUMAN REVIEW',h.stdout])+');'
  query+=f"INSERT INTO user_role SELECT LAST_INSERT_ID(),id,CURRENT_TIMESTAMP(6) FROM app_role WHERE role_key='{role}';"
 query+='COMMIT;'
 for table in ['scientific_review_state','scientific_decision','source_governance','source_license_event']:query+=f"GRANT INSERT ON {schema}.{table} TO '{e['GOVERNANCE_TEST_WRITER']}'@'%';"
 query+=f"GRANT UPDATE(state,version) ON {schema}.scientific_review_state TO '{e['GOVERNANCE_TEST_WRITER']}'@'%';GRANT UPDATE(version) ON {schema}.source_governance TO '{e['GOVERNANCE_TEST_WRITER']}'@'%';GRANT UPDATE(license_state) ON {schema}.scientific_source_version TO '{e['GOVERNANCE_TEST_WRITER']}'@'%';"
 query+=f"GRANT SELECT ON {schema}.plant TO '{e['GOVERNANCE_TEST_WRITER']}'@'%';GRANT SELECT ON {schema}.taxon TO '{e['GOVERNANCE_TEST_WRITER']}'@'%';GRANT SELECT ON {schema}.evidence TO '{e['GOVERNANCE_TEST_WRITER']}'@'%';GRANT SELECT ON {schema}.source_asset TO '{e['GOVERNANCE_TEST_WRITER']}'@'%';GRANT SELECT,INSERT ON {schema}.review_event TO '{e['GOVERNANCE_TEST_WRITER']}'@'%';GRANT UPDATE(review_state,visibility_state) ON {schema}.plant TO '{e['GOVERNANCE_TEST_WRITER']}'@'%';GRANT UPDATE(identity_state) ON {schema}.taxon TO '{e['GOVERNANCE_TEST_WRITER']}'@'%';"
 for table in ['app_user','app_role','user_role']:query+=f"GRANT SELECT ON {schema}.{table} TO '{e['GOVERNANCE_TEST_ACCOUNT']}'@'%';"
 query+=f"GRANT EXECUTE ON PROCEDURE {schema}.register_public_member TO '{e['GOVERNANCE_TEST_ACCOUNT']}'@'%';"
 db(query)
 a.report_directory.mkdir(parents=True,exist_ok=True)
 (a.report_directory/'isolated-environment.json').write_text(json.dumps({'schema':schema,'fixture_candidate_ids':ids,'technical_fixture_only':True,'production_writes':False,'graph_metadata':a.graph},indent=2))
 print('Seeded four real-source examples and explicit automated fixture users into isolated schema only.')
else:
 e=read();schema=checked(e);db('DROP DATABASE '+schema+';'+''.join("DROP USER IF EXISTS '"+e[k]+"'@'%';" for k in ['GOVERNANCE_TEST_RO','GOVERNANCE_TEST_WRITER','GOVERNANCE_TEST_MIGRATOR','GOVERNANCE_TEST_ACCOUNT']));PRIVATE.unlink();print('Removed isolated schema and temporary DB identities; protected inputs untouched.')
