#!/usr/bin/env python3
"""Disposable MySQL contract tests. Never claims to be a TiDB Cloud integration."""
import argparse,json,os,re,secrets,subprocess,uuid,tempfile,zipfile
from pathlib import Path
from compatibility import trait_support,trait_view,trait_count_view
ROOT=Path(__file__).resolve().parents[2]
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--bundle',type=Path,required=True);args=p.parse_args()
 from run_release import validate
 bundle=validate(args.bundle)
 suffix=uuid.uuid4().hex[:10];schema='viridexa_tidb_test_'+suffix
 ro='vt_ro_'+suffix;account='vt_ac_'+suffix;migrator='vt_mg_'+suffix;password=secrets.token_hex(32)
 def sql(text):
  r=subprocess.run(['docker','exec','-i','viridexa-db-v1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names'],input=text,text=True,capture_output=True)
  if r.returncode:
   code=re.search(r'ERROR (\d+)',r.stderr)
   raise RuntimeError('Isolated SQL failed / code '+(code.group(1) if code else 'unknown')+'; no payload printed')
  return r.stdout.strip()
 def rows(text):return [json.loads(x) for x in sql(text).splitlines() if x]
 created=False;report={'scope':'isolated MySQL portability contracts, not TiDB Cloud','productionWrites':False}
 try:
  sql('CREATE DATABASE '+schema+';'+''.join("CREATE USER '"+u+"'@'%' IDENTIFIED BY '"+password+"';" for u in [ro,account,migrator]));created=True
  sql('USE '+schema+';'+(bundle/'schema/V1__source_tables.sql').read_text()+(bundle/'schema/V2__tidb_portable_support.sql').read_text()+(bundle/'schema/V3__source_views.sql').read_text()+(ROOT/'scripts/tidb/import_checkpoints.sql').read_text())
  sql(f"GRANT SELECT ON {schema}.* TO '{ro}'@'%';GRANT ALL PRIVILEGES ON {schema}.* TO '{migrator}'@'%' WITH GRANT OPTION;")
  sql(f'INSERT INTO {schema}.app_role SELECT * FROM viridexa.app_role;')
  procedure=(ROOT/'apps/api/src/main/resources/db/migration/V14__public_member_registration.sql').read_text().split('CREATE PROCEDURE',1)[1].rstrip().removesuffix(';')
  sql('USE '+schema+';\nDELIMITER $$\nCREATE PROCEDURE'+procedure+'$$\nDELIMITER ;\n')
  sql(f"GRANT EXECUTE ON PROCEDURE {schema}.register_public_member TO '{account}'@'%';")
  for table in ['app_user','app_role','user_role','public_member_account']:sql(f"GRANT SELECT ON {schema}.{table} TO '{account}'@'%';")
  sql(f"GRANT INSERT(email,display_name,password_hash) ON {schema}.app_user TO '{account}'@'%';GRANT INSERT(user_id) ON {schema}.public_member_account TO '{account}'@'%';")
  ids=rows("SELECT JSON_QUOTE(c.id) FROM viridexa.scientific_candidate c JOIN viridexa.ontology_projection o ON o.candidate_id=c.id WHERE c.pipeline='MORPHOLOGY' ORDER BY c.id LIMIT 3;")
  keys=','.join("'"+x+"'" for x in ids)
  columns='id,source_id,pipeline,locator,raw_payload,normalized_payload,content_sha256,normalization_state,reason'
  sql(f'USE {schema};INSERT INTO scientific_source_version SELECT * FROM viridexa.scientific_source_version;INSERT INTO scientific_candidate({columns}) SELECT {columns} FROM viridexa.scientific_candidate WHERE id IN ({keys});INSERT INTO ontology_projection SELECT * FROM viridexa.ontology_projection WHERE candidate_id IN ({keys});INSERT INTO source_record_access SELECT * FROM viridexa.source_record_access WHERE candidate_id IN ({keys});INSERT INTO source_governance SELECT * FROM viridexa.source_governance;')
  def terms(db):return rows(f'SELECT JSON_ARRAY(candidate_id,field_key,ordinality,source_term,concept) FROM {db}.graph_trait WHERE candidate_id IN ({keys}) ORDER BY candidate_id,field_key,ordinality;')
  actual=terms(schema);expected=terms('viridexa')
  if actual!=expected:raise RuntimeError('Trait projection differs from original JSON_TABLE view')
  report['realSourceCandidates']=len(ids);report['matchingTraitRows']=len(actual)
  count=sql(f'SELECT COUNT(*) FROM viridexa.graph_trait WHERE candidate_id IN ({keys}) AND candidate_id IN(SELECT id FROM viridexa.public_source_record);')
  if sql(f'SELECT record_count FROM {schema}.graph_public_trait_count;')!=count:raise RuntimeError('Trait count differs')
  report['matchingPublicTraitCount']=int(count)
  reference=sql('SHOW CREATE VIEW viridexa.graph_trait;').split('\t')[1].replace('`viridexa`.','`'+schema+'`.')
  reference=re.sub(r'DEFINER\s*=\s*`[^`]+`@`[^`]+`\s*','',reference,flags=re.I)
  reference=re.sub(r'(\bVIEW\s+)(?:`[^`]+`\.)?`graph_trait`',r'\1`reference_graph_trait`',reference,count=1,flags=re.I)
  sql('USE '+schema+';'+reference+';')
  original=sql(f"SELECT HEX(CAST(projection AS CHAR)) FROM {schema}.ontology_projection WHERE candidate_id='{ids[0]}';")
  edge=json.dumps([{'source_term':None,'concept':None},{'source_term':'null','concept':'UNKNOWN'},{'source_term':True,'concept':False},{'source_term':3,'concept':'3'},{'source_term':{'unexpected':'object'},'concept':[]}],separators=(',',':'))
  try:
   sql(f"UPDATE {schema}.ontology_projection SET projection=JSON_SET(projection,'$.traits.leaf_shape',CAST('{edge}' AS JSON)) WHERE candidate_id='{ids[0]}';")
   reference_rows=rows(f'SELECT JSON_ARRAY(candidate_id,field_key,ordinality,source_term,concept) FROM {schema}.reference_graph_trait ORDER BY candidate_id,field_key,ordinality;')
   if terms(schema)!=reference_rows:raise RuntimeError('JSON null/scalar conversion differs from original view')
   report['nullScalarProjectionEquivalent']=True
  finally:sql(f"UPDATE {schema}.ontology_projection SET projection=CAST(CONVERT(0x{original} USING utf8mb4) AS JSON) WHERE candidate_id='{ids[0]}';")
  # Prove future overflow fails instead of dropping array entries from the view.
  huge=json.dumps([{'source_term':'isolated overflow fixture'}]*1001,separators=(',',':'))
  attempt=subprocess.run(['docker','exec','-i','viridexa-db-v1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'],input=f"UPDATE {schema}.ontology_projection SET projection=JSON_SET(projection,'$.traits.leaf_shape',CAST('{huge}' AS JSON)) WHERE candidate_id='{ids[0]}';",text=True,capture_output=True)
  if attempt.returncode==0:raise RuntimeError('Trait capacity constraint did not enforce')
  report['capacityOverflowRejected']=True
  runtime=ROOT/'.runtime'/schema;runtime.mkdir(parents=True,exist_ok=True)
  env=os.environ.copy();env.update(TIDB_TEST_SCHEMA=schema,TIDB_TEST_DB_URL=f'jdbc:mysql://127.0.0.1:3309/{schema}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC',TIDB_TEST_RO=ro,TIDB_TEST_ACCOUNT=account,TIDB_TEST_MIGRATOR=migrator,TIDB_TEST_PASSWORD=password,TIDB_TEST_ACCOUNT_PASSWORD=secrets.token_hex(18),TIDB_TEST_RUNTIME=str(runtime))
  reportdir=ROOT/'reports/tidb-compatibility';reportdir.mkdir(parents=True,exist_ok=True)
  with (reportdir/'portable-mysql.log').open('w') as output:
   r=subprocess.run([str(ROOT/'apps/api/mvnw'),'-o','-f',str(ROOT/'apps/api/pom.xml'),'-s',str(ROOT/'.mvn/settings.xml'),'-Dtest=PortableAccountMySqlIT','test'],env=env,stdout=output,stderr=subprocess.STDOUT)
  report['portableAccountMysqlPassed']=r.returncode==0
  if r.returncode==0:
   with tempfile.TemporaryDirectory(prefix='viridexa-tidb-contract-') as tmp:
    libs=[]
    with zipfile.ZipFile(ROOT/'apps/api/target/viridexa-api-0.1.0.jar') as jar:
     for name in jar.namelist():
      if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
       target=Path(tmp)/Path(name).name;target.write_bytes(jar.read(name));libs.append(str(target))
    cp=os.pathsep.join(libs)
    compiled=subprocess.run(['javac','-cp',cp,'-d',tmp,str(ROOT/'scripts/tidb/TiDBReleaseTool.java'),str(ROOT/'scripts/tidb/ImportContract.java')],capture_output=True,text=True)
    if compiled.returncode:raise RuntimeError('Importer contract compilation failed')
    with (reportdir/'importer-mysql.log').open('w') as output:
     contract=subprocess.run(['java','-Xmx256m','-cp',tmp+os.pathsep+cp,'ImportContract'],env=env,stdout=output,stderr=subprocess.STDOUT)
    report['resumableImportMysqlPassed']=contract.returncode==0
    if contract.returncode:raise RuntimeError('Importer MySQL contract failed')
  (reportdir/'mysql-result.json').write_text(json.dumps(report,indent=2)+'\n')
  print(json.dumps(report))
  if r.returncode:raise RuntimeError('Portable account integration failed; inspect ignored report')
 finally:
  if created:sql('DROP DATABASE '+schema+';'+''.join("DROP USER IF EXISTS '"+u+"'@'%';" for u in [ro,account,migrator]))
if __name__=='__main__':main()
