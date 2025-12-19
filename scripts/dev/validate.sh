#!/bin/bash
# Script to validate that the application is running correctly on localhost:3000

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

# Configuration
readonly APP_URL="http://localhost:3000"
readonly APP_PORT=3000
readonly TIMEOUT=5

# Change to project root
cd_project_root

# Detect if running in Docker
IS_DOCKER=false
if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^galaticos-app-dev$"; then
    IS_DOCKER=true
    log_info "Detected Docker environment"
fi

log_info "Validating Galáticos application..."
if [[ "$IS_DOCKER" == "true" ]]; then
    log_info "Running in Docker mode"
    log_info "For Docker-specific validation, use: ./bin/galaticos validate:docker"
fi
echo ""

# Check if server is running
log_step "Checking if server is running on port $APP_PORT..."
if ! command_exists curl; then
    log_error "curl is not installed. Please install curl to run validation."
    exit 1
fi

if ! curl -s --max-time $TIMEOUT "$APP_URL/health" >/dev/null 2>&1; then
    log_error "Server is not responding on $APP_URL"
    if [[ "$IS_DOCKER" == "true" ]]; then
        log_info "Make sure Docker containers are running: ./bin/galaticos docker:dev start"
        log_info "Or check container logs: ./bin/galaticos docker:dev logs"
    else
        log_info "Make sure the server is running: ./bin/galaticos run"
    fi
    exit 1
fi
log_success "Server is running"
echo ""

# Test health endpoint
log_step "Testing /health endpoint..."
health_response=$(curl -s --max-time $TIMEOUT "$APP_URL/health")
if echo "$health_response" | grep -q '"status":"ok"'; then
    log_success "Health check passed"
else
    log_error "Health check failed"
    echo "Response: $health_response"
    exit 1
fi
echo ""

# Test root endpoint returns HTML
log_step "Testing root endpoint (/) returns HTML..."
root_response=$(curl -s --max-time $TIMEOUT -I "$APP_URL/" | head -n 1)
content_type=$(curl -s --max-time $TIMEOUT -I "$APP_URL/" | grep -i "content-type" || echo "")

if echo "$root_response" | grep -q "200"; then
    log_success "Root endpoint returns 200 OK"
else
    log_error "Root endpoint did not return 200"
    echo "Response: $root_response"
    exit 1
fi

if echo "$content_type" | grep -qi "text/html"; then
    log_success "Content-Type is text/html (correct)"
else
    log_warning "Content-Type may not be text/html"
    echo "Content-Type header: $content_type"
fi

# Check if HTML contains expected elements
html_body=$(curl -s --max-time $TIMEOUT "$APP_URL/")
if echo "$html_body" | grep -q "<div id=\"app\">"; then
    log_success "HTML contains expected app div"
else
    log_warning "HTML may not contain expected app div"
fi

if echo "$html_body" | grep -q "/js/compiled/app.js"; then
    log_success "HTML references JavaScript file"
else
    log_warning "HTML does not reference JavaScript file"
fi
echo ""

# Test JavaScript file exists and returns JavaScript
log_step "Testing JavaScript file (/js/compiled/app.js)..."
js_response=$(curl -s --max-time $TIMEOUT -I "$APP_URL/js/compiled/app.js" | head -n 1)
js_content_type=$(curl -s --max-time $TIMEOUT -I "$APP_URL/js/compiled/app.js" | grep -i "content-type" || echo "")

if echo "$js_response" | grep -q "200"; then
    log_success "JavaScript file returns 200 OK"
else
    log_error "JavaScript file did not return 200"
    echo "Response: $js_response"
    if [[ "$IS_DOCKER" == "true" ]]; then
        log_info "Make sure ClojureScript was compiled during Docker build"
        log_info "Check Dockerfile.dev or rebuild: ./bin/galaticos docker:dev stop && ./bin/galaticos docker:dev start"
    else
        log_info "Make sure ClojureScript is compiled: clj -M:build:frontend dev"
    fi
    exit 1
fi

if echo "$js_content_type" | grep -qi "application/javascript\|text/javascript\|application/x-javascript"; then
    log_success "Content-Type is JavaScript (correct)"
else
    log_warning "Content-Type may not be JavaScript"
    echo "Content-Type header: $js_content_type"
fi

# Check if JavaScript file has content
js_body=$(curl -s --max-time $TIMEOUT "$APP_URL/js/compiled/app.js" | head -c 100)
if [[ -n "$js_body" && ${#js_body} -gt 10 ]]; then
    log_success "JavaScript file has content"
else
    log_error "JavaScript file appears to be empty or too small"
    exit 1
fi
echo ""

# Verify not downloading files
log_step "Verifying responses are not file downloads..."
if echo "$content_type" | grep -qi "application/octet-stream\|application/zip\|application/x-download"; then
    log_error "Root endpoint is returning a file download instead of HTML!"
    exit 1
else
    log_success "Root endpoint is serving HTML, not downloading files"
fi
echo ""

# Summary
echo ""
log_success "All validation checks passed!"
echo ""
log_info "Application is running correctly on $APP_URL"
log_info "You can now open $APP_URL in your browser"
if [[ "$IS_DOCKER" == "true" ]]; then
    echo ""
    log_info "Note: Running in Docker environment"
    log_info "For detailed Docker validation: ./bin/galaticos validate:docker"
fi
echo ""

