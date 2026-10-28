#!/usr/bin/env python3
"""Explicit local DBA grant for append-only names/media; never review/account rights."""
import subprocess
query=''.join(f"GRANT INSERT ON viridexa.{table} TO 'viridexa_importer'@'%';\n" for table in ['source_asset','evidence','plant_name','botanical_name_assertion','botanical_media'])
r=subprocess.run(['docker','exec','-i','viridexa-db-v1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'],input=query,text=True,capture_output=True)
if r.returncode:raise SystemExit(r.stderr)
print('Granted append-only name/evidence/media imports; no scientific decisions or user writes')
