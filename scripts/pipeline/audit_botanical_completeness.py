#!/usr/bin/env python3
"""Read-only botanical name/media completeness. Catalogue entries are not verified species."""
import collections,json,re
from pathlib import Path
from run_science import mysql
ROOT=Path(__file__).resolve().parents[2]
def main():
 rows=[json.loads(bytes.fromhex(line)) for line in mysql("SELECT HEX(CAST(JSON_OBJECT('plantId',p.id,'scientificName',t.canonical_name,'isPublic',g.is_public,'hasEvidence',t.evidence_id IS NOT NULL,'names',(SELECT JSON_ARRAYAGG(JSON_OBJECT('text',n.name_text,'state',n.review_state,'evidence',n.evidence_id)) FROM plant_name n WHERE n.plant_id=p.id AND n.name_role='COMMON' AND n.language_tag LIKE 'zh%')) AS CHAR)) FROM plant p JOIN taxon t ON t.id=p.taxon_id JOIN graph_catalog g ON g.id=p.id ORDER BY p.id;").splitlines()]
 media=[json.loads(bytes.fromhex(line)) for line in mysql("SELECT HEX(CAST(JSON_OBJECT('plantId',m.plant_id,'scientificName',m.scientific_name,'catalogName',t.canonical_name,'license',m.license_code,'state',m.state,'sourceUrl',m.source_url) AS CHAR)) FROM botanical_media m JOIN graph_catalog p ON p.id=m.plant_id JOIN taxon t ON t.id=p.taxon_id ORDER BY m.id;").splitlines()]
 mapped={r['plantId'] for r in media if r['state']=='PUBLIC' and r['license'] in {'CC0','CC-BY'} and r['scientificName']==r['catalogName']}
 static={'Ginkgo biloba','Camellia oleifera','Jatropha curcas'};counts=collections.Counter();details=[]
 for r in rows:
  names=sorted({n['text'] for n in r['names'] or [] if n['state'] in {'SOURCE_REPORTED','APPROVED','VERIFIED_FROM_PRINTED_HEADING'} and n['evidence'] and re.fullmatch('[\u3400-\u9fff·（）()\\-]{1,80}',n['text'])})
  words=r['scientificName'].split();fallback=' '.join(words[:2]) in static and not any(w in {'var.','f.','subsp.','ssp.','×'} for w in words[2:]) and not any(c in r['scientificName'] for c in "'\"“”‘’")
  photo=r['plantId'] in mapped or fallback
  counts['plants']+=1;counts['publicCatalog']+=int(bool(r['isPublic']));counts['withChineseName']+=bool(names);counts['publicWithChineseName']+=bool(names) and bool(r['isPublic']);counts['searchableChineseNames']+=len(names);counts['additionalChineseAliases']+=max(0,len(names)-1);counts['withMainImage']+=photo;counts['withChineseNameAndMainImage']+=bool(names) and photo;counts['withEvidence']+=bool(r['hasEvidence'])
  details.append({'plantId':r['plantId'],'scientificName':r['scientificName'],'chineseNames':names,'hasMainImage':photo,'imageType':'LOCAL_MEDIA' if r['plantId'] in mapped else 'LICENSED_STATIC' if fallback else 'NONE','reason':None if names else 'NO_EXACT_TRACEABLE_CHINESE_NAME'})
 result={'counts':dict(counts),'chineseCoveragePercent':round(100*counts['withChineseName']/counts['plants'],2),'publicChineseCoveragePercent':round(100*counts['publicWithChineseName']/counts['publicCatalog'],2),'imageCoveragePercent':round(100*counts['withMainImage']/counts['plants'],4),'withoutChineseName':counts['plants']-counts['withChineseName'],'dynamicMedia':len(media),'dynamicMediaPlants':len(mapped),'licenses':dict(collections.Counter(r['license'] for r in media)),'mediaSourceDistribution':{'iNaturalist':len(media),'WikimediaStaticFallback':3},'identityConflicts':sum(r['scientificName']!=r['catalogName'] for r in media),'formalScientificFacts':int(mysql('SELECT COUNT(*) FROM published_scientific_fact;').strip()),'note':'Catalogue-entry coverage; distinct biological species count is unresolved. Name metadata is source-reported, media identification is upstream, not human scientific review.'}
 out=ROOT/'reports/release-v0.1.0';out.mkdir(parents=True,exist_ok=True);(out/'botanical-completeness.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));(out/'botanical-completeness-by-plant.json').write_text(json.dumps(details,ensure_ascii=False,indent=2));print(json.dumps(result,ensure_ascii=False))
if __name__=='__main__':main()
