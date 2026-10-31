#!/usr/bin/env python3
"""Local Render-budget JVM against real TiDB; GET smoke checks only, no model/user/review writes."""
import argparse,json,os,shlex,subprocess,time,socket,urllib.request,urllib.error,urllib.parse
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--port',type=int,default=0);p.add_argument('--source-mysql',action='store_true');args=p.parse_args()
 if args.port==0:
  with socket.socket() as sock:sock.bind(('127.0.0.1',0));args.port=sock.getsockname()[1]
 env=os.environ.copy()
 for line in (ROOT/'.runtime/render-tidb.env').read_text().splitlines():
  key,value=line.split('=',1);env[key]=shlex.split(value)[0]
 if args.source_mysql:
  config=json.loads(subprocess.check_output(['docker','inspect','viridexa-api-1']))[0]['Config']['Env']
  source=dict(line.split('=',1) for line in config if '=' in line)
  for key in ['DB_USERNAME','DB_PASSWORD','DB_ACCOUNT_USERNAME','DB_ACCOUNT_PASSWORD','DB_REVIEW_USERNAME','DB_REVIEW_PASSWORD']:
   if key in source:env[key]=source[key]
  env['DB_URL']='jdbc:mysql://127.0.0.1:3309/viridexa?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
  env['SPRING_PROFILES_ACTIVE']='render'
 env.pop('OPENROUTER_API_KEY',None);env.update(PORT=str(args.port),SERVER_ADDRESS='127.0.0.1',AGENT_MODEL_ENABLED='false')
 reportdir=ROOT/('reports/tidb-cloud/source-mysql' if args.source_mysql else 'reports/tidb-cloud');reportdir.mkdir(parents=True,exist_ok=True)
 results={'scope':'local low-memory JVM with source MySQL' if args.source_mysql else 'local low-memory JVM with actual TiDB Cloud, not Render container/deployment','modelCalls':0,'checks':[]}
 def get(path,expected=200,method='GET'):
  request=urllib.request.Request(f'http://127.0.0.1:{args.port}'+path,data=b'{}' if method=='POST' else None,method=method,headers={'Content-Type':'application/json'})
  start=time.monotonic()
  try:
   with urllib.request.urlopen(request,timeout=40) as response:code=response.status;raw=response.read()
  except urllib.error.HTTPError as error:code=error.code;raw=error.read()
  body=json.loads(raw);results['checks'].append({'path':path,'method':method,'status':code,'expected':expected,'ms':round((time.monotonic()-start)*1000,2),'bytes':len(raw),'passed':code==expected})
  if any(term in raw.decode() for term in ['/Users/','TIDB_MIGRATOR_PASSWORD','password_hash']):raise RuntimeError('Sensitive data in public response')
  print(path+' -> '+str(code),flush=True);return body
 with (reportdir/'local-api.log').open('w') as log:
  process=subprocess.Popen(['java','-Xms64m','-Xmx224m','-XX:MaxMetaspaceSize=128m','-XX:ReservedCodeCacheSize=32m','-XX:MaxDirectMemorySize=16m','-Xss512k','-XX:+UseSerialGC','-XX:ActiveProcessorCount=2','-XX:+ExitOnOutOfMemoryError','-jar',str(ROOT/'apps/api/target/viridexa-api-0.1.0.jar')],env=env,stdout=log,stderr=subprocess.STDOUT)
  try:
   start=time.monotonic()
   while time.monotonic()-start<150:
    if process.poll() is not None:raise RuntimeError('API exited; inspect private local-api.log')
    try:
     with urllib.request.urlopen(f'http://127.0.0.1:{args.port}/actuator/health',timeout=2) as response:
      if response.status==200:break
    except Exception:time.sleep(1)
   else:raise RuntimeError('Health timeout')
   results['readyMs']=round((time.monotonic()-start)*1000,2)
   get('/actuator/health');get('/api/v1/account/register',403,'POST');get('/api/v1/account/session',401);get('/actuator/metrics',401);get('/api/v1/security/meta');get('/api/v1/agent/meta');get('/api/v1/admin/science/candidates',401)
   search=get('/api/v1/plants?query='+urllib.parse.quote('银杏')+'&limit=3')
   results['plantSearchShape']=list(search) if isinstance(search,dict) else 'list'
   items=search.get('items',search.get('plants',search.get('results',[]))) if isinstance(search,dict) else search
   # Inspect only known public record fields, never fabricate a fallback plant.
   if items and isinstance(items,list):
    plant=items[0];identifier=plant['id'];results['examplePlantId']=identifier
    get('/api/v1/plants/'+str(identifier));get('/api/v1/plants/'+str(identifier)+'/sources')
   get('/api/v1/plants/suggestions?query='+urllib.parse.quote('银')+'&limit=3')
   graph=get('/api/v1/graph/search?q=Ginkgo&limit=3')
   nodes=graph.get('nodes',[]) if isinstance(graph,dict) else []
   if nodes:get('/api/v1/graph/nodes/'+urllib.parse.quote(nodes[0]['id'],safe='')+'/neighbors?limit=5')
   get('/api/v1/workbench/overview');get('/api/v1/workbench/samples?limit=3',401);get('/api/v1/showcase')
   results['allPassed']=bool(items) and bool(nodes) and all(x['passed'] for x in results['checks'])
  finally:
   process.terminate()
   try:process.wait(timeout=15)
   except subprocess.TimeoutExpired:process.kill();process.wait()
   (reportdir/'smoke.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n')
 if not results.get('allPassed'):raise SystemExit('Some real TiDB API checks failed; inspect ignored report')
 print(('Source MySQL' if args.source_mysql else 'Real TiDB')+' API smoke checks passed; remote model calls=0.')
if __name__=='__main__':main()
