#!/usr/bin/env python3
"""Real-source staging runner. Protected sources are strictly read-only.
Reports/spools live in the repository reports directory, never in protected data.
"""
import argparse
import collections
import datetime
import hashlib
import importlib
import json
from pathlib import Path
import subprocess
from scientific import canonical, digest, scan
from import_candidates import literal
from bulk_sql import insert, bulk_sql

REPO = Path(__file__).resolve().parents[2]


def mysql(query, user='viridexa_importer'):
    password = 'MYSQL_IMPORTER_PASSWORD' if user == 'viridexa_importer' else 'MYSQL_ROOT_PASSWORD'
    result = subprocess.run(['docker','exec','-i','viridexa-db-v1','sh','-c',
        f'MYSQL_PWD="${password}" mysql -u{user} --default-character-set=utf8mb4 --batch --skip-column-names viridexa'],
        input=query, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr[:1500])
    return result.stdout


def fingerprint():
    # Stream ordered content and complete source/review relations; avoid GROUP_CONCAT truncation.
    queries = [
      'SELECT SHA2(CAST(JSON_ARRAY(id,dataset,version_label,path,sha256,license_state,integrity_state) AS CHAR),256) FROM scientific_source_version ORDER BY id;',
      'SELECT SHA2(CAST(JSON_ARRAY(id,source_id,pipeline,locator,raw_payload,normalized_payload,content_sha256,normalization_state,reason) AS CHAR),256) FROM scientific_candidate ORDER BY id;',
      'SELECT SHA2(CAST(JSON_ARRAY(id,candidate_id,reviewer_id,action,rationale,decided_at) AS CHAR),256) FROM scientific_decision ORDER BY id;',
      'SELECT SHA2(CAST(JSON_ARRAY(id,source_id,study_key,sample_key,identity_text,linkage_state) AS CHAR),256) FROM scientific_sample ORDER BY id;',
      'SELECT SHA2(CAST(JSON_ARRAY(candidate_id,source_id,sample_id,kind,raw_value,value_numeric,unit,basis,analyte) AS CHAR),256) FROM scientific_lipid_observation ORDER BY candidate_id;',
      'SELECT SHA2(CAST(JSON_ARRAY(id,candidate_id,document_source_id,locator,relation) AS CHAR),256) FROM scientific_evidence_link ORDER BY id;']
    return {name: digest(mysql(q)) for name,q in zip(('sources','candidates','decisions','samples','lipid_observations','document_evidence'),queries)}



