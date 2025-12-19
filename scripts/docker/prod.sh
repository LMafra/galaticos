#!/bin/bash
# Script to manage Docker production environment

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

# Configuration
readonly COMPOSE_FILE="docker-compose.prod.yml"

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
        log_info "Building production Docker images..."
        $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" build || {
            log_error "Failed to build Docker images"
            exit 1
        }
        log_success "Build complete!"
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
        echo "  build    - Build production images"
        echo "  start    - Start all services"
        echo "  stop     - Stop all services"
        echo "  restart  - Restart all services"
        echo "  logs     - Show logs (follow mode)"
        echo "  status   - Show services status"
        echo "  help     - Show this help message"
        ;;
esac

