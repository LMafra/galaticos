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

# Run coverage with Cloverage
log_step "Running Cloverage (80% lines, 70% branches threshold)..."
echo ""

if ! run_clojure -M:coverage; then
    log_error "Coverage failed to meet thresholds (80% lines, 70% branches)"
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

