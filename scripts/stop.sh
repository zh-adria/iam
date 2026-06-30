#!/usr/bin/env bash
# Stop everything dev.sh or start.sh may have started.
set -euo pipefail
cd "$(dirname "$0")/.."

# kill any locally-run backend jars
pkill -f "iam-auth-server-.*\.jar" 2>/dev/null || true
pkill -f "iam-admin-.*\.jar" 2>/dev/null || true
# stop vite dev server
pkill -f "vite" 2>/dev/null || true

# stop docker-compose stack
docker compose stop 2>/dev/null || true

echo "stopped: backend jars, vite, docker compose services"
