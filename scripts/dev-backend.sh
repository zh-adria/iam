#!/usr/bin/env bash
# Run only the backend services on H2 dev profile (no mysql, redis via docker).
set -euo pipefail
cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/d/Program Files (x86)/jdk17}"
export PATH="$JAVA_HOME/bin:/d/Program Files (x86)/apache-maven-3.9.7/bin:$PATH"

echo "redis check..."
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  docker compose up -d redis
  for i in $(seq 1 15); do
    docker exec iam-redis redis-cli ping >/dev/null 2>&1 && break
    sleep 1
  done
elif command -v redis-cli >/dev/null 2>&1 && redis-cli -h localhost -p 6379 ping 2>/dev/null | grep -q PONG; then
  echo "  using local redis on localhost:6379 (no docker)"
else
  echo "ERROR: redis not reachable. Install Docker Desktop or run a local redis."
  exit 1
fi

echo "building backend (skip tests)..."
( cd backend && mvn -B -q -DskipTests package )

mkdir -p logs

echo "starting iam-auth-server (8080) in background..."
java -jar backend/iam-auth-server/target/iam-auth-server-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=dev \
  > logs/auth-server.log 2>&1 &
AUTH_PID=$!
echo "  auth-server pid=$AUTH_PID  log=logs/auth-server.log"

echo "waiting for auth-server health..."
for i in $(seq 1 40); do
  curl -sf http://localhost:8080/iam/actuator/health >/dev/null 2>&1 && break
  sleep 1
done

trap '
  echo
  echo "stopping..."
  kill $AUTH_PID 2>/dev/null || true
  docker compose stop redis 2>/dev/null || true
' EXIT INT TERM

# foreground admin — Ctrl-C exits (also kills background auth-server via trap)
java -jar backend/iam-admin/target/iam-admin-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=dev
