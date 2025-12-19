#!/bin/bash
# Script to check if aggregated stats are being created for players

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

# Configuration
readonly DB_NAME="galaticos"
readonly MONGO_HOST="${MONGO_HOST:-localhost}"
readonly MONGO_PORT="${MONGO_PORT:-27017}"

cd_project_root

log_info "Checking aggregated stats in MongoDB..."
echo ""

# Check if MongoDB is accessible
if ! command_exists mongosh; then
    log_error "mongosh is not installed. Please install MongoDB shell tools."
    exit 1
fi

# Count players with and without stats
log_step "Checking players collection..."

TOTAL_PLAYERS=$(mongosh --quiet --eval "db.getSiblingDB('$DB_NAME').players.countDocuments({active: true})" mongodb://$MONGO_HOST:$MONGO_PORT 2>/dev/null || echo "0")
PLAYERS_WITH_STATS=$(mongosh --quiet --eval "db.getSiblingDB('$DB_NAME').players.countDocuments({active: true, 'aggregated-stats': {\$exists: true}})" mongodb://$MONGO_HOST:$MONGO_PORT 2>/dev/null || echo "0")
PLAYERS_WITH_GOALS=$(mongosh --quiet --eval "db.getSiblingDB('$DB_NAME').players.countDocuments({active: true, 'aggregated-stats.total.goals': {\$exists: true, \$gt: 0}})" mongodb://$MONGO_HOST:$MONGO_PORT 2>/dev/null || echo "0")
PLAYERS_WITH_ASSISTS=$(mongosh --quiet --eval "db.getSiblingDB('$DB_NAME').players.countDocuments({active: true, 'aggregated-stats.total.assists': {\$exists: true, \$gt: 0}})" mongodb://$MONGO_HOST:$MONGO_PORT 2>/dev/null || echo "0")

echo "📊 Statistics Summary:"
echo "  Total active players: $TOTAL_PLAYERS"
echo "  Players with aggregated-stats: $PLAYERS_WITH_STATS"
echo "  Players with goals > 0: $PLAYERS_WITH_GOALS"
echo "  Players with assists > 0: $PLAYERS_WITH_ASSISTS"
echo ""

# Check matches
TOTAL_MATCHES=$(mongosh --quiet --eval "db.getSiblingDB('$DB_NAME').matches.countDocuments({})" mongodb://$MONGO_HOST:$MONGO_PORT 2>/dev/null || echo "0")
echo "📋 Matches:"
echo "  Total matches: $TOTAL_MATCHES"
echo ""

# Show sample player with stats
log_step "Sample player with stats:"
SAMPLE_PLAYER=$(mongosh --quiet --eval "
var player = db.getSiblingDB('$DB_NAME').players.findOne(
  {active: true, 'aggregated-stats.total.goals': {\$exists: true, \$gt: 0}},
  {name: 1, 'aggregated-stats.total': 1}
);
if (player) {
  print(JSON.stringify(player, null, 2));
} else {
  print('No players with goals found');
}
" mongodb://$MONGO_HOST:$MONGO_PORT 2>/dev/null || echo "{}")

if [ "$SAMPLE_PLAYER" != "{}" ] && [ "$SAMPLE_PLAYER" != "No players with goals found" ]; then
    echo "$SAMPLE_PLAYER" | python3 -m json.tool 2>/dev/null || echo "$SAMPLE_PLAYER"
else
    log_warning "No players with stats found"
fi
echo ""

# Check if stats need to be updated
if [ "$TOTAL_MATCHES" -gt 0 ] && [ "$PLAYERS_WITH_GOALS" -eq 0 ]; then
    log_warning "⚠️  Matches exist but no players have stats!"
    log_info "You may need to run: update-all-player-stats"
    echo ""
    log_info "To update stats via REPL:"
    echo "  1. Run: ./scripts/dev/console.sh"
    echo "  2. Execute: (require '[galaticos.db.aggregations :as agg])"
    echo "  3. Execute: (agg/update-all-player-stats)"
else
    log_success "✅ Stats appear to be populated correctly"
fi

echo ""

