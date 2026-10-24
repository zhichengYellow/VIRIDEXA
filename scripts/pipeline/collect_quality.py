#!/usr/bin/env python3
"""Read-only operational report, separate from public scientific coverage."""
import json
from pathlib import Path
from run_science import mysql
QUERIES={
 'candidate_states': 'SELECT pipeline,normalization_state,COALESCE(reason,""),COUNT(*) FROM scientific_candidate GROUP BY pipeline,normalization_state,reason ORDER BY pipeline,normalization_state,reason',
 'sources': 'SELECT dataset,sha256,license_state,integrity_state FROM scientific_source_version ORDER BY dataset',
 'measurement_kinds': 'SELECT kind,COUNT(*),SUM(value_numeric IS NULL) FROM scientific_lipid_observation GROUP BY kind ORDER BY kind',
 'samples': 'SELECT linkage_state,COUNT(*) FROM scientific_sample GROUP BY linkage_state',
 'lineage_links': 'SELECT relation,COUNT(*) FROM scientific_evidence_link GROUP BY relation',
 'published': 'SELECT pipeline,COUNT(*) FROM published_scientific_fact GROUP BY pipeline',
 'named_decisions': 'SELECT action,COUNT(*) FROM scientific_decision GROUP BY action',
 'historical_source_ledger': 'SELECT source_table_name,transform_state,COUNT(*) FROM source_record GROUP BY source_table_name,transform_state ORDER BY source_table_name',
 'flyway': 'SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank',
 'unimported_historical_reason': 'SELECT "LEGACY_ADAPTER_NOT_IMPLEMENTED",COUNT(*) FROM source_record WHERE transform_state IN ("INDEXED","HELD")',
}
report={name:[line.split('\t') for line in mysql(query+';').splitlines()] for name,query in QUERIES.items()}
Path('reports/phase2/quality.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print('Wrote reports/phase2/quality.json (operational totals; no publication implied).')
