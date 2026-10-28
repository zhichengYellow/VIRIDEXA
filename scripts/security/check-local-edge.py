#!/usr/bin/env python3
"""Bounded isolated Nginx checks using only localhost and a disposable mock upstream."""
import http.server,json,pathlib,subprocess,tempfile,threading,time,urllib.request,urllib.error
ROOT=pathlib.Path(__file__).resolve().parents[2]; REPORT=ROOT/'reports/phase7s'; NAME='phase7s-edge-test'
class Mock(http.server.BaseHTTPRequestHandler):
 def do_GET(self):self.respond()
 def do_POST(self):self.rfile.read(int(self.headers.get('Content-Length','0')));self.respond()
 def respond(self):
  body=json.dumps({k:self.headers.get(k) for k in ['Host','X-Forwarded-For','X-Viridexa-Client-IP','X-Viridexa-Proto','CF-Connecting-IP']}).encode();self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 def log_message(self,*a):pass

def hit(path,method='GET',spoof='192.0.2.6',body=None):
 headers={'X-Forwarded-For':spoof,'CF-Connecting-IP':spoof,'X-Viridexa-Client-IP':spoof,'X-Viridexa-Proto':'https','X-Forwarded-Proto':'https'}
 if method=='POST':headers['Content-Type']='application/json'
 req=urllib.request.Request('http://127.0.0.1:5175'+path,data=(b'{}' if body is None else body) if method=='POST' else None,headers=headers,method=method)
 try:
  with urllib.request.urlopen(req,timeout=4) as r:return r.status,dict(r.headers),r.read().decode()
 except urllib.error.HTTPError as e:return e.code,dict(e.headers),e.read().decode()

def run():
 server=http.server.ThreadingHTTPServer(('127.0.0.1',18083),Mock);threading.Thread(target=server.serve_forever,daemon=True).start(); results={}
 try:
  with tempfile.TemporaryDirectory(prefix='viridexa-edge-') as directory:
   d=pathlib.Path(directory);(d/'trusted.conf').write_text((ROOT/'infra/security/nginx-trusted-proxies.conf').read_text());(d/'index.html').write_text('isolated edge fixture');(d/'app.css').write_text('body{}')
   common=(ROOT/'infra/nginx/proxy-common.conf').read_text().replace('http://api:8080','http://host.docker.internal:18083');(d/'proxy.conf').write_text(common)
   for observe in [True,False]:
    config=(ROOT/'infra/nginx/web.conf.template').read_text()
    for key,value in {'LISTEN':'80','RATE_API':'1r/s','RATE_SEARCH':'1r/s','RATE_AUTH':'1r/m','RATE_AGENT':'1r/s','RATE_ADMIN':'1r/m','OBSERVE':'on' if observe else 'off'}.items():config=config.replace('${VIRIDEXA_NGINX_'+key+'}',value)
    for burst in [40,30,24,10,8,5]: config=config.replace('burst='+str(burst)+' ', 'burst=2 ')
    (d/'trusted.conf').write_text((ROOT/'infra/security/nginx-trusted-proxies.conf').read_text())
    (d/'nginx.conf').write_text(config)
    args=['docker','run','--rm','-d','--name',NAME,'-p','127.0.0.1:5175:80','-v',f'{d}/nginx.conf:/etc/nginx/conf.d/default.conf:ro','-v',f'{d}/proxy.conf:/etc/nginx/proxy-common.conf:ro','-v',f'{ROOT}/infra/nginx/security-headers.conf:/etc/nginx/security-headers.conf:ro','-v',f'{d}/trusted.conf:/etc/nginx/trusted-proxies.conf:ro','-v',f'{d}:/usr/share/nginx/html:ro','nginx:1.27-alpine']
    subprocess.run(args,check=True,capture_output=True)
    try:
     for attempt in range(30):
      try:
       if hit('/')[0]==200:break
      except Exception:time.sleep(.1)
     else:raise AssertionError('Isolated edge failed to start')
     # The Host including port is preserved; all visitor forwarding claims are overwritten.
     status,headers,body=hit('/api/v1/metadata');payload=json.loads(body)
     assert status==200 and payload['Host']=='127.0.0.1:5175'
     assert payload['X-Viridexa-Client-IP']!='192.0.2.6' and payload['X-Forwarded-For']==payload['X-Viridexa-Client-IP'] and payload['X-Viridexa-Proto']=='http' and payload['CF-Connecting-IP'] is None
     scopes={}
     for group,path,method in [('search','/api/v1/plants','GET'),('agent','/api/v1/agent/runs','POST'),('admin','/api/v1/admin/fixture','POST'),('ordinary','/api/v1/metadata','GET'),('auth','/api/v1/account/login','POST')]:
      codes=[];retry=[]
      for i in range(8):
       status,headers,_=hit(path,method,spoof='192.0.2.'+str(10+i));codes.append(status)
       if status==429:retry.append(headers.get('Retry-After'))
      if observe and group!='auth':assert codes==[200]*8,(group,codes)
      else:assert 429 in codes and all(r=='10' for r in retry),(group,codes,retry)
      scopes[group]={'requests':len(codes),'ok':codes.count(200),'limited':codes.count(429),'retryAfterPresent':bool(retry)}
     assert all(hit('/app.css')[0]==200 for _ in range(8))
     assert hit('/api/v1/account/register','POST',body=b'x'*32769)[0]==413
     assert hit('/actuator/env')[0]==404
     recovered=True;trusted_edge=False
     if not observe:
      time.sleep(3.1)
      assert hit('/api/v1/metadata')[0]==200
      # Simulate a vetted edge peer in this disposable config only, never in the project config.
      peer=payload['X-Viridexa-Client-IP']
      import ipaddress
      ipaddress.ip_address(peer)
      (d/'trusted.conf').write_text(f'set_real_ip_from {peer};\nreal_ip_header CF-Connecting-IP;\nreal_ip_recursive off;\ngeo $realip_remote_addr $test_trusted_peer {{ default 0; {peer} 1; }}\nmap "$test_trusted_peer:$http_x_forwarded_proto" $viridexa_forwarded_scheme {{ default $scheme; "1:https" https; }}\n')
      subprocess.run(['docker','exec',NAME,'nginx','-t'],capture_output=True,check=True)
      subprocess.run(['docker','exec',NAME,'nginx','-s','reload'],capture_output=True,check=True)
      for attempt in range(20):
       status,_,body=hit('/api/v1/metadata',spoof='192.0.2.9');got=json.loads(body)
       if got.get('X-Viridexa-Proto')=='https' and got.get('X-Viridexa-Client-IP')=='192.0.2.9':trusted_edge=True;break
       time.sleep(.1)
      assert trusted_edge
     logs=subprocess.run(['docker','logs',NAME],capture_output=True,text=True,check=True)
     (REPORT/('nginx-observe.log' if observe else 'nginx-enforce.log')).write_text(logs.stdout+logs.stderr)
     if observe:assert 'REJECTED_DRY_RUN' in logs.stdout+logs.stderr
     results['observe' if observe else 'enforce']={'scopes':scopes,'spoofedHeadersIgnored':True,'hostPortPreserved':True,'staticAssetsNotApiLimited':True,'oversizedBodyRejected':True,'actuatorHidden':True,'rateRecoveryChecked':not observe,'trustedEdgeSimulationPassed':trusted_edge}
    finally:subprocess.run(['docker','rm','-f',NAME],capture_output=True,check=False)
 finally:server.shutdown();server.server_close()
 (REPORT/'nginx-bounded-checks.json').write_text(json.dumps(results,indent=2));print(json.dumps(results,indent=2))
if __name__=='__main__':run()
