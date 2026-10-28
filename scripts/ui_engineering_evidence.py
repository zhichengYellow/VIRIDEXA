#!/usr/bin/env python3
"""Publish a credential-free test summary from actual completed logs."""
import argparse, json, re
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--directory',type=Path,default=root/'reports/ui-x')
a=p.parse_args();folder=a.directory

def java(file):
 text=(folder/file).read_text()
 assert 'BUILD SUCCESS' in text, file
 rows=re.findall(r'^\[INFO\] Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)\s*$',text,re.M)
 assert len(rows)==1 and rows[0][1:]==('0','0','0'),file
 return int(rows[0][0])
def python(file):
 text=(folder/file).read_text()
 counts=re.findall(r'Ran (\d+) tests? in ',text)
 assert counts and len(re.findall(r'^OK$',text,re.M))==len(counts) and not re.search(r'FAILED|skipped=',text),file
 return sum(map(int,counts))
vue=(folder/'vue-tests.log').read_text()
num=lambda key:int(re.search(r'^# '+key+r' (\d+)$',vue,re.M).group(1))
assert num('tests')==num('pass') and num('fail')==num('skipped')==num('cancelled')==0
suites=[{'name':'Spring 单元 / 安全 / 模拟协议','passed':java('java-maven.log')},
 {'name':'真实隔离 MySQL / REST','passed':java('mysql-tests.log')},
 {'name':'Vue 交互及图像映射契约','passed':num('pass')},
 {'name':'Python 工程 / 来源 / 公开规则','passed':python('python-tests.log') if (folder/'python-tests.log').exists() else python('python-engineering-nondb.log')+python('python-history-readonly.log')},
 {'name':'科学管线解析与标准化','passed':python('pipeline-tests.log')}]
if (folder/'backup-controls-tests.log').exists():
 suites.append({'name':'备份恢复约束回归','passed':python('backup-controls-tests.log')})
assert 'built in' in (folder/'build.log').read_text()
report={'completedAt':datetime.now(ZoneInfo('Asia/Shanghai')).isoformat(timespec='seconds'),'uniqueAutomatedTests':sum(s['passed'] for s in suites),'passed':sum(s['passed'] for s in suites),'failed':0,'skipped':0,'suites':suites,'realModelCalls':0,'humanScientificReview':False,'browserFixturesExcludedFromProduction':True,'scope':'本地工程与隔离 MySQL；模型协议含 Mock，非真实 LLM 联调','productionReadonlyChecks':len(json.loads((folder/'runtime-regression.json').read_text())['checks'])}
(folder/'test-summary.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
(root/'apps/web/public/engineering-evidence.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(report,ensure_ascii=False))
