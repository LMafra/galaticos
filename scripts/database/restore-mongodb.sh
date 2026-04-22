#!/bin/bash
# Restore MongoDB from a mongodump archive (gzip).
#
# Environment:
#   MONGO_URI  — target connection string (default: mongodb://localhost:27017)
#   DB_NAME    — database name (default: galaticos); used with --nsInclude when restoring
#
# Usage:
#   ./bin/galaticos db:restore --archive backups/mongodb/galaticos-20260101-030000.archive.gz
#   ./bin/galaticos db:restore --archive path/to/dump.gz --drop
#
# Without --drop, mongorestore merges into existing data. With --drop, it drops each
# collection before restoring (destructive). Test restores on staging first.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

cd_project_root

MONGO_URI="${MONGO_URI:-mongodb://localhost:27017}"
DB_NAME="${DB_NAME:-galaticos}"

ARCHIVE=""
DROP_RESTORE=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --archive)
            ARCHIVE="${2:-}"
            shift 2
            ;;
        --drop)
            DROP_RESTORE=1
            shift
            ;;
        -h|--help)
            echo "Usage: $0 --archive PATH [--drop]"
            echo "  --archive  Path to .archive.gz from db:backup"
            echo "  --drop     Drop collections before restore (destructive)"
            exit 0
            ;;
        *)
            log_error "Unknown argument: $1"
            exit 1
            ;;
    esac
done

if [[ -z "$ARCHIVE" ]]; then
    log_error "Missing --archive PATH"
    log_info "Example: ./bin/galaticos db:restore --archive backups/mongodb/galaticos-20260101-030000.archive.gz"
    exit 1
fi

if [[ ! -f "$ARCHIVE" ]]; then
    log_error "Archive not found: $ARCHIVE"
    exit 1
fi

if ! command_exists mongorestore; then
    log_error "mongorestore not found. Install MongoDB Database Tools."
    exit 1
fi

log_header "MongoDB restore"
log_warning "Target: $MONGO_URI"
log_warning "Database namespace filter: ${DB_NAME}.*"
if [[ "$DROP_RESTORE" -eq 1 ]]; then
    log_warning "--drop enabled: existing collections in $DB_NAME will be replaced."
else
    log_info "No --drop: data will be merged with existing collections."
fi
echo ""

log_step "Running mongorestore..."
if [[ "$DROP_RESTORE" -eq 1 ]]; then
    mongorestore \
        --uri="$MONGO_URI" \
        --gzip \
        --archive="$ARCHIVE" \
        --nsInclude="${DB_NAME}.*" \
        --drop
else
    mongorestore \
        --uri="$MONGO_URI" \
        --gzip \
        --archive="$ARCHIVE" \
        --nsInclude="${DB_NAME}.*"
fi
log_success "Restore completed."
