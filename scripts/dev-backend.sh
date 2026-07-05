#!/usr/bin/env bash
# IAM — 仅启动后端（auth-server + admin）
# 用法: ./scripts/dev-backend.sh
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
  echo "ERROR: redis not reachable"
  exit 1
fi

echo "[2/4] building backend (skip tests)"
( cd backend && mvn -B -q -DskipTests package )

mkdir -p logs

echo "[3/4] starting iam-auth-server (8080) — dev profile"
java -jar backend/iam-auth-server/target/boot/iam-auth-server.jar \
  --spring.profiles.active=dev > logs/auth-server.log 2>&1 &
AUTH_PID=$!
echo "  auth-server pid=$AUTH_PID  log=logs/auth-server.log"

echo "      waiting for auth-server health..."
for i in $(seq 1 90); do
  curl -sf http://localhost:8080/iam/actuator/health >/dev/null 2>&1 && break
  sleep 1
done
if [ $i -eq 90 ]; then echo "ERROR: auth-server didn't become healthy in 90s"; kill $AUTH_PID 2>/dev/null; exit 1; fi

echo "      starting iam-admin (8081)"
java -jar backend/iam-admin/target/boot/iam-admin.jar \
  --spring.profiles.active=dev > logs/admin.log 2>&1 &
ADMIN_PID=$!
echo "  admin pid=$ADMIN_PID  log=logs/admin.log"

echo ""
echo "IAM 后端已启动 (dev profile):"
echo "  Auth Server:  http://localhost:8080/iam"
echo "  Admin Server: http://localhost:8081/iam"
echo "  演示账号:     admin / Iam@2026, alice / User@2026"
echo ""

echo "[4/4] starting frontend dev server (5173)"
( cd frontend && npm install --silent && npm run dev ) &
FRONT_PID=$!

trap '
  echo "shutting down..."
  kill $FRONT_PID $AUTH_PID $ADMIN_PID 2>/dev/null || true
' EXIT INT TERM

wait $FRONT_PID
