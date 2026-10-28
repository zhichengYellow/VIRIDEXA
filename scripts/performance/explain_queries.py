#!/usr/bin/env python3
"""Production SELECT-only plans; execution bounded at 5 seconds, no writes or global settings."""
import argparse,json,re,sys,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'scripts/pipeline'))
from run_science import mysql
p=argparse.ArgumentParser();p.add_argument('--label',required=True);a=p.parse_args()
s=(ROOT/'apps/api/src/main/java/org/viridexa/api/plant/PlantMapper.java').read_text()
blocks={k:v for k,v in re.findall(r'String (\w+) = """\n(.*?)""";',s,re.S)}
queries={'catalog-search-count':"SELECT COUNT(DISTINCT p.id) "+blocks['FROM_PLANTS'].replace('#{query}',"'Ginkgo'"),'catalog-detail':blocks['SELECT_COLUMNS']+blocks['FROM_PLANT_DETAIL']+' AND p.id=2','source-groups':'SELECT source_id,pipeline,COUNT(*) AS records FROM public_source_record GROUP BY source_id,pipeline','normalization-groups':"SELECT source_id,pipeline,COUNT(*) AS total,SUM(normalization_state='CANDIDATE') AS normalized,SUM(normalization_state='HELD') AS held FROM scientific_candidate GROUP BY source_id,pipeline","lipid-sample-page":"SELECT sm.id,sm.sample_key FROM scientific_sample sm WHERE EXISTS(SELECT 1 FROM scientific_lipid_observation o JOIN public_source_record f ON f.id=o.candidate_id WHERE o.sample_id=sm.id) ORDER BY sm.id LIMIT 10"}
rows=[]
for name,query in queries.items():
 item={'name':name,'sql':query,'scope':'production-readonly-select','maxExecutionMs':5000}
 for kind,prefix in [('estimate','EXPLAIN FORMAT=JSON '),('actual','EXPLAIN ANALYZE ')]:
  start=time.perf_counter()
  try:item[kind]=mysql('SET SESSION MAX_EXECUTION_TIME=5000;'+prefix+query,user='root');item[kind+'Status']='completed'
  except Exception as e:item[kind+'Status']='failed-or-timeout';item[kind+'Error']=str(e)
  item[kind+'WallMs']=round((time.perf_counter()-start)*1000,3)
 rows.append(item);print(name,item.get('actualStatus'),item.get('actualWallMs'),flush=True)
(ROOT/'reports/final-hardening'/f'{a.label}-sql-plans.json').write_text(json.dumps(rows,indent=2))
