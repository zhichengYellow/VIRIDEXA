#!/usr/bin/env python3
"""Read-only local Compose/data regression. No credentials, human accounts, or scientific writes."""
import hashlib,http.cookiejar,json,re,subprocess,urllib.request,urllib.error
from pathlib import Path
from urllib.parse import quote
ROOT=Path(__file__).resolve().parents[2];REPORT=ROOT/'reports/phase7s';BASE='http://127.0.0.1:5173'
def main():
 opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
 def request(path,body=None,headers=None,method=None):
  h={'Accept':'application/json',**(headers or {})}
  if body is not None:h['Content-Type']='application/json'
  req=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers=h,method=method)
  try:
   with opener.open(req,timeout=10) as r:return r.status,dict(r.headers),r.read().decode()
  except urllib.error.HTTPError as e:return e.code,dict(e.headers),e.read().decode()
 assert 'VIRIDEXA' in request('/')[2],'Refusing an unrelated localhost service'
 results={}
 for path,expected in [('/api/v1/plants',200),('/api/v1/security/meta',200),('/api/v1/graph/meta',200),('/api/v1/agent/meta',200),('/api/v1/workbench/overview',200),('/api/v1/workbench/samples',401),('/api/v1/admin/session',401),('/api-docs',401),('/actuator/health',404),('/actuator/env',404),('/api/v1/graph/search?limit=999',400),('/api/v1/science/plants?q='+'a'*121,400)]:
  status,headers,body=request(path);assert status==expected,(path,status);assert '/Users/' not in body and '/science-inputs/' not in body;results[path.split('?')[0]+(' [bounds]' if '?' in path else '')]=status
  if path=='/api/v1/agent/meta':results['agent']=json.loads(body)
  if path=='/api/v1/security/meta':results['security']=json.loads(body)
  assert headers.get('X-Content-Type-Options')=='nosniff'
 token=json.loads(request('/api/v1/csrf')[2])['token']
 for path in ['/api/v1/agent/runs','/api/v1/workbench/compare','/api/v1/admin/science/candidates/security-probe/decisions']:
  status,_,_=request(path,{},headers={'X-CSRF-TOKEN':token});assert status==401,(path,status);results['POST '+path]=status
 assert request('/api/v1/graph/meta',headers={'Origin':'https://untrusted.invalid'})[0]==403
 results['untrustedCorsRejected']=True
 graph=json.loads(request('/api/v1/graph/search?limit=1')[2]);node=graph['nodes'][0]['id'];status,_,_=request('/api/v1/graph/nodes/'+quote(node,safe='')+'/neighbors?depth=2');assert status==400;results['graphDepthBounded']=True
 hashes=[]
 for line in (ROOT/'docs/ASSET_MANIFEST.md').read_text().splitlines():
  cells=[c.strip() for c in line.split('|')]
  if len(cells)>5 and re.fullmatch('[0-9a-f]{64}',cells[4]):
   with (ROOT / cells[2]).open('rb') as f:matched=hashlib.file_digest(f,'sha256').hexdigest()==cells[4]
   hashes.append(matched)
 assert len(hashes)==292 and all(hashes);results['protectedFiles']={'manifest':292,'matched':sum(hashes),'modified':0}
 sql="USE viridexa;SELECT JSON_OBJECT('users',(SELECT COUNT(*) FROM app_user),'administrators',(SELECT COUNT(*) FROM user_role ur JOIN app_role r ON r.id=ur.role_id WHERE r.role_key='ADMIN'),'facts',(SELECT COUNT(*) FROM published_scientific_fact),'scientificDecisions',(SELECT COUNT(*) FROM scientific_decision),'candidates',(SELECT COUNT(*) FROM scientific_candidate),'normalized',(SELECT COUNT(*) FROM scientific_candidate WHERE normalization_state='CANDIDATE'),'held',(SELECT COUNT(*) FROM scientific_candidate WHERE normalization_state='HELD'),'unknownLicenses',(SELECT COUNT(*) FROM scientific_source_version WHERE license_state='UNKNOWN'),'publicPlants',(SELECT COUNT(*) FROM graph_catalog WHERE is_public=1));"
 r=subprocess.run(['docker','exec','-i','viridexa-db-v1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names'],input=sql,text=True,capture_output=True,check=True);results['productionCounts']=json.loads(r.stdout)
 previous=json.loads((ROOT/'reports/public-accounts/production-regression.json').read_text())['productionDatabase']
 for key in ['candidates','normalized','held','scientificDecisions']:assert results['productionCounts'][key]==previous[key],key
 results['scientificCountsUnchanged']=True
 jar=ROOT/'apps/api/target/viridexa-api-0.1.0.jar'
 with jar.open('rb') as f:local=hashlib.file_digest(f,'sha256').hexdigest()
 live=subprocess.run(['docker','compose','exec','-T','api','sha256sum','/app/app.jar'],cwd=ROOT,capture_output=True,text=True,check=True).stdout.split()[0];assert local==live;results['deployedJarSha256']=local
 subprocess.run(['docker','compose','exec','-T','web','nginx','-t'],cwd=ROOT,capture_output=True,text=True,check=True)
 statuses=subprocess.run(['docker','compose','ps','--format','json'],cwd=ROOT,capture_output=True,text=True,check=True).stdout
 results['compose']=[{k:item.get(k) for k in ['Service','State','Health']} for line in statuses.splitlines() if line for item in [json.loads(line)]]
 assert all(x['State']=='running' and (x['Service']=='web' or x['Health']=='healthy') for x in results['compose'])
 (REPORT/'runtime-and-integrity.json').write_text(json.dumps(results,ensure_ascii=False,indent=2));print(json.dumps({'checks':'passed','production':results['productionCounts'],'protectedFiles':results['protectedFiles'],'deployedJarMatches':True},ensure_ascii=False))
if __name__=='__main__':main()
