#!/usr/bin/env sh
set -eu

PROJECT_NAME=""
REMOVE_VOLUMES="false"

usage() {
    cat <<'EOF'
Usage: scripts/stop.sh [options]

Options:
  --project-name NAME      Docker Compose project name (default: lock file value, then edgar4j)
  --remove-volumes         Remove Docker volumes
  -h, --help               Show this help
EOF
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --project-name)
            PROJECT_NAME="${2:?Missing value for --project-name}"
            shift 2
            ;;
        --remove-volumes)
            REMOVE_VOLUMES="true"
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

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
LOCK_FILE="$SCRIPT_DIR/edgar4j.lock"

if [ -z "$PROJECT_NAME" ]; then
    if [ -f "$LOCK_FILE" ]; then
        PROJECT_NAME=$(sed -n 's/.*"projectName"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$LOCK_FILE" | head -n 1)
    fi
    [ -n "$PROJECT_NAME" ] || PROJECT_NAME="edgar4j"
fi

cd "$REPO_ROOT"
if [ "$REMOVE_VOLUMES" = "true" ]; then
    docker compose -p "$PROJECT_NAME" down -v
else
    docker compose -p "$PROJECT_NAME" down
fi

rm -f "$LOCK_FILE"

echo "EDGAR4J stopped for Docker Compose project '$PROJECT_NAME'."
if [ "$REMOVE_VOLUMES" = "true" ]; then
    echo "Docker volumes were removed."
fi
