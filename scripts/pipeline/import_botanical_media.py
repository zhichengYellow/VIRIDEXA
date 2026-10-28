#!/usr/bin/env python3
"""Verify local media bytes and exact public catalogue identity before append-only import."""
import argparse,hashlib,json,re
from pathlib import Path
from run_science import mysql
from import_candidates import literal
ROOT=Path(__file__).resolve().parents[2]

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apply',action='store_true');a=p.parse_args();rows=[];rejected=[]
 for path in sorted((ROOT/'data/raw/images').glob('*.json')):
  v=json.loads(path.read_text())
  if not isinstance(v,dict) or 'public_filename' not in v:continue
  sha=v.get('sha256','');name=v.get('scientific_name','');pid=v.get('plant_id')
  if not re.fullmatch('[a-f0-9]{64}',sha) or v.get('license_code') not in {'CC0','CC-BY'} or not isinstance(pid,int) or pid<1 or not re.fullmatch('https://www.inaturalist.org/observations/[0-9]+',v.get('source_url','')):rejected.append({'file':path.name,'reason':'INVALID_METADATA'});continue
  raw=ROOT/'data/raw/images'/f'{sha}.image'
  if not raw.is_file() or hashlib.sha256(raw.read_bytes()).hexdigest()!=sha:rejected.append({'file':path.name,'reason':'RAW_HASH_MISMATCH'});continue
  if any(not (ROOT/'data/media/public'/f'{sha}-{size}.webp').is_file() for size in ['640','960','full']):rejected.append({'file':path.name,'reason':'MISSING_DERIVATIVE'});continue
  if v.get('public_filename')!=f'{sha}-full.webp':rejected.append({'file':path.name,'reason':'BAD_PUBLIC_PATH'});continue
  exact=mysql('SELECT COUNT(*) FROM graph_catalog p JOIN taxon t ON t.id=p.taxon_id WHERE p.id='+str(pid)+' AND p.is_public=1 AND BINARY t.canonical_name='+literal(name)+';').strip()=='1'
  if not exact:rejected.append({'file':path.name,'reason':'CATALOG_IDENTITY_MISMATCH'});continue
  if a.apply:
   fields=['plant_id','scientific_name','author','license_code','source_url','source_taxon_id','source_observation_id','sha256','public_filename','width','height','state']
   mysql('START TRANSACTION;INSERT IGNORE INTO botanical_media('+','.join(fields)+') VALUES('+','.join(literal(v[k]) for k in fields)+');COMMIT;')
  rows.append({'plantId':pid,'scientificName':name,'sha256':sha,'license':v['license_code'],'sourceUrl':v['source_url']})
 report={'mode':'APPLY' if a.apply else 'DRY_RUN','verifiedMedia':len(rows),'plants':len({v['plantId'] for v in rows}),'rejected':rejected,'records':rows};out=ROOT/'reports/release-v0.1.0';out.mkdir(parents=True,exist_ok=True);(out/('media-import-applied.json' if a.apply else 'media-import-dry-run.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps({k:v for k,v in report.items() if k!='records'},ensure_ascii=False))
if __name__=='__main__':main()
