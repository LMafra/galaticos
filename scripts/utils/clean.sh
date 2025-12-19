#!/bin/bash
# Script to clean build artifacts and temporary files

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

# Change to project root
cd_project_root

log_info "Cleaning build artifacts..."
echo ""

CLEANED_ANY=false

# Remove target directory
if [[ -d "target" ]]; then
    log_step "Removing target/ directory..."
    rm -rf target
    log_success "Cleaned target/"
    CLEANED_ANY=true
fi

# Remove .cpcache (Clojure CLI cache)
if [[ -d ".cpcache" ]]; then
    log_step "Removing .cpcache/ directory..."
    rm -rf .cpcache
    log_success "Cleaned .cpcache/"
    CLEANED_ANY=true
fi

# Remove Python cache
if find . -type d -name "__pycache__" -print -quit | grep -q .; then
    log_step "Removing __pycache__/ directories..."
    find . -type d -name "__pycache__" -exec rm -r {} + 2>/dev/null || true
    log_success "Cleaned Python cache"
    CLEANED_ANY=true
fi

# Remove .pyc files
if find . -name "*.pyc" -type f -print -quit | grep -q .; then
    log_step "Removing .pyc files..."
    find . -name "*.pyc" -type f -delete
    log_success "Cleaned .pyc files"
    CLEANED_ANY=true
fi

echo ""
if [[ "$CLEANED_ANY" == "true" ]]; then
    log_success "Cleanup complete!"
else
    log_info "No artifacts to clean."
fi

