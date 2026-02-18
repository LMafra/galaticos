#!/bin/bash
# Run Playwright E2E tests against a running Galáticos instance.
#
# Usage:
#   ./bin/galaticos e2e                      # uses default http://localhost:3000
#   ./bin/galaticos e2e http://localhost:3000
#
# Notes:
# - This script expects the app to already be running at the given URL.
# - The E2E suite uses hash routing, e.g. /#/login.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

cd_project_root

BASE_URL="http://localhost:3000"

# If the first argument looks like a URL, treat it as the base URL and shift.
if [[ "${1:-}" =~ ^https?:// ]]; then
  BASE_URL="$1"
  shift
fi

# Accept the common npm-style separator (so users can run: ./bin/galaticos e2e -- --grep foo)
if [[ "${1:-}" == "--" ]]; then
  shift
fi

if ! command_exists node; then
  log_error "Node.js not found (required for Playwright)."
  exit 1
fi

if ! command_exists npm; then
  log_error "npm not found (required for Playwright)."
  exit 1
fi

log_info "Running Playwright E2E tests..."
log_info "E2E_BASE_URL: $BASE_URL"
echo ""

# Prefer local install via npm scripts
if [[ $# -gt 0 ]]; then
  E2E_BASE_URL="$BASE_URL" npm run e2e -- "$@"
else
  E2E_BASE_URL="$BASE_URL" npm run e2e
fi


