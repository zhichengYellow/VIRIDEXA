#!/usr/bin/env python3
"""Bounded local-only benchmark. Percentiles describe this fixture/run, not production SLOs."""
import argparse,concurrent.futures,http.cookiejar,json,math,time,urllib.request,urllib.error
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--label',required=True);p.add_argument('--samples',type=int,default=40);a=p.parse_args()
assert 10<=a.samples<=100
base='http://127.0.0.1:18082';e=dict(x.split('=',1) for x in (ROOT/'.env.governance-test').read_text().splitlines() if '=' in x)
assert e['GOVERNANCE_TEST_SCHEMA'].startswith('viridexa_governance_test_')
def login(email):
 jar=http.cookiejar.CookieJar();client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
 def req(path,data=None,token=None):
  h={'Accept':'application/json'}
  if token:h['X-CSRF-TOKEN']=token
  if data is not None:h['Content-Type']='application/json'
  with client.open(urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=h),timeout=35) as r:return json.load(r)
 token=req('/api/v1/csrf')['token'];req('/api/v1/account/login',{'email':email,'password':e['GOVERNANCE_FIXTURE_PASSWORD']},token)
 csrf=req('/api/v1/csrf')['token'];return '; '.join(c.name+'='+c.value for c in jar),csrf
admin,admin_csrf=login('automated-isolated-fixture@example.invalid');member,csrf=login('researcher-fixture@example.invalid')
def call(path,cookie='',body=None,token=None):
 start=time.perf_counter();code=0;size=0;data={}
 h={'Accept':'application/json'}
 if cookie:h['Cookie']=cookie
 if token:h['X-CSRF-TOKEN']=token
 if body is not None:h['Content-Type']='application/json'
 try:
  with urllib.request.urlopen(urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),headers=h),timeout=35) as r:
   code=r.status;raw=r.read();size=len(raw);data=json.loads(raw)
 except urllib.error.HTTPError as ex:code=ex.code;size=len(ex.read())
 except Exception as ex:data={'errorType':type(ex).__name__}
 return {'ms':round((time.perf_counter()-start)*1000,3),'status':code,'bytes':size},data
routes=[('home-catalog','/api/v1/plants?limit=6',''),('name-search','/api/v1/plants?query=Ginkgo&limit=6',''),('plant-detail','/api/v1/plants/2',''),('graph-neighbors','/api/v1/graph/nodes/plant:2/neighbors?limit=10',''),('workbench-samples','/api/v1/workbench/samples?limit=10',member),('evidence-lineage','/api/v1/science/lineage/00577bcb3dc07f726c1694f3eefe210f46c4564bd97058d2563c8000d12c2ece',''),('technology-showcase','/api/v1/showcase',''),('agent-meta','/api/v1/agent/meta',''),('admin-candidates','/api/v1/admin/science/candidates?size=10&page=0',admin)]
report={'label':a.label,'schema':e['GOVERNANCE_TEST_SCHEMA'],'origin':base,'concurrency':2,'samplesPerRoute':a.samples,'warmupsPerRoute':2,'routes':[],'notProductionPercentiles':True,'raw':{}}
for name,path,cookie in routes:
 for _ in range(2):call(path,cookie)
 start=time.perf_counter()
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:rows=list(pool.map(lambda _:call(path,cookie)[0],range(a.samples)))
 elapsed=time.perf_counter()-start;lat=sorted(r['ms'] for r in rows)
 percentile=lambda q:lat[max(0,math.ceil(q*len(lat))-1)]
 result={'name':name,'path':path,'p50ms':percentile(.5),'p95ms':percentile(.95),'p99ms':percentile(.99),'requestsPerSecond':round(len(rows)/elapsed,3),'errors':sum(r['status']!=200 for r in rows),'statuses':{str(s):sum(r['status']==s for r in rows) for s in {r['status'] for r in rows}},'maxResponseBytes':max(r['bytes'] for r in rows)}
 report['routes'].append(result);report['raw'][name]=rows;print(json.dumps(result),flush=True)
# Agent lifecycle is measured separately: asynchronous duration and bounded polling, not POST latency alone.
report['agentLifecycle']=[]
for _ in range(3):
 start=time.perf_counter();row,run=call('/api/v1/agent/runs',member,{'question':'Ginkgo 来源及图谱'},csrf)
 if row['status']!=202:report['agentLifecycle'].append({'start':row});continue
 polls=0
 while run.get('status') not in ['COMPLETED','DEGRADED','FAILED','TIMED_OUT','CANCELLED'] and time.perf_counter()-start<32:
  time.sleep(.2);polls+=1;_,run=call('/api/v1/agent/runs/'+run['id'],member)
 report['agentLifecycle'].append({'ms':round((time.perf_counter()-start)*1000,3),'status':run.get('status'),'polls':polls,'modelCalls':run.get('usage',{}).get('modelCalls'),'toolCalls':run.get('usage',{}).get('toolCalls')})
(ROOT/'reports/final-hardening'/f'{a.label}-api.json').write_text(json.dumps(report,indent=2))
print('Agent lifecycle:',report['agentLifecycle'])
