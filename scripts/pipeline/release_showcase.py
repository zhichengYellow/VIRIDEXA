#!/usr/bin/env python3
"""Explicit source-record visibility release, never scientific approval.
Default dry-run. Root is only used by the controlled local provisioning entry point;
all publication statements run as the dedicated viridexa_publisher database identity.
No files under the protected directory are written.
"""
import argparse, collections, hashlib, json, os, re, secrets, subprocess
from pathlib import Path
from run_science import mysql, fingerprint
from import_candidates import literal
ROOT=Path(__file__).resolve().parents[2]
AUTH='OWNER_SHOWCASE_FIRST_2026-10-26'
POLICY='showcase-structured-facts-v1'
# Numeric/lexical factual records only. No paper text, images, PDF bytes or scanned tables.
DATASETS={'plants.json','combined.zip','efloras.flora_of_china.zip','wfo_plantlist_2026-06.zip',
 'match_candidates_20261011.jsonl','oil_samples.json','fatty_acid_literature_extraction.json',
 'fatty_acid_literature_candidates.csv','fatty_acid_literature_samples.csv',
 'FoodData_Central_foundation_food_csv_2026-04-30.zip'}

def records(sql):
 return [json.loads(bytes.fromhex(line)) for line in mysql('SELECT HEX(CAST('+sql+' AS CHAR));',user='root').splitlines()]
def query(sql):
 return [json.loads(bytes.fromhex(line)) for line in mysql(sql,user='root').splitlines()]
def eligible(r):
 if r['dataset'].split('/')[-1] not in DATASETS:return 'NO_STRUCTURED_DISPLAY_BASIS'
 if r['integrity']!='VERIFIED':return 'SOURCE_INTEGRITY'
 if r['license']=='RESTRICTED':return 'RESTRICTED_LICENSE'
 if r['state']!='CANDIDATE' or not isinstance(r['normalized'],dict):return 'HELD_OR_UNNORMALIZED'
 if r['decision'] or r['review'] not in (None,'PENDING'):return 'EXISTING_REVIEW_DECISION'
 n=r['normalized']
 if r['pipeline']=='LIPID':
  if n.get('value') is None or not n.get('unit') or not n.get('basis') or not n.get('sample_id') or not n.get('study_id'):return 'MISSING_MEASUREMENT_BASIS'
 elif r['pipeline']=='MORPHOLOGY':
  if not n.get('scientific_name') or not isinstance(n.get('traits'),dict) or not n['traits']:return 'UNRESOLVED_NAME_OR_TRAIT'
  if any(not isinstance(v,str) or len(v)>300 for v in n['traits'].values()):return 'LONG_TEXT_NOT_PUBLIC'
 elif not any(n.get(k) for k in ('scientific_name','accepted_name','source_taxon_id','source_synonym_id')):return 'UNRESOLVED_TAXONOMY'
 return None

