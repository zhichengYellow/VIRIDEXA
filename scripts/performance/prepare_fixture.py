#!/usr/bin/env python3
"""Bounded, FK-preserving real-source performance slice. Never writes production."""
import json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/pipeline'))
from run_science import mysql
e=dict(x.split('=',1) for x in (ROOT/'.env.governance-test').read_text().splitlines() if '=' in x)
s=e['GOVERNANCE_TEST_SCHEMA']
assert s.startswith('viridexa_governance_test_') and s.replace('_','').isalnum()
def sql(q):return mysql('USE '+s+';'+q,user='root')
assert int(sql('SELECT COUNT(*) FROM scientific_candidate;').strip())<=1204
# Primary keys keep reruns idempotent. No integrity constraint is disabled.
q='CREATE TEMPORARY TABLE chosen_candidates(id CHAR(64) PRIMARY KEY);'
for pipeline in ['TAXONOMY','MORPHOLOGY','LIPID']:
 q+=f"INSERT IGNORE INTO chosen_candidates SELECT id FROM viridexa.scientific_candidate WHERE pipeline='{pipeline}' AND normalization_state='CANDIDATE' ORDER BY id LIMIT 400;"
columns='id,source_id,pipeline,locator,raw_payload,normalized_payload,content_sha256,normalization_state,reason'
q+='START TRANSACTION;INSERT IGNORE INTO scientific_candidate('+columns+') SELECT '+','.join('c.'+x for x in columns.split(','))+' FROM viridexa.scientific_candidate c JOIN chosen_candidates x ON x.id=c.id;'
q+='INSERT IGNORE INTO scientific_sample SELECT sm.* FROM viridexa.scientific_sample sm WHERE sm.id IN(SELECT o.sample_id FROM viridexa.scientific_lipid_observation o JOIN chosen_candidates x ON x.id=o.candidate_id);'
for table in ['scientific_lipid_observation','scientific_evidence_link','ontology_projection']:
 q+=f'INSERT IGNORE INTO {table} SELECT o.* FROM viridexa.{table} o JOIN chosen_candidates x ON x.id=o.candidate_id;'
# Clone existing visibility only: no new scientific decisions or inferred identities.
for table in ['source_record_access','source_release_event']:
 q+=f'INSERT IGNORE INTO {table} SELECT a.* FROM viridexa.{table} a JOIN chosen_candidates x ON x.id=a.candidate_id;'
q+='COMMIT;'
sql(q)
plant_ids=sql('SELECT id FROM viridexa.plant WHERE id IN(2,9445,30610) UNION SELECT id FROM (SELECT id FROM viridexa.plant ORDER BY id LIMIT 1000) x;').splitlines()
assert len(plant_ids)<=1003 and all(x.isdigit() for x in plant_ids)
ids=','.join(plant_ids)
ancestors=sql(f"WITH RECURSIVE a AS(SELECT t.id,t.parent_id,0 depth FROM viridexa.taxon t JOIN viridexa.plant p ON p.taxon_id=t.id WHERE p.id IN({ids}) UNION ALL SELECT t.id,t.parent_id,a.depth+1 FROM a JOIN viridexa.taxon t ON t.id=a.parent_id WHERE a.depth<12) SELECT id,MAX(depth) depth FROM a GROUP BY id ORDER BY depth DESC,id;").splitlines()
taxons=[x.split('\t')[0] for x in ancestors];taxon_keys=','.join(taxons)
q=f'START TRANSACTION;INSERT IGNORE INTO evidence SELECT e.* FROM viridexa.evidence e WHERE e.id IN(SELECT evidence_id FROM viridexa.taxon WHERE id IN({taxon_keys}));'
for key in taxons:q+=f'INSERT IGNORE INTO taxon SELECT * FROM viridexa.taxon WHERE id={key};'
q+=f'INSERT IGNORE INTO plant SELECT * FROM viridexa.plant WHERE id IN({ids});'
q+=f'INSERT IGNORE INTO plant_name SELECT * FROM viridexa.plant_name WHERE plant_id IN({ids});'
q+=f'INSERT IGNORE INTO taxon_name SELECT * FROM viridexa.taxon_name WHERE taxon_id IN({taxon_keys});'
q+=f"INSERT IGNORE INTO review_event SELECT * FROM viridexa.review_event WHERE entity_type='PLANT' AND entity_key IN(SELECT record_key FROM plant WHERE id IN({ids}));"
q+=f'INSERT IGNORE INTO catalog_display_access SELECT * FROM viridexa.catalog_display_access WHERE plant_id IN({ids});COMMIT;'
sql(q)
counts={t:int(sql('SELECT COUNT(*) FROM '+t).strip()) for t in ['plant','scientific_candidate','scientific_sample','scientific_lipid_observation','ontology_projection','published_scientific_fact','scientific_decision']}
assert counts['scientific_decision']==counts['published_scientific_fact']==0
(ROOT/'reports/final-hardening/performance-fixture.json').write_text(json.dumps({'schema':s,'counts':counts,'productionWrites':False,'syntheticScientificFacts':False,'fullProductionCardinality':False,'reason':'Docker disk headroom prevents full candidate table clone; bounded real-source slice only.'},indent=2))
print(counts)
