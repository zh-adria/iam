#!/usr/bin/env bash
# Run only the frontend dev server (assumes backend services already up).
set -euo pipefail
cd "$(dirname "$0")/.."

cd frontend
npm install --silent
npm run dev
