#!/bin/bash
# Backup MongoDB database using mongodump (single gzipped archive).
#
# Environment:
#   MONGO_URI  — connection string (default: mongodb://localhost:27017)
#   DB_NAME    — database name (default: galaticos)
#   BACKUP_DIR — output directory (default: backups/mongodb)
#
# Usage:
#   ./bin/galaticos db:backup
#   MONGO_URI='mongodb://user:pass@host:27017/?authSource=admin' DB_NAME=galaticos ./bin/galaticos db:backup

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

cd_project_root

MONGO_URI="${MONGO_URI:-mongodb://localhost:27017}"
DB_NAME="${DB_NAME:-galaticos}"
BACKUP_DIR="${BACKUP_DIR:-backups/mongodb}"

STAMP="$(date +%Y%m%d-%H%M%S)"
ARCHIVE_PATH="$BACKUP_DIR/${DB_NAME}-${STAMP}.archive.gz"

log_info "MongoDB backup (mongodump)"
log_info "Database: $DB_NAME"
log_info "Backup dir: $BACKUP_DIR"
echo ""

if ! command_exists mongodump; then
    log_error "mongodump not found. Install MongoDB Database Tools or use a container image that includes them."
    exit 1
fi

mkdir -p "$BACKUP_DIR"

log_step "Running mongodump..."
if mongodump --uri="$MONGO_URI" --db="$DB_NAME" --archive="$ARCHIVE_PATH" --gzip; then
    log_success "Backup written: $ARCHIVE_PATH"
else
    log_error "mongodump failed"
    rm -f "$ARCHIVE_PATH" 2>/dev/null || true
    exit 1
fi

if command_exists du; then
    log_info "Size: $(du -h "$ARCHIVE_PATH" | cut -f1)"
fi

log_info "Restore (staging test): ./bin/galaticos db:restore --archive $ARCHIVE_PATH"
