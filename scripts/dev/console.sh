#!/bin/bash
# Script to start a Clojure REPL console for interactive development

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

# Change to project root
cd_project_root

log_info "Starting Clojure REPL console..."
echo ""
echo "Useful commands:"
echo "  (require '[galaticos.db.core :as db])"
echo "  (db/connect!)"
echo "  (db/db)"
echo "  (db/disconnect!)"
echo ""
log_info "Press Ctrl+D or type (exit) to quit"
echo ""

# Start REPL
run_clojure -M:dev

