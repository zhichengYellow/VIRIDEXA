#!/usr/bin/env python3
"""Bounded public iFlora name-metadata queries. No full text/media redistribution.
SP2000 protected downloads are supplied by the user, never bypassed. API snapshot
version is explicit; it is not called Catalogue of Life China 2026.
"""
import argparse,collections,datetime,hashlib,json,re,sys,time,urllib.parse,urllib.request
from pathlib import Path
from run_science import mysql
from import_candidates import literal
ROOT=Path(__file__).resolve().parents[2]
BASE='https://api.iflora.cn/API/Taxon'
VERSION='iFlora COLChina API snapshot 2026-10-28; upstream release unspecified'

def accepted_names(scientific,response):
 if response.get('Message')!='Success':return [],'UPSTREAM_QUOTA_EXHAUSTED' if '最大' in str(response.get('Message','')) else 'UPSTREAM_ERROR'
 if not re.fullmatch(r'[A-Z][a-z]+ [a-z][a-z-]+',scientific):return [],'NON_BINOMIAL_OR_AMBIGUOUS_NAME'
 candidates=[]
 for row in response.get('Data',[]):
  if row.get('taxonomicStatus','').lower()!='accepted' or row.get('taxonRank','').lower()!='species':continue
  if row.get('canonicalName')!=scientific or row.get('scientificName')!=scientific:continue
  name=row.get('vernacularName','').strip()
  if re.fullmatch(r'[\u3400-\u9fff·（）()\-]{1,80}',name) and row.get('taxonID'):candidates.append((name,row['taxonID']))
 ids={x[1] for x in candidates}
 if len(ids)>1:return [],'CONFLICTING_ACCEPTED_IDENTITIES'
 return sorted(set(candidates)),None if candidates else 'NO_EXACT_ACCEPTED_CHINESE_NAME'

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--limit',type=int,default=100);p.add_argument('--apply',action='store_true');p.add_argument('--offline',action='store_true');a=p.parse_args()
 if not 1<=a.limit<=300:p.error('bounded limit 1..300')
 out=ROOT/'reports/release-v0.1.0';out.mkdir(parents=True,exist_ok=True);raw=ROOT/'data/raw/iplant/iflora-names';raw.mkdir(parents=True,exist_ok=True)
 # Exact bare binomials only. No nearest-name or genus inheritance.
 rows=mysql("SELECT p.id,t.canonical_name FROM graph_catalog p JOIN taxon t ON t.id=p.taxon_id WHERE p.is_public=1 AND t.canonical_name REGEXP '^[A-Z][a-z]+ [a-z][a-z-]+$' AND NOT EXISTS(SELECT 1 FROM plant_name n WHERE n.plant_id=p.id AND n.name_role='COMMON' AND n.language_tag LIKE 'zh%' AND n.review_state IN('SOURCE_REPORTED','APPROVED','VERIFIED_FROM_PRINTED_HEADING')) ORDER BY (p.id IN(9445,30610)) DESC,p.id LIMIT "+str(a.limit)+';')
 report=[];counts=collections.Counter();start=time.monotonic()
 for line in rows.splitlines():
  pid,text=line.split('\t',1);pid=int(pid);key=hashlib.sha256(text.encode()).hexdigest();file=raw/(key+'.json');url=BASE+'?'+urllib.parse.urlencode({'datasetID':'COLChina','scientificName':text,'pageIndex':1})
  if not file.exists():
   if a.offline:counts['CACHE_MISSING']+=1;continue
   time.sleep(1)
   try:
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'VIRIDEXA-course-project/0.1 (bounded name metadata lookup)'}),timeout=15) as r:body=r.read(2_000_000)
    response=json.loads(body)
    with file.open('xb') as f:f.write(body)
   except Exception as e:counts['FETCH_FAILED']+=1;report.append({'plantId':pid,'scientificName':text,'reason':'FETCH_FAILED','errorType':type(e).__name__});continue
  body=file.read_bytes();response=json.loads(body);names,reason=accepted_names(text,response);sha=hashlib.sha256(body).hexdigest();queried=datetime.datetime.fromtimestamp(file.stat().st_mtime,datetime.timezone.utc).isoformat()
  if reason:counts[reason]+=1
  if reason in {'UPSTREAM_QUOTA_EXHAUSTED','UPSTREAM_ERROR'}:
   report.append({'plantId':pid,'scientificName':text,'reason':reason,'sourceUrl':url});break
  for name,tid in names:
   counts['MATCHED']+=1
   if a.apply:
    assetkey=hashlib.sha256(('name-asset:'+sha).encode()).hexdigest();evkey=hashlib.sha256(('name-evidence:'+sha+tid+name).encode()).hexdigest();nk=hashlib.sha256(f'public-zh:{pid}:{name}:{sha}'.encode()).hexdigest();aid=hashlib.sha256((nk+url).encode()).hexdigest()
    loc=json.dumps({'source_url':url,'authority_taxon_id':tid,'scientific_name':text,'version':VERSION,'queried_at':queried},ensure_ascii=False);pair=json.dumps({'scientific_name':text,'name_zh':name},ensure_ascii=False)
    vals=lambda seq:','.join(literal(x) for x in seq)
    q='START TRANSACTION;'
    q+='INSERT IGNORE INTO source_asset(asset_key,relative_path,sha256,size_bytes,media_type,asset_kind) VALUES('+vals([assetkey,str(file.relative_to(ROOT)),sha,len(body),'application/json','NAME_METADATA'])+');'
    q+='INSERT IGNORE INTO evidence(evidence_key,source_asset_id,locator_type,locator,raw_excerpt) SELECT '+literal(evkey)+',id,\'API_RECORD\','+literal(loc)+','+literal(pair)+' FROM source_asset WHERE asset_key='+literal(assetkey)+';'
    q+='INSERT IGNORE INTO plant_name(name_key,plant_id,name_role,language_tag,name_text,review_state,evidence_id) SELECT '+vals([nk,pid,'COMMON','zh',name,'SOURCE_REPORTED'])+',id FROM evidence WHERE evidence_key='+literal(evkey)+';'
    q+='INSERT IGNORE INTO botanical_name_assertion(id,plant_name_id,source_url,source_version,authority_taxon_id,matched_scientific_name,match_method,confidence,display_basis,queried_at) SELECT '+literal(aid)+',id,'+vals([url,VERSION,tid,text,'ACCEPTED_EXACT','SOURCE_REPORTED','PUBLIC_TAXONOMIC_NAME_METADATA_ONLY',queried.replace('T',' ').split('+')[0]])+' FROM plant_name WHERE name_key='+literal(nk)+';COMMIT;'
    mysql(q)
  report.append({'plantId':pid,'scientificName':text,'names':[n for n,t in names],'authorityIds':list({t for n,t in names}),'reason':reason,'sourceUrl':url,'sourceSha256':sha,'version':VERSION,'queriedAt':queried})
 result={'mode':'APPLY' if a.apply else 'DRY_RUN','requested':len(rows.splitlines()),'processed':len(report),'counts':dict(counts),'seconds':round(time.monotonic()-start,2),'licenseBoundary':'Public factual name metadata only; no blanket source dataset or image license claimed','rows':report};(out/('names-applied.json' if a.apply else 'names-dry-run.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps({k:v for k,v in result.items() if k!='rows'},ensure_ascii=False))
if __name__=='__main__':main()
