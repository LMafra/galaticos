#!/bin/bash
# Build standalone uberjar (depstar alias :uberjar in deps.edn)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

cd_project_root

log_info "Building uberjar (deps.edn alias :uberjar)..."
run_clojure -M:uberjar

log_success "Uberjar: target/galaticos-standalone.jar"
