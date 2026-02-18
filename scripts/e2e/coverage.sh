#!/bin/bash
# Script to run E2E test coverage for the Galáticos project

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

# Change to project root
cd_project_root

log_info "Running E2E test coverage for Galáticos..."
echo ""

# Check if Playwright is installed
if ! command_exists npx; then
    log_error "npx not found. Please install Node.js and npm."
    exit 1
fi

# Check if @playwright/test is installed
if [[ ! -d "node_modules/@playwright/test" ]]; then
    log_warning "Playwright not installed. Installing dependencies..."
    npm install
fi

# Set environment variable to enable coverage collection
export COVERAGE=true

log_step "Running Playwright E2E tests with coverage..."
echo ""

# Run E2E tests
if ! "$SCRIPT_DIR/run.sh" "$@"; then
    log_error "E2E tests failed"
    exit 1
fi

echo ""
log_success "E2E tests completed!"

# Check if coverage was collected
if [[ -d "playwright-coverage" ]]; then
    log_info "Coverage data available at: playwright-coverage/"
    
    # If nyc is available, generate report
    if command_exists npx && [[ -f "package.json" ]]; then
        log_step "Generating coverage report..."
        
        # Check if there's coverage data
        if ls playwright-coverage/*.json 1> /dev/null 2>&1; then
            # Generate HTML report using nyc
            if npx nyc report --reporter=html --reporter=text --report-dir=playwright-coverage/report --temp-dir=playwright-coverage 2>/dev/null; then
                log_success "Coverage report generated at: playwright-coverage/report/index.html"
            else
                log_warning "Could not generate coverage report. Install nyc for better reports: npm install --save-dev nyc"
                log_info "Raw coverage data available at: playwright-coverage/"
            fi
        else
            log_warning "No coverage data collected. Ensure your application serves instrumented code."
            log_info "See docs/testing-coverage.md for setup instructions."
        fi
    fi
else
    log_warning "No coverage data collected."
    log_info "To enable E2E coverage:"
    log_info "  1. Instrument your JavaScript code with istanbul/nyc"
    log_info "  2. Serve instrumented code in test environment"
    log_info "  3. Use playwright coverage helpers to collect data"
    log_info ""
    log_info "See docs/testing-coverage.md for detailed setup instructions."
fi

echo ""

