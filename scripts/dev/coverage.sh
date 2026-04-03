#!/bin/bash
# Script to run test coverage for the Galáticos backend (Clojure)

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

# Change to project root
cd_project_root

log_info "Running test coverage for Galáticos backend..."
echo ""

# Check if test directory exists
if [[ ! -d "test" ]]; then
    log_warning "No test directory found."
    log_info "Add tests under test/ to run coverage analysis."
    exit 0
fi

# Run coverage with Cloverage (--fail-threshold in deps.edn; gate is min of % lines and % forms)
log_step "Running Cloverage (threshold: min(% lines, % forms) ≥ 70; see deps.edn :coverage)..."
echo ""

if ! run_clojure -M:coverage; then
    log_error "Cloverage exited with failure. Common causes:"
    log_error "  • Test failures (see FAIL / errors above)"
    log_error "  • Coverage below deps.edn :coverage --fail-threshold (min of % lines and % forms vs project total)"
    echo ""
    log_info "Coverage report available at: target/coverage/index.html"
    exit 1
fi

echo ""
log_success "Coverage thresholds met! ✅"
log_info "Coverage report available at: target/coverage/index.html"
echo ""

# Show quick summary if coverage.txt exists
if [[ -f "target/coverage/coverage.txt" ]]; then
    log_info "Coverage Summary:"
    grep -E "Lines|Branches" "target/coverage/coverage.txt" || true
fi

