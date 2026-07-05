#!/usr/bin/env bash
# ponytail: dev mode — requires local Redis + remote MySQL + IAM_CONFIG_KEY.
# Both backend services use the same encrypted MySQL config with ENC() values.
# The IAM_CONFIG_KEY decrypts datasource credentials at startup via
# EncryptedPropertyEnvironmentPostProcessor.
set -euo pipefail
cd "$(dirname "$0")/.."

ROOT="$(pwd)"
export JAVA_HOME="${JAVA_HOME:-/d/Program Files (x86)/jdk17}"
export PATH="$JAVA_HOME/bin:/d/Program Files (x86)/apache-maven-3.9.7/bin:$PATH"

echo "[1/4] redis check"
if command -v redis-cli >/dev/null 2>&1 && redis-cli -h localhost -p 6379 ping 2>/dev/null | grep -q PONG; then
  echo "      using local redis on localhost:6379"
elif command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  echo "      docker available — starting redis container"
  docker compose up -d redis 2>/dev/null || true
  echo "      waiting for redis..."
  for i in $(seq 1 15); do docker exec iam-redis redis-cli ping >/dev/null 2>&1 && break; sleep 1; done
else
  echo "ERROR: redis not reachable. Either:"
  echo "  - install Docker Desktop"
  echo "  - or run a local redis on localhost:6379"
  exit 1
fi

echo "[2/4] checking IAM_CONFIG_KEY"
if [ -z "${IAM_CONFIG_KEY:-}" ]; then
  echo "WARNING: IAM_CONFIG_KEY not set. If your application*.yml contains ENC() values,"
  echo "         decryption will fail. Set it with: export IAM_CONFIG_KEY=<your-key>"
fi

echo "[3/4] building backend (skip tests)"
( cd backend && mvn -B -q -DskipTests package )

mkdir -p logs

echo "[4/4] starting iam-auth-server (8080) — dev profile"
java -jar backend/iam-auth-server/target/boot/iam-auth-server.jar \
  --spring.profiles.active=dev \
  -Diam.config-key="${IAM_CONFIG_KEY:-}" \
  > logs/auth-server.log 2>&1 &
AUTH_PID=$!
echo "  auth-server pid=$AUTH_PID  log=logs/auth-server.log"

echo "      waiting for auth-server health..."
for i in $(seq 1 90); do
  curl -sf http://localhost:8080/iam/actuator/health >/dev/null 2>&1 && break
  sleep 1
done

echo "      starting iam-admin (8081)"
java -jar backend/iam-admin/target/boot/iam-admin.jar \
  --spring.profiles.active=dev \
  -Diam.config-key="${IAM_CONFIG_KEY:-}" \
  > logs/admin.log 2>&1 &
ADMIN_PID=$!
echo "  admin pid=$ADMIN_PID  log=logs/admin.log"

echo "[5/5] starting frontend dev server (5173)"
( cd frontend && npm install --silent && npm run dev ) &
FRONT_PID=$!

echo ""
echo "IAM 平台已启动 (dev profile, 远程MySQL, ENC加密配置):"
echo "  前端:        http://localhost:5173"
echo "  Auth Server: http://localhost:8080/iam"
echo "  Admin Server: http://localhost:8081/iam"
echo "  MySQL:       mysql6.sqlpub.com:3311/dev_2026"
echo "  Redis:       localhost:6379"
echo "  演示账号:    admin / Iam@2026, alice / User@2026"
echo "  OAuth2:      demo-client / demo-secret"
echo "  Ctrl-C 停止所有服务"
echo ""

trap '
  echo
  echo "shutting down..."
  kill $FRONT_PID $AUTH_PID $ADMIN_PID 2>/dev/null || true
' EXIT INT TERM

wait $FRONT_PID
