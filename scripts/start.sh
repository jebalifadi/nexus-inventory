#!/usr/bin/env bash
# Starts Nexus for daily use: database, backend and web app, then opens it in the browser.
#
#   scripts/start.sh
#
# - Makes the day's first backup before the backend starts (so before any database update).
# - Settings from a .env file in the repository root are used (see .env.example).
# - Logs: .run/backend.log and .run/frontend.log. Stop with scripts/stop.sh.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUN="$ROOT/.run"
APP_URL="http://localhost:4173"
mkdir -p "$RUN"

running() { [ -f "$RUN/$1.pid" ] && kill -0 "$(cat "$RUN/$1.pid")" 2>/dev/null; }
port_busy() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }

if running backend && running frontend; then
  echo "Nexus is already running: $APP_URL"
  exit 0
fi

# 1. Tools
docker info >/dev/null 2>&1 || { echo "Docker is not running. Open Docker Desktop, then try again." >&2; exit 1; }
command -v java >/dev/null || { echo "Java 25 is missing (java -version)." >&2; exit 1; }
command -v node >/dev/null || { echo "Node.js is missing: brew install node" >&2; exit 1; }

# 2. Settings from .env, if there is one
if [ -f "$ROOT/.env" ]; then
  set -a; . "$ROOT/.env"; set +a
fi

# 3. Database
echo "Starting the database…"
docker compose -f "$ROOT/deploy/docker/docker-compose.yml" up -d --wait >/dev/null 2>&1 || { echo "The database did not start. Check Docker Desktop." >&2; exit 1; }

# 4. The day's first backup, before the backend can change anything
BACKUP_DIR="${NEXUS_BACKUP_DIR:-$HOME/Documents/nexus-backups}"
if ! ls "$BACKUP_DIR"/nexus-"$(date +%Y-%m-%d)"_*.dump >/dev/null 2>&1; then
  "$ROOT/scripts/backup.sh"
fi

# 5. Backend
if ! running backend; then
  port_busy 8080 && { echo "Port 8080 is in use by another program (lsof -i :8080). Stop it first." >&2; exit 1; }
  echo "Building the backend…"
  (cd "$ROOT/services/core-service" && ./gradlew --quiet bootJar)
  jar="$(ls "$ROOT"/services/core-service/build/libs/*.jar | grep -v -- '-plain.jar$' | head -1)"
  nohup java -jar "$jar" > "$RUN/backend.log" 2>&1 &
  echo $! > "$RUN/backend.pid"
  echo -n "Starting the backend"
  for _ in $(seq 1 90); do
    if curl -fs http://localhost:8080/actuator/health >/dev/null 2>&1; then break; fi
    if ! running backend; then echo; echo "The backend stopped. Last lines of .run/backend.log:" >&2; tail -20 "$RUN/backend.log" >&2; exit 1; fi
    echo -n "."; sleep 1
  done
  echo
  curl -fs http://localhost:8080/actuator/health >/dev/null || { echo "The backend did not start in time; see .run/backend.log" >&2; exit 1; }
fi

# 6. Web app: built once, then served with the /api proxy to the backend
if ! running frontend; then
  port_busy 4173 && { echo "Port 4173 is in use by another program (lsof -i :4173). Stop it first." >&2; exit 1; }
  echo "Building the web app…"
  cd "$ROOT/frontend"
  [ -d node_modules ] || npm ci --silent
  npm run --silent build >/dev/null
  nohup node node_modules/vite/bin/vite.js preview --port 4173 --strictPort > "$RUN/frontend.log" 2>&1 &
  echo $! > "$RUN/frontend.pid"
  for _ in $(seq 1 30); do curl -fs "$APP_URL" >/dev/null 2>&1 && break; sleep 0.5; done
fi

echo "Nexus is running: $APP_URL   (stop with scripts/stop.sh)"
command -v open >/dev/null && open "$APP_URL"
