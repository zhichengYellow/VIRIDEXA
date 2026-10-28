#!/usr/bin/env python3
"""Read-only production regression: publication and scientific review remain independent."""
import json,time,urllib.request,urllib.error,sys
from pathlib import Path
from run_science import mysql,fingerprint
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'reports/showcase'
def get(path):
 start=time.monotonic()
 try:
  with urllib.request.urlopen('http://127.0.0.1:5173'+path,timeout=35) as r:code=r.status;body=r.read().decode()
 except urllib.error.HTTPError as e:code=e.code;body=e.read().decode()
 checks.append({'path':path,'status':code,'seconds':round(time.monotonic()-start,3)})
 if code==200:
  for term in ('/Users/','/home/','/science-inputs/','raw_payload','reviewer_id','password_hash'):assert term not in body,(path,term)
 return code,json.loads(body) if body else None
checks=[]
a,data=get('/api/v1/plants?limit=2');assert a==200 and data['total']>5 and len(data['items'])==2
ids=[p['id'] for p in data['items']];a,nextpage=get('/api/v1/plants?limit=2&afterId='+str(data['nextAfterId']));assert a==200 and not set(ids)&{p['id'] for p in nextpage['items']}
a,show=get('/api/v1/showcase');assert a==200 and show['counts']['humanVerified']==0 and show['counts']['publicSourceRecords']>0
for path,status in [('/api/v1/workbench/samples',401),('/api/v1/admin/science/candidates',401),('/api/v1/graph/nodes/plant:9999999999999999999',400),('/api/v1/graph/nodes/plant:99999999',404),('/api/v1/graph/nodes/plant:2/neighbors?depth=2',400),('/api/v1/plants?limit=1001',400)]:assert get(path)[0]==status
assert get('/api/v1/science/facts')[1]==[]
assert get('/api/v1/graph/nodes/plant:2/neighbors?limit=10')[0]==200
code,meta=get('/api/v1/graph/meta');assert code==200 and meta['publishedFactCount']==0 and '统计快照' in meta['notice']
code,entry=get('/api/v1/graph/search?q=Ginkgo&limit=3');assert code==200 and entry['nodes'] and entry['hasMore']
code,expanded=get('/api/v1/graph/nodes/candidate:0002a7f5a47529d863fdfced899c46d349c250d73629a62e2960b805b6ca9021/neighbors?limit=10');assert code==200 and any(e['relationType']=='HAS_TRAIT' for e in expanded['edges'])
code,sourcePage=get('/api/v1/graph/nodes/source:5b6b6371efe85c81b2cac313e366c2c187a4e7ca8f9cac7e85fbfc3a065d1345/neighbors?limit=10');assert code==200 and len(sourcePage['edges'])<=10 and sourcePage['hasMore']
code,rows=get('/api/v1/science/plants?q=Taraxacum%20celsum&limit=3');assert code==200 and any(r['knowledgeConfidence']=='MACHINE_EXTRACTED' for r in rows)
code,measurements=get('/api/v1/science/measurements?kind=FAME_COMPOSITION&limit=2');assert code==200 and measurements and all(m.get('knowledgeConfidence')!='HUMAN_VERIFIED' for m in measurements)
id=measurements[0]['evidenceId'];assert get('/api/v1/science/lineage/'+id)[0]==200
assert get('/api/v1/workbench/overview')[0]==200
assert get('/api/v1/agent/meta')[1]['modelConfigured'] is False
assert mysql('SELECT @@foreign_key_checks;',user='root').strip()=='1'
assert mysql('SELECT COUNT(*) FROM scientific_decision;',user='root').strip()=='0'
assert mysql('SELECT COUNT(*) FROM app_user;',user='root').strip()=='0'
grants=mysql("SHOW GRANTS FOR 'viridexa_ro'@'%';",user='root');assert 'SELECT' in grants and not any(op in grants for op in ['GRANT INSERT','GRANT UPDATE','GRANT DELETE','ALL PRIVILEGES'])
source=json.loads((OUT/'release-applied.json').read_text());assert source['scientificFingerprintsAfter']==fingerprint()
report={'checks':checks,'counts':show['counts'],'graph':{'nodes':sum(meta['nodeCounts'].values()),'edges':sum(meta['edgeCounts'].values()),'notice':meta['notice']},'humanScientificDecisions':0,'productionAccounts':0,'modelConfigured':False,'readonlyApiAccountVerified':True,'foreignKeysEnabled':True,'scientificContentAndSourceRelationsUnchanged':True,'catalogPaginationDistinct':True}
(OUT/'runtime-regression.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps({'checks':len(checks),'passed':True,'publicPlants':show['counts']['publicPlants'],'publicRecords':show['counts']['publicSourceRecords']}))