def publisher(sql):
 r=subprocess.run(['docker','exec','-i','viridexa-db-v1','sh','-c','MYSQL_PWD="$SHOWCASE_PUBLISHER_PASSWORD" mysql -uviridexa_publisher --default-character-set=utf8mb4 viridexa'],input=sql,text=True,capture_output=True)
 if r.returncode:raise RuntimeError(r.stderr[:900])
 return r.stdout

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apply',action='store_true');p.add_argument('--report',type=Path,default=ROOT/'reports/showcase/release-dry-run.json');p.add_argument('--batch-size',type=int,default=500);a=p.parse_args()
 if not 1<=a.batch_size<=1000:p.error('batch size 1..1000')
 # Verify every registered source from actual protected bytes, not historical audit assertions.
 src=query("SELECT HEX(CAST(JSON_OBJECT('id',id,'path',path,'dataset',dataset,'sha256',sha256) AS CHAR)) FROM scientific_source_version ORDER BY id;")
 protected=(ROOT).resolve();hashes={}
 for s in src:
  if s['dataset']=='LEGACY_SNAPSHOT':continue
  path=(protected/s['path']).resolve()
  if not path.is_relative_to(protected) or not path.is_file():raise ValueError('Missing protected source')
  with path.open('rb') as f:d=hashlib.file_digest(f,'sha256').hexdigest()
  if d!=s['sha256']:raise ValueError('Source hash mismatch')
  hashes[s['id']]=d
 rows=query("SELECT HEX(CAST(JSON_OBJECT('id',c.id,'source',c.source_id,'dataset',s.dataset,'source_sha',s.sha256,'content_sha',c.content_sha256,'pipeline',c.pipeline,'integrity',s.integrity_state,'license',s.license_state,'state',c.normalization_state,'normalized',c.normalized_payload,'decision',EXISTS(SELECT 1 FROM scientific_decision d WHERE d.candidate_id=c.id),'review',(SELECT r.state FROM scientific_review_state r WHERE r.candidate_id=c.id)) AS CHAR)) FROM scientific_candidate c JOIN scientific_source_version s ON s.id=c.source_id ORDER BY c.id;")
 good=[];reasons=collections.Counter();by=collections.defaultdict(collections.Counter)
 for r in rows:
  reason=eligible(r)
  if r['source'] not in hashes:reason=reason or 'NO_FILE_HASH'
  by[r['pipeline']]['scanned']+=1
  if reason:reasons[reason]+=1;by[r['pipeline']]['excluded']+=1
  else:good.append(r);by[r['pipeline']]['eligible']+=1
 existing={line.split('\t')[0]:line.split('\t')[1:] for line in mysql('SELECT candidate_id,visibility,knowledge_confidence,content_sha256,source_sha256 FROM source_record_access ORDER BY candidate_id;',user='root').splitlines()}
 pending=[r for r in good if r['id'] not in existing]
 before=fingerprint() if a.apply else None
 if a.apply:
  # Provision a process-scoped least-privilege identity; it has no scientific UPDATE/decision rights.
  password=secrets.token_urlsafe(32)
  grants="CREATE USER IF NOT EXISTS 'viridexa_publisher'@'localhost' IDENTIFIED BY '"+password+"';ALTER USER 'viridexa_publisher'@'localhost' IDENTIFIED BY '"+password+"';GRANT CREATE TEMPORARY TABLES ON viridexa.* TO 'viridexa_publisher'@'localhost';"
  for table in ('scientific_candidate','scientific_source_version','scientific_decision','scientific_review_state','source_record_access','source_release_event'):grants+="GRANT SELECT ON viridexa."+table+" TO 'viridexa_publisher'@'localhost';"
  for table in ('source_record_access','source_release_event'):grants+="GRANT INSERT ON viridexa."+table+" TO 'viridexa_publisher'@'localhost';"
  try:mysql(grants,user='root')
  except RuntimeError:raise RuntimeError('Publication identity provisioning failed; credentials redacted') from None
  # Feed secret to the child environment, never command line, reports or repository.
  original=publisher
  def run(sql):
   r=subprocess.run(['docker','exec','-i','-e','SHOWCASE_PUBLISHER_PASSWORD','viridexa-db-v1','sh','-c','MYSQL_PWD="$SHOWCASE_PUBLISHER_PASSWORD" mysql -uviridexa_publisher --default-character-set=utf8mb4 viridexa'],input=sql,text=True,capture_output=True,env={**os.environ,'SHOWCASE_PUBLISHER_PASSWORD':password})
   if r.returncode:raise RuntimeError(r.stderr[:900])
  try:
   for start in range(0,len(pending),a.batch_size):
    batch=pending[start:start+a.batch_size]
    values=','.join('('+','.join(literal(x) for x in [r['id'],'MACHINE_EXTRACTED' if r['pipeline']=='MORPHOLOGY' else 'SOURCE_REPORTED',r['content_sha'],r['source_sha']])+')' for r in batch)
    sql="""CREATE TEMPORARY TABLE release_candidates(candidate_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,confidence VARCHAR(24),content_sha CHAR(64) CHARACTER SET ascii COLLATE ascii_bin,source_sha CHAR(64) CHARACTER SET ascii COLLATE ascii_bin);
    INSERT INTO release_candidates VALUES """+values+""";
    START TRANSACTION;
    INSERT INTO source_record_access(candidate_id,visibility,knowledge_confidence,content_sha256,source_sha256,authorization_reference,display_basis)
    SELECT c.id,'PUBLIC',n.confidence,c.content_sha256,s.sha256,"""+literal(AUTH)+""",'FACTUAL_STRUCTURED_TERMS_ONLY; no original text/media; original license retained'
    FROM release_candidates n JOIN scientific_candidate c ON c.id=n.candidate_id JOIN scientific_source_version s ON s.id=c.source_id
    WHERE c.content_sha256=n.content_sha AND s.sha256=n.source_sha AND s.integrity_state='VERIFIED' AND s.license_state<>'RESTRICTED'
    AND c.normalization_state='CANDIDATE' AND NOT EXISTS(SELECT 1 FROM scientific_decision d WHERE d.candidate_id=c.id)
    AND NOT EXISTS(SELECT 1 FROM scientific_review_state r WHERE r.candidate_id=c.id AND r.state<>'PENDING')
    AND NOT EXISTS(SELECT 1 FROM source_record_access a WHERE a.candidate_id=c.id);
    INSERT INTO source_release_event
    SELECT SHA2(CONCAT("""+literal(POLICY)+""",a.candidate_id,a.content_sha256),256),a.candidate_id,'PUBLIC',"""+literal(AUTH)+""",
    JSON_OBJECT('policy',"""+literal(POLICY)+""",'source_sha256',a.source_sha256,'content_sha256',a.content_sha256,'human_scientific_review',FALSE,'raw_original_public',FALSE),CURRENT_TIMESTAMP(6)
    FROM release_candidates n JOIN source_record_access a ON a.candidate_id=n.candidate_id
    WHERE NOT EXISTS(SELECT 1 FROM source_release_event e WHERE e.event_key=SHA2(CONCAT("""+literal(POLICY)+""",a.candidate_id,a.content_sha256),256));
    COMMIT;"""
    run(sql)

  finally:
   mysql("DROP USER IF EXISTS 'viridexa_publisher'@'localhost';",user='root')
 after=fingerprint() if a.apply else None
 if before!=after:raise RuntimeError('Scientific content/review/source relationship fingerprint changed')
 persisted=mysql("SELECT COUNT(*) FROM public_source_record;",user='root').strip() if a.apply else None
 ledger=mysql("SELECT SHA2(CAST(JSON_ARRAY(candidate_id,visibility,knowledge_confidence,content_sha256,source_sha256,authorization_reference) AS CHAR),256) FROM source_record_access ORDER BY candidate_id;",user='root') if a.apply else ''
 report={'mode':'APPLY' if a.apply else 'DRY_RUN','policy':POLICY,'authorization':AUTH,'scanned':len(rows),'eligible':len(good),'existingAccessEntries':len(existing),'newAccessEntriesPlanned':len(pending),'excluded':len(rows)-len(good),'exclusionReasons':dict(reasons),'pipelines':dict(by),'verifiedRegisteredFileHashes':len(hashes),'publicSourceRecords':int(persisted) if persisted else None,'scientificFingerprintsBefore':before,'scientificFingerprintsAfter':after,'accessContentFingerprint':hashlib.sha256(ledger.encode()).hexdigest() if a.apply else None,'humanReviewCreated':False,'sourceLicensesChanged':False,'originalFilesReleased':False,'publicBasis':'Owner-authorized limited factual lexical/numeric source records with attribution; UNKNOWN is retained, not a license grant. Long prose, raw payloads, original files, images and full tables are excluded.'}
 a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps({k:report[k] for k in ['mode','scanned','eligible','excluded','publicSourceRecords']}))
if __name__=='__main__':main()
