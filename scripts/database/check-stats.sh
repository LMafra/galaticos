#!/bin/bash
# Quick check of aggregated stats and match counts in MongoDB.
# Uses MONGO_URI / DB_NAME (same as seed.sh); loads config/docker/.env when needed.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

readonly _DEFAULT_MONGO_URI="mongodb://localhost:27017"
DB_NAME="${DB_NAME:-galaticos}"
MONGO_URI="${MONGO_URI:-$_DEFAULT_MONGO_URI}"
MONGO_HOST="${MONGO_HOST:-localhost}"
MONGO_PORT="${MONGO_PORT:-27017}"

cd_project_root

if [[ "$MONGO_URI" == "$_DEFAULT_MONGO_URI" && -f "config/docker/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "config/docker/.env"
    set +a
    if [[ -n "${MONGO_INITDB_ROOT_USERNAME:-}" && -n "${MONGO_INITDB_ROOT_PASSWORD:-}" ]]; then
        MONGO_URI="mongodb://${MONGO_INITDB_ROOT_USERNAME}:${MONGO_INITDB_ROOT_PASSWORD}@127.0.0.1:27017/${DB_NAME}?authSource=admin"
        MONGO_HOST="127.0.0.1"
    fi
fi

# mongosh connection string (prefer full MONGO_URI when it includes auth/db)
if [[ "$MONGO_URI" =~ ^mongodb(\+srv)?://[^/]+/[^/?]+ ]]; then
    MONGO_SHELL_URI="$MONGO_URI"
else
    MONGO_SHELL_URI="${MONGO_URI%/}/$DB_NAME"
fi

log_info "Checking aggregated stats in MongoDB..."
log_info "Database: $DB_NAME"
echo ""

USE_DOCKER_SHELL=false
MONGO_CONTAINER=""

if command_exists mongosh; then
    USE_DOCKER_SHELL=false
elif command_exists docker; then
    MONGO_CONTAINER="$(docker ps --format '{{.Names}}' 2>/dev/null | grep -E 'galaticos-mongodb' | head -n 1 || true)"
    if [[ -n "$MONGO_CONTAINER" ]]; then
        log_info "mongosh not found locally; using Docker container: $MONGO_CONTAINER"
        USE_DOCKER_SHELL=true
    else
        log_error "mongosh is not installed and no galaticos-mongodb container is running."
        log_info "Start MongoDB: ./bin/galaticos docker:dev start"
        exit 1
    fi
else
    log_error "mongosh is not installed and Docker is not available."
    exit 1
fi

run_mongo() {
    local eval_str="$1"
    if [[ "$USE_DOCKER_SHELL" == "true" ]]; then
        docker exec "$MONGO_CONTAINER" mongosh --quiet --eval "$eval_str" "$MONGO_SHELL_URI"
    else
        mongosh --quiet --eval "$eval_str" "$MONGO_SHELL_URI"
    fi
}

log_step "Checking players collection..."

TOTAL_PLAYERS=$(run_mongo "db.getSiblingDB('$DB_NAME').players.countDocuments({active: true})" 2>/dev/null || echo "0")
PLAYERS_WITH_STATS=$(run_mongo "db.getSiblingDB('$DB_NAME').players.countDocuments({active: true, 'aggregated-stats': {\$exists: true}})" 2>/dev/null || echo "0")
PLAYERS_WITH_GOALS=$(run_mongo "db.getSiblingDB('$DB_NAME').players.countDocuments({active: true, 'aggregated-stats.total.goals': {\$exists: true, \$gt: 0}})" 2>/dev/null || echo "0")
PLAYERS_WITH_ASSISTS=$(run_mongo "db.getSiblingDB('$DB_NAME').players.countDocuments({active: true, 'aggregated-stats.total.assists': {\$exists: true, \$gt: 0}})" 2>/dev/null || echo "0")
PLAYERS_WITH_PM=$(run_mongo "db.getSiblingDB('$DB_NAME').players.countDocuments({'aggregated-stats.by-championship.pre-match-stats': {\$exists: true}})" 2>/dev/null || echo "0")
PLAYERS_WITH_BASELINE=$(run_mongo "db.getSiblingDB('$DB_NAME').players.countDocuments({'aggregated-stats.by-championship.baseline-match-rollup': {\$exists: true}})" 2>/dev/null || echo "0")

echo "📊 Statistics Summary:"
echo "  Total active players: $TOTAL_PLAYERS"
echo "  Players with aggregated-stats: $PLAYERS_WITH_STATS"
echo "  Players with goals > 0: $PLAYERS_WITH_GOALS"
echo "  Players with assists > 0: $PLAYERS_WITH_ASSISTS"
echo "  Players with pre-match-stats rows: $PLAYERS_WITH_PM"
echo "  Players with baseline-match-rollup rows: $PLAYERS_WITH_BASELINE"
echo ""

TOTAL_MATCHES=$(run_mongo "db.getSiblingDB('$DB_NAME').matches.countDocuments({})" 2>/dev/null || echo "0")
TOTAL_SEASONS=$(run_mongo "db.getSiblingDB('$DB_NAME').seasons.countDocuments({})" 2>/dev/null || echo "0")
echo "📋 Matches / seasons:"
echo "  Total matches: $TOTAL_MATCHES"
echo "  Total seasons: $TOTAL_SEASONS"
echo ""

log_step "Sample player with stats:"
SAMPLE_PLAYER=$(run_mongo "
var player = db.getSiblingDB('$DB_NAME').players.findOne(
  {active: true, 'aggregated-stats.total.goals': {\$exists: true, \$gt: 0}},
  {name: 1, 'aggregated-stats.total': 1}
);
if (player) {
  print(JSON.stringify(player, null, 2));
} else {
  print('No players with goals found');
}
" 2>/dev/null || echo "{}")

if [ "$SAMPLE_PLAYER" != "{}" ] && [ "$SAMPLE_PLAYER" != "No players with goals found" ]; then
    echo "$SAMPLE_PLAYER" | python3 -m json.tool 2>/dev/null || echo "$SAMPLE_PLAYER"
else
    log_warning "No players with stats found"
fi
echo ""

if [ "$TOTAL_MATCHES" -gt 0 ] && [ "$PLAYERS_WITH_GOALS" -eq 0 ]; then
    log_warning "Matches exist but no players have goals in aggregated-stats"
    log_info "Run: ./bin/galaticos db:seed-full --reset"
    log_info "Or reconcile: clojure -M:dev -m galaticos.tasks.reconcile-player-stats"
elif [ "$TOTAL_MATCHES" -eq 0 ] && [ "$PLAYERS_WITH_GOALS" -gt 0 ]; then
    log_info "Stats from planilha only (no matches). For full career + tournament data:"
    log_info "  ./bin/galaticos db:seed-full --reset"
elif [ "$TOTAL_MATCHES" -eq 0 ]; then
    log_info "Empty or table-only DB. Full populate:"
    log_info "  ./bin/galaticos db:setup && ./bin/galaticos db:seed-full --reset"
else
    log_success "Stats and matches appear populated"
    if [ "$PLAYERS_WITH_BASELINE" -eq 0 ] && [ "$TOTAL_MATCHES" -gt 0 ]; then
        log_info "Tip: run reconcile for hybrid baselines:"
        log_info "  clojure -M:dev -m galaticos.tasks.reconcile-player-stats"
    fi
fi

echo ""
