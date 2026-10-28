#!/usr/bin/env python3
"""Fetch official Cloudflare CIDRs and emit a review-only Nginx include."""
import ipaddress
import sys
import urllib.request
from pathlib import Path

URLS = (
    "https://www.cloudflare.com/ips-v4",
    "https://www.cloudflare.com/ips-v6",
)
OUT = Path(__file__).resolve().parents[2] / "infra/security/nginx-cloudflare-trusted-proxies.conf"

def main() -> None:
    nets = []
    for url in URLS:
        request = urllib.request.Request(url, headers={"User-Agent": "VIRIDEXA-security-cidr-refresh/1.0"})
        with urllib.request.urlopen(request, timeout=15) as response:
            body = response.read(65537)
        if len(body) > 65536:
            raise ValueError("CIDR response exceeded size limit")
        parsed = [ipaddress.ip_network(line.strip(), strict=True) for line in body.decode("ascii").splitlines() if line.strip()]
        expected = 4 if url.endswith("ips-v4") else 6
        if not parsed or any(net.version != expected or not net.is_global or net.prefixlen < (8 if expected == 4 else 16) for net in parsed):
            raise ValueError(f"invalid or empty CIDR list from {url}")
        nets.extend(parsed)
    if len(set(nets)) != len(nets):
        raise ValueError("duplicate CIDR in official lists")
    content = "# Generated from official Cloudflare CIDR endpoints; review before deployment.\n"
    content += "# http-context include; activate only with origin ingress restricted to Cloudflare.\n"
    content += "".join(f"set_real_ip_from {net};\n" for net in nets)
    content += "real_ip_header CF-Connecting-IP;\nreal_ip_recursive off;\n"
    content += "geo $realip_remote_addr $trusted_cf_peer {\n    default 0;\n"
    content += "".join(f"    {net} 1;\n" for net in sorted(nets, key=lambda n: (n.version, int(n.network_address), n.prefixlen)))
    content += "}\nmap \"$trusted_cf_peer:$http_x_forwarded_proto\" $viridexa_forwarded_scheme {\n    default $scheme;\n    \"1:https\" https;\n}\n"
    temp = OUT.with_suffix(OUT.suffix + ".tmp")
    temp.write_text(content, encoding="ascii")
    temp.replace(OUT)
    print(f"Wrote {len(nets)} validated ranges to {OUT}; review before use.")

if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"CIDR refresh failed: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise SystemExit(1)
