"""Explicit, auditable TiDB baseline transformations. Never edits MySQL migrations."""
import re
FIELDS=('leaf_shape','leaf_margin','flower_colour','growth_form','fruit_type','fruit_shape')
IDENTIFIER=re.compile(r'^[a-zA-Z0-9_]+$')
def identifier(value):
    if not IDENTIFIER.fullmatch(value):raise ValueError('Unsafe database identifier')
    return '`'+value+'`'
def ordered(nodes,dependencies):
    pending=set(nodes);result=[]
    while pending:
        ready=sorted(n for n in pending if not (set(dependencies.get(n,())) & pending))
        if not ready:raise ValueError('Cyclic dependencies require explicit migration handling')
        result.extend(ready);pending.difference_update(ready)
    return result

def portable_view(sql,schema):
    sql=re.sub(r'\bALGORITHM\s*=\s*\w+\s*','',sql,flags=re.I)
    sql=re.sub(r'DEFINER\s*=\s*`[^`]+`@`[^`]+`\s*','',sql,flags=re.I)
    sql=sql.replace(identifier(schema)+'.','')
    if re.search(r'\bJSON_TABLE\s*\(',sql,re.I):raise ValueError('JSON_TABLE requires an explicit view replacement')
    return sql.rstrip(';')+';'

def trait_support(limit=1000):
    if limit<1 or limit>100000:raise ValueError('Ordinal support limit out of range')
    statements=['CREATE TABLE tidb_json_ordinal (n INT NOT NULL PRIMARY KEY);']
    for start in range(0,limit,500):statements.append('INSERT INTO tidb_json_ordinal(n) VALUES '+','.join(f'({n})' for n in range(start,min(start+500,limit)))+';')
    # Fail on future oversized arrays instead of silently dropping scientific terms.
    checks=' AND '.join(f"COALESCE(JSON_LENGTH(JSON_EXTRACT(projection,'$.traits.{f}')),0)<={limit}" for f in FIELDS)
    statements.append('ALTER TABLE ontology_projection ADD CONSTRAINT ck_tidb_trait_capacity CHECK ('+checks+');')
    return '\n'.join(statements)+'\n'

def text_value(expr,limit=None):
    value=f"CASE WHEN JSON_TYPE({expr}) IN ('NULL','OBJECT','ARRAY') THEN NULL ELSE JSON_UNQUOTE({expr}) END"
    return f'LEFT(({value}),{limit})' if limit else value

def trait_view():
    path="CONCAT('$.traits.',k.field_key,'[',n.n,']')"
    source=text_value(f"JSON_EXTRACT(o.projection,CONCAT({path},'.source_term'))")
    concept=text_value(f"JSON_EXTRACT(o.projection,CONCAT({path},'.concept'))",80)
    keys=' UNION ALL '.join("SELECT '"+field+"' AS field_key" for field in FIELDS)
    return f'''CREATE VIEW graph_trait AS
SELECT c.id AS candidate_id,c.source_id,c.locator,c.graph_state,c.publication_state,o.vocabulary_version,
k.field_key,n.n+1 AS ordinality,{source} AS source_term,{concept} AS concept
FROM graph_candidate c JOIN ontology_projection o ON o.candidate_id=c.id
CROSS JOIN ({keys}) k JOIN tidb_json_ordinal n ON n.n<COALESCE(JSON_LENGTH(JSON_EXTRACT(o.projection,CONCAT('$.traits.',k.field_key))),0)
WHERE c.pipeline='MORPHOLOGY' AND ({source}) IS NOT NULL;
'''

def trait_count_view():
    return "CREATE VIEW graph_public_trait_count AS SELECT COUNT(*) AS record_count FROM graph_trait t JOIN public_source_record f ON f.id=t.candidate_id WHERE f.pipeline='MORPHOLOGY';\n"

def portable_table(sql):
    """Unify ASCII key charset for TiDB JSON comparisons, preserving ASCII-only checks."""
    checks=[]
    table=re.match(r"\s*CREATE TABLE `([a-zA-Z0-9_]+)`",sql).group(1)
    for line in sql.splitlines():
        if re.search(r'CHARACTER SET ascii COLLATE ascii_bin',line,re.I):
            column=re.match(r'\s*`([a-zA-Z0-9_]+)`',line)
            if not column:raise ValueError('Unrecognized ASCII column DDL')
            name=column.group(1)
            checks.append('CONSTRAINT '+identifier(('ca_'+table+'_'+name)[:64])+' CHECK (CHAR_LENGTH('+identifier(name)+')=OCTET_LENGTH('+identifier(name)+'))')
    sql=re.sub(r'CHARACTER SET ascii COLLATE ascii_bin','CHARACTER SET utf8mb4 COLLATE utf8mb4_bin',sql,flags=re.I)
    if checks:
        marker=sql.rfind(') ENGINE=')
        if marker<0:raise ValueError('Unrecognized table DDL boundary')
        sql=sql[:marker].rstrip()+',\n  '+',\n  '.join(checks)+'\n'+sql[marker:]
    return sql
