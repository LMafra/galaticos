#!/bin/bash
# Full database seed: Excel + BASE_DADOS + legacy imports + tournament matches + ASBAC,
# then Clojure reconcile (hybrid aggregated-stats aligned with the app).
#
# Usage:
#   ./seed-full.sh                    # Idempotent full import (may skip duplicates)
#   ./seed-full.sh --reset            # Wipe and reload everything (recommended for dev)
#   ./seed-full.sh --reset --keep-admins
#
# Requires: data/raw/galaticos.xlsm (or EXCEL_FILE), MongoDB running.
# Do not run on the same DB as db:seed-smoke without --reset.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

DB_NAME="${DB_NAME:-galaticos}"
MONGO_URI="${MONGO_URI:-mongodb://localhost:27017}"
readonly _DEFAULT_MONGO_URI="mongodb://localhost:27017"

cd_project_root

if [[ "$MONGO_URI" == "$_DEFAULT_MONGO_URI" && -f "config/docker/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "config/docker/.env"
    set +a
    if [[ -n "${MONGO_INITDB_ROOT_USERNAME:-}" && -n "${MONGO_INITDB_ROOT_PASSWORD:-}" ]]; then
        MONGO_URI="mongodb://${MONGO_INITDB_ROOT_USERNAME}:${MONGO_INITDB_ROOT_PASSWORD}@127.0.0.1:27017/${DB_NAME}?authSource=admin"
    fi
fi

if [[ "$MONGO_URI" =~ ^mongodb(\+srv)?://[^/]+/[^/?]+ ]]; then
    DATABASE_URL="$MONGO_URI"
else
    DATABASE_URL="${MONGO_URI%/}/$DB_NAME"
fi
export DATABASE_URL
export DATABASE_NAME="${DATABASE_NAME:-$DB_NAME}"
export MONGO_URI
export DB_NAME

log_header "Full database seed (all data sources)"

log_info "1/2 Python seed (--full): planilha, CSV, partidas de torneio, ASBAC"
log_info "2/2 Clojure reconcile: aggregated-stats híbridos a partir de matches"
echo ""

# Pass through --reset, --keep-admins, --excel, etc.; always enable --full for Python
SEED_ARGS=(--full)
for arg in "$@"; do
    if [[ "$arg" != "--full" ]]; then
        SEED_ARGS+=("$arg")
    fi
done

if ! "$SCRIPT_DIR/seed.sh" "${SEED_ARGS[@]}"; then
    log_error "Python full seed failed"
    exit 1
fi

echo ""
log_step "Reconciling player aggregated-stats (Clojure hybrid merge)..."

if check_clojure_cli >/dev/null 2>&1; then
    if run_clojure -M:dev -m galaticos.tasks.reconcile-player-stats; then
        log_success "Stats reconcile completed"
    else
        log_warning "Stats reconcile failed — run manually: clojure -M:dev -m galaticos.tasks.reconcile-player-stats"
        exit 1
    fi
elif command_exists docker; then
    log_info "Clojure CLI not found; running reconcile in Docker..."
    if docker run --rm \
        --network host \
        -e DATABASE_URL="$DATABASE_URL" \
        -e DATABASE_NAME="$DATABASE_NAME" \
        -v "$(pwd)":/app \
        -w /app \
        clojure:temurin-21-tools-deps \
        clj -M:dev -m galaticos.tasks.reconcile-player-stats; then
        log_success "Stats reconcile completed (Docker)"
    else
        log_warning "Stats reconcile failed in Docker"
        exit 1
    fi
else
    log_warning "Clojure/Docker unavailable — seed data loaded but stats may need manual reconcile:"
    log_info "  clojure -M:dev -m galaticos.tasks.reconcile-player-stats"
    exit 1
fi

echo ""
log_success "Full seed finished (all imports + hybrid stats reconcile)"
log_info "Verify: ./bin/galaticos db:check-stats  (or scripts/database/check-stats.sh)"
