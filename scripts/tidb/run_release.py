#!/usr/bin/env python3
"""Verified TLS TiDB-only preflight/migrate/import/verify using packaged JDBC/Flyway."""
import argparse,hashlib,json,os,subprocess,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def validate(bundle):
    bundle=bundle.resolve()
    if (bundle/'INCOMPLETE').exists():raise ValueError('Incomplete bundle')
    manifest=json.loads((bundle/'manifest.json').read_text())
    if not manifest.get('exported'):raise ValueError('Not an exported release')
    for name,info in manifest['files'].items():
        path=(bundle/name).resolve()
        if bundle not in path.parents or not path.is_file():raise ValueError('Invalid bundle path')
        with path.open('rb') as data:digest=hashlib.file_digest(data,'sha256').hexdigest()
        if digest!=info['sha256'] or path.stat().st_size!=info['bytes']:raise ValueError('Bundle hash mismatch')
    return bundle

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=['validate-bundle','inspect-target','create-target','status','preflight','migrate','import','verify']);parser.add_argument('--bundle',type=Path)
    parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    if args.action in ('create-target','migrate','import') and not args.apply:raise SystemExit('Cloud writes require explicit --apply')
    if args.action not in ('preflight','inspect-target','create-target','status') and not args.bundle:raise SystemExit('--bundle required')
    bundle=validate(args.bundle) if args.bundle else None
    if args.action=='validate-bundle':
        manifest=json.loads((bundle/'manifest.json').read_text())
        print(json.dumps({'validatedFiles':len(manifest['files']),'sourceTables':len(manifest['tables']),'sourceRows':sum(manifest['tables'].values()),'cloudCalls':0}));return 0
    for key in ['TIDB_JDBC_URL','TIDB_MIGRATOR_USER','TIDB_MIGRATOR_PASSWORD']:
        if not os.environ.get(key):raise SystemExit('Missing server-only environment: '+key+' (never send credentials in chat)')
    with tempfile.TemporaryDirectory(prefix='viridexa-tidb-jdbc-') as tmp:
        libs=[]
        with zipfile.ZipFile(ROOT/'apps/api/target/viridexa-api-0.1.0.jar') as jar:
            for name in jar.namelist():
                if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
                    target=Path(tmp)/Path(name).name;target.write_bytes(jar.read(name));libs.append(str(target))
        return subprocess.call(['java','-Xmx256m','--class-path',os.pathsep.join(libs),str(ROOT/'scripts/tidb/TiDBReleaseTool.java'),args.action,*([str(bundle)] if bundle else [])])
if __name__=='__main__':
    try:raise SystemExit(main())
    except ValueError as error:raise SystemExit(str(error))
