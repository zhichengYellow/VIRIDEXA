#!/usr/bin/env python3
"""Read-only local MySQL snapshot -> new private TiDB release bundle. No cloud calls."""
import argparse,gzip,hashlib,json,os,re,subprocess,sys,tempfile
from datetime import datetime,timezone
from pathlib import Path
from compatibility import identifier,ordered,portable_view,trait_support,trait_view,trait_count_view,FIELDS,portable_table
ROOT=Path(__file__).resolve().parents[2]
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--container',default='viridexa-db-v1');parser.add_argument('--schema',default='viridexa')
    parser.add_argument('--output',type=Path);parser.add_argument('--apply-export',action='store_true')
    args=parser.parse_args();schema=args.schema;identifier(schema)
    if not re.fullmatch('[a-zA-Z0-9_-]+',args.container):raise SystemExit('Invalid container name')
    def query(sql):
        r=subprocess.run(['docker','exec','-i',args.container,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names'],input=sql,text=True,capture_output=True)
        if r.returncode:raise RuntimeError('Local metadata query failed; no credentials printed')
        return r.stdout.strip()
    def rows(sql):return [json.loads(x) for x in query(sql).splitlines() if x]
    def source_revision():
        return query(f"SELECT JSON_ARRAY((SELECT COUNT(*) FROM {identifier(schema)}.scientific_candidate),(SELECT COALESCE(SUM(CRC32(CONCAT(id,content_sha256,normalization_state))),0) FROM {identifier(schema)}.scientific_candidate),(SELECT COUNT(*) FROM {identifier(schema)}.review_event),(SELECT COUNT(*) FROM {identifier(schema)}.source_record_access),(SELECT COALESCE(SUM(CRC32(CONCAT(candidate_id,visibility,content_sha256))),0) FROM {identifier(schema)}.source_record_access),(SELECT COALESCE(SUM(CRC32(projection_sha256)),0) FROM {identifier(schema)}.ontology_projection));")
    metadata=rows(f"SELECT JSON_OBJECT('name',table_name,'type',table_type) FROM information_schema.tables WHERE table_schema='{schema}';")
    tables={x['name'] for x in metadata if x['type']=='BASE TABLE'}-{'flyway_schema_history'};views={x['name'] for x in metadata if x['type']=='VIEW'}
    foreign=rows(f"SELECT JSON_OBJECT('table',table_name,'parent',referenced_table_name,'column',column_name,'parentColumn',referenced_column_name) FROM information_schema.key_column_usage WHERE table_schema='{schema}' AND referenced_table_name IS NOT NULL;")
    deps={t:{f['parent'] for f in foreign if f['table']==t and f['parent']!=t} for t in tables}
    table_order=ordered(tables,deps)
    # A self-FK must be satisfiable by the snapshot's primary-key ordering. Never disable FKs.
    for f in foreign:
        if f['parent']==f['table']:
            bad=query(f"SELECT COUNT(*) FROM {identifier(schema)}.{identifier(f['table'])} WHERE {identifier(f['column'])} IS NOT NULL AND {identifier(f['column'])}>={identifier(f['parentColumn'])};")
            if bad!='0':raise RuntimeError('Self-FK ordering needs explicit handling; refusing to disable constraints')
    if query(f'SELECT COUNT(*) FROM {identifier(schema)}.app_user;')!='0':raise RuntimeError('Human account data requires a separate privacy migration plan')
    lengths=','.join(f"COALESCE(JSON_LENGTH(JSON_EXTRACT(projection,'$.traits.{field}')),0)" for field in FIELDS)
    maximum=int(query(f'SELECT COALESCE(MAX(GREATEST({lengths})),0) FROM {identifier(schema)}.ontology_projection;'))
    if maximum>1000:raise RuntimeError('Source trait capacity exceeds baseline bound; enlarge explicitly, never truncate')
    view_dep=rows(f"SELECT JSON_OBJECT('view',view_name,'table',table_name) FROM information_schema.view_table_usage WHERE view_schema='{schema}' AND table_schema='{schema}';")
    vd={v:{d['table'] for d in view_dep if d['view']==v and d['table'] in views} for v in views}
    if 'graph_public_trait_count' in views:vd['graph_public_trait_count']={'graph_trait','public_source_record'}
    view_order=ordered(views,vd)
    counts={t:int(query(f'SELECT COUNT(*) FROM {identifier(schema)}.{identifier(t)};')) for t in table_order}
    revision=source_revision()
    report={'scope':'complete business tables; source Flyway history archived separately; no account data present','sourceSchema':schema,'tables':counts,'views':view_order,'maxTraitArrayLength':maximum,'ordinalCapacity':1000,'sourceRevision':revision,'exported':False,'cloudImportPerformed':False,'asciiKeyCompatibility':'utf8mb4_bin with ASCII-length CHECK guards'}
    if not args.apply_export:
        print(json.dumps(report,ensure_ascii=False,indent=2));return
    if not args.output:raise SystemExit('--output is required for apply-export')
    dest=args.output.resolve()
    if dest.exists():raise SystemExit('Output already exists; never overwrite a release')
    if ROOT/'data' not in dest.parents:raise SystemExit('Private exports must live under ignored project data/')
    dest.mkdir(parents=True,mode=0o700);(dest/'schema').mkdir();os.chmod(dest,0o700)
    (dest/'INCOMPLETE').write_text('Not accepted for cloud import until manifest validation completes.\n')
    sql=[]
    for table in table_order:
        ddl=query(f'SHOW CREATE TABLE {identifier(schema)}.{identifier(table)};').split('\t',1)[1]
        if re.search(r'\bJSON_TABLE\s*\(|\bCREATE\s+(PROCEDURE|FUNCTION|TRIGGER)',ddl,re.I):raise RuntimeError('Unsupported table DDL requires explicit handling')
        sql.append(portable_table(ddl).rstrip(';')+';')
    (dest/'schema/V1__source_tables.sql').write_text('\n'.join(sql)+'\n')
    members='' if 'public_member_account' in tables else (ROOT/'apps/api/src/main/resources/db/migration/V26__portable_public_membership.sql').read_text()
    (dest/'schema/V2__tidb_portable_support.sql').write_text(members+trait_support())
    sql=[]
    for view in view_order:
        if view=='graph_trait':ddl=trait_view()
        elif view=='graph_public_trait_count':ddl=trait_count_view()
        else:
            ddl=query(f'SHOW CREATE VIEW {identifier(schema)}.{identifier(view)};').split('\t')[1]
            ddl=portable_view(ddl,schema)
        sql.append(ddl)
    (dest/'schema/V3__source_views.sql').write_text('\n'.join(sql)+'\n')
    (dest/'schema/V4__import_checkpoints.sql').write_text((ROOT/'scripts/tidb/import_checkpoints.sql').read_text())
    dump=['docker','exec',args.container,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot "$@"','sh','--single-transaction','--tz-utc','--quick','--compact','--no-create-info','--skip-triggers','--set-gtid-purged=OFF','--column-statistics=0','--no-tablespaces','--hex-blob','--default-character-set=utf8mb4','--complete-insert','--order-by-primary',schema,*table_order]
    with tempfile.TemporaryFile() as errors, gzip.open(dest/'business-data.sql.gz','wb') as output:
        process=subprocess.Popen(dump,stdout=subprocess.PIPE,stderr=errors)
        try:
            while chunk:=process.stdout.read(1024*1024):output.write(chunk)
            if process.wait()!=0:raise RuntimeError('Snapshot dump failed; release remains INCOMPLETE')
        finally:
            if process.poll() is None:process.terminate();process.wait()
    # Explicit compact dump must not disable referential/check validation.
    with gzip.open(dest/'business-data.sql.gz','rt') as stream:
        for line in stream:
            if re.search(r'(FOREIGN_KEY_CHECKS|CHECK_CONSTRAINT_CHECKS)\s*=\s*0',line,re.I):raise RuntimeError('Dump tries to disable integrity constraints')
    if revision!=source_revision():raise RuntimeError('Source changed during export; release remains INCOMPLETE')
    report['sourceFlywayHistory']=rows(f"SELECT JSON_OBJECT('version',version,'script',script,'checksum',checksum,'success',success) FROM {identifier(schema)}.flyway_schema_history ORDER BY installed_rank;")
    report['sourceMigrations']={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((ROOT/'apps/api/src/main/resources/db/migration').glob('*.sql'))}
    report['files']={str(p.relative_to(dest)):{'sha256':hashlib.file_digest(p.open('rb'),'sha256').hexdigest(),'bytes':p.stat().st_size} for p in dest.rglob('*') if p.is_file() and p.name!='INCOMPLETE'}
    report['exported']=True;report['createdAt']=datetime.now(timezone.utc).isoformat()
    (dest/'manifest.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');(dest/'INCOMPLETE').unlink()
    print(json.dumps({'exportedTables':len(tables),'sourceRows':sum(counts.values()),'views':len(views),'maxTraitArrayLength':maximum,'cloudImportPerformed':False,'bundle':str(dest)},ensure_ascii=False))
if __name__=='__main__':
    try:main()
    except (RuntimeError,ValueError) as error:raise SystemExit(str(error))
