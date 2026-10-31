#!/usr/bin/env python3
"""Explicit bounded capability probe: enables CHECK if allowed; creates/removes only its own disposable objects."""
import argparse,os,subprocess,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apply',action='store_true');p.add_argument('--inspect-views',type=Path);args=p.parse_args()
 if not args.apply and not args.inspect_views:raise SystemExit('Probe writes require --apply; never operates on business tables')
 with tempfile.TemporaryDirectory(prefix='viridexa-probe-') as tmp:
  libs=[]
  with zipfile.ZipFile(ROOT/'apps/api/target/viridexa-api-0.1.0.jar') as jar:
   for name in jar.namelist():
    if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
     path=Path(tmp)/Path(name).name;path.write_bytes(jar.read(name));libs.append(str(path))
  cp=os.pathsep.join(libs)
  r=subprocess.run(['javac','-cp',cp,'-d',tmp,str(ROOT/'scripts/tidb/TiDBReleaseTool.java'),str(ROOT/'scripts/tidb/CapabilityProbe.java'),str(ROOT/'scripts/tidb/InspectViews.java')],capture_output=True)
  if r.returncode:raise SystemExit('Probe compilation failed')
  return subprocess.call(['java','-Xmx256m','-cp',tmp+os.pathsep+cp,'InspectViews' if args.inspect_views else 'CapabilityProbe',*([str(args.inspect_views)] if args.inspect_views else [])])
if __name__=='__main__':raise SystemExit(main())
