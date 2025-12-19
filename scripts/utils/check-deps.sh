#!/bin/bash
# Script to check if all required dependencies are installed

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

log_info "Checking dependencies..."
echo ""

MISSING_DEPS=0

# Check Clojure CLI (prefer clojure over clj)
if command_exists clojure; then
    CLJ_VERSION=$(clojure --version 2>&1 | head -n 1)
    log_success "Clojure CLI: $CLJ_VERSION (using 'clojure' command)"
elif command_exists clj; then
    CLJ_VERSION=$(clj --version 2>&1 | head -n 1)
    log_warning "Clojure CLI: $CLJ_VERSION (using 'clj' - consider installing rlwrap for better REPL)"
    log_info "Note: Scripts will use 'clojure' command if available to avoid rlwrap requirement"
else
    log_error "Clojure CLI: Not found"
    log_info "Install from: https://clojure.org/guides/install_clojure"
    MISSING_DEPS=1
fi

# Check Java
if command_exists java; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1)
    log_success "Java: $JAVA_VERSION"
else
    log_error "Java: Not found"
    log_info "Install Java 11 or higher"
    MISSING_DEPS=1
fi

# Check Python
if command_exists python3; then
    PYTHON_VERSION=$(python3 --version)
    log_success "Python: $PYTHON_VERSION"
else
    log_error "Python 3: Not found"
    log_info "Install Python 3.8 or higher"
    MISSING_DEPS=1
fi

# Check MongoDB (local or Docker)
if is_mongodb_running || command_exists mongod; then
    log_success "MongoDB: Available"
else
    log_warning "MongoDB: Not running locally"
    log_info "You can use Docker: ./bin/galaticos docker:dev start"
fi

# Check Docker (optional but recommended)
if command_exists docker; then
    DOCKER_VERSION=$(docker --version)
    log_success "Docker: $DOCKER_VERSION"
else
    log_warning "Docker: Not found (optional, but recommended)"
fi

echo ""

if [[ $MISSING_DEPS -eq 0 ]]; then
    log_success "All required dependencies are installed!"
    exit 0
else
    log_error "Some dependencies are missing. Please install them before proceeding."
    exit 1
fi

