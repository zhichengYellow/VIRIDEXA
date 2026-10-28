#!/usr/bin/env python3
"""Provision service DB permissions only. Does not create human users or decisions."""
import os,re,secrets,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[2]
env=root/'.env'
lines=env.read_text().splitlines(); values=dict(line.split('=',1) for line in lines if '=' in line and not line.startswith('#'))
password=values.get('MYSQL_REVIEW_PASSWORD') or secrets.token_hex(32)
if not re.fullmatch('[a-f0-9]{64}',password):raise SystemExit('Expected a generated hex service secret')
if not values.get('MYSQL_REVIEW_PASSWORD'):
    lines=[line for line in lines if not line.startswith('MYSQL_REVIEW_PASSWORD=')]
    env.write_text('\n'.join(lines)+'\nMYSQL_REVIEW_PASSWORD='+password+'\n')
    os.chmod(env,0o600)
query="CREATE USER IF NOT EXISTS 'viridexa_reviewer'@'%' IDENTIFIED BY '"+password+"';\n"
for table in ['app_user','app_role','user_role','scientific_candidate','scientific_source_version','scientific_decision','scientific_review_state','scientific_sample','scientific_lipid_observation','scientific_evidence_link','source_governance','source_license_event','plant','taxon','evidence','source_asset','review_event']:
    query+=f"GRANT SELECT ON viridexa.{table} TO 'viridexa_reviewer'@'%';\n"
for table in ['scientific_decision','scientific_review_state','source_governance','source_license_event']:
    query+=f"GRANT INSERT ON viridexa.{table} TO 'viridexa_reviewer'@'%';\n"
query+="GRANT UPDATE (state,version) ON viridexa.scientific_review_state TO 'viridexa_reviewer'@'%';\n"
query+="GRANT UPDATE (version) ON viridexa.source_governance TO 'viridexa_reviewer'@'%';\n"
query+="GRANT UPDATE (license_state) ON viridexa.scientific_source_version TO 'viridexa_reviewer'@'%';\n"
query+="GRANT INSERT ON viridexa.review_event TO 'viridexa_reviewer'@'%';GRANT UPDATE (review_state,visibility_state) ON viridexa.plant TO 'viridexa_reviewer'@'%';GRANT UPDATE (identity_state) ON viridexa.taxon TO 'viridexa_reviewer'@'%';\n"
for table in ['historical_mapping','historical_evidence_link','ontology_projection']:
    query+=f"GRANT INSERT ON viridexa.{table} TO 'viridexa_importer'@'%';\n"
r=subprocess.run(['docker','exec','-i','viridexa-db-v1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'],input=query,text=True,capture_output=True)
if r.returncode:raise SystemExit(r.stderr)
print('Provisioned separate least-privilege reviewer DB identity; no human administrator created.')
