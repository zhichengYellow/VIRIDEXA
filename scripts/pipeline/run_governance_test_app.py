#!/usr/bin/env python3
"""Launch isolated app on 18082 using private test environment."""
import os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
e=dict(line.split('=',1) for line in (ROOT/'.env.governance-test').read_text().splitlines() if '=' in line)
env=os.environ.copy();env.update(e)
env.update(DB_URL=e['GOVERNANCE_TEST_DB_URL'],DB_USERNAME=e['GOVERNANCE_TEST_RO'],DB_PASSWORD=e['GOVERNANCE_TEST_PASSWORD'],
 DB_MIGRATION_USERNAME=e['GOVERNANCE_TEST_MIGRATOR'],DB_MIGRATION_PASSWORD=e['GOVERNANCE_TEST_PASSWORD'],
 DB_ACCOUNT_USERNAME=e['GOVERNANCE_TEST_ACCOUNT'],DB_ACCOUNT_PASSWORD=e['GOVERNANCE_TEST_PASSWORD'],
 DB_REVIEW_USERNAME=e['GOVERNANCE_TEST_WRITER'],DB_REVIEW_PASSWORD=e['GOVERNANCE_TEST_PASSWORD'],AGENT_RUNTIME_DIRECTORY=str(ROOT/'.runtime'/'isolated'/e['GOVERNANCE_TEST_SCHEMA']),SERVER_PORT='18082',WEB_ORIGIN='http://localhost:5174',FLYWAY_ENABLED='true')
raise SystemExit(subprocess.call(['java','-jar',str(ROOT/'apps/api/target/viridexa-api-0.1.0.jar')],env=env))
