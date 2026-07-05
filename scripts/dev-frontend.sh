#!/usr/bin/env bash
# IAM — 仅启动前端（Vite dev server）
# 用法: ./scripts/dev-frontend.sh
set -euo pipefail
cd "$(dirname "$0")/.."

echo "starting frontend dev server (5173)..."
( cd frontend && npm install --silent && npm run dev )
