"""MySQL 8 batched insert-only statements with full constraints enabled."""
import json
from import_candidates import literal

SCHEMAS = {
 'scientific_source_version': [('id','CHAR(64)'),('dataset','VARCHAR(191)'),('version_label','VARCHAR(191)'),('path','VARCHAR(700)'),('sha256','CHAR(64)'),('license_state','VARCHAR(16)'),('integrity_state','VARCHAR(16)')],
 'scientific_candidate': [('id','CHAR(64)'),('source_id','CHAR(64)'),('pipeline','VARCHAR(16)'),('locator','JSON'),('raw_payload','JSON'),('normalized_payload','JSON'),('content_sha256','CHAR(64)'),('normalization_state','VARCHAR(16)'),('reason','VARCHAR(255)')],
 'scientific_sample': [('id','CHAR(64)'),('source_id','CHAR(64)'),('study_key','VARCHAR(191)'),('sample_key','VARCHAR(191)'),('identity_text','VARCHAR(4000)'),('linkage_state','VARCHAR(16)')],
 'scientific_lipid_observation': [('candidate_id','CHAR(64)'),('source_id','CHAR(64)'),('sample_id','CHAR(64)'),('kind','VARCHAR(32)'),('raw_value','VARCHAR(4000)'),('value_numeric','DECIMAL(30,12)'),('unit','VARCHAR(80)'),('basis','VARCHAR(4000)'),('analyte','VARCHAR(255)')],
 'scientific_evidence_link': [('id','CHAR(64)'),('candidate_id','CHAR(64)'),('document_source_id','CHAR(64)'),('locator','JSON'),('relation','VARCHAR(32)')]
}


def insert(table, values, key):
    if table not in SCHEMAS or len(values)!=len(SCHEMAS[table]): raise ValueError('Invalid batch schema')
    return table, values, key


def bulk_sql(batch):
    grouped={table:{} for table in SCHEMAS}
    for table,values,key in batch:
        row=[json.loads(v) if typ=='JSON' and v is not None else v for v,(_,typ) in zip(values,SCHEMAS[table])]
        grouped[table].setdefault(key,row)
    statements=[]
    for table,schema in SCHEMAS.items():
        if not grouped[table]: continue
        columns=','.join(f"{name} {typ} PATH '$[{i}]' ERROR ON ERROR" for i,(name,typ) in enumerate(schema))
        payload=literal(json.dumps(list(grouped[table].values()),ensure_ascii=False,separators=(',',':'),allow_nan=False))
        key=schema[0][0]
        statements.append(f"INSERT INTO {table} SELECT jt.* FROM JSON_TABLE({payload}, '$[*]' COLUMNS({columns})) jt WHERE NOT EXISTS (SELECT 1 FROM {table} existing WHERE existing.{key}=jt.{key});")
    return '\n'.join(statements)
