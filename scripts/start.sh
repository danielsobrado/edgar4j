#!/usr/bin/env sh
set -eu

PROFILE="high"
BACKEND_PORT="8080"
FRONTEND_PORT="3000"
PROJECT_NAME="edgar4j"
NO_BUILD="false"
FORCE="false"

usage() {
    cat <<'EOF'
Usage: scripts/start.sh [options]

Options:
  --profile high|low       Docker Compose profile to start (default: high)
  --backend-port PORT      Backend host port (default: 8080)
  --frontend-port PORT     Frontend host port (default: 3000)
  --project-name NAME      Docker Compose project name (default: edgar4j)
  --no-build               Start without rebuilding images
  --force                  Remove a stale lock if no services are running
  -h, --help               Show this help
EOF
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --profile)
            PROFILE="${2:?Missing value for --profile}"
            shift 2
            ;;
        --backend-port)
            BACKEND_PORT="${2:?Missing value for --backend-port}"
            shift 2
            ;;
        --frontend-port)
            FRONTEND_PORT="${2:?Missing value for --frontend-port}"
            shift 2
            ;;
        --project-name)
            PROJECT_NAME="${2:?Missing value for --project-name}"
            shift 2
            ;;
        --no-build)
            NO_BUILD="true"
            shift
            ;;
        --force)
            FORCE="true"
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [ "$PROFILE" != "high" ] && [ "$PROFILE" != "low" ]; then
    echo "--profile must be 'high' or 'low'" >&2
    exit 2
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
LOCK_FILE="$SCRIPT_DIR/edgar4j.lock"

if [ -f "$LOCK_FILE" ]; then
    LOCK_PROJECT=$(sed -n 's/.*"projectName"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$LOCK_FILE" | head -n 1)
    [ -n "$LOCK_PROJECT" ] || LOCK_PROJECT="$PROJECT_NAME"

    RUNNING_SERVICES=$(docker compose -p "$LOCK_PROJECT" ps --status running --services 2>/dev/null || true)
    if [ -n "$RUNNING_SERVICES" ] && [ "$FORCE" != "true" ]; then
        echo "EDGAR4J is already running for Compose project '$LOCK_PROJECT'. Run scripts/stop.sh first, or pass --force to refresh the lock." >&2
        exit 1
    fi

    rm -f "$LOCK_FILE"
fi

COMPOSE_ARGS="compose -p $PROJECT_NAME --profile $PROFILE up -d"
if [ "$NO_BUILD" != "true" ]; then
    COMPOSE_ARGS="$COMPOSE_ARGS --build"
fi

cd "$REPO_ROOT"
BACKEND_PORT="$BACKEND_PORT" FRONTEND_PORT="$FRONTEND_PORT" docker $COMPOSE_ARGS

cat > "$LOCK_FILE" <<EOF
{
  "projectName": "$PROJECT_NAME",
  "profile": "$PROFILE",
  "backendPort": $BACKEND_PORT,
  "frontendPort": $FRONTEND_PORT,
  "startedAt": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
}
EOF

echo "EDGAR4J started with Docker Compose profile '$PROFILE'."
echo "Frontend: http://localhost:$FRONTEND_PORT"
echo "Backend:  http://localhost:$BACKEND_PORT"
echo "Lock:     $LOCK_FILE"
