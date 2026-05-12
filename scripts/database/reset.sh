#!/bin/bash
# Script to completely reset the MongoDB database (dev environment only)
#
# Usage:
#   ./bin/galaticos db:reset                    # Drop all collections (with confirmation)
#   ./bin/galaticos db:reset --confirm          # Drop all collections without confirmation
#   ./bin/galaticos db:reset --keep-admins      # Keep admin users
#   ./bin/galaticos db:reset --confirm --keep-admins
#
# This is different from `db:seed --reset`:
#   - db:reset: Only drops collections, does NOT re-seed
#   - db:seed --reset: Drops collections AND re-populates from Excel
#
# Safety:
#   - Refuses to run if GALATICOS_ENV=production (unless ALLOW_DESTRUCTIVE_RESET=1)
#   - Shows document counts before dropping
#   - Requires interactive confirmation (unless --confirm)

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

# Configuration
DB_NAME="${DB_NAME:-galaticos}"
readonly _DEFAULT_MONGO_URI="mongodb://localhost:27017"
MONGO_URI="${MONGO_URI:-$_DEFAULT_MONGO_URI}"

# Parse arguments
CONFIRM=false
KEEP_ADMINS=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --confirm|-y)
            CONFIRM=true
            shift
            ;;
        --keep-admins)
            KEEP_ADMINS=true
            shift
            ;;
        --help|-h)
            echo "Usage: ./bin/galaticos db:reset [OPTIONS]"
            echo ""
            echo "Drop all collections in the MongoDB database (dev environment only)."
            echo ""
            echo "Options:"
            echo "  --confirm, -y     Skip confirmation prompt"
            echo "  --keep-admins     Preserve admin users collection"
            echo "  --help, -h        Show this help message"
            echo ""
            echo "Environment variables:"
            echo "  DB_NAME           Database name (default: galaticos)"
            echo "  MONGO_URI         MongoDB connection URI (default: mongodb://localhost:27017)"
            echo "  GALATICOS_ENV     Set to 'production' to block destructive operations"
            echo "  ALLOW_DESTRUCTIVE_RESET=1  Override production safety (use with caution)"
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            log_info "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Change to project root
cd_project_root

