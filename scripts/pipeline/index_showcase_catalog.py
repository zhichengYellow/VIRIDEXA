#!/usr/bin/env python3
"""Reproducible catalog index over already public source names. No observation duplication or scientific review.
Controlled deployment CLI (schema write identity required); never callable through an anonymous API.
"""
import argparse,hashlib,json,re,sys,os,secrets,subprocess
from pathlib import Path
from run_science import mysql
from import_candidates import literal
ROOT=Path(__file__).resolve().parents[2]
AUTH='OWNER_SHOWCASE_FIRST_2026-10-26'
def read(q):return [json.loads(bytes.fromhex(x)) for x in mysql(q,user='root').splitlines()]
def digest(x):return hashlib.sha256(x.encode()).hexdigest()
def key(scope,value):return digest('SHOWCASE_CATALOG_V1:'+scope+':'+value)
def valid(name):return isinstance(name,str) and len(name)<=300 and bool(re.fullmatch(r'[A-Z][A-Za-z-]+ [a-z][a-z-]+(?: [A-Za-zÀ-ž(). ×-]+)?',name))
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apply',action='store_true');p.add_argument('--report',type=Path,default=ROOT/'reports/showcase/catalog-dry-run.json');p.add_argument('--batch-size',type=int,default=500);a=p.parse_args()
 if not 1<=a.batch_size<=1000:p.error('batch size 1..1000')
 # Use one deterministic originating candidate per exact literal name, without synonym collapse.
 names=read("SELECT HEX(CAST(JSON_OBJECT('name',n.name,'candidate',MIN(n.id)) AS CHAR)) FROM (SELECT f.id,JSON_UNQUOTE(JSON_EXTRACT(f.normalized_payload,'$.scientific_name')) COLLATE utf8mb4_bin AS name FROM public_source_record f WHERE f.pipeline IN ('MORPHOLOGY','TAXONOMY')) n WHERE n.name IS NOT NULL GROUP BY n.name ORDER BY n.name;")
 eligible=[r for r in names if valid(r['name'])]
 existing_names=set(mysql('SELECT t.canonical_name FROM plant p JOIN taxon t ON t.id=p.taxon_id;',user='root').splitlines())
 pending_names=[r for r in eligible if r['name'] not in existing_names]
 original=read("SELECT HEX(CAST(JSON_OBJECT('id',p.id,'record_key',p.record_key,'name',t.canonical_name,'sha',s.sha256,'source',s.id) AS CHAR)) FROM plant p JOIN taxon t ON t.id=p.taxon_id JOIN evidence e ON e.id=t.evidence_id JOIN source_asset a ON a.id=e.source_asset_id JOIN scientific_source_version s ON s.sha256=a.sha256 WHERE p.review_state='REVIEW_PENDING' AND s.integrity_state='VERIFIED' AND s.license_state<>'RESTRICTED' AND EXISTS(SELECT 1 FROM plant_name n WHERE n.plant_id=p.id AND n.name_role='SCIENTIFIC' AND n.review_state='VERIFIED_FROM_PRINTED_HEADING') ORDER BY p.id;")
 before=int(mysql('SELECT COUNT(*) FROM plant;',user='root').strip());review_before=mysql('SELECT id,candidate_id,reviewer_id,action FROM scientific_decision ORDER BY id;',user='root')
 if a.apply:
  password=secrets.token_urlsafe(32)
  grants="CREATE USER IF NOT EXISTS 'viridexa_catalog_publisher'@'localhost' IDENTIFIED BY '"+password+"';ALTER USER 'viridexa_catalog_publisher'@'localhost' IDENTIFIED BY '"+password+"';GRANT CREATE TEMPORARY TABLES ON viridexa.* TO 'viridexa_catalog_publisher'@'localhost';"
  for table in ('evidence','source_asset','scientific_candidate','source_record_access','scientific_source_version','plant','taxon','plant_name','review_event','catalog_display_access'):grants+="GRANT SELECT ON viridexa."+table+" TO 'viridexa_catalog_publisher'@'localhost';"
  for table in ('evidence','taxon','plant','plant_name','review_event','catalog_display_access'):grants+="GRANT INSERT ON viridexa."+table+" TO 'viridexa_catalog_publisher'@'localhost';"
  grants+="GRANT UPDATE(visibility_state) ON viridexa.plant TO 'viridexa_catalog_publisher'@'localhost';"
  try:mysql(grants,user='root')
  except RuntimeError:raise RuntimeError('Catalog identity provisioning failed; credentials redacted') from None
  def write(sql):
   result=subprocess.run(['docker','exec','-i','-e','SHOWCASE_PUBLISHER_PASSWORD','viridexa-db-v1','sh','-c','MYSQL_PWD="$SHOWCASE_PUBLISHER_PASSWORD" mysql -uviridexa_catalog_publisher --default-character-set=utf8mb4 viridexa'],input=sql,text=True,capture_output=True,env={**os.environ,'SHOWCASE_PUBLISHER_PASSWORD':password})
   if result.returncode:raise RuntimeError(result.stderr[:600])
  try:
   for start in range(0,len(pending_names),a.batch_size):
    batch=pending_names[start:start+a.batch_size]
    values=','.join('('+literal(r['name'])+','+literal(r['candidate'])+')' for r in batch)
    # A temporary staging list belongs only to this CLI connection and vanishes on rollback/exit.
    sql="""CREATE TEMPORARY TABLE release_names(name VARCHAR(500) NOT NULL,candidate_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
    INSERT INTO release_names VALUES """+values+""";
    START TRANSACTION;
    DELETE n FROM release_names n JOIN taxon t ON t.canonical_name=n.name AND BINARY t.canonical_name=BINARY n.name JOIN plant p ON p.taxon_id=t.id;
    INSERT INTO evidence(evidence_key,source_asset_id,locator_type,locator,evidence_state)
    SELECT SHA2(CONCAT('SHOWCASE_CATALOG_V1:evidence:',n.candidate_id),256),MIN(a.id),'SOURCE_RECORD',ANY_VALUE(c.locator),'EXTRACTED'
    FROM release_names n JOIN scientific_candidate c ON c.id=n.candidate_id JOIN source_record_access ra ON ra.candidate_id=c.id AND ra.visibility='PUBLIC' AND ra.content_sha256=c.content_sha256 JOIN scientific_source_version s ON s.id=c.source_id AND ra.source_sha256=s.sha256 AND s.integrity_state='VERIFIED' AND s.license_state<>'RESTRICTED' JOIN source_asset a ON a.sha256=s.sha256
    WHERE NOT EXISTS(SELECT 1 FROM evidence e WHERE e.evidence_key=SHA2(CONCAT('SHOWCASE_CATALOG_V1:evidence:',n.candidate_id),256)) GROUP BY n.candidate_id;
    INSERT INTO taxon(taxon_key,taxon_rank,canonical_name,identity_state,evidence_id)
    SELECT SHA2(CONCAT('SHOWCASE_CATALOG_V1:taxon:',n.name),256),'UNRANKED',n.name,'SOURCE_REPORTED',e.id FROM release_names n JOIN evidence e ON e.evidence_key=SHA2(CONCAT('SHOWCASE_CATALOG_V1:evidence:',n.candidate_id),256)
    WHERE NOT EXISTS(SELECT 1 FROM taxon t WHERE t.taxon_key=SHA2(CONCAT('SHOWCASE_CATALOG_V1:taxon:',n.name),256));
    INSERT INTO plant(record_key,source_record_key,taxon_id,review_state,visibility_state)
    SELECT SHA2(CONCAT('SHOWCASE_CATALOG_V1:plant:',n.name),256),CONCAT('SOURCE_NAME:',n.candidate_id),t.id,'REVIEW_PENDING','RESEARCH_PREVIEW' FROM release_names n JOIN taxon t ON t.taxon_key=SHA2(CONCAT('SHOWCASE_CATALOG_V1:taxon:',n.name),256)
    WHERE NOT EXISTS(SELECT 1 FROM plant p WHERE p.record_key=SHA2(CONCAT('SHOWCASE_CATALOG_V1:plant:',n.name),256));
    INSERT INTO plant_name(name_key,plant_id,name_role,language_tag,name_text,review_state,evidence_id)
    SELECT SHA2(CONCAT('SHOWCASE_CATALOG_V1:name:',n.name),256),p.id,'SCIENTIFIC','la',n.name,'SOURCE_REPORTED',t.evidence_id FROM release_names n JOIN plant p ON p.record_key=SHA2(CONCAT('SHOWCASE_CATALOG_V1:plant:',n.name),256) JOIN taxon t ON t.id=p.taxon_id
    WHERE NOT EXISTS(SELECT 1 FROM plant_name pn WHERE pn.name_key=SHA2(CONCAT('SHOWCASE_CATALOG_V1:name:',n.name),256));
    COMMIT;"""
    write(sql)
   # Metadata authorization never claims an anonymous executor is a scientific reviewer.
   releasable=read("SELECT HEX(CAST(JSON_OBJECT('id',p.id,'record_key',p.record_key,'name',t.canonical_name,'sha',s.sha256,'source',s.id) AS CHAR)) FROM plant p JOIN taxon t ON t.id=p.taxon_id JOIN evidence e ON e.id=t.evidence_id JOIN source_asset a ON a.id=e.source_asset_id JOIN scientific_source_version s ON s.sha256=a.sha256 WHERE p.review_state='REVIEW_PENDING' AND s.integrity_state='VERIFIED' AND s.license_state<>'RESTRICTED' AND EXISTS(SELECT 1 FROM plant_name n WHERE n.plant_id=p.id AND n.name_role='SCIENTIFIC' AND n.review_state IN ('VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED')) ORDER BY p.id;")
   for start in range(0,len(releasable),a.batch_size):
    ids=','.join(str(r['id']) for r in releasable[start:start+a.batch_size])
    sql="""START TRANSACTION;
    INSERT INTO review_event(event_key,entity_type,entity_key,action,prior_state,resulting_state,actor_user_id,happened_at,note,raw_payload)
    SELECT SHA2(CONCAT('SHOWCASE_CATALOG_V1:release:',p.record_key,s.sha256),256),'PLANT',p.record_key,'CATALOG_METADATA_RELEASE',p.review_state,'METADATA_ONLY',NULL,CURRENT_TIMESTAMP(6),'Owner authorized source-name display; no scientific review.',
    JSON_OBJECT('scope','CATALOG_METADATA_ONLY','authorization_reference','OWNER_SHOWCASE_FIRST_2026-10-26','scientific_name',t.canonical_name,'source_sha256',s.sha256,'human_scientific_review',FALSE,'raw_content_public',FALSE)
    FROM plant p JOIN taxon t ON t.id=p.taxon_id JOIN evidence e ON e.id=t.evidence_id JOIN source_asset a ON a.id=e.source_asset_id JOIN scientific_source_version s ON s.sha256=a.sha256
    WHERE p.id IN ("""+ids+""") AND p.review_state='REVIEW_PENDING' AND s.integrity_state='VERIFIED' AND s.license_state<>'RESTRICTED'
    AND NOT EXISTS(SELECT 1 FROM review_event h WHERE h.entity_type='PLANT' AND h.entity_key=p.record_key AND h.actor_user_id IS NOT NULL)
    AND NOT EXISTS(SELECT 1 FROM review_event old WHERE old.event_key=SHA2(CONCAT('SHOWCASE_CATALOG_V1:release:',p.record_key,s.sha256),256));
    UPDATE plant p JOIN taxon t ON t.id=p.taxon_id JOIN evidence e ON e.id=t.evidence_id JOIN source_asset a ON a.id=e.source_asset_id JOIN scientific_source_version s ON s.sha256=a.sha256
    JOIN review_event r ON r.event_key=SHA2(CONCAT('SHOWCASE_CATALOG_V1:release:',p.record_key,s.sha256),256)
    SET p.visibility_state='PUBLIC'
    WHERE p.id IN ("""+ids+""") AND p.review_state='REVIEW_PENDING' AND p.visibility_state<>'PUBLIC'
    AND NOT EXISTS(SELECT 1 FROM review_event later WHERE later.entity_type=r.entity_type AND later.entity_key=r.entity_key AND later.id>r.id);
    INSERT INTO catalog_display_access(plant_id,event_id,source_id,source_sha256,scientific_name)
    SELECT p.id,r.id,s.id,s.sha256,t.canonical_name FROM plant p JOIN taxon t ON t.id=p.taxon_id
    JOIN evidence e ON e.id=t.evidence_id JOIN source_asset a ON a.id=e.source_asset_id JOIN scientific_source_version s ON s.sha256=a.sha256
    JOIN review_event r ON r.event_key=SHA2(CONCAT('SHOWCASE_CATALOG_V1:release:',p.record_key,s.sha256),256)
    WHERE p.id IN ("""+ids+""") AND p.review_state='REVIEW_PENDING'
    AND NOT EXISTS(SELECT 1 FROM catalog_display_access x WHERE x.plant_id=p.id)
    AND NOT EXISTS(SELECT 1 FROM review_event later WHERE later.entity_type=r.entity_type AND later.entity_key=r.entity_key AND later.id>r.id);
    COMMIT;"""
    write(sql)
  finally:
   mysql("DROP USER IF EXISTS 'viridexa_catalog_publisher'@'localhost';",user='root')
 after=int(mysql('SELECT COUNT(*) FROM plant;',user='root').strip())
 if mysql('SELECT id,candidate_id,reviewer_id,action FROM scientific_decision ORDER BY id;',user='root')!=review_before:raise RuntimeError('Review changed')
 fp=mysql("SELECT SHA2(CAST(JSON_ARRAY(p.record_key,p.taxon_id,p.review_state,p.visibility_state,t.canonical_name,e.evidence_key,e.source_asset_id,e.locator) AS CHAR),256) FROM plant p JOIN taxon t ON t.id=p.taxon_id JOIN evidence e ON e.id=t.evidence_id ORDER BY p.record_key;",user='root')
 event_fp=mysql("SELECT SHA2(CAST(JSON_ARRAY(event_key,entity_key,action,actor_user_id,raw_payload) AS CHAR),256) FROM review_event ORDER BY event_key;",user='root')
 name_fp=mysql("SELECT SHA2(CAST(JSON_ARRAY(name_key,plant_id,name_text,name_role,review_state,evidence_id) AS CHAR),256) FROM plant_name ORDER BY name_key;",user='root')
 report={'mode':'APPLY' if a.apply else 'DRY_RUN','publicSourceNameStrings':len(names),'eligibleNameStrings':len(eligible),'excludedNameShape':len(names)-len(eligible),'existingCourseHeadingEligible':len(original),'before':before,'after':after,'added':after-before,'publicCatalog':int(mysql('SELECT COUNT(*) FROM graph_catalog WHERE is_public=1;',user='root').strip()),'catalogContentFingerprint':digest(fp),'catalogNamesFingerprint':digest(name_fp),'catalogHistoryFingerprint':digest(event_fp),'scientificDecisionsChanged':False,'confidence':'SOURCE_REPORTED; exact literal names, not verified independent species','noChineseNamesInvented':True,'noObservationsCopied':True}
 a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps(report))
if __name__=='__main__':main()
