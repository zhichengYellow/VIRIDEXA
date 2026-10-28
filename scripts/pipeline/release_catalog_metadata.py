#!/usr/bin/env python3
"""Owner-operated metadata publication. Does not create users or approve scientific data."""
import argparse,hashlib,json
from pathlib import Path
from run_science import mysql
from import_candidates import literal
ROOT=Path(__file__).resolve().parents[2]

def plan(ids,authorization):
 if not 1<=len(ids)<=5 or len(set(ids))!=len(ids) or any(i<1 for i in ids):raise ValueError('Select 1–5 distinct real plant IDs')
 result=[]
 for id in ids:
  sql="""SELECT JSON_OBJECT('id',p.id,'record_key',p.record_key,'name',t.canonical_name,'identity_state',t.identity_state,'review_state',p.review_state,'source_id',s.id,'source_path',s.path,'sha256',s.sha256,'license_state',s.license_state,'integrity_state',s.integrity_state,'locator',e.locator,'latest_event',COALESCE((SELECT MAX(r.id) FROM review_event r WHERE r.entity_type='PLANT' AND r.entity_key=p.record_key),0)) FROM plant p JOIN taxon t ON t.id=p.taxon_id JOIN evidence e ON e.id=t.evidence_id JOIN source_asset a ON a.id=e.source_asset_id JOIN scientific_source_version s ON s.sha256=a.sha256 WHERE p.id="""+str(id)+';'
  rows=[json.loads(x) for x in mysql(sql,user='root').splitlines()]
  if len(rows)!=1:raise ValueError('Missing or ambiguous plant source')
  r=rows[0];base=(ROOT).resolve();file=(base/r['source_path']).resolve()
  if not file.is_relative_to(base) or r['integrity_state']!='VERIFIED' or hashlib.sha256(file.read_bytes()).hexdigest()!=r['sha256']:raise ValueError('Source integrity failed')
  if r['review_state']!='REVIEW_PENDING':raise ValueError('Will not override reviewed/rejected catalogue')
  r['event_key']=hashlib.sha256(('CATALOG_METADATA_ONLY:'+authorization+':'+r['record_key']+':'+r['sha256']).encode()).hexdigest();result.append(r)
 return result

def apply(rows,authorization):
 keys=','.join(str(r['id']) for r in rows);q=f'START TRANSACTION;SELECT id FROM plant WHERE id IN ({keys}) ORDER BY id FOR UPDATE;'
 for r in rows:
  payload=json.dumps({'scope':'CATALOG_METADATA_ONLY','authorization_reference':authorization,'authorization_channel':'EXPLICIT_USER_INSTRUCTION','executor':'AUTOMATED_DEPLOYMENT','human_scientific_review':False,'scientific_name':r['name'],'source_sha256':r['sha256'],'raw_content_public':False},ensure_ascii=False)
  q+=f"""INSERT INTO review_event(event_key,entity_type,entity_key,action,prior_state,resulting_state,actor_user_id,happened_at,note,raw_payload)
SELECT {literal(r['event_key'])},'PLANT',p.record_key,'CATALOG_METADATA_RELEASE',p.review_state,'METADATA_ONLY',NULL,CURRENT_TIMESTAMP(6),'Owner authorized course catalogue metadata display; no named scientific review; source text and measurements remain restricted.',{literal(payload)}
FROM plant p JOIN taxon t ON t.id=p.taxon_id JOIN evidence e ON e.id=t.evidence_id JOIN source_asset a ON a.id=e.source_asset_id JOIN scientific_source_version s ON s.sha256=a.sha256
WHERE p.id={r['id']} AND p.review_state='REVIEW_PENDING' AND t.canonical_name={literal(r['name'])} AND s.id={literal(r['source_id'])} AND s.sha256={literal(r['sha256'])} AND s.integrity_state='VERIFIED'
AND COALESCE((SELECT MAX(v.id) FROM review_event v WHERE v.entity_type='PLANT' AND v.entity_key=p.record_key),0)={int(r['latest_event'])}
AND NOT EXISTS(SELECT 1 FROM review_event old WHERE old.event_key={literal(r['event_key'])});
UPDATE plant p SET visibility_state='PUBLIC' WHERE p.id={r['id']} AND p.review_state='REVIEW_PENDING' AND EXISTS(SELECT 1 FROM review_event r WHERE r.event_key={literal(r['event_key'])} AND NOT EXISTS(SELECT 1 FROM review_event later WHERE later.entity_type=r.entity_type AND later.entity_key=r.entity_key AND later.id>r.id));"""
 q+='COMMIT;';mysql(q,user='root')
 counts=mysql(f'SELECT COUNT(*) FROM graph_catalog WHERE id IN ({keys}) AND is_public=1;',user='root').strip()
 if int(counts)!=len(rows):raise RuntimeError('Some selected records were not released; inspect audit and retry after resolving changed evidence')

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--plant-ids',required=True);p.add_argument('--authorization-reference',required=True);p.add_argument('--apply',action='store_true');p.add_argument('--report',type=Path,required=True);a=p.parse_args()
 if len(a.authorization_reference.strip())<10 or len(a.authorization_reference)>191:p.error('Explicit owner authorization reference required')
 rows=plan([int(x) for x in a.plant_ids.split(',')],a.authorization_reference)
 if a.apply:apply(rows,a.authorization_reference)
 report={'mode':'APPLY' if a.apply else 'DRY_RUN','scope':'CATALOG_METADATA_ONLY','authorization_reference':a.authorization_reference,'human_scientific_review':False,'raw_content_released':False,'scientific_candidates_approved':0,'plants':rows}
 a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps({'mode':report['mode'],'plants':len(rows),'scientific_candidates_approved':0}))
