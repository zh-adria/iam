#!/usr/bin/env bash
# Clean build: backend mvn package + frontend npm build. No run.
set -euo pipefail
cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/d/Program Files (x86)/jdk17}"
export PATH="$JAVA_HOME/bin:/d/Program Files (x86)/apache-maven-3.9.7/bin:$PATH"

echo "=== backend: mvn clean package (with tests) ==="
( cd backend && mvn -B clean package )

echo "=== frontend: npm build ==="
( cd frontend && npm install --silent && npm run build )

echo
echo "build OK:"
echo "  backend/iam-auth-server/target/iam-auth-server-1.0.0-SNAPSHOT.jar"
echo "  backend/iam-admin/target/iam-admin-1.0.0-SNAPSHOT.jar"
echo "  frontend/dist/"