# Docker prod: Mongo may require auth. Load credentials from config if available.
if [[ "$MONGO_URI" == "$_DEFAULT_MONGO_URI" && -f "config/docker/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "config/docker/.env"
    set +a
    if [[ -n "${MONGO_INITDB_ROOT_USERNAME:-}" && -n "${MONGO_INITDB_ROOT_PASSWORD:-}" ]]; then
        MONGO_URI="mongodb://${MONGO_INITDB_ROOT_USERNAME}:${MONGO_INITDB_ROOT_PASSWORD}@127.0.0.1:27017/${DB_NAME}?authSource=admin"
    fi
fi

# Helper to redact password from URI for logging
redact_uri() {
    echo "$1" | sed -E 's|(mongodb://[^:]+:)[^@]+@|\1***@|'
}

# Helper to get mongosh connection URI
mongosh_uri() {
    local u="$MONGO_URI"
    if [[ "$u" == *"/${DB_NAME}"* ]] || [[ "$u" == *"?"* ]]; then
        echo "$u"
    else
        echo "${u}/${DB_NAME}"
    fi
}

# Check for production environment
is_production() {
    local env="${GALATICOS_ENV:-}"
    [[ "$env" == "production" || "$env" == "prod" ]]
}

destructive_allowed() {
    local allow="${ALLOW_DESTRUCTIVE_RESET:-}"
    [[ "$allow" == "1" || "$allow" == "true" || "$allow" == "yes" ]]
}

print_header "MongoDB Database Reset"

log_info "Database: $DB_NAME"
log_info "MongoDB URI: $(redact_uri "$MONGO_URI")"
echo ""

# Safety check for production
if is_production; then
    if ! destructive_allowed; then
        log_error "Refusing to reset database: GALATICOS_ENV is set to production."
        log_info "Set ALLOW_DESTRUCTIVE_RESET=1 only if you intentionally want to wipe this database."
        exit 1
    fi
    log_warning "ALLOW_DESTRUCTIVE_RESET is set - proceeding despite production environment"
fi

# Check if MongoDB is running
if ! is_mongodb_running; then
    log_error "MongoDB is not running."
    log_info "Please start MongoDB first:"
    log_info "  - Local: sudo systemctl start mongod (or mongodb)"
    log_info "  - Docker: ./bin/galaticos docker:dev start"
    exit 1
fi

# Determine execution method: mongosh, mongo, or Python
USE_MONGOSH=false
USE_PYTHON=false

if command_exists mongosh; then
    USE_MONGOSH=true
    MONGO_CLIENT="mongosh"
elif command_exists mongo; then
    USE_MONGOSH=true
    MONGO_CLIENT="mongo"
elif python_available 2>/dev/null || command_exists docker; then
    USE_PYTHON=true
else
    log_error "No MongoDB client available."
    log_info "Install one of:"
    log_info "  - MongoDB Shell: https://www.mongodb.com/try/download/shell"
    log_info "  - Python 3 with pymongo: pip install pymongo"
    log_info "  - Docker"
    exit 1
fi

# Python inline script for database reset
run_reset_via_python() {
    local keep_admins="$1"
    local confirm="$2"
    
    python3 -c "
import sys
from pymongo import MongoClient
from pymongo.errors import ServerSelectionTimeoutError

MONGO_URI = '${MONGO_URI}'
DB_NAME = '${DB_NAME}'
KEEP_ADMINS = ${keep_admins}
CONFIRM = ${confirm}

try:
    client = MongoClient(MONGO_URI, serverSelectionTimeoutMS=5000)
    client.admin.command('ping')
    print('✓ Connected to MongoDB')
except ServerSelectionTimeoutError:
    print('✗ Connection timeout', file=sys.stderr)
    sys.exit(1)
except Exception as e:
    print(f'✗ Connection failed: {e}', file=sys.stderr)
    sys.exit(1)

db = client[DB_NAME]

# Get collection stats
collections = db.list_collection_names()
total_docs = 0
print('')
print('Collection statistics:')
for name in sorted(collections):
    count = db[name].count_documents({})
    total_docs += count
    print(f'  {name}: {count} documents')

print('---')
print(f'TOTAL: {total_docs} documents in {len(collections)} collections')
print('')

if KEEP_ADMINS:
    print('⚠  All collections EXCEPT \"admins\" will be PERMANENTLY DELETED')
else:
    print('⚠  ALL collections will be PERMANENTLY DELETED')
print('')

if not CONFIRM:
    response = input('Are you sure you want to proceed? (y/N): ')
    if response.lower() != 'y':
        print('ℹ Aborted by user')
        sys.exit(0)
    print('')

print('→ Dropping database...')
if KEEP_ADMINS:
    dropped = 0
    for name in collections:
        if name != 'admins':
            db.drop_collection(name)
            print(f'  Dropped: {name}')
            dropped += 1
        else:
            print(f'  Preserved: {name}')
    print(f'---')
    print(f'Dropped {dropped} collections (admins preserved)')
else:
    client.drop_database(DB_NAME)
    print(f'Database dropped: {DB_NAME}')

print('')
print('✓ Database reset complete!')
print('')

if KEEP_ADMINS:
    print('ℹ Admin users were preserved. You can still log in with existing credentials.')
else:
    print('ℹ All data has been deleted. Run \"db:seed\" to repopulate:')
    print('  ./bin/galaticos db:seed')
"
}

# Run reset via Docker Python if local Python not available
run_reset_via_docker_python() {
    local keep_admins="$1"
    local confirm="$2"
    
    docker run --rm -i \
        --network host \
        -e MONGO_URI="${MONGO_URI}" \
        -e DB_NAME="${DB_NAME}" \
        python:3.11-slim \
        bash -c "pip install -q --no-cache-dir pymongo && python3 -c \"
import sys
from pymongo import MongoClient
from pymongo.errors import ServerSelectionTimeoutError

MONGO_URI = '${MONGO_URI}'
DB_NAME = '${DB_NAME}'
KEEP_ADMINS = ${keep_admins}
CONFIRM = ${confirm}

try:
    client = MongoClient(MONGO_URI, serverSelectionTimeoutMS=5000)
    client.admin.command('ping')
    print('✓ Connected to MongoDB')
except ServerSelectionTimeoutError:
    print('✗ Connection timeout', file=sys.stderr)
    sys.exit(1)
except Exception as e:
    print(f'✗ Connection failed: {e}', file=sys.stderr)
    sys.exit(1)

db = client[DB_NAME]

# Get collection stats
collections = db.list_collection_names()
total_docs = 0
print('')
print('Collection statistics:')
for name in sorted(collections):
    count = db[name].count_documents({})
    total_docs += count
    print(f'  {name}: {count} documents')

print('---')
print(f'TOTAL: {total_docs} documents in {len(collections)} collections')
print('')

if KEEP_ADMINS:
    print('⚠  All collections EXCEPT \\\"admins\\\" will be PERMANENTLY DELETED')
else:
    print('⚠  ALL collections will be PERMANENTLY DELETED')
print('')

if not CONFIRM:
    print('ℹ Running in Docker - use --confirm flag for non-interactive mode')
    sys.exit(1)

print('→ Dropping database...')
if KEEP_ADMINS:
    dropped = 0
    for name in collections:
        if name != 'admins':
            db.drop_collection(name)
            print(f'  Dropped: {name}')
            dropped += 1
        else:
            print(f'  Preserved: {name}')
    print(f'---')
    print(f'Dropped {dropped} collections (admins preserved)')
else:
    client.drop_database(DB_NAME)
    print(f'Database dropped: {DB_NAME}')

print('')
print('✓ Database reset complete!')
print('')

if KEEP_ADMINS:
    print('ℹ Admin users were preserved. You can still log in with existing credentials.')
else:
    print('ℹ All data has been deleted. Run \\\"db:seed\\\" to repopulate:')
    print('  ./bin/galaticos db:seed')
\""
}

# Execute based on available method
if [[ "$USE_MONGOSH" == "true" ]]; then
    CONNECTION_URI="$(mongosh_uri)"
    
    # Test connection
    log_step "Testing MongoDB connection..."
    if ! $MONGO_CLIENT "$CONNECTION_URI" --quiet --eval "db.adminCommand('ping')" >/dev/null 2>&1; then
        log_error "Cannot connect to MongoDB at $(redact_uri "$CONNECTION_URI")"
        exit 1
    fi
    log_success "Connected to MongoDB"
    echo ""
    
    # Get collection stats
    log_step "Fetching collection statistics..."
    echo ""
    
    STATS_OUTPUT=$($MONGO_CLIENT "$CONNECTION_URI" --quiet --eval "
        const collections = db.getCollectionNames();
        let total = 0;
        collections.forEach(function(name) {
            const count = db.getCollection(name).countDocuments({});
            total += count;
            print(name + ': ' + count + ' documents');
        });
        print('---');
        print('TOTAL: ' + total + ' documents in ' + collections.length + ' collections');
    ")
    
    if [[ -z "$STATS_OUTPUT" || "$STATS_OUTPUT" == "---"* ]]; then
        log_info "Database is empty (no collections)"
        echo ""
    else
        echo "$STATS_OUTPUT"
        echo ""
    fi
    
    # Show what will be dropped
    if [[ "$KEEP_ADMINS" == "true" ]]; then
        log_warning "All collections EXCEPT 'admins' will be PERMANENTLY DELETED"
    else
        log_warning "ALL collections will be PERMANENTLY DELETED"
    fi
    echo ""
    
    # Confirmation
    if [[ "$CONFIRM" != "true" ]]; then
        echo -n "Are you sure you want to proceed? (y/N): "
        read -r response
        if [[ ! "$response" =~ ^[Yy]$ ]]; then
            log_info "Aborted by user"
            exit 0
        fi
        echo ""
    fi
    
    # Perform the reset
    log_step "Dropping database..."
    
    if [[ "$KEEP_ADMINS" == "true" ]]; then
        $MONGO_CLIENT "$CONNECTION_URI" --quiet --eval "
            const collections = db.getCollectionNames();
            let dropped = 0;
            collections.forEach(function(name) {
                if (name !== 'admins') {
                    db.getCollection(name).drop();
                    print('Dropped: ' + name);
                    dropped++;
                } else {
                    print('Preserved: ' + name);
                }
            });
            print('---');
            print('Dropped ' + dropped + ' collections (admins preserved)');
        "
    else
        $MONGO_CLIENT "$CONNECTION_URI" --quiet --eval "
            db.dropDatabase();
            print('Database dropped: $DB_NAME');
        "
    fi
    
    echo ""
    log_success "Database reset complete!"
    echo ""
    
    if [[ "$KEEP_ADMINS" == "true" ]]; then
        log_info "Admin users were preserved. You can still log in with existing credentials."
    else
        log_info "All data has been deleted. Run 'db:seed' to repopulate:"
        log_info "  ./bin/galaticos db:seed"
    fi

elif [[ "$USE_PYTHON" == "true" ]]; then
    # Convert bash booleans to Python booleans
    PYTHON_KEEP_ADMINS="False"
    PYTHON_CONFIRM="False"
    [[ "$KEEP_ADMINS" == "true" ]] && PYTHON_KEEP_ADMINS="True"
    [[ "$CONFIRM" == "true" ]] && PYTHON_CONFIRM="True"
    
    if python_available 2>/dev/null; then
        log_info "Using Python for database operations..."
        run_reset_via_python "$PYTHON_KEEP_ADMINS" "$PYTHON_CONFIRM"
    elif command_exists docker; then
        log_info "Using Docker Python for database operations..."
        if [[ "$CONFIRM" != "true" ]]; then
            log_warning "Interactive mode not supported via Docker. Use --confirm flag."
            exit 1
        fi
        run_reset_via_docker_python "$PYTHON_KEEP_ADMINS" "$PYTHON_CONFIRM"
    fi
fi
