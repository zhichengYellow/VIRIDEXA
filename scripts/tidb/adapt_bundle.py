#!/usr/bin/env python3
"""Derive a new sealed charset-compatible bundle without changing its source or raw data."""
import argparse,hashlib,json,shutil
from pathlib import Path
from compatibility import portable_table
from run_release import validate,ROOT

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--input',type=Path,required=True);p.add_argument('--output',type=Path,required=True);args=p.parse_args()
 src=validate(args.input);dst=args.output.resolve()
 if ROOT/'data' not in dst.parents or dst.exists():raise SystemExit('Requires fresh ignored project data directory')
 shutil.copytree(src,dst)
 (dst/'INCOMPLETE').write_text('Derived bundle not yet sealed\n')
 path=dst/'schema/V1__source_tables.sql'
 path.write_text(';\n'.join(portable_table(s.lstrip()) for s in path.read_text().split(';') if s.strip())+';\n')
 m=json.loads((src/'manifest.json').read_text());m['derivedFromManifestSha256']=hashlib.sha256((src/'manifest.json').read_bytes()).hexdigest();m['asciiKeyCompatibility']='utf8mb4_bin with ASCII-length CHECK guards'
 for name in m['files']:
  file=dst/name
  with file.open('rb') as f:digest=hashlib.file_digest(f,'sha256').hexdigest()
  m['files'][name]={'sha256':digest,'bytes':file.stat().st_size}
 (dst/'manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2)+'\n');(dst/'INCOMPLETE').unlink();validate(dst)
 print('Derived sealed bundle: original data bytes unchanged; target schema charset guards added.')
if __name__=='__main__':main()
