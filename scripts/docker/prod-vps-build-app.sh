#!/usr/bin/env bash
# VPS helpers for production app image build when Clojars times out from Docker.
# Prefer `docker build --network host` for the full Dockerfile: `docker compose build` can still
# use the bridge for some BuildKit steps even with compose `build.network: host`, so `clj -P` may
# succeed while a later `clj -M:build:frontend` times out to repo.clojars.org.
# See docs/informacao/operacao/vps-hospedeiro.md §4.
#
# Usage (from repo root):
#   ./scripts/docker/prod-vps-build-app.sh compose [--no-cache]
#   ./scripts/docker/prod-vps-build-app.sh compose-deploy [--no-cache]
#   ./scripts/docker/prod-vps-build-app.sh host [--no-cache]
#   ./scripts/docker/prod-vps-build-app.sh host-deploy [--no-cache]
#   ./scripts/docker/prod-vps-build-app.sh mtu-hint
#   ./scripts/docker/prod-vps-build-app.sh ci-hint
#
# Override image tag for host / host-deploy when Compose cannot resolve it:
#   PROD_APP_IMAGE=galaticos-app:latest ./scripts/docker/prod-vps-build-app.sh host-deploy

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../utils/common.sh
source "$SCRIPT_DIR/../utils/common.sh"

readonly COMPOSE_FILE="${COMPOSE_FILE:-config/docker/docker-compose.prod.yml}"
readonly APP_CONTAINER_NAME="${APP_CONTAINER_NAME:-galaticos-app-prod}"

docker_compose() {
  if docker compose version &>/dev/null 2>&1; then
    docker compose "$@"
  else
    docker-compose "$@"
  fi
}

# Image tag Compose will use for the built app service (for docker build -t …).
resolve_app_image_tag() {
  if [[ -n "${PROD_APP_IMAGE:-}" ]]; then
    echo "$PROD_APP_IMAGE"
    return
  fi
  if docker inspect "$APP_CONTAINER_NAME" &>/dev/null; then
    docker inspect "$APP_CONTAINER_NAME" --format '{{.Config.Image}}'
    return
  fi
  local line img=""
  if docker_compose -f "$COMPOSE_FILE" config --images &>/dev/null; then
    while IFS= read -r line; do
      [[ -z "${line// }" ]] && continue
      [[ "$line" == mongo:* ]] && continue
      img="$line"
      break
    done < <(docker_compose -f "$COMPOSE_FILE" config --images 2>/dev/null) || true
  fi
  if [[ -n "$img" ]]; then
    echo "$img"
    return
  fi
  # Compose default when project name is the directory holding the compose file (e.g. config/docker → docker-app).
  local proj
  proj="$(basename "$(cd "$(dirname "$COMPOSE_FILE")" && pwd)" | tr '[:upper:]' '[:lower:]')"
  log_warning "Could not resolve tag from container or \`compose config --images\`; using ${proj}-app:latest (override with PROD_APP_IMAGE=... if wrong)."
  echo "${proj}-app:latest"
}

show_help() {
  cat <<'EOF'
VPS production app build helpers (Clojars / Docker bridge timeouts).

  compose          docker compose build app (bridge; VPS: pode dar timeout a meio do Dockerfile)
  compose-deploy   compose + recreate app only (MongoDB untouched)
  host             docker build --network host … Dockerfile.prod
  host-deploy      host + recreate app only
  mtu-hint         print /etc/docker/daemon.json MTU 1400 fragment + restart hint
  ci-hint          print off-VPS build / registry workflow hint

Environment:
  PROD_APP_IMAGE     Explicit -t for host / host-deploy when auto-resolve fails
  COMPOSE_FILE       Default: config/docker/docker-compose.prod.yml
  APP_CONTAINER_NAME Default: galaticos-app-prod

Docs: docs/informacao/operacao/vps-hospedeiro.md §4
EOF
}

cmd_compose() {
  cd_project_root
  local -a extra=()
  for a in "$@"; do
    case "$a" in
      --no-cache) extra+=(--no-cache) ;;
      -h|--help) show_help; exit 0 ;;
      *)
        log_error "Unknown option: $a"
        show_help
        exit 1
        ;;
    esac
  done
  log_step "docker compose -f $COMPOSE_FILE build ${extra[*]:-} app"
  docker_compose -f "$COMPOSE_FILE" build "${extra[@]}" app
  log_success "Compose build finished."
}

cmd_compose_deploy() {
  cmd_compose "$@"
  log_step "Recreating app container (MongoDB untouched)…"
  docker_compose -f "$COMPOSE_FILE" up -d --force-recreate --no-deps app
  log_success "App container recreated."
}

cmd_host() {
  cd_project_root
  local -a extra=()
  for a in "$@"; do
    case "$a" in
      --no-cache) extra+=(--no-cache) ;;
      -h|--help) show_help; exit 0 ;;
      *)
        log_error "Unknown option: $a"
        show_help
        exit 1
        ;;
    esac
  done
  local tag
  tag="$(resolve_app_image_tag)"
  log_info "Image tag: $tag"
  log_step "docker build --network host ${extra[*]:-} -f config/docker/Dockerfile.prod -t \"$tag\" ."
  DOCKER_BUILDKIT=1 docker build --network host "${extra[@]}" \
    -f config/docker/Dockerfile.prod \
    -t "$tag" \
    .
  log_success "Host-network build finished."
}

cmd_host_deploy() {
  cmd_host "$@"
  log_step "Recreating app container…"
  docker_compose -f "$COMPOSE_FILE" up -d --force-recreate --no-deps app
  log_success "App container recreated."
}

cmd_mtu_hint() {
  log_header "Docker MTU workaround (vps-hospedeiro.md §4 — Opção B)"
  cat <<'EOF'
Some VPSes need a lower Docker bridge MTU so HTTPS to Maven/Clojars works.

1. Edit (or create) /etc/docker/daemon.json — merge with existing keys; example:

   { "mtu": 1400 }

2. Restart Docker:

   sudo systemctl restart docker

3. Retry your build (compose or ./bin/galaticos docker:prod deploy:vps-host).

If the file already has other settings, combine into one JSON object (e.g. log-driver + mtu).
EOF
}

cmd_ci_hint() {
  log_header "Build off the VPS (vps-hospedeiro.md §4 — Opção C)"
  cat <<'EOF'
Avoid Clojars/Maven from the VPS entirely:

1. In CI (e.g. GitHub Actions), run docker build with network adequate for the runner,
   then docker push to your registry (GHCR, Docker Hub, etc.).

2. On the VPS: set `image:` on the `app` service to that registry URL (or pull + tag
   to the name Compose expects), then:

   docker compose -f config/docker/docker-compose.prod.yml pull app
   docker compose -f config/docker/docker-compose.prod.yml up -d --force-recreate --no-deps app

Keep MongoDB volume untouched; do not use `down -v` in production.
EOF
}

main() {
  local sub="${1:-help}"
  shift || true
  case "$sub" in
    compose) cmd_compose "$@" ;;
    compose-deploy) cmd_compose_deploy "$@" ;;
    host) cmd_host "$@" ;;
    host-deploy) cmd_host_deploy "$@" ;;
    mtu-hint) cmd_mtu_hint ;;
    ci-hint) cmd_ci_hint ;;
    help|-h|--help) show_help ;;
    *)
      log_error "Unknown command: $sub"
      show_help
      exit 1
      ;;
  esac
}

main "$@"
