#!/usr/bin/env bash
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAVEN_IMAGE="maven:3.9-eclipse-temurin-21"
M2_VOLUME="storagesign-m2"
COMPOSE_FILE="$ROOT_DIR/e2e/compose.yml"

usage() {
  echo "Usage: $0 unit|integration|e2e [1.21.4|1.21.8|1.21.11]|all" >&2
  exit 2
}

maven_root() {
  docker run --rm \
    -v "$ROOT_DIR:/workspace" \
    -v "$M2_VOLUME:/root/.m2" \
    -w /workspace \
    "$MAVEN_IMAGE" mvn "$@"
}

maven_harness() {
  docker run --rm \
    -v "$ROOT_DIR/e2e/harness:/workspace" \
    -v "$M2_VOLUME:/root/.m2" \
    -w /workspace \
    "$MAVEN_IMAGE" mvn "$@"
}

run_unit() {
  maven_root -DexcludedGroups=integration test
}

run_integration() {
  maven_root -Dgroups=integration test
}

paper_build() {
  case "$1" in
    1.21.4) echo 232 ;;
    1.21.8) echo 60 ;;
    1.21.11) echo 69 ;;
    *) return 1 ;;
  esac
}

wait_for_server() {
  local container_id
  container_id="$(docker compose -f "$COMPOSE_FILE" ps -q server)"
  for _ in $(seq 1 48); do
    if [ "$(docker inspect -f '{{.State.Status}}' "$container_id" 2>/dev/null)" = "exited" ]; then
      return 1
    fi
    if [ "$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}starting{{end}}' "$container_id" 2>/dev/null)" = "healthy" ]; then
      return 0
    fi
    sleep 5
  done
  return 1
}

prepare_e2e_jars() {
  maven_root -DskipTests package || return 1
  maven_harness -DskipTests package || return 1
}

run_e2e_version() {
  local version="$1"
  local build="$2"
  local runtime_dir="$ROOT_DIR/e2e/runtime/$version"
  local artifact_dir="$ROOT_DIR/e2e/artifacts/$version"
  local project="storagesign-e2e-${version//./-}"
  local result=0

  rm -rf "$runtime_dir" "$artifact_dir"
  mkdir -p "$runtime_dir/data" "$runtime_dir/plugins" "$artifact_dir"
  cp "$ROOT_DIR/e2e/config/spigot.yml" "$runtime_dir/data/spigot.yml"
  cp "$ROOT_DIR/target/StorageSign-Refactored-3.0.0.jar" \
     "$runtime_dir/plugins/StorageSign-Refactored.jar"
  cp "$ROOT_DIR/e2e/harness/target/storagesign-e2e-harness-1.0.0.jar" \
     "$runtime_dir/plugins/StorageSignE2EHarness.jar"

  export MC_VERSION="$version"
  export PAPER_BUILD="$build"
  export E2E_DATA_DIR="$runtime_dir/data"
  export E2E_PLUGIN_DIR="$runtime_dir/plugins"
  export E2E_PORT="${E2E_PORT:-25565}"
  export COMPOSE_PROJECT_NAME="$project"

  echo "==> Paper $version build $build"
  docker compose -f "$COMPOSE_FILE" build bot || result=1
  docker compose -f "$COMPOSE_FILE" up -d --build server || result=1
  if [ "$result" -eq 0 ] && ! wait_for_server; then
    echo "Paper $version did not become healthy" >&2
    result=1
  fi

  if [ "$result" -eq 0 ]; then
    E2E_PHASE=main docker compose -f "$COMPOSE_FILE" run --rm bot \
      >"$artifact_dir/bot-main.log" 2>&1 || result=1
  fi

  if [ "$result" -eq 0 ]; then
    docker compose -f "$COMPOSE_FILE" restart server || result=1
    if [ "$result" -eq 0 ] && ! wait_for_server; then result=1; fi
  fi

  if [ "$result" -eq 0 ]; then
    E2E_PHASE=restart docker compose -f "$COMPOSE_FILE" run --rm bot \
      >"$artifact_dir/bot-restart.log" 2>&1 || result=1
  fi

  docker compose -f "$COMPOSE_FILE" logs --no-color server \
    >"$artifact_dir/paper.log" 2>&1 || true
  docker compose -f "$COMPOSE_FILE" down -v --remove-orphans || true

  if [ "$result" -ne 0 ]; then
    echo "Paper $version failed; see $artifact_dir" >&2
  else
    echo "Paper $version passed"
  fi
  return "$result"
}

run_e2e() {
  local requested="${1:-}"
  local versions=(1.21.4 1.21.8 1.21.11)
  local failed=0

  if [ -n "$requested" ]; then
    paper_build "$requested" >/dev/null || usage
    versions=("$requested")
  fi

  prepare_e2e_jars || return 1
  for version in "${versions[@]}"; do
    run_e2e_version "$version" "$(paper_build "$version")" || failed=1
  done
  return "$failed"
}

case "${1:-}" in
  unit) run_unit ;;
  integration) run_integration ;;
  e2e) run_e2e "${2:-}" ;;
  all)
    run_unit && run_integration && run_e2e
    ;;
  *) usage ;;
esac
