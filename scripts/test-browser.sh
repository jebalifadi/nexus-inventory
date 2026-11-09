#!/usr/bin/env bash
# Runs the browser tests against their own throwaway database, backend and web server.
# Your real data and a running Nexus are never touched: other ports, other database.
#
#   scripts/test-browser.sh            run all of them
#   scripts/test-browser.sh --headed   watch them in a browser window
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CONTAINER=nexus-e2e-pg
DB_PORT=55433

cleanup() { docker rm -f "$CONTAINER" >/dev/null 2>&1 || true; }
trap cleanup EXIT
cleanup

docker run -d --rm --name "$CONTAINER" \
  -e POSTGRES_DB=nexus_e2e -e POSTGRES_USER=nexus_e2e -e POSTGRES_PASSWORD=nexus_e2e \
  -p "127.0.0.1:$DB_PORT:5432" postgres:18-alpine >/dev/null
for _ in $(seq 1 60); do
  docker exec "$CONTAINER" pg_isready -h 127.0.0.1 -U nexus_e2e >/dev/null 2>&1 && break
  sleep 1
done

export DATABASE_URL="jdbc:postgresql://127.0.0.1:$DB_PORT/nexus_e2e"
export DATABASE_USER=nexus_e2e
export DATABASE_PASSWORD=nexus_e2e
export NEXUS_OWNER_USERNAME=owner
export NEXUS_OWNER_PASSWORD=e2e-owner-password
export NEXUS_RATES_BANK_SYNC=false

cd "$ROOT/frontend"
npx playwright test "$@"
