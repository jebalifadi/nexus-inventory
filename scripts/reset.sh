#!/usr/bin/env bash
# Empties Nexus completely, to start from the beginning (for example after trying it with test data).
#
#   scripts/reset.sh
#
# The current data is backed up first, so a reset can be undone with scripts/restore.sh.
# On the next scripts/start.sh, Nexus creates its tables again and the first owner account
# from NEXUS_OWNER_USERNAME / NEXUS_OWNER_PASSWORD (see .env.example).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CONTAINER=nexus-postgres

if [ -f "$ROOT/.run/backend.pid" ] && kill -0 "$(cat "$ROOT/.run/backend.pid")" 2>/dev/null; then
  echo "Nexus is running. Stop it first with scripts/stop.sh, then reset." >&2
  exit 1
fi
if ! docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null | grep -q true; then
  echo "The database is not running. Start it with: cd deploy/docker && docker compose up -d" >&2
  exit 1
fi

echo "This DELETES ALL Nexus data: products, stock, sales, purchases, expenses, users…"
echo "A backup is made first, so you can bring it back with scripts/restore.sh."
read -r -p "Type 'reset' to continue: " answer
[ "$answer" = "reset" ] || { echo "Cancelled."; exit 1; }

echo "Backing up the current data first…"
"$ROOT/scripts/backup.sh"

docker exec "$CONTAINER" psql -U nexus -d postgres -q -c "DROP DATABASE nexus WITH (FORCE)" -c "CREATE DATABASE nexus OWNER nexus"
echo "Nexus is empty. Start it with scripts/start.sh and sign in with the first owner account."
