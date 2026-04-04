#!/bin/bash
# Script to seed the MongoDB database with data from Excel file
#
# Usage:
#   ./seed.sh              # Normal seed (idempotent - won't create duplicates)
#   ./seed.sh --reset      # Clear all data and reseed
#   ./seed.sh --reset --keep-admins  # Clear data but keep admin users
#
# After db:seed-smoke: do not run this without --reset on the same DB — the Python seed
# refuses to merge official Excel data with smoke/E2E data (use --reset or another DB_NAME).

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

# Configuration (EXCEL_FILE may be set in the environment, same as seed_mongodb.py)
EXCEL_FILE="${EXCEL_FILE:-data/galaticos.xlsm}"
readonly SEED_SCRIPT="scripts/python/seed_mongodb.py"
# Default admin credentials (must match seed_mongodb.py create_admin defaults)
readonly ADMIN_USER="admin"
readonly ADMIN_PASS="admin"
# Allow DB_NAME and MONGO_URI to be overridden via environment variable (not readonly so they can be exported)
DB_NAME="${DB_NAME:-galaticos}"
MONGO_URI="${MONGO_URI:-mongodb://localhost:27017}"

# Change to project root
cd_project_root

print_admin_credentials() {
    echo ""
    log_info "Admin credentials (login no sistema):"
    echo "   Username: $ADMIN_USER"
    echo "   Password: $ADMIN_PASS"
    echo ""
}

log_info "Seeding MongoDB database..."
log_info "Database: $DB_NAME"
log_info "MongoDB URI: $MONGO_URI"
log_info "Excel file (EXCEL_FILE): $EXCEL_FILE"

# Parse arguments and pass them to Python script
SEED_ARGS=("$@")
if [[ ${#SEED_ARGS[@]} -eq 0 ]]; then
    log_info "Note: Running in idempotent mode (won't create duplicates)"
    log_info "      Use --reset to clear existing data before seeding"
fi
echo ""

# Check if MongoDB is running
if ! is_mongodb_running; then
    log_error "MongoDB is not running."
    log_info "Please start MongoDB first:"
    log_info "  - Local: sudo systemctl start mongod (or mongodb)"
    log_info "  - Docker: ./bin/galaticos docker:dev start"
    exit 1
fi

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

USE_PYTHON_LOCALLY=false
if python_available 2>/dev/null; then
    USE_PYTHON_LOCALLY=true
fi

if [[ "$USE_PYTHON_LOCALLY" == "true" ]]; then
    # Test MongoDB connection using Python
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
        exit 1
    fi
    log_success "MongoDB connection verified"
    echo ""

    # Run seed script locally
    log_step "Running seed script..."
    if MONGO_URI="$MONGO_URI" DB_NAME="$DB_NAME" EXCEL_FILE="$EXCEL_FILE" python3 "$SEED_SCRIPT" "${SEED_ARGS[@]}"; then
        echo ""
        log_success "Seed script completed successfully!"
        log_step "Verifying data insertion..."
        if MONGO_URI="$MONGO_URI" DB_NAME="$DB_NAME" python3 scripts/python/verify_seed.py 2>&1; then
            log_success "Database seeded successfully!"
        else
            log_warning "Seed completed but verification had issues (data may still be present)"
        fi
        echo ""
        print_admin_credentials
    else
        log_error "Seed script failed"
        exit 1
    fi
elif command_exists docker; then
    log_info "Python not available locally; running seed via Docker..."
    echo ""

    log_step "Running seed script (Docker)..."
    if ! MONGO_URI="$MONGO_URI" DB_NAME="$DB_NAME" EXCEL_FILE="$EXCEL_FILE" run_python_in_docker "$SEED_SCRIPT" "${SEED_ARGS[@]}"; then
        log_error "Seed script failed"
        exit 1
    fi
    echo ""
    log_success "Seed script completed successfully!"

    log_step "Verifying data insertion..."
    if MONGO_URI="$MONGO_URI" DB_NAME="$DB_NAME" run_python_in_docker scripts/python/verify_seed.py 2>&1; then
        log_success "Database seeded successfully!"
    else
        log_warning "Seed completed but verification had issues (data may still be present)"
    fi
    echo ""
    print_admin_credentials
else
    log_error "Python (with venv and dependencies) or Docker is required to run the seed."
    log_info "  - Install Python 3 and run: python3 -m venv venv && source venv/bin/activate && pip install -r requirements.txt"
    log_info "  - Or install Docker and ensure the MongoDB container is running: ./bin/galaticos docker:dev start"
    exit 1
fi