def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root',type=Path,default=REPO.parent/'VIRIDEXA-protected')
    p.add_argument('--pipeline',choices=('botany','lipid','all'),default='all')
    p.add_argument('--apply',action='store_true')
    p.add_argument('--replay',type=Path,help='Replay an audited dry-run directory without reparsing original archives')
    p.add_argument('--botany-cache',type=Path,help='Reuse checksum-verified botanical rows while refreshing the lipid adapter')
    p.add_argument('--dry-run',action='store_true')
    p.add_argument('--batch-size',type=int,default=250)
    p.add_argument('--output',type=Path,default=REPO/'reports/phase2/import')
    a=p.parse_args()
    if a.batch_size<1 or (a.apply and a.dry_run): p.error('Invalid mode or batch size')
    inventory=scan(a.root,REPO/'docs/ASSET_MANIFEST.md')
    bad=[x for x in inventory if x['state']!='VERIFIED']
    if bad: raise SystemExit('Source integrity failure: '+canonical(bad))
    hashes={x['path']:x['sha256'] for x in inventory}
    if a.replay and a.replay.resolve()==a.output.resolve(): p.error('Replay input and output must differ')
    a.output.mkdir(parents=True,exist_ok=True)
    spool=a.output/'candidates.jsonl'
    sources={}; counts=collections.Counter(); reasons=collections.Counter(); source_counts=collections.Counter()
    before=fingerprint() if a.apply else None
    batch=[]; committed=0; checksum=hashlib.sha256(); expected={}; adapter_stats={}
    try:
        with spool.open('w') as out:
            for module in (['replay'] if a.replay else (['source_botany','source_lipid'] if a.pipeline=='all' else ['source_'+('botany' if a.pipeline=='botany' else 'lipid')])):
                if module=='replay':
                    replay_report=json.loads((a.replay/'report.json').read_text())
                    replay_file=a.replay/'candidates.jsonl'
                    with replay_file.open('rb') as replay_stream:
                        if hashlib.file_digest(replay_stream,'sha256').hexdigest()!=replay_report['spool_sha256']:
                            raise ValueError('Replay spool checksum mismatch')
                    adapter_stats.update(replay_report.get('adapter_stats',{}))
                    def replay_rows():
                        with replay_file.open() as replay_stream:
                            for line in replay_stream:
                                item=json.loads(line)
                                yield {k:item[k] for k in ('pipeline','source_path','locator','raw','normalized','reason')}
                    rows=replay_rows()
                else:
                    if module=='source_botany' and a.botany_cache:
                        cache_report=json.loads((a.botany_cache/'report.json').read_text())
                        cache_file=a.botany_cache/'candidates.jsonl'
                        with cache_file.open('rb') as cache_stream:
                            if hashlib.file_digest(cache_stream,'sha256').hexdigest()!=cache_report['spool_sha256']:
                                raise ValueError('Botany cache checksum mismatch')
                        adapter_stats.update(cache_report.get('adapter_stats',{}))
                        def cached_rows():
                            with cache_file.open() as cache_stream:
                                for line in cache_stream:
                                    item=json.loads(line)
                                    if item['pipeline'] in ('TAXONOMY','MORPHOLOGY'):
                                        yield {k:item[k] for k in ('pipeline','source_path','locator','raw','normalized','reason')}
                        rows=cached_rows()
                    else:
                        adapter=importlib.import_module(module)
                        rows=adapter.extract(a.root,stats=adapter_stats) if module=='source_botany' else adapter.extract(a.root)
                for row in rows:
                    path=row['source_path']
                    if path not in hashes: raise ValueError('Unmanifested source: '+path)
                    source=digest(path+':'+hashes[path])
                    if source not in sources:
                        sources[source]=path
                        batch.append(insert('scientific_source_version',[source,path,hashes[path],path,hashes[path],'UNKNOWN','VERIFIED'],source))
                    normalized=row.get('normalized'); reason=row.get('reason')
                    if normalized is None and not reason: raise ValueError('Held row needs reason')
                    content=canonical(row)
                    key=digest(source+':'+row['pipeline']+':'+canonical(row['locator']))
                    content_hash=digest(content)
                    if key in expected: raise ValueError('Duplicate locator within source: '+canonical(row['locator']))
                    expected[key]=content_hash
                    batch.append(insert('scientific_candidate',[key,source,row['pipeline'],canonical(row['locator']),canonical(row['raw']),
                        None if normalized is None else canonical(normalized),content_hash,'HELD' if normalized is None else 'CANDIDATE',reason],key))
                    if normalized and normalized.get('paper'):
                        paper=normalized['paper']; paper_path=paper['path']
                        if hashes.get(paper_path)!=paper['sha256']: raise ValueError('Unverified declared document '+paper_path)
                        paper_id=digest(paper_path+':'+paper['sha256'])
                        if paper_id not in sources:
                            sources[paper_id]=paper_path
                            batch.append(insert('scientific_source_version',[paper_id,paper_path,paper['sha256'],paper_path,paper['sha256'],'UNKNOWN','VERIFIED'],paper_id))
                        link_id=digest(key+':'+paper_id)
                        batch.append(insert('scientific_evidence_link',[link_id,key,paper_id,canonical(normalized.get('source_location') or row['locator']),'EXPLICIT_SOURCE_DOCUMENT'],link_id))
                    if row['pipeline']=='LIPID' and normalized is not None:
                        n=normalized
                        if not n.get('sample_id') or not n.get('study_id') or not n.get('basis'):
                            raise ValueError('Normalized lipid requires study/sample/basis')
                        sample=digest(str(n['study_id'])+':'+str(n['sample_id']))
                        batch.append(insert('scientific_sample',[sample,source,str(n['study_id']),str(n['sample_id']),n.get('identity_text'),'UNLINKED'],sample))
                        batch.append(insert('scientific_lipid_observation',[key,source,sample,n['kind'],str(n.get('raw_value',n.get('value'))) if n.get('raw_value',n.get('value')) is not None else None,n.get('value'),n.get('unit'),n['basis'],n.get('analyte')],key))
                    counts[row['pipeline']]+=1
                    counts['held' if normalized is None else 'normalized']+=1
                    source_counts[path]+=1
                    if reason: reasons[reason]+=1
                    serialized=canonical(dict(id=key,source_id=source,content_sha256=content_hash,**row))
                    checksum.update((serialized+'\n').encode());out.write(serialized+'\n')
                    if len(batch)>=a.batch_size:
                        if a.apply: mysql('START TRANSACTION;\n'+bulk_sql(batch)+'COMMIT;'); committed+=1
                        batch=[]
            if batch and a.apply: mysql('START TRANSACTION;\n'+bulk_sql(batch)+'COMMIT;');committed+=1
    except Exception as e:
        (a.output/'failure.json').write_text(canonical(dict(error=str(e),committed_batches=committed,counts=dict(counts),recover='Replay same immutable source; committed batches are preserved.')))
        raise
    after=fingerprint() if a.apply else None
    if a.apply:
        actual=dict(line.split('\t') for line in mysql('SELECT id,content_sha256 FROM scientific_candidate;').splitlines())
        mismatches=[key for key,value in expected.items() if actual.get(key)!=value]
        if mismatches: raise RuntimeError('Persisted content conflicts: '+str(len(mismatches)))
    report=dict(mode='applied' if a.apply else 'dry-run',manifest_verified=len(inventory),counts=dict(counts),reasons=dict(reasons),
                adapter_stats=adapter_stats,source_counts=dict(source_counts),source_versions=len(sources),license_unknown_sources=len(sources),
                confirmed_new=0,committed_batches=committed,spool_sha256=checksum.hexdigest(),before=before,after=after,
                identical_database_replay=(before==after) if a.apply else None,
                publication='No decisions or licenses granted by importer',
                generated_at=datetime.datetime.now(datetime.timezone.utc).isoformat())
    (a.output/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(canonical(report))

if __name__=='__main__': main()
