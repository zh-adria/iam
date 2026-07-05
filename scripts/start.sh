#!/usr/bin/env bash
# ponytail: production-like single-command bring-up via docker compose.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -z "${IAM_CONFIG_KEY:-}" ]; then
  echo "ERROR: IAM_CONFIG_KEY environment variable is required."
  echo "  export IAM_CONFIG_KEY=your-secret-key"
  echo "  Or create a .env file in the project root."
  exit 1
fi

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
