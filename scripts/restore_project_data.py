#!/usr/bin/env python3
"""Copy an external research backup into project data without overwriting any file."""
import argparse
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def destination(logical):
    if logical.parts[0] == 'data':
        return logical
    if logical.parts[0] == 'database':
        return Path('data/backups/legacy-database').joinpath(*logical.parts[1:])
    raise ValueError(f'Unexpected backup category: {logical}')

def restore(source, project, manifest, apply=False):
    source, project = Path(source).resolve(), Path(project).resolve()
    if source == project or source in project.parents or project in source.parents:
        raise ValueError('Source and destination trees must be separate')
    expected = {}
    for line in Path(manifest).read_text().splitlines():
        cells = [c.strip() for c in line.split('|')]
        if len(cells) >= 6 and cells[1].startswith(('data/', 'database/')):
            expected[cells[1]] = cells[4]
    if not expected:
        raise ValueError('An original hash manifest is required')
    rows = []
    # Preflight the entire tree before copying; reject symlinks and differing targets.
    for path in sorted(source.rglob('*')):
        if path.is_symlink():
            raise ValueError(f'Symlink forbidden in backup: {path}')
        if not path.is_file():
            continue
        logical = path.relative_to(source)
        target = project / destination(logical)
        for parent in [target, *target.parents]:
            if parent == project:
                break
            if parent.is_symlink():
                raise ValueError(f'Symlink forbidden in destination: {parent}')
        digest = sha(path)
        if logical.as_posix() in expected and expected[logical.as_posix()] != digest:
            raise ValueError(f'Original manifest mismatch: {logical}')
        exists = target.exists()
        if exists and (not target.is_file() or sha(target) != digest):
            raise FileExistsError(f'Existing destination differs; no overwrite: {target}')
        rows.append({'source':logical.as_posix(),'destination':target.relative_to(project).as_posix(),
                     'bytes':path.stat().st_size,'sha256':digest,'action':'UNCHANGED' if exists else 'COPY'})
    if not set(expected).issubset({r['source'] for r in rows}):
        raise ValueError('One or more manifest assets are missing from the backup')
    if apply:
        for row in rows:
            target = project / row['destination']
            if row['action'] == 'COPY':
                target.parent.mkdir(parents=True, exist_ok=True)
                # Exclusive creation protects even against a file appearing after preflight.
                with (source / row['source']).open('rb') as src, target.open('xb') as dst:
                    shutil.copyfileobj(src, dst, length=1024*1024)
            if sha(target) != row['sha256']:
                raise ValueError(f'Destination verification failed: {target}')
    return {'mode':'APPLY' if apply else 'DRY_RUN','files':len(rows),'bytes':sum(r['bytes'] for r in rows),
            'copied':sum(r['action']=='COPY' for r in rows),'unchanged':sum(r['action']=='UNCHANGED' for r in rows),
            'manifestEntries':len(expected),'manifestMatched':len(expected),'allDestinationHashesVerified':apply,
            'externalBackupRetained':True,'entries':rows}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source',type=Path,default=ROOT.parent/'VIRIDEXA-protected')
    p.add_argument('--manifest',type=Path,default=ROOT/'reports/data-restoration/original-asset-manifest.md')
    p.add_argument('--apply',action='store_true')
    p.add_argument('--report',type=Path,default=ROOT/'reports/data-restoration/copy-report.json')
    a=p.parse_args();report=restore(a.source,ROOT,a.manifest,a.apply)
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:v for k,v in report.items() if k!='entries'},ensure_ascii=False))
if __name__=='__main__':main()
