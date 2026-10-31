#!/usr/bin/env python3
"""Run actual domain registration on disposable TiDB tables. Never writes production accounts."""
import argparse,os,subprocess,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apply',action='store_true');p.add_argument('--graph-readonly',action='store_true');args=p.parse_args()
 if not args.apply and not args.graph_readonly:raise SystemExit('Disposable cloud objects require --apply')
 with tempfile.TemporaryDirectory(prefix='viridexa-cloud-contract-') as tmp:
  libs=[];classes=Path(tmp)/'classes'
  with zipfile.ZipFile(ROOT/'apps/api/target/viridexa-api-0.1.0.jar') as jar:
   for name in jar.namelist():
    if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
     path=Path(tmp)/Path(name).name;path.write_bytes(jar.read(name));libs.append(str(path))
    elif name.startswith('BOOT-INF/classes/') and name.endswith('.class'):
     path=classes/name.removeprefix('BOOT-INF/classes/');path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(jar.read(name))
  cp=os.pathsep.join([str(classes),*libs])
  r=subprocess.run(['javac','-cp',cp,'-d',tmp,str(ROOT/'scripts/tidb/TiDBReleaseTool.java'),str(ROOT/'scripts/tidb/CloudAccountContract.java'),str(ROOT/'scripts/tidb/DomainProbe.java')],capture_output=True)
  if r.returncode:raise SystemExit('Cloud contract compilation failed')
  env=os.environ.copy()
  if args.graph_readonly:
   import shlex
   for line in (ROOT/'.runtime/render-tidb.env').read_text().splitlines():
    key,value=line.split('=',1);env[key]=shlex.split(value)[0]
  return subprocess.call(['java','-Xmx192m','-cp',tmp+os.pathsep+cp,'DomainProbe' if args.graph_readonly else 'CloudAccountContract'],env=env)
if __name__=='__main__':raise SystemExit(main())
