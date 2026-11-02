#!/usr/bin/env bash
# Saves a copy of the Nexus database, with the date in the file name.
#
#   scripts/backup.sh
#
# Backups go to ~/Documents/nexus-backups (change with NEXUS_BACKUP_DIR). The newest 30 are kept
# (change with NEXUS_BACKUP_KEEP). Each backup is read back once to make sure it is usable.
set -euo pipefail

BACKUP_DIR="${NEXUS_BACKUP_DIR:-$HOME/Documents/nexus-backups}"
KEEP="${NEXUS_BACKUP_KEEP:-30}"
CONTAINER=nexus-postgres

if ! docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null | grep -q true; then
  echo "The database is not running. Start it with scripts/start.sh (or: cd deploy/docker && docker compose up -d)." >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"
file="$BACKUP_DIR/nexus-$(date +%Y-%m-%d_%H%M%S).dump"
trap 'rm -f "$file.partial"' EXIT

# Custom format: compressed, and restorable with pg_restore (see scripts/restore.sh).
docker exec "$CONTAINER" pg_dump -U nexus -d nexus --format=custom > "$file.partial"
docker exec -i "$CONTAINER" pg_restore --list < "$file.partial" > /dev/null # fails if the copy is unreadable
mv "$file.partial" "$file"
echo "Backup saved: $file ($(du -h "$file" | cut -f1 | tr -d ' '))"

# Remove the oldest backups beyond the newest $KEEP.
ls -1t "$BACKUP_DIR"/nexus-*.dump | tail -n +"$((KEEP + 1))" | while read -r old; do
  rm -- "$old"
  echo "Removed old backup: $(basename "$old")"
done
