#!/usr/bin/env bash
# Read-only readiness gate. Never prunes Docker or changes any host configuration.
set -euo pipefail
cd "$(dirname "$0")/../.."
fail(){ printf 'BLOCKED: %s\n' "$1" >&2; exit 1; }
command -v docker >/dev/null || fail 'Docker is unavailable'
docker compose version >/dev/null || fail 'Docker Compose v2 is unavailable'
docker compose config -q >/dev/null 2>&1 || fail 'Local Compose/environment configuration is incomplete'
minimum="${VIRIDEXA_MIN_DOCKER_FREE_MB:-5120}"
[[ "$minimum" =~ ^[0-9]+$ ]] && ((minimum>=1024)) || fail 'Free-space threshold must be at least 1024 MB'
container="$(docker compose ps -q db)"
if [[ -n "$container" ]]; then
  available="$(docker exec "$container" df -Pk /var/lib/mysql | awk 'NR==2 {print $4}')"
  [[ "$available" =~ ^[0-9]+$ ]] || fail 'Cannot inspect the database volume filesystem'
  ((available>=minimum*1024)) || fail 'Docker database filesystem has insufficient free space; ask its owner to expand or review cleanup. Do not prune shared assets.'
else
  docker_root="$(docker info --format '{{.DockerRootDir}}')"
  [[ -d "$docker_root" ]] || fail 'Docker disk is remote/virtualized; its owner must verify free space before first deployment'
  available="$(df -Pk "$docker_root" | awk 'NR==2 {print $4}')"
  ((available>=minimum*1024)) || fail 'Insufficient Docker storage'
fi
printf 'Disk/configuration readiness passed. Run production security preflight and isolated integration tests separately.\n'
