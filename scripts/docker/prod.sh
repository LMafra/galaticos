#!/bin/bash
# Script to manage Docker production environment

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

# Configuration
readonly COMPOSE_FILE="config/docker/docker-compose.prod.yml"

# Change to project root
cd_project_root

COMMAND="${1:-help}"

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

case "$COMMAND" in
    build)
        log_info "Building production app image (docker build --network host; reliable Clojars on VPS)…"
        exec "$SCRIPT_DIR/prod-vps-build-app.sh" host
        ;;
    start)
        log_info "Starting Docker production environment..."
        $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" up -d || {
            log_error "Failed to start Docker services"
            exit 1
        }
        echo ""
        log_success "Services started!"
        echo ""
        log_info "To view logs: ./bin/galaticos docker:prod logs"
        log_info "To stop: ./bin/galaticos docker:prod stop"
        ;;
    stop)
        log_info "Stopping Docker production environment..."
        $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" down || {
            log_error "Failed to stop Docker services"
            exit 1
        }
        log_success "Services stopped!"
        ;;
    restart)
        log_info "Restarting Docker production environment..."
        $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" restart || {
            log_error "Failed to restart Docker services"
            exit 1
        }
        log_success "Services restarted!"
        ;;
    deploy)
        log_info "Deploy: host-network Docker build + recreate app (MongoDB untouched; avoids Clojars timeouts from compose/BuildKit)…"
        exec "$SCRIPT_DIR/prod-vps-build-app.sh" host-deploy
        ;;
    deploy:clean)
        log_info "Deploy (no cache): host-network Docker build + recreate app…"
        exec "$SCRIPT_DIR/prod-vps-build-app.sh" host-deploy --no-cache
        ;;
    clean)
        log_info "Removing stopped app container and dangling images..."
        $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" rm -f app || true
        docker image prune -f
        log_success "Cleanup complete!"
        ;;
    build:app)
        shift
        exec "$SCRIPT_DIR/prod-vps-build-app.sh" host "$@"
        ;;
    build:app:clean)
        exec "$SCRIPT_DIR/prod-vps-build-app.sh" host --no-cache
        ;;
    deploy:app)
        shift
        exec "$SCRIPT_DIR/prod-vps-build-app.sh" host-deploy "$@"
        ;;
    deploy:app:clean)
        exec "$SCRIPT_DIR/prod-vps-build-app.sh" host-deploy --no-cache
        ;;
    build:app:compose)
        shift
        exec "$SCRIPT_DIR/prod-vps-build-app.sh" compose "$@"
        ;;
    deploy:app:compose)
        shift
        exec "$SCRIPT_DIR/prod-vps-build-app.sh" compose-deploy "$@"
        ;;
    build:vps-host)
        shift
        exec "$SCRIPT_DIR/prod-vps-build-app.sh" host "$@"
        ;;
    deploy:vps-host)
        shift
        exec "$SCRIPT_DIR/prod-vps-build-app.sh" host-deploy "$@"
        ;;
    hint:vps-docker-mtu)
        exec "$SCRIPT_DIR/prod-vps-build-app.sh" mtu-hint
        ;;
    hint:vps-ci-build)
        exec "$SCRIPT_DIR/prod-vps-build-app.sh" ci-hint
        ;;
    logs)
        log_info "Showing Docker logs (Ctrl+C to exit)..."
        $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" logs -f
        ;;
    status)
        log_info "Docker services status:"
        $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" ps
        ;;
    help|*)
        echo "Docker Production Environment Manager"
        echo ""
        echo "Usage: ./bin/galaticos docker:prod [command]"
        echo ""
        echo "Commands:"
        echo "  deploy         - docker build --network host + recreate app (default; Clojars-safe on VPS)"
        echo "  deploy:clean   - Same with --no-cache (deps / Dockerfile changes)"
        echo "  build          - Host-network build of app image only (same Dockerfile as deploy)"
        echo "  build:app      - Same as build (optional --no-cache)"
        echo "  build:app:clean - build:app with --no-cache"
        echo "  deploy:app     - Same as deploy (optional --no-cache)"
        echo "  deploy:app:clean - Same as deploy:clean"
        echo "  build:app:compose   - docker compose build app (bridge; may timeout to Clojars on VPS)"
        echo "  deploy:app:compose  - compose build + recreate app"
        echo "  build:vps-host  - Same as build:app (explicit name)"
        echo "  deploy:vps-host - Same as deploy (override tag: PROD_APP_IMAGE=...)"
        echo "  hint:vps-docker-mtu - print MTU 1400 /etc/docker/daemon.json hint"
        echo "  hint:vps-ci-build   - print CI + registry offload hint"
        echo "  start        - Start all services"
        echo "  stop         - Stop all services"
        echo "  restart      - Restart all services"
        echo "  logs         - Show logs (follow mode)"
        echo "  status       - Show services status"
        echo "  clean        - Remove stopped app container and dangling images"
        echo "  help         - Show this help message"
        echo ""
        echo "MongoDB is never touched by deploy, deploy:clean, or clean."
        ;;
esac

