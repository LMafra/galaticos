#!/bin/bash
# Start shadow-cljs watch mode for the frontend with hot reload.

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

ensure_node() {
    if ! command_exists npm; then
        log_error "npm is required to run the CLJS watcher. Install Node.js 18+."
        exit 1
    fi
    if [[ ! -d node_modules ]]; then
        log_step "Installing npm dependencies..."
        npm ci --no-fund --no-audit
    fi
}

cd_project_root
ensure_node

log_info "Starting shadow-cljs watch (build id: app)..."
log_info "Press Ctrl+C to stop."
npm run cljs:watch

