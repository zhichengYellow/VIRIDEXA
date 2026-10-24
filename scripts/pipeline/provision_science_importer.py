#!/usr/bin/env python3
"""Explicit local DBA provisioning after Flyway; API remains SELECT-only."""
import subprocess
query="""
REVOKE ALL PRIVILEGES ON viridexa.* FROM 'viridexa_importer'@'%';
GRANT SELECT ON viridexa.* TO 'viridexa_importer'@'%';
GRANT INSERT ON viridexa.scientific_source_version TO 'viridexa_importer'@'%';
GRANT INSERT ON viridexa.scientific_candidate TO 'viridexa_importer'@'%';
GRANT INSERT ON viridexa.scientific_sample TO 'viridexa_importer'@'%';
GRANT INSERT ON viridexa.scientific_lipid_observation TO 'viridexa_importer'@'%';
GRANT INSERT ON viridexa.scientific_evidence_link TO 'viridexa_importer'@'%';
"""
r=subprocess.run(['docker','exec','-i','viridexa-db-v1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'],input=query,text=True,capture_output=True)
if r.returncode: raise SystemExit(r.stderr)
print('Importer: SELECT plus INSERT on scientific source/candidate tables only; no decision writes.')
