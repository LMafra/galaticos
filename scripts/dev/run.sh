#!/bin/bash
# Script to run the Galáticos application in development mode

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

# Configuration
readonly APP_PORT=3000
readonly MAIN_NAMESPACE="galaticos.core"

ensure_node_deps() {
    if ! command_exists npm; then
        log_error "npm is required to build the frontend. Please install Node.js 18+."
        exit 1
    fi
    if [[ ! -d node_modules ]]; then
        log_step "Installing npm dependencies (once)..."
        npm ci --no-fund --no-audit
    fi
}

# Change to project root
cd_project_root

log_info "Starting Galáticos application..."
echo ""

# Check if MongoDB is running
if ! is_mongodb_running; then
    log_warning "MongoDB doesn't seem to be running."
    log_info "Make sure MongoDB is running before starting the application."
    log_info "You can start it with: ./bin/galaticos docker:dev start"
    echo ""
fi

# Compile ClojureScript for development
log_step "Compiling ClojureScript for development..."
ensure_node_deps
if ! run_clojure -M:build:frontend dev; then
    log_error "Failed to compile ClojureScript"
    exit 1
fi
log_success "ClojureScript compiled successfully"
echo ""

# Run the application
log_info "Starting server on port $APP_PORT..."
log_info "Press Ctrl+C to stop the server"
echo ""

# Suppress deprecation warnings that cause auto-qualification issues
# The warnings are about unqualified libs, but they work fine from Clojars
export CLJ_LOG=error

# Note: Deprecation warnings about unqualified libs are expected and can be ignored.
# The dependencies work correctly from Clojars, but Clojure CLI 1.12.3 auto-qualifies
# them and checks Maven Central first, which causes resolution issues.
# This is a known issue that will be resolved in future Clojure CLI versions.

# Run Clojure application with deprecation warning filtering
if ! run_clojure -M:dev -m "$MAIN_NAMESPACE" 2> >(grep -v "DEPRECATED" >&2); then
    log_warning "Note: If you see deprecation warnings, they can be safely ignored."
    log_info "The dependencies are correctly resolved from Clojars."
    run_clojure -M:dev -m "$MAIN_NAMESPACE"
fi

