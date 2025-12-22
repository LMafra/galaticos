#!/bin/bash
# Script to validate that the application is running correctly in Docker

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

# Configuration
readonly COMPOSE_FILE="config/docker/docker-compose.dev.yml"
readonly APP_URL="http://localhost:3000"
readonly APP_CONTAINER="galaticos-app-dev"
readonly MONGO_CONTAINER="galaticos-mongodb-dev"
readonly TIMEOUT=10

# Change to project root
cd_project_root

log_info "Validating Galáticos application in Docker..."
echo ""

# Check if docker-compose is available
if ! command_exists docker-compose && ! docker compose version &>/dev/null; then
    log_error "docker-compose is not installed or not available"
    log_info "Please install Docker Compose: https://docs.docker.com/compose/install/"
    exit 1
fi

# Use docker compose (v2) if available, otherwise docker-compose (v1)
if docker compose version &>/dev/null 2>&1; then
    readonly DOCKER_COMPOSE_CMD="docker compose"
else
    readonly DOCKER_COMPOSE_CMD="docker-compose"
fi

# Check if containers are running
log_step "Checking if Docker containers are running..."
if ! docker ps --format '{{.Names}}' | grep -q "^${APP_CONTAINER}$"; then
    log_error "Application container '${APP_CONTAINER}' is not running"
    log_info "Start containers with: ./bin/galaticos docker:dev start"
    exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -q "^${MONGO_CONTAINER}$"; then
    log_error "MongoDB container '${MONGO_CONTAINER}' is not running"
    log_info "Start containers with: ./bin/galaticos docker:dev start"
    exit 1
fi
log_success "Docker containers are running"
echo ""

# Check container health
log_step "Checking container health..."
app_status=$(docker inspect --format='{{.State.Status}}' "$APP_CONTAINER" 2>/dev/null || echo "unknown")
if [[ "$app_status" != "running" ]]; then
    log_error "Application container is not in 'running' state (current: $app_status)"
    exit 1
fi
log_success "Application container is running"
echo ""

# Run test suite inside the container to ensure parity
log_step "Running test suite inside container..."
if ! docker exec "$APP_CONTAINER" ./bin/galaticos test; then
    log_error "Container test suite failed"
    exit 1
fi
log_success "Container test suite passed"
echo ""

# Wait a bit for the application to be ready
log_step "Waiting for application to be ready..."
sleep 3

# Check if server is responding
log_step "Checking if server is responding on $APP_URL..."
if ! command_exists curl; then
    log_error "curl is not installed. Please install curl to run validation."
    exit 1
fi

if ! curl -s --max-time $TIMEOUT "$APP_URL/health" >/dev/null 2>&1; then
    log_error "Server is not responding on $APP_URL"
    log_info "Checking container logs..."
    docker logs --tail 20 "$APP_CONTAINER" 2>&1 | head -n 10
    exit 1
fi
log_success "Server is responding"
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
    log_info "Checking if JavaScript file exists in container..."
    if docker exec "$APP_CONTAINER" test -f "/app/resources/public/js/compiled/app.js" 2>/dev/null; then
        log_info "JavaScript file exists in container"
    else
        log_error "JavaScript file does not exist in container"
        log_info "ClojureScript may need to be compiled. Check Dockerfile.dev build step."
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

# Check if JavaScript file exists in container
log_step "Verifying JavaScript file exists in container..."
if docker exec "$APP_CONTAINER" test -f "/app/resources/public/js/compiled/app.js" 2>/dev/null; then
    log_success "JavaScript file exists in container"
    file_size=$(docker exec "$APP_CONTAINER" stat -c%s "/app/resources/public/js/compiled/app.js" 2>/dev/null || echo "0")
    if [[ "$file_size" -gt 1000 ]]; then
        log_success "JavaScript file has reasonable size ($file_size bytes)"
    else
        log_warning "JavaScript file seems small ($file_size bytes)"
    fi
else
    log_warning "JavaScript file not found in container (may be served from volume)"
fi
echo ""

# Check container logs for errors
log_step "Checking container logs for errors..."
recent_errors=$(docker logs --tail 50 "$APP_CONTAINER" 2>&1 | grep -i "error\|exception\|failed" | head -n 5 || true)
if [[ -n "$recent_errors" ]]; then
    log_warning "Found recent errors in container logs:"
    echo "$recent_errors" | while IFS= read -r line; do
        echo "  $line"
    done
else
    log_success "No recent errors found in container logs"
fi
echo ""

# Summary
echo ""
log_success "All Docker validation checks passed!"
echo ""
log_info "Application is running correctly in Docker on $APP_URL"
log_info "You can now open $APP_URL in your browser"
echo ""
log_info "Container information:"
echo "   App container: $APP_CONTAINER"
echo "   MongoDB container: $MONGO_CONTAINER"
echo ""
log_info "To view logs: ./bin/galaticos docker:dev logs"
log_info "To stop: ./bin/galaticos docker:dev stop"
echo ""

