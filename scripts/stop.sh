#!/usr/bin/env bash
# Stops Nexus. The database keeps running (and keeps its data) unless you add --all.
#
#   scripts/stop.sh          stop the web app and the backend
#   scripts/stop.sh --all    also stop the database
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUN="$ROOT/.run"

stop() {
  local name=$1 pid
  [ -f "$RUN/$name.pid" ] || return 0
  pid="$(cat "$RUN/$name.pid")"
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" # polite stop: the backend finishes open requests first
    for _ in $(seq 1 30); do kill -0 "$pid" 2>/dev/null || break; sleep 0.5; done
    kill -0 "$pid" 2>/dev/null && kill -9 "$pid"
    echo "Stopped the $name."
  fi
  rm -f "$RUN/$name.pid"
}

stop frontend
stop backend
if [ "${1:-}" = "--all" ]; then
  docker compose -f "$ROOT/deploy/docker/docker-compose.yml" stop >/dev/null
  echo "Stopped the database (data is kept)."
fi
