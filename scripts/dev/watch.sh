#!/bin/bash
# Start combined CSS + CLJS watch for local development.

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

ensure_node() {
    if ! command_exists npm; then
        log_error "npm is required to run the watchers. Install Node.js 18+."
        exit 1
    fi
    # Check if postcss binary is available
    if [[ ! -d node_modules ]] || [[ ! -f node_modules/.bin/postcss ]]; then
        log_step "Installing npm dependencies..."
        # postcss, tailwindcss, and autoprefixer are now in dependencies (not devDependencies)
        # so they will always be installed
        npm install --no-fund --no-audit || {
            log_error "Failed to install npm dependencies"
            exit 1
        }
        # Verify postcss was installed
        if [[ ! -f node_modules/.bin/postcss ]]; then
            log_error "postcss binary was not created after install."
            log_info "Try running manually: npm install"
            exit 1
        fi
    fi
}

cd_project_root
ensure_node

log_info "Starting CSS + CLJS watchers..."
log_info "Press Ctrl+C to stop."
npm run dev:watch
