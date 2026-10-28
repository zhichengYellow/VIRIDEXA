#!/usr/bin/env python3
"""Build a read-only exact-ID Phase 7 review queue from the live science DB and Phase 2B traces."""
import json
from pathlib import Path
import sys
HERE=Path(__file__).resolve(); REPO=HERE.parents[2]; OUT=REPO/'reports/phase7'
sys.path.insert(0,str(HERE.parent)); from run_science import mysql

def rows(q): return [[None if v=='NULL' else v for v in x.split('\t')] for x in mysql(q,user='root').splitlines()]
def obj(raw): return json.loads(bytes.fromhex(raw).decode()) if raw else None
def candidates(ids):
    if not ids: return {}
    r=rows("SELECT c.id,c.source_id,s.path,s.sha256,s.license_state,s.integrity_state,c.pipeline,HEX(CAST(c.locator AS CHAR)),HEX(CAST(c.raw_payload AS CHAR)),HEX(CAST(c.normalized_payload AS CHAR)),c.content_sha256,c.normalization_state,c.reason,COALESCE(rs.state,'PENDING'),COALESCE(rs.version,0),HEX(CAST(COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT('decision_id',d.id,'reviewer_id',d.reviewer_id,'action',d.action,'rationale',d.rationale,'decided_at',d.decided_at)) FROM scientific_decision d WHERE d.candidate_id=c.id),'[]') AS CHAR)) FROM scientific_candidate c JOIN scientific_source_version s ON s.id=c.source_id LEFT JOIN scientific_review_state rs ON rs.candidate_id=c.id WHERE c.id IN ("+','.join("'"+cid+"'" for cid in ids)+")")
    result={}
    for a in r:
      cid=a[0]; result[cid]=dict(candidate_id=a[0],source_id=a[1],source_path=a[2],source_sha256=a[3],license_state=a[4],integrity_state=a[5],pipeline=a[6],locator=obj(a[7]),raw=obj(a[8]),normalized=obj(a[9]),content_sha256=a[10],normalization_state=a[11],reason=a[12],current_review_state=a[13],review_version=int(a[14]),decisions=obj(a[15]) or [],review_url='/review?candidateId='+cid)
    return result
