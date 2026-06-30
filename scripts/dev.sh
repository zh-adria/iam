#!/usr/bin/env bash
# ponytail: dev mode on H2 — no mysql container needed. Only redis runs in
# docker. Both backend services use file-based H2 with AUTO_SERVER=TRUE so
# they share ~/iam-dev/iam.* across JVMs. Ctrl-C stops everything.
set -euo pipefail
cd "$(dirname "$0")/.."

ROOT="$(pwd)"
export JAVA_HOME="${JAVA_HOME:-/d/Program Files (x86)/jdk17}"
export PATH="$JAVA_HOME/bin:/d/Program Files (x86)/apache-maven-3.9.7/bin:$PATH"

echo "[1/4] redis check"
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  echo "      docker available — starting redis container"
  docker compose up -d redis
  echo "      waiting for redis..."
  for i in $(seq 1 15); do
    docker exec iam-redis redis-cli ping >/dev/null 2>&1 && break
    sleep 1
  done
elif command -v redis-cli >/dev/null 2>&1 && redis-cli -h localhost -p 6379 ping 2>/dev/null | grep -q PONG; then
  echo "      using local redis on localhost:6379 (no docker)"
else
  echo "ERROR: redis not reachable. Either:"
  echo "  - install Docker Desktop: https://www.docker.com/products/docker-desktop/"
  echo "  - or run a local redis on localhost:6379 (Memurai / WSL redis / tporadowski port)"
  exit 1
fi

echo "[2/4] building backend (skip tests)"
( cd backend && mvn -B -q -DskipTests package )

mkdir -p logs

echo "[3/4] starting iam-auth-server (8080) — H2 dev profile"
java -jar backend/iam-auth-server/target/iam-auth-server-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=dev \
  > logs/auth-server.log 2>&1 &
AUTH_PID=$!
echo "  auth-server pid=$AUTH_PID  log=logs/auth-server.log"

echo "      waiting for auth-server to publish schema + seed..."
for i in $(seq 1 40); do
  curl -sf http://localhost:8080/iam/actuator/health >/dev/null 2>&1 && break
  sleep 1
done

echo "      starting iam-admin (8081) — shares H2 file, liquibase disabled"
java -jar backend/iam-admin/target/iam-admin-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=dev \
  > logs/admin.log 2>&1 &
ADMIN_PID=$!
echo "  admin pid=$ADMIN_PID       log=logs/admin.log"

echo "[4/4] starting frontend dev server (5173)"
( cd frontend && npm install --silent && npm run dev ) &
FRONT_PID=$!

trap '
  echo
  echo "shutting down..."
  kill $FRONT_PID $AUTH_PID $ADMIN_PID 2>/dev/null || true
  docker compose stop redis 2>/dev/null || true
' EXIT INT TERM

echo
echo "IAM 平台已启动 (dev mode, H2):"
echo "  前端:        http://localhost:5173"
echo "  Auth Server: http://localhost:8080/iam"
echo "  Admin Server: http://localhost:8081/iam"
echo "  H2 JDBC URL: jdbc:h2:file:$HOME/iam-dev/iam;AUTO_SERVER=TRUE;MODE=MySQL"
echo "  Redis:       localhost:6379"
echo "  演示账号:    admin / Iam@2026,  alice / User@2026"
echo "  OAuth2:      demo-client / demo-secret"
echo "  H2 数据文件: ~/iam-dev/iam.*  (删除即重置)"
echo "  Ctrl-C 停止所有服务"
echo

wait $FRONT_PID
