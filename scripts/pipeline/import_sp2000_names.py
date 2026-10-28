#!/usr/bin/env python3
"""Append-only COL China 2022 factual Chinese names; dry-run by default.
Only exact, accepted Plantae species from the actual supplied workbook qualify.
No synonym guessing, source redistribution grant or scientific approval is implied.
"""
import argparse, collections, datetime, hashlib, json, re
from pathlib import Path
import openpyxl
from run_science import mysql
from import_candidates import literal
ROOT=Path(__file__).resolve().parents[2]
VERSION='Catalogue of Life China 2022; user-supplied structured export'
URL='http://www.sp2000.org.cn/'
REQUIRED={'code','拉丁名','中文名','等级','界','名称状态','种'}
def ascii_key(value):
 if not re.fullmatch('[0-9a-f]{64}',value):raise ValueError('Invalid digest')
 return "'"+value+"'"
BINOMIAL=re.compile(r'[A-Z][a-z]+ [a-z][a-z-]+')
CHINESE=re.compile(r'[\u3400-\u9fff·（）()\-]{1,80}')
def qualify(row):
 if row.get('界')!='Plantae':return None,'OTHER_KINGDOM'
 if row.get('等级')!='种':return None,'NON_SPECIES'
 if row.get('名称状态')!='接受名':return None,'NOT_ACCEPTED'
 name=str(row.get('拉丁名') or '').strip();zh=str(row.get('中文名') or '').strip()
 if not BINOMIAL.fullmatch(name) or row.get('种')!=name:return None,'NON_CANONICAL_SPECIES'
 if not CHINESE.fullmatch(zh):return None,'NO_VALID_CHINESE_NAME'
 if not row.get('code'):return None,'NO_AUTHORITY_ID'
 return {'scientificName':name,'nameZh':zh,'authorityId':str(row['code'])},None

def read_source(path,sha):
 cache=ROOT/'data/processed/sp2000'/f'{sha}-accepted-v1.json'
 if cache.exists():return json.loads(cache.read_text())
 w=openpyxl.load_workbook(path,read_only=True,data_only=True);counts=collections.Counter();accepted=[];scanned=0
 try:
  for s in w:
   rows=s.iter_rows(values_only=True);header=next(rows)
   if not REQUIRED.issubset(header):raise ValueError('Unrecognized workbook columns: '+s.title)
   for num,values in enumerate(rows,2):
    scanned+=1;item,reason=qualify(dict(zip(header,values)))
    if reason:counts[reason]+=1;continue
    item.update(sheet=s.title,row=num);accepted.append(item);counts['ELIGIBLE_SOURCE_ROWS']+=1
 finally:w.close()
 result={'sha256':sha,'scanned':scanned,'classification':dict(counts),'accepted':accepted}
 cache.parent.mkdir(parents=True,exist_ok=True)
 with cache.open('x') as f:json.dump(result,f,ensure_ascii=False)
 return result

