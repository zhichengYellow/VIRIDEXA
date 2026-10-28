# Phase 7S Ubuntu deployment overlay

This is an opt-in production overlay for the existing Compose project. It does not change host firewall rules or publish database/API ports. Keep secrets in a root-owned environment file or secret manager; never commit it. Set `WEB_ORIGIN`, MySQL passwords, and production Turnstile values before running `scripts/security/preflight.sh`. The preflight validates presence and Compose interpolation without printing resolved configuration or secret values.

Validate and start direct-origin mode with:

```sh
docker compose -f compose.yaml -f compose.production.yaml config -q
scripts/security/preflight.sh
docker compose -f compose.yaml -f compose.production.yaml up -d --build
```

The overlay removes all base host port mappings, isolates MySQL on an internal network, gives only the API a non-internal egress network for outbound verification/model calls, and runs Nginx as UID/GID 101 with writable UID-owned tmpfs for generated configuration and runtime state. Nginx listens on container port 8080. API documentation endpoints are disabled. It gives Nginx the static address `172.30.0.10`, and grants the API trust in only that exact proxy address. Nginx's default include preserves `$scheme` and trusts no forwarded headers. The application must receive Nginx-sanitized `X-Viridexa-Client-IP` and `X-Viridexa-Proto`; never enable framework-wide forwarded-header processing. Public HTTPS termination must occur at the Nginx host or a separately configured trusted edge. The secure cookie flag is forced on.

Turnstile is required in production mode. Set a production site key, secret, and exact lowercase hostnames (comma-separated); hostnames must include the hostname in `WEB_ORIGIN`. Keep the widget key public, and keep the secret private.

## Cloudflare deployment modes

Choose one path and configure Nginx for it before exposing the service:

- **Direct origin:** Leave the default include mounted. Remote clients are identified by the socket peer. Use HTTPS at the host edge and do not accept client-supplied proxy headers.
- **Cloudflare CDN proxy:** Restrict origin ingress to Cloudflare at the host/network edge. Run `python3 scripts/security/update-cloudflare-cidrs.py` to fetch, validate, and atomically write an http-context include. Review it before mounting it in place of the default include. It gates `X-Forwarded-Proto` on `$realip_remote_addr` being in the official Cloudflare ranges; only the exact trusted `https` value yields HTTPS, with `$scheme` as fallback. The generated include restores client IP via `set_real_ip_from` for the same vetted ranges and `real_ip_header CF-Connecting-IP`. Do not blindly trust `X-Forwarded-For` or `CF-Visitor` from arbitrary peers. Re-fetch and review CIDRs periodically; the script never reloads Nginx or applies firewall changes.
- **Cloudflare Tunnel:** Use `-f compose.yaml -f compose.production.yaml -f compose.tunnel.yaml` and set `CLOUDFLARE_TUNNEL_TOKEN` via the host environment/secret manager. The cloudflared service has no host ports and can reach only Nginx's edge network. Configure the tunnel hostname to `http://web:8080`. Nginx sees the tunnel connector as its peer, so use the supplied `infra/security/nginx-tunnel-trusted-proxies.conf` only with the tunnel overlay network, whose connector address is pinned to `172.31.0.2`. The tunnel overlay automatically replaces the default include with this variant; never combine both map definitions. Run `scripts/security/preflight.sh --tunnel` before starting. It accepts only `https` from that exact peer. The Tunnel include restores the visitor IP only from that exact connector peer via `CF-Connecting-IP`. Do not use CDN CIDRs as a substitute for Tunnel peer identity. The default direct-origin include deliberately does not trust tunnel headers.

Generated CIDR candidates and custom proxy include changes require review and an Nginx syntax check before deployment. This repository does not configure Ubuntu UFW, TLS certificates, DNS, or tunnel credentials.
