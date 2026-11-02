#!/usr/bin/env bash
# Replaces the Nexus database with a backup made by scripts/backup.sh.
#
#   scripts/restore.sh                       list the backups
#   scripts/restore.sh <backup file>         restore one (asks first)
#
# The current data is backed up first, so a restore can itself be undone.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKUP_DIR="${NEXUS_BACKUP_DIR:-$HOME/Documents/nexus-backups}"
CONTAINER=nexus-postgres

if [ $# -eq 0 ]; then
  echo "Backups in $BACKUP_DIR (newest first):"
  ls -1t "$BACKUP_DIR"/nexus-*.dump 2>/dev/null | sed 's/^/  /' || echo "  (none)"
  echo
  echo "Restore one with: scripts/restore.sh <file>"
  exit 0
fi

file="$1"
[ -f "$file" ] || { echo "No such file: $file" >&2; exit 1; }

if [ -f "$ROOT/.run/backend.pid" ] && kill -0 "$(cat "$ROOT/.run/backend.pid")" 2>/dev/null; then
  echo "Nexus is running. Stop it first with scripts/stop.sh, then restore." >&2
  exit 1
fi

echo "This replaces ALL current Nexus data with: $(basename "$file")"
read -r -p "Type 'restore' to continue: " answer
[ "$answer" = "restore" ] || { echo "Cancelled."; exit 1; }

echo "Backing up the current data first…"
"$ROOT/scripts/backup.sh"

docker exec "$CONTAINER" psql -U nexus -d postgres -q -c "DROP DATABASE nexus WITH (FORCE)" -c "CREATE DATABASE nexus OWNER nexus"
docker exec -i "$CONTAINER" pg_restore -U nexus -d nexus --no-owner < "$file"
echo "Restored $(basename "$file"). Start Nexus again with scripts/start.sh."