def stable_fingerprint():
 q="SELECT SHA2(CAST(JSON_ARRAY(n.id,n.name_key,n.plant_id,n.name_text,n.review_state,n.evidence_id,a.id,a.source_url,a.source_version,a.authority_taxon_id,a.match_method,a.confidence,e.evidence_key,e.source_asset_id,e.locator,e.raw_excerpt,s.sha256) AS CHAR),256) FROM botanical_name_assertion a JOIN plant_name n ON n.id=a.plant_name_id JOIN evidence e ON e.id=n.evidence_id JOIN source_asset s ON s.id=e.source_asset_id ORDER BY a.id;"
 return hashlib.sha256(mysql(q).encode()).hexdigest()

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('workbook',type=Path);p.add_argument('--apply',action='store_true');p.add_argument('--batch-size',type=int,default=250);a=p.parse_args()
 path=a.workbook.resolve()
 if not path.is_relative_to(ROOT/'data/raw/sp2000') or not path.is_file():p.error('Input must be an existing project data/raw/sp2000 workbook')
 if not 1<=a.batch_size<=500:p.error('Batch size must be 1..500')
 sha=hashlib.sha256(path.read_bytes()).hexdigest();source=read_source(path,sha);by_name=collections.defaultdict(list)
 for row in source['accepted']:by_name[row['scientificName']].append(row)
 catalog=[(int(pid),name) for pid,name in (r.split('\t',1) for r in mysql('SELECT p.id,t.canonical_name FROM graph_catalog p JOIN taxon t ON t.id=p.taxon_id WHERE p.is_public=1 ORDER BY p.id;').splitlines())]
 matches=[];excluded=collections.Counter();excluded_rows=[]
 for pid,name in catalog:
  records=by_name.get(name,[])
  if not records:reason='NO_EXACT_ACCEPTED_SOURCE_MATCH'
  elif len({(r['authorityId'],r['nameZh']) for r in records})!=1:reason='CONFLICTING_SOURCE_IDENTITIES_OR_NAMES'
  else:matches.append(dict(records[0],plantId=pid));continue
  excluded[reason]+=1;excluded_rows.append({'plantId':pid,'scientificName':name,'reason':reason})
 before=stable_fingerprint();assetkey=hashlib.sha256(('sp2000-2022:'+sha).encode()).hexdigest();stamp=datetime.datetime.now(datetime.timezone.utc);batches=0
 vals=lambda seq:','.join(literal(x) for x in seq)
 if a.apply:
  mysql('INSERT IGNORE INTO source_asset(asset_key,relative_path,sha256,size_bytes,media_type,asset_kind) VALUES('+vals([assetkey,str(path.relative_to(ROOT)),sha,path.stat().st_size,'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet','NAME_METADATA'])+');')
  for offset in range(0,len(matches),a.batch_size):
   batch=matches[offset:offset+a.batch_size];ev=[];names=[];assertions=[]
   for r in batch:
    key=hashlib.sha256(f"sp2022:{sha}:{r['sheet']}:{r['row']}:{r['plantId']}".encode()).hexdigest();nk=hashlib.sha256(('name:'+key).encode()).hexdigest();aid=hashlib.sha256(('assert:'+key).encode()).hexdigest()
    loc=json.dumps({'sheet':r['sheet'],'row':r['row'],'authority_taxon_id':r['authorityId'],'version':VERSION,'source_url':URL},ensure_ascii=False);raw=json.dumps({'scientific_name':r['scientificName'],'name_zh':r['nameZh']},ensure_ascii=False)
    ev.append('SELECT '+ascii_key(key)+',id,\'WORKBOOK_ROW\','+literal(loc)+','+literal(raw)+' FROM source_asset WHERE asset_key='+ascii_key(assetkey))
    names.append('SELECT '+vals([nk,r['plantId'],'COMMON','zh',r['nameZh'],'SOURCE_REPORTED'])+',id FROM evidence WHERE evidence_key='+ascii_key(key))
    assertions.append('SELECT '+literal(aid)+',id,'+vals([URL,VERSION,r['authorityId'],r['scientificName'],'ACCEPTED_EXACT','SOURCE_REPORTED','PUBLIC_TAXONOMIC_NAME_METADATA_ONLY',stamp.strftime('%Y-%m-%d %H:%M:%S')])+' FROM plant_name WHERE name_key='+ascii_key(nk))
   sql='START TRANSACTION;INSERT IGNORE INTO evidence(evidence_key,source_asset_id,locator_type,locator,raw_excerpt) '+' UNION ALL '.join(ev)+';'
   sql+='INSERT IGNORE INTO plant_name(name_key,plant_id,name_role,language_tag,name_text,review_state,evidence_id) '+' UNION ALL '.join(names)+';'
   sql+='INSERT IGNORE INTO botanical_name_assertion(id,plant_name_id,source_url,source_version,authority_taxon_id,matched_scientific_name,match_method,confidence,display_basis,queried_at) '+' UNION ALL '.join(assertions)+';COMMIT;'
   mysql(sql);batches+=1
 report={'mode':'APPLY' if a.apply else 'DRY_RUN','sourceVersion':VERSION,'sourceSha256':sha,'sourceFile':str(path.relative_to(ROOT)),'sourceRows':source['scanned'],'sourceClassification':source['classification'],'publicCatalog':len(catalog),'matchedPlants':len(matches),'excluded':dict(excluded),'committedBatches':batches,'fingerprintBefore':before,'fingerprintAfter':stable_fingerprint(),'licenseBoundary':'Factual name metadata only; workbook not redistributed; no scientific approval','matches':matches,'excludedPlants':excluded_rows}
 out=ROOT/'reports/release-v0.1.0';out.mkdir(parents=True,exist_ok=True);file=out/f"sp2000-2022-{stamp.strftime('%Y%m%dT%H%M%S%f')}-{'apply' if a.apply else 'dry-run'}.json";file.write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps({k:v for k,v in report.items() if k not in {'matches','excludedPlants'}},ensure_ascii=False))
if __name__=='__main__':main()
