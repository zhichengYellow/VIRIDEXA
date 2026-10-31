#!/usr/bin/env python3
"""Provision three table-scoped Starter identities; persist only ignored server-side credentials."""
import argparse,os,secrets,subprocess,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apply',action='store_true');args=p.parse_args()
 if not args.apply:raise SystemExit('Requires --apply')
 target=ROOT/'.runtime/render-tidb.env'
 target.parent.mkdir(mode=0o700,exist_ok=True)
 if not target.exists():
  prefix=os.environ['TIDB_MIGRATOR_USER'].split('.')[0]
  url=os.environ['TIDB_JDBC_URL']
  if 'forceConnectionTimeZoneToSession=' not in url:url+='&forceConnectionTimeZoneToSession=true'
  values={'DB_URL':url,'SPRING_PROFILES_ACTIVE':'render,tidb','AGENT_MODEL_ENABLED':'false','WEB_ORIGIN':'https://viridexa.366001769.workers.dev'}
  for key,role in [('DB','ro'),('DB_ACCOUNT','member'),('DB_REVIEW','review')]:values.update({key+'_USERNAME':prefix+'.vx_'+role+'_r1',key+'_PASSWORD':secrets.token_urlsafe(36)})
  fd=os.open(target,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
  with os.fdopen(fd,'w') as f:f.write(''.join(k+"='"+v+"'\n" for k,v in values.items()))
 import shlex
 env=os.environ.copy()
 for line in target.read_text().splitlines():
  key,value=line.split('=',1);env[key]=shlex.split(value)[0]
 with tempfile.TemporaryDirectory(prefix='viridexa-provision-') as tmp:
  libs=[]
  with zipfile.ZipFile(ROOT/'apps/api/target/viridexa-api-0.1.0.jar') as jar:
   for name in jar.namelist():
    if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
     path=Path(tmp)/Path(name).name;path.write_bytes(jar.read(name));libs.append(str(path))
  cp=os.pathsep.join(libs)
  r=subprocess.run(['javac','-cp',cp,'-d',tmp,str(ROOT/'scripts/tidb/TiDBReleaseTool.java'),str(ROOT/'scripts/tidb/RuntimeProvision.java')],capture_output=True)
  if r.returncode:raise SystemExit('Provision compilation failed')
  return subprocess.call(['java','-Xmx256m','-cp',tmp+os.pathsep+cp,'RuntimeProvision',str(ROOT/'scripts/tidb/provision-grants.starter.example.sql')],env=env)
if __name__=='__main__':raise SystemExit(main())
