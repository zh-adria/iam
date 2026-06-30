#!/usr/bin/env bash
# ponytail: production-like single-command bring-up via docker compose.
# Builds both backend images (Dockerfile SERVICE arg) + frontend not included
# (serve frontend from nginx/CDN separately in prod).
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose up -d --build

echo
echo "IAM 平台已启动 (docker):"
echo "  Auth Server: http://localhost:8080/iam"
echo "  Admin Server: http://localhost:8081/iam"
echo "  MySQL:       localhost:3306  (iam / iam123)"
echo "  Redis:       localhost:6379"
echo "  演示账号:    admin / Iam@2026,  alice / User@2026"
echo "  OAuth2:      demo-client / demo-secret"
echo
echo "  日志: docker compose logs -f iam-auth-server"
echo "  停止: ./scripts/stop.sh"
