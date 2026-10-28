#!/usr/bin/env python3
"""Run Phase 4 integration suites only against the explicitly prepared isolated schema."""
import os
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parents[2]
private=ROOT/'.env.governance-test'
if not private.exists():raise SystemExit('Prepare an isolated governance test environment first; production is forbidden.')
e=dict(line.split('=',1) for line in private.read_text().splitlines() if '=' in line)
schema=e.get('GOVERNANCE_TEST_SCHEMA','')
if not schema.startswith('viridexa_governance_test_') or not schema.replace('_','').isalnum():raise SystemExit('Refusing non-test schema')
env=os.environ.copy();env.update(e)
raise SystemExit(subprocess.call([str(ROOT/'apps/api/mvnw'),'-f',str(ROOT/'apps/api/pom.xml'),'-s',str(ROOT/'.mvn/settings.xml'),'-Dtest=GraphMySqlIT,GovernanceMySqlIT,AgentMySqlIT','test'],cwd=ROOT,env=env))