def main():
    counts=dict(
      source_records=int(rows('SELECT COUNT(*) FROM source_record')[0][0]),
      course_plants=int(rows('SELECT COUNT(*) FROM plant')[0][0]),
      course_samples=int(rows('SELECT COUNT(*) FROM research_sample')[0][0]),
      course_studies=int(rows('SELECT COUNT(*) FROM research_study')[0][0]),
      source_checked_oil=int(rows("SELECT COUNT(*) FROM measurement WHERE measurement_kind='OIL_CONTENT' AND measurement_state='SOURCE_CHECKED'")[0][0]),
      source_versions=int(rows('SELECT COUNT(*) FROM scientific_source_version')[0][0]))
    license_rows=rows('SELECT license_state,integrity_state,COUNT(*) FROM scientific_source_version GROUP BY license_state,integrity_state ORDER BY license_state,integrity_state')
    license_groups=[dict(license_state=x[0],integrity_state=x[1],count=int(x[2])) for x in license_rows]
    # Catalogue proposal source evidence is taken from current plant/taxon and matching science taxonomy candidate.
    plants=rows("SELECT p.id,p.source_record_key,t.canonical_name,t.taxon_rank,t.identity_state,p.taxon_id FROM plant p LEFT JOIN taxon t ON t.id=p.taxon_id ORDER BY p.id LIMIT 5")
    catalog=[]
    for pid,srk,name,rank,state,tid in plants:
        ev=rows("SELECT c.id,c.source_id,s.path,s.sha256,s.license_state,s.integrity_state,HEX(CAST(c.locator AS CHAR)),HEX(CAST(c.raw_payload AS CHAR)),HEX(CAST(c.normalized_payload AS CHAR)),c.content_sha256 FROM scientific_candidate c JOIN scientific_source_version s ON s.id=c.source_id WHERE c.pipeline='TAXONOMY' AND JSON_UNQUOTE(JSON_EXTRACT(c.raw_payload,'$.source_key'))='"+srk.replace("'","''")+"' ORDER BY c.id LIMIT 1")
        evidence=None
        if ev:
            x=ev[0]; c=candidates([x[0]]).get(x[0]); evidence=dict(candidate=c,consistency=dict(exact_id_exists=True,source_hash_consistent=(c['source_sha256']==x[3]),source_path_consistent=(c['source_path']==x[2])))
        catalog.append(dict(plant_id=int(pid),taxon_id=int(tid) if tid else None,source_record_key=srk,proposal=name,rank=rank,identity_state=state,candidate_evidence=evidence,catalog_review_url='/review?catalogId='+pid,blockers=['Named human reviewer and accepted catalogue authority required','Check source license/permission before publication']))
    # Resolve the actual catalogue evidence FK chain separately from the derived JSON candidate.
    for item in catalog:
      refs=rows("SELECT sv.id,sv.path,sv.sha256,sv.version_label,sv.license_state,sv.integrity_state,HEX(CAST(e.locator AS CHAR)) FROM plant p JOIN taxon t ON t.id=p.taxon_id JOIN evidence e ON e.id=t.evidence_id JOIN source_asset a ON a.id=e.source_asset_id JOIN scientific_source_version sv ON sv.sha256=a.sha256 WHERE p.id="+str(item['plant_id']))
      item['catalog_sources']=[dict(source_id=x[0],path=x[1],sha256=x[2],version=x[3],license_state=x[4],integrity_state=x[5],locator=obj(x[6])) for x in refs]
    trait_traces=[json.loads(line) for line in (REPO/'reports/phase2b/history/trait_pending_candidates.jsonl').read_text().splitlines()]
    oil_traces=[json.loads(line) for line in (REPO/'reports/phase2b/history/oil_88.jsonl').read_text().splitlines()]
    current=candidates([x['id'] for x in trait_traces]+[x['candidate_matches'][0]['id'] for x in oil_traces])
    traits=[]
    for trace in trait_traces:
        cid=trace['id']; cur=current.get(cid); loc=trace['locator']
        traits.append(dict(candidate_id=cid,trait=trace['normalized']['trait'],value=trace['normalized']['value'],raw_value=trace['raw'],normalized_value=trace['normalized'],identity_sample_link=dict(plant_id=(trace['raw'].get('historical_confirmation_event',{}).get('after_data') or {}).get('plant_id'),sample_id=None,identity_state=trace['normalized'].get('identity_state')),locator=loc,source=cur and {k:cur[k] for k in ('source_id','source_path','source_sha256','license_state','integrity_state')},content_sha256=cur and cur['content_sha256'],current_review_state=cur and cur['current_review_state'],review_history=cur and cur['decisions'],history_trace=dict(source_record_keys=[loc.get('legacy_trait_value_source_record_key'),loc.get('review_event_source_record_key')]),consistency=dict(exact_candidate_exists=cur is not None,content_hash_matches=cur is not None and cur['content_sha256']==trace['content_sha256'],locator_matches=cur is not None and cur['locator']==loc),review_url='/review?candidateId='+cid))
    course_rows=rows('SELECT id,sample_key,source_sample_key,plant_id,taxon_id,identification_text,plant_part,linkage_state,review_state,evidence_id FROM research_sample ORDER BY id')
    samples_by_plant={}
    for cr in course_rows: samples_by_plant.setdefault(cr[3],cr)
    oil=[]
    for tr in oil_traces:
        m=tr['candidate_matches'][0]; cid=m['id']; cur=current.get(cid)
        course_sample=[samples_by_plant[str(tr['plant_id'])]] if str(tr['plant_id']) in samples_by_plant else []
        sample_link=dict(course_sample_id=int(course_sample[0][0]),sample_key=course_sample[0][1],source_sample_key=course_sample[0][2],plant_id=int(course_sample[0][3]),taxon_id=course_sample[0][4],identification_text=course_sample[0][5],plant_part=course_sample[0][6],linkage_state=course_sample[0][7],review_state=course_sample[0][8],evidence_id=course_sample[0][9]) if course_sample else None
        oil.append(dict(candidate_id=cid,plant_id=tr['plant_id'],course_sample_link=sample_link,legacy_sample_id=tr['legacy_sample_id'],sample_key=tr['source_key'],identity_text=None,identity_state='UNVERIFIED',raw_value=(cur or {}).get('raw'),normalized_value=(cur or {}).get('normalized'),historical_image_value=tr['oil_content_pct'],unit=(cur or {}).get('normalized',{}).get('unit'),basis=(cur or {}).get('normalized',{}).get('basis'),locator=cur and cur['locator'],source={k:cur[k] for k in ('source_id','source_path','source_sha256','license_state','integrity_state')} if cur else None,source_record_key=tr['source_record_key'],source_row_sha256=tr['source_row_sha256'],image_check=tr['image_check'],content_sha256=cur and cur['content_sha256'],current_review_state=cur and cur['current_review_state'],review_history=cur and cur['decisions'],consistency=dict(exact_candidate_exists=cur is not None,content_hash_matches=cur is not None and cur['content_sha256']==m['content_sha256'],locator_matches=cur is not None and cur['locator']==m['locator'],source_hash_matches=cur is not None and cur['source_sha256']==m['source_sha256'],historical_image_value_matches_candidate=cur is not None and (cur['normalized'] or {}).get('value')==tr['oil_content_pct']),review_url='/review?candidateId='+cid))
    # Check technical approval prerequisites without making a scientific decision.
    for item in traits:
      n=item['normalized_value'] or {}; item['approval_blockers']=[]
      if not n.get('scientific_name') and not n.get('identity_text'):item['approval_blockers'].append('MISSING_NORMALIZED_PLANT_IDENTITY: current approval API rejects this structure; needs source-grounded normalization revision')
      if (item['source'] or {}).get('license_state')!='PERMITTED':item['approval_blockers'].append('SOURCE_LICENSE_NOT_PERMITTED')
      item['technical_checks_without_scientific_decision']=not item['approval_blockers'];item['human_review_required']=True
    links={x[0]:dict(sample_id=x[1],study_key=x[2],sample_key=x[3],identity_text=x[4],linkage_state=x[5]) for x in rows("SELECT o.candidate_id,s.id,s.study_key,s.sample_key,s.identity_text,s.linkage_state FROM scientific_lipid_observation o JOIN scientific_sample s ON s.id=o.sample_id WHERE o.candidate_id IN ("+','.join("'"+x['candidate_id']+"'" for x in oil)+")")}
    for item in oil:
      item['independent_sample']=links.get(item['candidate_id']);item['approval_blockers']=[]
      if not item['independent_sample']:item['approval_blockers'].append('INDEPENDENT_SAMPLE_LINK_MISSING')
      if (item['source'] or {}).get('license_state')!='PERMITTED':item['approval_blockers'].append('SOURCE_LICENSE_NOT_PERMITTED')
      item['species_association_notice']='Course ledger linkage and independent research sample linkage are separate. No species association is inferred from similar identifiers.'
      item['basis_notice']='As-reported basis is not a demonstrated dry/wet mass or analytical basis; direct comparison remains insufficient without supporting metadata.'
      item['technical_checks_without_scientific_decision']=not item['approval_blockers'];item['human_review_required']=True
    lipid=rows("SELECT c.id FROM scientific_lipid_observation l JOIN scientific_candidate c ON c.id=l.candidate_id JOIN scientific_sample s ON s.id=l.sample_id WHERE s.identity_text IS NOT NULL AND TRIM(s.identity_text)<>'' AND s.linkage_state='LINKED' AND l.kind IN ('FAME_COMPOSITION','OIL_FA_COMPOSITION') AND l.unit IS NOT NULL AND TRIM(l.unit)<>'' AND l.basis IS NOT NULL AND TRIM(l.basis)<>'' ORDER BY c.id")
    result=dict(audit='phase7-review-queue',read_only=True,counts=counts,license_groups=license_groups,catalog_proposals=catalog,traits=traits,oil=oil,qualifying_lipid_candidate_count=len(lipid),qualifying_lipid_candidate_ids=[x[0] for x in lipid],lipid_blocker=None if lipid else 'No records meet explicit identity, linked sample, unit, and basis requirements; no taxon inferred.',checkpoints=['Name reviewer and catalogue authority','Check license/permission state','Verify source locator and raw transcription','Confirm identity/sample link','Preserve reported unit and basis, including NULL values','Treat approval/publication as separate human decision'])
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/'review-queue.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    (OUT/'catalog-proposals.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n')
    (OUT/'data-audit.json').write_text(json.dumps(dict(audit=result['audit'],read_only=True,counts=counts,license_groups=license_groups,prepared_morphology_candidates=len(traits),oil_queue_rows=len(oil),qualifying_lipid_candidate_count=len(lipid),lipid_blocker=result['lipid_blocker']),ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(dict(counts=counts,license_groups=license_groups,catalog_proposals=len(catalog),traits=len(traits),oil=len(oil),qualifying_lipid_candidates=len(lipid)),ensure_ascii=False))
if __name__=='__main__': main()
