#!/bin/bash
# Script to manage Docker development environment

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

# Configuration
readonly COMPOSE_FILE="docker-compose.dev.yml"
readonly MONGO_URI="mongodb://localhost:27017/galaticos"
readonly APP_URL="http://localhost:3000"

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

# Variable to track if we should stop services on exit (for run command)
SHOULD_STOP_ON_EXIT=false

# Trap to handle cleanup on exit
cleanup() {
    local exit_code=$?
    if [[ "$SHOULD_STOP_ON_EXIT" == "true" ]]; then
        echo ""
        log_info "Stopping services..."
        $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" down --remove-orphans >/dev/null 2>&1 || true
        log_success "Services stopped!"
    fi
    exit $exit_code
}

case "$COMMAND" in
    start)
        log_info "Starting Docker development environment..."
        $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" up -d || {
            log_error "Failed to start Docker services"
            exit 1
        }
        echo ""
        log_success "Services started!"
        echo "   MongoDB: $MONGO_URI"
        echo "   App: $APP_URL"
        echo ""
        log_info "To view logs: ./bin/galaticos docker:dev logs [service]"
        log_info "To run with logs: ./bin/galaticos docker:dev run [service]"
        log_info "To stop: ./bin/galaticos docker:dev stop"
        ;;
    run)
        # Set trap only for run command
        trap cleanup EXIT INT TERM
        
        log_info "Starting Docker development environment and showing logs..."
        log_info "Press Ctrl+C to stop all services"
        echo ""
        
        # Check if services are already running
        local running_services
        running_services=$($DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" ps --services --filter "status=running" 2>/dev/null || echo "")
        
        if [[ -z "$running_services" ]]; then
            log_step "Starting services..."
            $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" up -d || {
                log_error "Failed to start Docker services"
                exit 1
            }
            SHOULD_STOP_ON_EXIT=true
            # Give services a moment to start
            sleep 2
        else
            log_info "Services already running, following logs only..."
            SHOULD_STOP_ON_EXIT=false
        fi
        
        # Show logs - filter by service if provided
        SERVICE="${2:-}"
        if [[ -n "$SERVICE" ]]; then
            log_info "Following logs for service: $SERVICE"
            $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" logs -f --tail=100 "$SERVICE"
        else
            log_info "Following logs for all services..."
            $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" logs -f --tail=100
        fi
        ;;
    stop)
        log_info "Stopping Docker development environment..."
        $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" down --remove-orphans || {
            log_error "Failed to stop Docker services"
            exit 1
        }
        log_success "Services stopped!"
        ;;
    restart)
        log_info "Restarting Docker development environment..."
        $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" restart || {
            log_error "Failed to restart Docker services"
            exit 1
        }
        log_success "Services restarted!"
        ;;
    logs)
        # Set trap for logs command too (but don't stop services, just exit cleanly)
        trap 'exit 0' INT TERM
        
        SERVICE="${2:-}"
        if [[ -n "$SERVICE" ]]; then
            log_info "Showing logs for service: $SERVICE (Ctrl+C to exit)..."
            $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" logs -f --tail=100 "$SERVICE"
        else
            log_info "Showing logs for all services (Ctrl+C to exit)..."
            log_info "Tip: Specify a service name to filter (e.g., app, mongodb, frontend-watch)"
            echo ""
            $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" logs -f --tail=100
        fi
        ;;
    status)
        log_info "Docker services status:"
        $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" ps
        ;;
    validate)
        exec "$SCRIPT_DIR/docker/validate.sh"
        ;;
    clean)
        log_warning "Cleaning Docker volumes and containers..."
        read -p "This will delete all data. Are you sure? (y/N) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" down -v || {
                log_error "Failed to clean Docker resources"
                exit 1
            }
            log_success "Cleaned!"
        else
            log_info "Cancelled."
        fi
        ;;
    help|*)
        echo "Docker Development Environment Manager"
        echo ""
        echo "Usage: ./bin/galaticos docker:dev [command] [service]"
        echo ""
        echo "Commands:"
        echo "  start    - Start all services in background"
        echo "  run      - Start services and show logs (Ctrl+C stops all)"
        echo "  stop     - Stop all services"
        echo "  restart  - Restart all services"
        echo "  logs     - Show logs (follow mode), optionally filtered by service"
        echo "  status   - Show services status"
        echo "  validate - Validate application is running correctly"
        echo "  clean    - Remove containers and volumes"
        echo "  help     - Show this help message"
        echo ""
        echo "Services:"
        echo "  mongodb, app, frontend-watch"
        echo ""
        echo "Examples:"
        echo "  ./bin/galaticos docker:dev run              # Start and follow all logs"
        echo "  ./bin/galaticos docker:dev run app          # Start and follow app logs"
        echo "  ./bin/galaticos docker:dev logs mongodb     # Follow MongoDB logs"
        ;;
esac

