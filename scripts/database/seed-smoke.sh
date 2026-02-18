#!/bin/bash
# Seed minimal deterministic data for smoke/E2E tests (no Excel required).
#
# Uses Clojure seed task:
#   clj -M:dev -m galaticos.tasks.seed-smoke
#
# Env:
#   - DATABASE_URL / DATABASE_NAME (preferred)
#   - or MONGO_URI + DB_NAME

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

# Allow overrides
DB_NAME="${DB_NAME:-galaticos}"
MONGO_URI="${MONGO_URI:-mongodb://localhost:27017}"
DATABASE_NAME="${DATABASE_NAME:-$DB_NAME}"

cd_project_root

log_info "Seeding smoke dataset..."

# Construct DATABASE_URL if not provided
if [[ -z "${DATABASE_URL:-}" ]]; then
  # If MONGO_URI already includes a database path (after host), use it as-is.
  # Examples with DB:
  #   mongodb://localhost:27017/galaticos
  #   mongodb://user:pass@localhost:27017/galaticos?authSource=admin
  # Example without DB:
  #   mongodb://localhost:27017
  if [[ "$MONGO_URI" =~ ^mongodb(\+srv)?://[^/]+/[^/?]+ ]]; then
    DATABASE_URL="$MONGO_URI"
  else
    DATABASE_URL="${MONGO_URI%/}/$DB_NAME"
  fi
fi

export DATABASE_URL
export DATABASE_NAME

log_info "DATABASE_URL: $DATABASE_URL"
log_info "DATABASE_NAME: $DATABASE_NAME"
echo ""

# Verify clojure cli
HAVE_CLJ=true
if ! check_clojure_cli >/dev/null 2>&1; then
  HAVE_CLJ=false
fi

# Best-effort Mongo connection check
log_step "Testing MongoDB connection..."
if ! check_mongodb_connection "$DATABASE_URL"; then
  log_warning "Mongo connection check failed (will still attempt seed via app code)."
else
  log_success "MongoDB connection OK"
fi
echo ""

log_step "Running smoke seed task..."
if [[ "$HAVE_CLJ" == "true" ]]; then
  run_clojure -M:dev -m galaticos.tasks.seed-smoke
else
  if ! command_exists docker; then
    log_error "Clojure CLI not found and docker is not available to run seed in a container."
    exit 1
  fi
  log_warning "Clojure CLI not found; running seed task in a temporary Docker container."
  docker run --rm \
    --network host \
    -e DATABASE_URL="$DATABASE_URL" \
    -e DATABASE_NAME="$DATABASE_NAME" \
    -v "$(pwd)":/app \
    -w /app \
    clojure:temurin-21-tools-deps \
    clj -M:dev -m galaticos.tasks.seed-smoke
fi
log_success "Smoke seed completed"


