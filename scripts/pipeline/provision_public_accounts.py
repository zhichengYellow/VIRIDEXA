#!/usr/bin/env python3
"""Provision a server-only identity with SELECT and fixed MEMBER procedure EXECUTE only."""
import os,re,secrets,subprocess,argparse
from pathlib import Path
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--prepare-only',action='store_true');args=parser.parse_args()
root=Path(__file__).resolve().parents[2];path=root/'.env';lines=path.read_text().splitlines();values=dict(line.split('=',1) for line in lines if '=' in line and not line.startswith('#'))
password=values.get('MYSQL_ACCOUNT_PASSWORD') or secrets.token_hex(32)
if not re.fullmatch('[a-f0-9]{64}',password):raise SystemExit('Account service secret must be a generated hex value')
if not values.get('MYSQL_ACCOUNT_PASSWORD'):
 lines=[x for x in lines if not x.startswith('MYSQL_ACCOUNT_PASSWORD=')];path.write_text('\n'.join(lines)+'\nMYSQL_ACCOUNT_PASSWORD='+password+'\n');os.chmod(path,0o600)
if args.prepare_only:print('Prepared server-only account identity secret; no human account created.');raise SystemExit(0)
query="CREATE USER IF NOT EXISTS 'viridexa_accounts'@'%' IDENTIFIED BY '"+password+"';"
query+=''.join(f"GRANT SELECT ON viridexa.{t} TO 'viridexa_accounts'@'%';" for t in ['app_user','app_role','user_role'])
query+="GRANT EXECUTE ON PROCEDURE viridexa.register_public_member TO 'viridexa_accounts'@'%';"
r=subprocess.run(['docker','exec','-i','viridexa-db-v1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'],input=query,text=True,capture_output=True)
if r.returncode:raise SystemExit('Provision failed. Apply Flyway V14 first; no secret has been printed.')
print('Provisioned account identity: SELECT identity tables and EXECUTE fixed registration; no direct DML or scientific writes.')
