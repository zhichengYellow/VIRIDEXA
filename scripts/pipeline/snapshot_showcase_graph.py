#!/usr/bin/env python3
"""Reproducible graph aggregates over live base relations, without copying graph/scientific data.
Only aggregate counts and verification hashes are retained. Default dry-run; --apply stores a snapshot.
Public queries continue to check current permissions; this snapshot is explicitly dated metadata.
"""
import argparse,collections,hashlib,json,sys
from pathlib import Path
from run_science import mysql
from import_candidates import literal
ROOT=Path(__file__).resolve().parents[2]
def rows(q):return [json.loads(bytes.fromhex(x)) for x in mysql(q,user='root').splitlines()]
def query(expr,frm):return rows('SELECT HEX(CAST('+expr+' AS CHAR)) FROM '+frm+';')
def digest(v):return hashlib.sha256(json.dumps(v,sort_keys=True,separators=(',',':'),ensure_ascii=False).encode()).hexdigest()
def count(q):return int(mysql(q+';',user='root').strip())
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apply',action='store_true');p.add_argument('--report',type=Path,default=ROOT/'reports/showcase/graph-snapshot.json');a=p.parse_args()
 def revision():return mysql("SELECT JSON_ARRAY((SELECT COALESCE(MAX(id),0) FROM scientific_decision),(SELECT COALESCE(MAX(id),0) FROM review_event),(SELECT COUNT(*) FROM scientific_candidate),(SELECT COALESCE(SUM(CRC32(CONCAT(id,content_sha256,normalization_state))),0) FROM scientific_candidate),(SELECT COALESCE(SUM(CRC32(CONCAT(id,license_state,integrity_state))),0) FROM scientific_source_version),(SELECT COALESCE(SUM(CRC32(CONCAT(candidate_id,visibility,content_sha256))),0) FROM source_record_access),(SELECT COALESCE(SUM(CRC32(projection_sha256)),0) FROM ontology_projection));",user='root')
 initial_revision=revision()
 catalog=query("JSON_OBJECT('id',p.id,'name',t.canonical_name,'taxon',p.taxon_id)",'graph_catalog p JOIN taxon t ON t.id=p.taxon_id WHERE p.is_public=1 ORDER BY p.id')
 taxa=query("JSON_OBJECT('id',id,'parent',parent_id,'source',source_id)",'graph_taxon WHERE is_public=1 ORDER BY id')
 source_rows=query("JSON_OBJECT('id',f.id,'pipeline',f.pipeline,'source',f.source_id,'content',f.content_sha256,'confidence',f.knowledge_confidence,'name',JSON_UNQUOTE(JSON_EXTRACT(f.normalized_payload,'$.scientific_name')),'namespace',JSON_UNQUOTE(JSON_EXTRACT(f.normalized_payload,'$.source_namespace')),'taxon',JSON_UNQUOTE(JSON_EXTRACT(f.normalized_payload,'$.source_taxon_id')),'accepted',JSON_UNQUOTE(JSON_EXTRACT(f.normalized_payload,'$.accepted_taxon_id')),'parent',JSON_UNQUOTE(JSON_EXTRACT(f.normalized_payload,'$.parent_taxon_id')))",'public_source_record f ORDER BY f.id')
 pub={r['id'] for r in source_rows};plant_ids={r['id'] for r in catalog};taxon_ids={r['id'] for r in taxa}
 plant_names=query("JSON_OBJECT('id',id,'owner',plant_id,'state',review_state)",'plant_name ORDER BY id')
 taxon_names=query("JSON_OBJECT('id',id,'owner',taxon_id,'state',review_state)",'taxon_name ORDER BY id')
 allowed={'APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED'}
 name_count=sum(r['owner'] in plant_ids and r['state'] in allowed for r in plant_names)+sum(r['owner'] in taxon_ids and r['state'] in allowed for r in taxon_names)
 obs=query("JSON_OBJECT('id',o.candidate_id,'sample',o.sample_id,'sameSource',c.source_id=o.source_id,'sameStudy',JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.study_id'))=sm.study_key,'sameSample',JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.sample_id'))=sm.sample_key)",'scientific_lipid_observation o JOIN scientific_candidate c ON c.id=o.candidate_id JOIN scientific_sample sm ON sm.id=o.sample_id ORDER BY o.candidate_id')
 visible_obs=[o for o in obs if o['id'] in pub];samples={o['sample'] for o in visible_obs}
 projections=query("JSON_OBJECT('id',o.candidate_id,'sha',o.projection_sha256,'projection',o.projection)","ontology_projection o JOIN scientific_candidate c ON c.id=o.candidate_id WHERE c.pipeline='MORPHOLOGY' ORDER BY o.candidate_id")
 keys=('leaf_shape','leaf_margin','flower_colour','growth_form','fruit_type','fruit_shape');trait_counts={}
 for r in projections:
  if r['id'] not in pub:continue
  traits=(r['projection'] or {}).get('traits',{});n=0
  for k in keys:
   v=traits.get(k) or []
   if not isinstance(v,list):raise ValueError('Unexpected ontology shape; no estimated count allowed')
   n+=sum(isinstance(term,dict) and term.get('source_term') is not None for term in v)
  trait_counts[r['id']]=n
 # Check a bounded real subset against the actual graph-trait read model, not an invented fixture.
 subset=list(trait_counts)[:120];ids=','.join(literal(k) for k in subset)
 sql_check=count('SELECT COUNT(*) FROM graph_trait WHERE candidate_id IN ('+ids+')') if subset else 0
 if sql_check!=sum(trait_counts[k] for k in subset):raise RuntimeError('Trait projection count differs from graph SQL')
 types=collections.Counter({'SOURCE':count('SELECT COUNT(*) FROM scientific_source_version'),'FILE':count('SELECT COUNT(*) FROM source_asset a WHERE EXISTS(SELECT 1 FROM scientific_source_version s WHERE s.sha256=a.sha256)'),'PLANT':len(catalog),'TAXON':len(taxa),'NAME':name_count,'SAMPLE':len(samples),'TRAIT':sum(trait_counts.values())})
 for r in source_rows:types['MEASUREMENT' if r['pipeline']=='LIPID' else r['pipeline']]+=1
 edges=collections.Counter({'SOURCE_FILE':count('SELECT COUNT(*) FROM scientific_source_version s JOIN source_asset a ON a.sha256=s.sha256'),'IDENTITY_REFERENCE':sum(r['taxon'] is not None for r in catalog),'PARENT_TAXON':sum(r['parent'] in taxon_ids for r in taxa),'HAS_NAME':name_count,'SOURCED_FROM':sum(r['source'] is not None for r in taxa)+len(source_rows)+len(samples),'HAS_MEASUREMENT':sum(bool(o['sameSource'] and o['sameStudy'] and o['sameSample']) for o in visible_obs),'HAS_TRAIT':sum(trait_counts.values()),'SUPPORTED_BY':0})
 links=query("JSON_OBJECT('candidate',candidate_id)",'scientific_evidence_link ORDER BY id');edges['SUPPORTED_BY']=sum(r['candidate'] in pub for r in links)
 by_native=collections.defaultdict(list)
 for r in source_rows:
  if r['pipeline']=='TAXONOMY' and isinstance(r['namespace'],str) and isinstance(r['taxon'],str):
   if len(r['namespace'])>32 or len(r['taxon'])>191:raise ValueError('Native key width requires direct SQL verification')
   by_native[(r['source'],r['namespace'],r['taxon'])].append(r['id'])
 for r in source_rows:
  if r['pipeline']!='TAXONOMY':continue
  for field,relation in [('accepted','ACCEPTED_TAXON'),('parent','PARENT_TAXON')]:
   if isinstance(r[field],str) and r[field] and isinstance(r['namespace'],str):edges[relation]+=len(by_native.get((r['source'],r['namespace'],r[field]),[]))
 owners=collections.Counter(r['name'] for r in catalog)
 edges['SOURCE_NAME_RECORD']=sum(owners[r['name']] for r in source_rows if isinstance(r['name'],str))
 # Every count is derived from the same native IDs/FKs/literal-name predicate as V15/V18 graph views.
 fingerprint=digest({'catalog':catalog,'taxa':taxa,'names':[plant_names,taxon_names],'sources':source_rows,'observations':visible_obs,'projections':[(r['id'],r['sha']) for r in projections],'links':links})
 validation={'rules':'graph-read-model-v22','boundedTraitSqlRecords':len(subset),'boundedTraitSqlCount':sql_check,'fullTraitProjectionCount':sum(trait_counts.values()),'rawScientificRowsCopied':False,'humanReviewCreated':False}
 if revision()!=initial_revision:raise RuntimeError('Knowledge state changed during snapshot; rerun before publishing statistics')
 if a.apply:
  mysql('INSERT INTO graph_statistics_snapshot(scope,source_fingerprint,node_counts,edge_counts,validation) SELECT '+','.join(literal(x) for x in ['PUBLIC',fingerprint,json.dumps(dict(types)),json.dumps(dict(edges)),json.dumps(validation)])+' WHERE NOT EXISTS(SELECT 1 FROM graph_statistics_snapshot WHERE scope=\'PUBLIC\' AND source_fingerprint='+literal(fingerprint)+');',user='root')
 report={'mode':'APPLY' if a.apply else 'DRY_RUN','scope':'PUBLIC','sourceFingerprint':fingerprint,'nodeCounts':dict(types),'edgeCounts':dict(edges),'totalNodes':sum(types.values()),'totalEdges':sum(edges.values()),'validation':validation,'notice':'Dated aggregate release snapshot; node/edge queries independently enforce current visibility.'}
 a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps({'nodes':report['totalNodes'],'edges':report['totalEdges'],'mode':report['mode']}))
if __name__=='__main__':main()
