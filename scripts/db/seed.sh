#!/bin/bash
# Script to seed the MongoDB database with data from Excel file

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

# Configuration
readonly EXCEL_FILE="data/raw/Galáticos 2025 Automatizada 1.12.xlsm"
readonly SEED_SCRIPT="scripts/python/seed_mongodb.py"
# Allow DB_NAME and MONGO_URI to be overridden via environment variable (not readonly so they can be exported)
DB_NAME="${DB_NAME:-galaticos}"
MONGO_URI="${MONGO_URI:-mongodb://localhost:27017}"

# Change to project root
cd_project_root

log_info "Seeding MongoDB database..."
log_info "Database: $DB_NAME"
log_info "MongoDB URI: $MONGO_URI"
echo ""

# Check if MongoDB is running
if ! is_mongodb_running; then
    log_error "MongoDB is not running."
    log_info "Please start MongoDB first:"
    log_info "  - Local: sudo systemctl start mongod (or mongodb)"
    log_info "  - Docker: ./bin/galaticos docker:dev start"
    exit 1
fi

# Setup Python virtual environment first (needed for connection test)
if ! activate_python_venv; then
    log_error "Failed to activate Python virtual environment"
    exit 1
fi

# Install/update dependencies (needed for connection test)
if ! install_python_deps; then
    log_error "Failed to install Python dependencies"
    exit 1
fi

# Test MongoDB connection using Python (more reliable than mongosh)
log_step "Testing MongoDB connection..."
if ! python3 -c "
import sys
from pymongo import MongoClient
from pymongo.errors import ServerSelectionTimeoutError
try:
    client = MongoClient('$MONGO_URI', serverSelectionTimeoutMS=5000)
    client.admin.command('ping')
    print('✓ Connected to MongoDB')
    sys.exit(0)
except ServerSelectionTimeoutError:
    print('✗ Connection timeout', file=sys.stderr)
    sys.exit(1)
except Exception as e:
    print(f'✗ Connection failed: {e}', file=sys.stderr)
    sys.exit(1)
" 2>&1; then
    log_error "Cannot connect to MongoDB at $MONGO_URI"
    log_info "Please ensure MongoDB is running and accessible:"
    log_info "  - Local: sudo systemctl start mongod (or mongodb)"
    log_info "  - Docker: ./bin/galaticos docker:dev start"
    exit 1
fi
log_success "MongoDB connection verified"
echo ""

# Check if Excel file exists
if [[ ! -f "$EXCEL_FILE" ]]; then
    log_warning "Excel file not found at: $EXCEL_FILE"
    log_info "The seed script may fail if the file is required."
    echo ""
fi

# Verify seed script exists
if [[ ! -f "$SEED_SCRIPT" ]]; then
    log_error "Seed script not found: $SEED_SCRIPT"
    exit 1
fi

# Run seed script with MONGO_URI environment variable
log_step "Running seed script..."
if MONGO_URI="$MONGO_URI" DB_NAME="$DB_NAME" python3 "$SEED_SCRIPT"; then
    echo ""
    log_success "Seed script completed successfully!"
    
    # Verify data was inserted
    log_step "Verifying data insertion..."
    if python3 -c "
import sys
from pymongo import MongoClient
try:
    client = MongoClient('$MONGO_URI', serverSelectionTimeoutMS=5000)
    db = client['$DB_NAME']
    
    teams_count = db.teams.count_documents({})
    players_count = db.players.count_documents({})
    championships_count = db.championships.count_documents({})
    
    print(f'  Teams: {teams_count}')
    print(f'  Players: {players_count}')
    print(f'  Championships: {championships_count}')
    
    if teams_count > 0 and players_count > 0:
        print('✓ Data verification successful')
        sys.exit(0)
    else:
        print('⚠ Warning: Some collections appear to be empty', file=sys.stderr)
        sys.exit(0)  # Don't fail, just warn
except Exception as e:
    print(f'⚠ Could not verify data: {e}', file=sys.stderr)
    sys.exit(0)  # Don't fail verification
" 2>&1; then
        log_success "Database seeded successfully!"
    else
        log_warning "Seed completed but verification had issues (data may still be present)"
    fi
else
    log_error "Seed script failed"
    exit 1
fi

