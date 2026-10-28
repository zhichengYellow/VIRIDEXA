#!/usr/bin/env python3
import os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
env=os.environ.copy();env.update(dict(line.split('=',1) for line in (ROOT/'.env.governance-test').read_text().splitlines() if '=' in line))
raise SystemExit(subprocess.call([str(ROOT/'apps/api/mvnw'),'-f',str(ROOT/'apps/api/pom.xml'),'-s',str(ROOT/'.mvn/settings.xml'),'-Dtest=GovernanceMySqlIT','test'],cwd=ROOT,env=env))
