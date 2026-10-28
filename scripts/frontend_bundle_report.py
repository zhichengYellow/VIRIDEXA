#!/usr/bin/env python3
"""Reproducible emitted-byte/gzip inventory; does not infer latency improvement."""
import gzip,json,re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
dist=root/'apps/web/dist'
assets=[{'file':p.name,'bytes':p.stat().st_size,'gzip':len(gzip.compress(p.read_bytes(),mtime=0))} for p in sorted((dist/'assets').iterdir()) if p.is_file()]
html=(dist/'index.html').read_text()
names=re.findall(r'/assets/([^"\s]+\.js)',html)
by={p['file']:p for p in assets}
initial={k:sum(by[n][k] for n in names) for k in ['bytes','gzip']}
baseline=json.loads((root/'reports/phase6/build-before.json').read_text())
before=next(x for x in baseline if x['file'].startswith('index-') and x['file'].endswith('.js'))
report={'beforeInitialJs':before,'afterInitialJs':initial,'initialAssets':names,'initialByteReductionPercent':round(100*(before['bytes']-initial['bytes'])/before['bytes'],2),'beforeWorkbenchRoute':next(x for x in baseline if x['file'].startswith('WorkbenchView-')),'afterWorkbenchRoute':next(x for x in assets if x['file'].startswith('WorkbenchView-') and x['file'].endswith('.js')),'deferredChartEngine':next(x for x in assets if x['file'].startswith('charts-')),'note':'Emitted bytes and gzip only; no controlled browser latency baseline. The deferred engine is still required for real charts.'}
(root/'reports/phase6/build-after.json').write_text(json.dumps(assets,indent=2))
(root/'reports/phase6/performance.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
