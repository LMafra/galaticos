#!/bin/bash
# Script to set up MongoDB indexes

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

# Configuration (override with MONGO_URI / DB_NAME for remote or prod)
DB_NAME="${DB_NAME:-galaticos}"
readonly _DEFAULT_MONGO_URI="mongodb://localhost:27017"
MONGO_URI="${MONGO_URI:-$_DEFAULT_MONGO_URI}"
readonly INDEXES_SCRIPT="scripts/mongodb/mongodb-indexes.js"

# Change to project root
cd_project_root

# Docker prod: Mongo exige auth. Se MONGO_URI ainda é o default, carregar config/docker/.env
# (MONGO_INITDB_ROOT_*) e montar URI com authSource=admin em 127.0.0.1:27017 (porta publicada no host).
if [[ "$MONGO_URI" == "$_DEFAULT_MONGO_URI" && -f "config/docker/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "config/docker/.env"
    set +a
    if [[ -n "${MONGO_INITDB_ROOT_USERNAME:-}" && -n "${MONGO_INITDB_ROOT_PASSWORD:-}" ]]; then
        MONGO_URI="mongodb://${MONGO_INITDB_ROOT_USERNAME}:${MONGO_INITDB_ROOT_PASSWORD}@127.0.0.1:27017/${DB_NAME}?authSource=admin"
    fi
fi

# URI passada ao mongosh: se já tem /db ou ? (auth), usar tal qual; senão anexar /DB_NAME (dev sem auth).
mongosh_connection_uri() {
    local u="$MONGO_URI"
    if [[ "$u" == *"/${DB_NAME}"* ]] || [[ "$u" == *"?"* ]]; then
        echo "$u"
    else
        echo "${u}/${DB_NAME}"
    fi
}

redact_mongo_uri_for_log() {
    echo "$1" | sed -E 's|(mongodb://[^:]+:)[^@]+@|\1***@|'
}

log_info "Setting up MongoDB indexes..."
echo ""

# Check if MongoDB is running
if ! is_mongodb_running; then
    log_error "MongoDB is not running."
    log_info "Please start MongoDB first:"
    log_info "  - Local: sudo systemctl start mongod (or mongodb)"
    log_info "  - Docker: ./bin/galaticos docker:dev start"
    exit 1
fi

# Verify indexes script exists
if [[ ! -f "$INDEXES_SCRIPT" ]]; then
    log_error "Indexes script not found: $INDEXES_SCRIPT"
    exit 1
fi

# Check MongoDB connection with retry (MongoDB might still be initializing)
log_step "Testing MongoDB connection..."
MAX_RETRIES=3
RETRY_DELAY=2
retry_count=0

while [ $retry_count -lt $MAX_RETRIES ]; do
    if check_mongodb_connection "$MONGO_URI"; then
        log_success "MongoDB connection verified"
        break
    fi
    
    retry_count=$((retry_count + 1))
    if [ $retry_count -lt $MAX_RETRIES ]; then
        log_info "Connection attempt $retry_count/$MAX_RETRIES failed, retrying in ${RETRY_DELAY}s..."
        sleep $RETRY_DELAY
    fi
done

if [ $retry_count -eq $MAX_RETRIES ]; then
    log_error "Cannot connect to MongoDB at $(redact_mongo_uri_for_log "$MONGO_URI") after $MAX_RETRIES attempts"
    log_info "Trying alternative connection test..."
    
    # Try using Python as alternative (more reliable)
    if command_exists python3; then
        if python3 -c "
import sys
from pymongo import MongoClient
from pymongo.errors import ServerSelectionTimeoutError
try:
    client = MongoClient('$MONGO_URI', serverSelectionTimeoutMS=5000)
    client.admin.command('ping')
    print('✓ Python connection test: OK')
    sys.exit(0)
except ServerSelectionTimeoutError:
    print('✗ Python connection test: Timeout', file=sys.stderr)
    sys.exit(1)
except Exception as e:
    print(f'✗ Python connection test: {e}', file=sys.stderr)
    sys.exit(1)
" 2>&1; then
            log_success "MongoDB connection verified via Python"
        else
            log_error "Both mongosh and Python connection tests failed"
            log_info "Please ensure MongoDB is running and accessible:"
            log_info "  - Check if MongoDB container is running: docker ps | grep mongodb"
            log_info "  - Check MongoDB logs: ./bin/galaticos docker:dev logs"
            log_info "  - Restart MongoDB: ./bin/galaticos docker:dev restart"
        fi
    else
        log_error "Cannot verify MongoDB connection (Python not available)"
        log_info "Please ensure MongoDB is running and accessible:"
        log_info "  - Check if MongoDB container is running: docker ps | grep mongodb"
        log_info "  - Check MongoDB logs: ./bin/galaticos docker:dev logs"
    fi
    exit 1
fi

log_info "Database: $DB_NAME"
log_info "MongoDB URI: $(redact_mongo_uri_for_log "$MONGO_URI")"
echo ""

# Run indexes script with appropriate MongoDB shell
log_step "Creating indexes in database '$DB_NAME'..."
if command_exists mongosh; then
    # mongosh: use --file flag for script execution with full URI including database
    if mongosh "$(mongosh_connection_uri)" --file "$INDEXES_SCRIPT"; then
        log_success "MongoDB indexes created successfully in database '$DB_NAME'!"
    else
        log_error "Failed to create indexes"
        exit 1
    fi
elif command_exists mongo; then
    # mongo: use full URI with database name to ensure correct connection
    if mongo "$(mongosh_connection_uri)" "$INDEXES_SCRIPT"; then
        log_success "MongoDB indexes created successfully in database '$DB_NAME'!"
    else
        log_error "Failed to create indexes"
        exit 1
    fi
else
    # Fallback: try running indexes inside MongoDB Docker container
    if command_exists docker; then
        MONGO_CONTAINER="$(docker ps --format '{{.Names}}' 2>/dev/null | grep -E 'galaticos-mongodb' | head -n 1 || true)"
        if [[ -n "$MONGO_CONTAINER" ]]; then
            log_info "Mongo shell not found locally, running indexes inside container: $MONGO_CONTAINER"
            # Pipe script into mongosh inside the container
            if docker exec -i "$MONGO_CONTAINER" mongosh "$(mongosh_connection_uri)" --quiet < "$INDEXES_SCRIPT"; then
                log_success "MongoDB indexes created successfully in database '$DB_NAME' (via Docker)!"
            else
                log_error "Failed to create indexes inside MongoDB container"
                exit 1
            fi
        else
            log_error "Neither mongosh nor mongo command found, and no MongoDB container detected."
            log_info "Please install MongoDB shell tools or ensure the MongoDB Docker container is running."
            exit 1
        fi
    else
        log_error "Neither mongosh nor mongo command found, and 'docker' is not available."
        log_info "Please install MongoDB shell tools."
        exit 1
    fi
fi

# Verify indexes were created
log_step "Verifying indexes..."
if command_exists mongosh; then
    if mongosh "$(mongosh_connection_uri)" --quiet --eval "
        print('Collections with indexes:');
        db.getCollectionNames().forEach(function(coll) {
            var indexes = db.getCollection(coll).getIndexes();
            if (indexes.length > 0) {
                print('  ' + coll + ': ' + indexes.length + ' index(es)');
            }
        });
    " 2>/dev/null; then
        log_success "Index verification complete"
    else
        log_warning "Could not verify indexes (this is not critical)"
    fi
elif command_exists mongo; then
    if mongo "$(mongosh_connection_uri)" --quiet --eval "
        print('Collections with indexes:');
        db.getCollectionNames().forEach(function(coll) {
            var indexes = db.getCollection(coll).getIndexes();
            if (indexes.length > 0) {
                print('  ' + coll + ': ' + indexes.length + ' index(es)');
            }
        });
    " 2>/dev/null; then
        log_success "Index verification complete"
    else
        log_warning "Could not verify indexes (this is not critical)"
    fi
else
    log_warning "Skipping index verification (no local MongoDB shell available)"
fi

# Update titles-count for championships based on player data
log_step "Updating titles-count for championships..."
if command_exists python3; then
    # Setup Python virtual environment first (needed for pymongo)
    if ! activate_python_venv; then
        log_warning "Could not activate Python virtual environment, skipping titles-count update"
    elif ! install_python_deps; then
        log_warning "Could not install Python dependencies, skipping titles-count update"
    else
        if python3 -c "
import sys
from datetime import datetime, timezone
from pymongo import MongoClient
from bson import ObjectId

try:
    client = MongoClient('$MONGO_URI', serverSelectionTimeoutMS=5000)
    db = client['$DB_NAME']
    
    championships = db.championships.find({})
    updated_count = 0
    
    for championship in championships:
        championship_id = championship['_id']
        max_titles = 0
        
        # Find all players with stats for this championship
        # Use string comparison to handle ObjectId properly
        players = db.players.find({
            'aggregated-stats.by-championship.championship-id': championship_id
        })
        
        for player in players:
            aggregated_stats = player.get('aggregated-stats', {})
            by_championship = aggregated_stats.get('by-championship', [])
            
            # Find stats for this championship
            # The query already filtered by championship-id, so we just need to find the matching entry
            for champ_stats in by_championship:
                champ_id = champ_stats.get('championship-id')
                # Compare ObjectIds (handle both ObjectId and string representations)
                if str(champ_id) == str(championship_id):
                    titles = champ_stats.get('titles', 0)
                    if isinstance(titles, (int, float)) and titles > max_titles:
                        max_titles = int(titles)
                    break
        
        # Update championship with max titles
        result = db.championships.update_one(
            {'_id': championship_id},
            {
                '\$set': {
                    'titles-count': max_titles,
                    'updated-at': datetime.now(timezone.utc)
                }
            }
        )
        
        if result.modified_count > 0:
            updated_count += 1
            print(f'  Updated {championship.get(\"name\", \"Unknown\")}: titles-count = {max_titles}')
    
    print(f'✓ Updated {updated_count} championship(s)')
    sys.exit(0)
except Exception as e:
    print(f'✗ Error updating titles-count: {e}', file=sys.stderr)
    sys.exit(1)
" 2>&1; then
            log_success "Titles-count updated successfully"
        else
            log_warning "Could not update titles-count (this is not critical)"
        fi
    fi
else
    log_warning "Python3 not available, skipping titles-count update"
fi

