#!/usr/bin/env python3
"""Explicit candidate adapter for CSV/TSV/JSONL, never approves or links samples.
A source-specific mapping must be validated against real files before production use.
"""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path
from scientific import canonical, digest, normalize, records


def literal(value):
    return "CONVERT(X'" + str(value).encode().hex() + "' USING utf8mb4)"


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--input',type=Path,required=True)
    p.add_argument('--sha256',required=True)
    p.add_argument('--dataset',required=True)
    p.add_argument('--version',required=True)
    p.add_argument('--pipeline',choices=['TAXONOMY','MORPHOLOGY','LIPID'],required=True)
    p.add_argument('--batch-size',type=int,default=100)
    p.add_argument('--apply',action='store_true')
    p.add_argument('--dry-run',action='store_true')
    p.add_argument('--report',type=Path,required=True)
    a=p.parse_args()
    if a.batch_size<1 or (a.apply and a.dry_run): p.error('Invalid batch size or conflicting modes')
    with a.input.open('rb') as f: sha=hashlib.file_digest(f,'sha256').hexdigest()
    if sha!=a.sha256: raise SystemExit('HASH_MISMATCH: no database writes')
    source=digest(a.dataset+':'+sha)
    prepared=[]; reasons={}; count=0
    for index,row in enumerate(records(a.input),1):
        count+=1
        normalized,reason=normalize(row,a.pipeline)
        if reason: reasons[reason]=reasons.get(reason,0)+1
        content=canonical(dict(raw=row, normalized=normalized, reason=reason, pipeline=a.pipeline))
        key=digest(source+':'+str(index)+':'+digest(content))
        vals=[key,source,a.pipeline,canonical({'record_ordinal':index}),canonical(row),canonical(normalized),digest(content),'HELD' if reason else 'CANDIDATE']
        prepared.append('INSERT INTO scientific_candidate SELECT '+','.join(literal(v) for v in vals)+','+('NULL' if reason is None else literal(reason))+' WHERE NOT EXISTS (SELECT 1 FROM scientific_candidate WHERE id='+literal(key)+');')
    header='INSERT INTO scientific_source_version SELECT '+','.join(literal(v) for v in [source,a.dataset,a.version,str(a.input.resolve()),sha,'UNKNOWN','VERIFIED'])+' WHERE NOT EXISTS (SELECT 1 FROM scientific_source_version WHERE id='+literal(source)+');'
    completed=0
    if a.apply:
        for start in range(0,len(prepared),a.batch_size):
            query='USE viridexa; START TRANSACTION;'+header+''.join(prepared[start:start+a.batch_size])+'COMMIT;'
            r=subprocess.run(['docker','exec','-i','viridexa-db-v1','sh','-c','MYSQL_PWD="$MYSQL_IMPORTER_PASSWORD" mysql -uviridexa_importer --default-character-set=utf8mb4'],input=query,text=True,capture_output=True)
            if r.returncode: raise SystemExit('DATABASE_BATCH_FAILED: prior batches committed; replay safe; '+r.stderr)
            completed+=1
    report=dict(mode='applied' if a.apply else 'dry-run',source=source,sha256=sha,records=count,
                normalized=count-sum(reasons.values()),held=sum(reasons.values()),reasons=reasons,
                confirmed=0,license='UNKNOWN',completed_batches=completed,
                content_checksum=digest(''.join(prepared)),unlinked_samples='all lipid samples remain unlinked',
                limitation='Generic explicit fields only; real source adapters require file validation')
    a.report.parent.mkdir(parents=True,exist_ok=True)
    a.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(canonical(report))

if __name__=='__main__': main()
