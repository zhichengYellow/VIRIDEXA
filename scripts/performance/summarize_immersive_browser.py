#!/usr/bin/env python3
"""Summarize saved CDP lab traces; these are not field Core Web Vitals."""
import json
import statistics
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / 'reports/immersive'
rows = []
for file in sorted(REPORT.glob('*-browser-*-*.json')):
    source = json.loads(file.read_text())
    if not isinstance(source, dict) or 'metrics' not in source:
        continue
    events = [event for block in source['events'] for event in block.get('params', {}).get('value', [])]
    # Trace delivery can repeat events at a buffer boundary; count each event once.
    events = list({json.dumps(event, sort_keys=True): event for event in events}.values())
    navigation = next(event for event in events if event['name'] == 'navigationStart'
                      and event.get('args', {}).get('data', {}).get('documentLoaderURL') == 'http://127.0.0.1:5173/')
    nav_id = navigation['args']['data']['navigationId']
    lcp = [event for event in events if event['name'] == 'largestContentfulPaint::Candidate'
           and event['args']['data'].get('navigationId') == nav_id]
    shifts = [event for event in events if event['name'] == 'LayoutShift'
              and not event.get('args', {}).get('data', {}).get('had_recent_input', False)]
    # CLS session windows: <=1s between shifts and <=5s per window.
    score = 0.0
    window = 0.0
    start = previous = None
    for event in sorted(shifts, key=lambda item: item['ts']):
        ts = event['ts'] / 1_000_000
        if previous is None or ts - previous > 1 or ts - start > 5:
            start = ts
            window = 0.0
        window += event.get('args', {}).get('data', {}).get('score', 0)
        previous = ts
        score = max(score, window)
    interactions = {}
    for event in events:
        data = event.get('args', {}).get('data', {})
        if event['name'] == 'EventTiming' and data.get('interactionId'):
            interactions[data['interactionId']] = max(interactions.get(data['interactionId'], 0), data.get('duration', 0))
    # Two deliberate UI interactions per load: maximum is the lab INP candidate.
    finish = {event['args']['data']['requestId']: event['args']['data'] for event in events if event['name'] == 'ResourceFinish'}
    metrics = {item['name']: item['value'] for item in source['metrics']['metrics']}
    rows.append({'file': file.name, 'label': source['label'], 'mobile': source['mobile'],
                 'lcpMs': round((lcp[-1]['ts'] - navigation['ts']) / 1000, 2) if lcp else None,
                 'cls': round(score, 5), 'labInpCandidateMs': round(max(interactions.values()), 2) if interactions else None,
                 'interactionCount': len(interactions), 'finishedRequests': len(finish),
                 'encodedBytesThroughInteraction': sum(item.get('encodedDataLength', 0) for item in finish.values()),
                 'jsHeapBytes': metrics.get('JSHeapUsedSize'), 'truncated': source['truncated']})
summary = []
for label in ['baseline', 'after']:
    for mobile in [False, True]:
        group = [row for row in rows if row['label'] == label and row['mobile'] == mobile]
        summary.append({'label': label, 'mobile': mobile, 'runs': len(group), 'validLcpSamples':sum(row['lcpMs'] is not None for row in group), **{
            key + 'Median': statistics.median(row[key] for row in group if row[key] is not None)
            for key in ['lcpMs', 'cls', 'labInpCandidateMs', 'encodedBytesThroughInteraction', 'jsHeapBytes']}})
report = {'conditions': 'Cold cache, local production read-only API; desktop 1440x1000, mobile 375x812 CPU4x latency150ms down200000B/s up93750B/s. Shortcut-search open/close requested per navigation; actual EventTiming interactionCount is recorded.',
          'limitations': 'n=3 navigations per condition; one after desktop trace has no LCP candidate before interaction, so its LCP median uses n=2. INP candidate from lab EventTiming, not real-user p75 INP. Resource byte window ends after search-dialog close, not an identical whole-page load boundary.',
          'summary': summary, 'runs': rows}
(REPORT / 'browser-performance-summary.json').write_text(json.dumps(report, indent=2))
print(json.dumps(summary, indent=2))
