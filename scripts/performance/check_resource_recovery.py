#!/usr/bin/env python3
"""Bounded fault injection on the explicitly disposable governance schema only.

Never locks production tables, restarts MySQL, or changes server settings.
"""
import concurrent.futures
import json
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ENV = dict(line.split('=', 1) for line in (ROOT / '.env.governance-test').read_text().splitlines() if '=' in line)
SCHEMA = ENV['GOVERNANCE_TEST_SCHEMA']
if not SCHEMA.startswith('viridexa_governance_test_') or not SCHEMA.replace('_', '').isalnum():
    raise SystemExit('Refusing non-test schema')
BASE = 'http://127.0.0.1:18082'


def request():
    started = time.monotonic()
    try:
        with urllib.request.urlopen(BASE + '/api/v1/science/coverage', timeout=12) as response:
            return {'status': response.status, 'ms': round((time.monotonic() - started) * 1000, 2)}
    except urllib.error.HTTPError as error:
        body = error.read().decode()
        assert not any(value in body for value in ['jdbc:', SCHEMA, 'SQLException', 'SELECT ', '/Users/'])
        return {'status': error.code, 'ms': round((time.monotonic() - started) * 1000, 2), 'body': json.loads(body)}


def main():
    assert request()['status'] == 200
    command = ['docker', 'exec', '-i', 'viridexa-db-v1', 'sh', '-c',
               'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names --unbuffered']
    process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    try:
        # The lock is connection-scoped and released even if this script fails.
        process.stdin.write(f'USE `{SCHEMA}`; LOCK TABLES scientific_candidate WRITE; SELECT "LOCKED"; SELECT SLEEP(7); UNLOCK TABLES;\n')
        process.stdin.close()
        assert process.stdout.readline().strip() == 'LOCKED'
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            rows = list(pool.map(lambda _: request(), range(8)))
        process.wait(timeout=10)
        assert process.returncode == 0, process.stderr.read()
    finally:
        if process.poll() is None:
            process.terminate()
            process.wait(timeout=5)
    assert any(row['status'] == 503 for row in rows), rows
    assert all(row['status'] in (200, 503) and row['ms'] < 11000 for row in rows), rows
    recovered = [request() for _ in range(3)]
    assert all(row['status'] == 200 for row in recovered), recovered
    report = {'scope': SCHEMA, 'tableLockSeconds': 7, 'concurrency': 8,
              'requests': rows, 'recovery': recovered, 'productionModified': False,
              'note': 'Connection/query exhaustion is bounded; driver cancellation is not assumed instantaneous.'}
    (ROOT / 'reports/final-hardening/resource-recovery.json').write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
