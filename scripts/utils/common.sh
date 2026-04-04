#!/bin/bash
# Common utility functions for Galáticos scripts
# Source this file in other scripts: source "$(dirname "$0")/utils/common.sh"

# Colors for output (if terminal supports it)
if [[ -t 1 ]]; then
    readonly RED='\033[0;31m'
    readonly GREEN='\033[0;32m'
    readonly YELLOW='\033[1;33m'
    readonly BLUE='\033[0;34m'
    readonly MAGENTA='\033[0;35m'
    readonly CYAN='\033[0;36m'
    readonly NC='\033[0m' # No Color
else
    readonly RED=''
    readonly GREEN=''
    readonly YELLOW=''
    readonly BLUE=''
    readonly MAGENTA=''
    readonly CYAN=''
    readonly NC=''
fi

# Logging functions
log_info() {
    echo -e "${BLUE}ℹ${NC} $*" >&2
}

log_success() {
    echo -e "${GREEN}✓${NC} $*" >&2
}

log_warning() {
    echo -e "${YELLOW}⚠${NC}  $*" >&2
}

log_error() {
    echo -e "${RED}✗${NC} $*" >&2
}

log_step() {
    echo -e "${CYAN}→${NC} $*" >&2
}

# Section header (stderr, like other log_* helpers)
log_header() {
    local title="$1"
    local width="${2:-60}"
    echo "" >&2
    echo "=$(printf '=%.0s' $(seq 1 "$width"))" >&2
    echo "$title" >&2
    echo "=$(printf '=%.0s' $(seq 1 "$width"))" >&2
    echo "" >&2
}

# Get project root directory (cached)
get_project_root() {
    if [[ -z "${PROJECT_ROOT:-}" ]]; then
        local script_dir
        script_dir="$(cd "$(dirname "${BASH_SOURCE[1]}")" && pwd)"
        PROJECT_ROOT="$(cd "$script_dir/../.." && pwd)"
    fi
    echo "$PROJECT_ROOT"
}

# Change to project root
cd_project_root() {
    cd "$(get_project_root)" || exit 1
}

# Check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Check if MongoDB is running (local or Docker)
is_mongodb_running() {
    pgrep -x "mongod" >/dev/null 2>&1 || docker ps --format '{{.Names}}' 2>/dev/null | grep -q "galaticos-mongodb"
}

# Check if Clojure CLI is available
check_clojure_cli() {
    if command_exists clojure; then
        echo "clojure"
    elif command_exists clj; then
        echo "clj"
    else
        return 1
    fi
}

# Run Clojure command with fallback
run_clojure() {
    local clj_cmd
    clj_cmd=$(check_clojure_cli)
    if [[ -z "$clj_cmd" ]]; then
        log_error "Neither 'clojure' nor 'clj' command found."
        log_info "Please install Clojure CLI tools: https://clojure.org/guides/install_clojure"
        return 1
    fi
    "$clj_cmd" "$@"
}

# Check if Python virtual environment exists and activate it
activate_python_venv() {
    local project_root
    project_root=$(get_project_root)
    local venv_path="$project_root/venv"
    
    if [[ ! -d "$venv_path" ]]; then
        log_warning "Python virtual environment not found at: $venv_path"
        log_step "Creating Python virtual environment..."
        python3 -m venv "$venv_path" || return 1
        log_success "Created virtual environment"
    fi
    
    # Activate virtual environment
    if [[ -f "$venv_path/bin/activate" ]]; then
        # shellcheck source=/dev/null
        source "$venv_path/bin/activate" || return 1
        return 0
    else
        log_error "Virtual environment activation script not found"
        return 1
    fi
}

# Install Python dependencies from requirements.txt
install_python_deps() {
    local project_root
    project_root=$(get_project_root)
    local requirements_file="$project_root/requirements.txt"
    
    if [[ ! -f "$requirements_file" ]]; then
        log_warning "requirements.txt not found at: $requirements_file"
        return 1
    fi
    
    log_step "Installing Python dependencies..."
    pip install -q -r "$requirements_file" || return 1
    log_success "Python dependencies installed"
    return 0
}

# Check if we can run Python (either local python3+venv or Docker)
python_available() {
    if command_exists python3; then
        activate_python_venv 2>/dev/null && install_python_deps 2>/dev/null && return 0
    fi
    return 1
}

# Run a Python script via Docker when Python is not available locally.
# Usage: run_python_in_docker <script_path> [args...]
# Expects MONGO_URI and DB_NAME to be set in the environment (for scripts that need them).
run_python_in_docker() {
    local script_path="$1"
    shift
    local project_root
    project_root=$(get_project_root)
    if [[ ! -f "$project_root/$script_path" ]]; then
        log_error "Script not found: $script_path"
        return 1
    fi
    docker run --rm \
        --network host \
        -e MONGO_URI="${MONGO_URI:-mongodb://localhost:27017}" \
        -e DB_NAME="${DB_NAME:-galaticos}" \
        -e EXCEL_FILE="${EXCEL_FILE:-}" \
        -v "$project_root:/app" \
        -w /app \
        python:3.11-slim \
        bash -c "pip install -q --no-cache-dir -r requirements.txt && exec python $script_path \"\$@\"" _ "$@"
    return $?
}

# Check MongoDB connection with timeout
check_mongodb_connection() {
    local uri="${1:-mongodb://localhost:27017}"
    
    if command_exists mongosh; then
        # mongosh: try to connect and run a simple command
        # Use --eval with a simple expression that will succeed if connected
        mongosh "$uri" --quiet --eval "db.adminCommand('ping')" >/dev/null 2>&1
        return $?
    elif command_exists mongo; then
        # mongo: older client, use eval with ping
        mongo "$uri" --quiet --eval "db.adminCommand('ping')" >/dev/null 2>&1
        return $?
    else
        # Fallback: just check if process is running
        is_mongodb_running
    fi
}

# Print script header
print_header() {
    local title="$1"
    local width="${2:-60}"
    echo ""
    echo "=$(printf '=%.0s' $(seq 1 $width))"
    echo "$title"
    echo "=$(printf '=%.0s' $(seq 1 $width))"
    echo ""
}

# Print section separator
print_section() {
    local title="$1"
    echo ""
    echo "--- $title ---"
    echo ""
}

