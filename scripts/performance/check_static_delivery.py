#!/usr/bin/env python3
"""Read-only checks against the isolated final-hardening Nginx on localhost."""
import gzip
import json
import re
import urllib.request
import urllib.error
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = 'http://127.0.0.1:5174'


def get(path, headers=None):
    try:
        with urllib.request.urlopen(urllib.request.Request(BASE + path, headers=headers or {}), timeout=10) as r:
            return r.status, dict(r.headers), r.read()
    except urllib.error.HTTPError as r:
        return r.code, dict(r.headers), r.read()


rows = []
index = (ROOT / 'apps/web/dist/index.html').read_text()
asset = re.search(r'src="(/assets/[^\"]+\.js)"', index).group(1)
for path in ['/', '/index.html', '/explore', asset, '/botanical/ginkgo-biloba-hero.webp', '/engineering-evidence.json', '/api/v1/csrf', '/api/v1/showcase']:
    code, headers, body = get(path, {'Accept-Encoding': 'gzip'})
    assert code == 200, (path, code)
    cache = headers.get('Cache-Control', '')
    encoding = headers.get('Content-Encoding')
    assert headers.get('X-Content-Type-Options') == 'nosniff'
    assert 'Content-Security-Policy' in headers
    if path == asset:
        assert 'immutable' in cache and '31536000' in cache
        assert encoding == 'gzip'
        decoded_bytes = len(gzip.decompress(body))
        plain = get(path)[2]
        assert gzip.decompress(body) == plain
        assert get(path, {'Accept-Encoding': 'gzip', 'If-None-Match': headers['ETag']})[0] == 304
    else:
        decoded_bytes = len(gzip.decompress(body)) if encoding == 'gzip' else len(body)
    if path.startswith('/api/'):
        assert 'no-store' in cache and encoding is None
    elif path.startswith('/botanical/'):
        assert 'must-revalidate' in cache and headers.get('ETag') and headers.get('Last-Modified')
        assert get(path, {'If-None-Match': headers['ETag']})[0] == 304
    elif path != asset:
        assert 'no-cache' in cache or 'no-store' in cache
    rows.append({'path': path, 'status': code, 'cacheControl': cache, 'encoding': encoding,
                 'wireBodyBytes': len(body), 'decodedBytes': decoded_bytes})
(ROOT / 'reports/final-hardening/static-delivery.json').write_text(json.dumps(rows, indent=2))
print(json.dumps(rows, indent=2))
