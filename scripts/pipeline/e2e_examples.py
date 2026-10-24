#!/usr/bin/env python3
"""Read actual imported examples and verify candidate publication isolation."""
import json
from pathlib import Path
from run_science import mysql
cases={
 'taxonomy': "c.pipeline='TAXONOMY' AND s.path LIKE '%wfo_plantlist%' AND JSON_UNQUOTE(JSON_EXTRACT(c.locator,'$.archive_member'))='taxon.tsv'",
 'morphology': "c.pipeline='MORPHOLOGY' AND s.path LIKE '%combined.zip' AND JSON_EXTRACT(c.normalized_payload,'$.traits.\"leaf shape\"') IS NOT NULL",
 'lipid': "c.pipeline='LIPID' AND s.path='data/fatty_acid_literature_extraction.json' AND JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.kind'))='OIL_CONTENT'"
}
examples={}
for name,where in cases.items():
    query="SELECT JSON_OBJECT('candidate_id',c.id,'source_sha256',s.sha256,'source_path',s.path,'locator',c.locator,'state',c.normalization_state,'published_count',(SELECT COUNT(*) FROM published_scientific_fact f WHERE f.id=c.id)) FROM scientific_candidate c JOIN scientific_source_version s ON s.id=c.source_id WHERE "+where+' ORDER BY c.id LIMIT 1;'
    output=mysql(query).strip()
    if not output: raise AssertionError('Missing real E2E '+name)
    row=json.loads(output)
    assert row['state']=='CANDIDATE' and row['published_count']==0
    examples[name]=row
Path('reports/phase2/e2e-examples.json').write_text(json.dumps(examples,ensure_ascii=False,indent=2)+'\n')
print('PASS: real WFO, NHM and literature JSON -> candidate database -> unpublished isolation; see e2e-examples.json')
