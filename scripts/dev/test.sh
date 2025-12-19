#!/bin/bash
# Script to run tests for the Galáticos project

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

# Change to project root
cd_project_root

log_info "Running tests for Galáticos..."
echo ""

# Check if any test directories exist
if [[ ! -d "test" ]] && [[ ! -d "src/galaticos/test" ]] && [[ ! -d "test-cljs" ]]; then
    log_warning "No test directories found."
    log_info "Add tests under test/ for backend or test-cljs/ for CLJS."
    exit 0
fi

# Run backend tests
log_step "Running backend tests (clojure.test)..."
if ! run_clojure -M:test; then
    log_error "Backend tests failed or encountered an error"
    exit 1
fi
log_success "Backend tests completed"
echo ""

# Run ClojureScript tests (requires Node.js)
if command_exists node; then
    log_step "Running ClojureScript tests (node runner)..."
    if ! run_clojure -M:cljs-test; then
        log_error "ClojureScript tests failed or encountered an error"
        exit 1
    fi
    log_success "ClojureScript tests completed"
else
    log_warning "Node.js not found; skipping ClojureScript tests."
    log_info "Install Node.js to run CLJS tests locally, or run inside Docker."
fi

echo ""
log_success "Tests completed!"

