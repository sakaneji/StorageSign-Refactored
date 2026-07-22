#!/usr/bin/env bash
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAVEN_IMAGE="maven:3.9-eclipse-temurin-21"
M2_VOLUME="storagesign-m2"
COMPOSE_FILE="$ROOT_DIR/e2e/compose.yml"
TEST_LOG_DIR="$ROOT_DIR/target/test-artifacts"
TEST_VERBOSE="${STORAGESIGN_TEST_VERBOSE:-0}"
FAILURE_TAIL_LINES="${STORAGESIGN_FAILURE_TAIL_LINES:-40}"
TIMING_FILE="${STORAGESIGN_TIMING_FILE:-$TEST_LOG_DIR/e2e-timings.tsv}"
TIMING_UNKNOWN_POOL_SECONDS=180
TIMING_INITIAL_BUFFER_SECONDS=30
DEFAULT_E2E_VERSIONS=(1.21.4 1.21.8 1.21.11)
SUPPORTED_E2E_VERSIONS=(1.21.4 1.21.8 1.21.11 26.1.2 26.2)

usage() {
  cat >&2 <<'EOF'
Usage:
  ./scripts/test.sh unit
  ./scripts/test.sh integration
  ./scripts/test.sh coverage
  ./scripts/test.sh e2e [1.21.4|1.21.8|1.21.11|26.1.2|26.2] [both|with-logger|without-logger]
  ./scripts/test.sh banner-compat [1.21.11|all]
  ./scripts/test.sh all
EOF
  exit 2
}

initialize_timing_cache() {
  mkdir -p "$(dirname "$TIMING_FILE")"
  if [ -f "$TIMING_FILE" ] && ! awk -F '\t' '
    NF != 3 || $1 == "" || $2 !~ /^[0-9]+$/ || $2 < 1 \
      || $3 !~ /^[0-9]+$/ || $3 < 1 { exit 1 }
  ' "$TIMING_FILE"; then
    local invalid_file="$TIMING_FILE.invalid.$(date +%s)"
    mv "$TIMING_FILE" "$invalid_file"
    echo "WARN timing-cache invalid=$invalid_file fallback_seconds=$TIMING_UNKNOWN_POOL_SECONDS" >&2
  fi
  [ -f "$TIMING_FILE" ] || : >"$TIMING_FILE"
}

timing_lookup() {
  local key="$1"
  awk -F '\t' -v key="$key" '$1 == key { print $2; found=1; exit } END { if (!found) exit 1 }' \
    "$TIMING_FILE"
}

timing_record() {
  local key="$1"
  local actual_seconds="$2"
  [ "$actual_seconds" -ge 1 ] || actual_seconds=1
  local temp_file
  temp_file="$(mktemp "$TIMING_FILE.tmp.XXXXXX")" || return 1
  awk -F '\t' -v OFS='\t' -v key="$key" -v actual="$actual_seconds" '
    $1 == key {
      samples = $3 + 1
      average = int((($2 * $3) + actual) / samples + 0.5)
      print key, average, samples
      found = 1
      next
    }
    { print }
    END { if (!found) print key, actual, 1 }
  ' "$TIMING_FILE" >"$temp_file" && mv "$temp_file" "$TIMING_FILE"
}

timing_record_if_success() {
  local key="$1"
  local actual_seconds="$2"
  local status="$3"
  [ "$status" -eq 0 ] || return 0
  timing_record "$key" "$actual_seconds"
}

timing_estimate() {
  local total=0
  local unknown=0
  local key seconds
  for key in "$@"; do
    if seconds="$(timing_lookup "$key" 2>/dev/null)"; then
      total=$((total + seconds))
    else
      unknown=1
    fi
  done
  if [ "$unknown" -eq 1 ]; then
    total=$((total + TIMING_UNKNOWN_POOL_SECONDS))
    echo "$total fallback"
  else
    echo "$total saved"
  fi
}

emit_wait_hint() {
  local scope="$1"
  local stage="$2"
  local completed="$3"
  local total="$4"
  local buffer_seconds="$5"
  shift 5
  local remaining="$#"
  [ "$remaining" -gt 0 ] || return 0
  local estimate source
  read -r estimate source <<<"$(timing_estimate "$@")"
  echo "WAIT_HINT scope=$scope stage=$stage completed=$completed total=$total remaining=$remaining estimate_seconds=$estimate wait_seconds=$((estimate + buffer_seconds)) source=$source"
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

run_logged() {
  local label="$1"
  local log_file="$2"
  shift 2
  mkdir -p "$(dirname "$log_file")"
  if [ "$TEST_VERBOSE" = "1" ]; then
    "$@" 2>&1 | tee "$log_file"
    return "${PIPESTATUS[0]}"
  fi
  "$@" >"$log_file" 2>&1
  local status=$?
  if [ "$status" -eq 0 ]; then
    return 0
  fi
  echo "FAIL $label; log=$log_file" >&2
  echo "--- last $FAILURE_TAIL_LINES lines ---" >&2
  tail -n "$FAILURE_TAIL_LINES" "$log_file" >&2 || true
  return "$status"
}

run_step() {
  local log_file="$1"
  shift
  if [ "$TEST_VERBOSE" = "1" ]; then
    "$@" 2>&1 | tee -a "$log_file"
    return "${PIPESTATUS[0]}"
  fi
  "$@" >>"$log_file" 2>&1
}

summarize_surefire() {
  local scope="$1"
  local report_dir="$ROOT_DIR/target/surefire-reports"
  local tests=0 failures=0 errors=0 skipped=0 found=0 line value
  for report in "$report_dir"/TEST-*.xml; do
    [ -f "$report" ] || continue
    line="$(grep -m 1 '<testsuite ' "$report")"
    [ -n "$line" ] || continue
    found=1
    value="$(sed -E 's/.* tests="([0-9]+)".*/\1/' <<<"$line")"; tests=$((tests + value))
    value="$(sed -E 's/.* failures="([0-9]+)".*/\1/' <<<"$line")"; failures=$((failures + value))
    value="$(sed -E 's/.* errors="([0-9]+)".*/\1/' <<<"$line")"; errors=$((errors + value))
    value="$(sed -E 's/.* skipped="([0-9]+)".*/\1/' <<<"$line")"; skipped=$((skipped + value))
  done
  if [ "$found" -ne 1 ]; then
    echo "FAIL $scope; Surefire reports were not created" >&2
    return 1
  fi
  echo "PASS $scope tests=$tests failures=$failures errors=$errors skipped=$skipped"
}

run_unit() {
  rm -rf "$ROOT_DIR/target/surefire-reports"
  run_logged python-tools "$TEST_LOG_DIR/python-tools.log" \
    python3 -m unittest discover -s "$ROOT_DIR/tools/tests" -p 'test_*.py' || return 1
  echo "PASS python-tools"
  run_logged unit "$TEST_LOG_DIR/unit.log" \
    maven_root -DexcludedGroups=integration test || return 1
  run_logged runner-selftest "$TEST_LOG_DIR/runner-selftest.log" \
    bash "$ROOT_DIR/scripts/test-runner-selftest.sh" || return 1
  summarize_surefire unit
}

run_integration() {
  rm -rf "$ROOT_DIR/target/surefire-reports"
  run_logged integration "$TEST_LOG_DIR/integration.log" \
    maven_root -Dgroups=integration test || return 1
  summarize_surefire integration
}

summarize_coverage() {
  local csv="$ROOT_DIR/target/site/jacoco/jacoco.csv"
  [ -s "$csv" ] || { echo "FAIL coverage; report=$csv was not created" >&2; return 1; }
  awk -F, '
    NR > 1 {
      lineMissed += $8; lineCovered += $9;
      branchMissed += $6; branchCovered += $7
    }
    END {
      lineTotal = lineMissed + lineCovered;
      branchTotal = branchMissed + branchCovered;
      linePct = lineTotal ? (100 * lineCovered / lineTotal) : 0;
      branchPct = branchTotal ? (100 * branchCovered / branchTotal) : 0;
      printf "PASS coverage lines=%.1f%% branches=%.1f%% report=target/site/jacoco/index.html\n", linePct, branchPct
    }
  ' "$csv"
}

run_coverage() {
  rm -rf "$ROOT_DIR/target/surefire-reports" "$ROOT_DIR/target/site/jacoco" "$ROOT_DIR/target/jacoco.exec"
  run_logged coverage "$TEST_LOG_DIR/coverage.log" maven_root -Pcoverage test || return 1
  summarize_surefire coverage || return 1
  summarize_coverage
}

paper_build() {
  case "$1" in
    1.21.4) echo 232 ;;
    1.21.8) echo 60 ;;
    1.21.11) echo 69 ;;
    26.1.2) echo 72 ;;
    26.2) echo 24 ;;
    *) return 1 ;;
  esac
}

minecraft_server_image() {
  case "$1" in
    26.*) echo "itzg/minecraft-server:java25" ;;
    *) echo "itzg/minecraft-server:java21" ;;
  esac
}

wait_for_server() {
  local container_id
  container_id="$(docker compose -f "$COMPOSE_FILE" ps -q server)"
  [ -n "$container_id" ] || return 1
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
  rm -rf "$ROOT_DIR/target/e2e-deps"
  run_logged "E2E plugin build" "$TEST_LOG_DIR/e2e-plugin-build.log" \
    maven_root -DskipTests package \
    org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies \
    -DincludeGroupIds=com.github.teruteru128 \
    -DincludeArtifactIds=logger \
    -DoutputDirectory=/workspace/target/e2e-deps || return 1
  run_logged "E2E harness build" "$TEST_LOG_DIR/e2e-harness-build.log" \
    maven_harness -DskipTests package || return 1
  if ! find "$ROOT_DIR/target/e2e-deps" -name 'logger-*.jar' -type f | grep -q .; then
    echo "FAIL E2E dependency preparation; Logger jar was not copied" >&2
    return 1
  fi
}

copy_e2e_plugins() {
  local plugin_dir="$1"
  local logger_mode="$2"
  cp "$ROOT_DIR/target/StorageSign-Refactored-3.0.0.jar" \
     "$plugin_dir/StorageSign-Refactored.jar"
  cp "$ROOT_DIR/e2e/harness/target/storagesign-e2e-harness-1.0.0.jar" \
     "$plugin_dir/StorageSignE2EHarness.jar"
  if [ "$logger_mode" = "with-logger" ]; then
    local logger_jar
    logger_jar="$(find "$ROOT_DIR/target/e2e-deps" -name 'logger-*.jar' -type f -print -quit)"
    [ -n "$logger_jar" ] || return 1
    cp "$logger_jar" "$plugin_dir/Logger.jar"
  fi
}

capture_server_log() {
  local output="$1"
  docker compose -f "$COMPOSE_FILE" logs --no-color server >"$output" 2>&1 || true
}

run_e2e_version() {
  local version="$1"
  local build="$2"
  local logger_mode="$3"
  local runtime_dir="$ROOT_DIR/e2e/runtime/$version/$logger_mode"
  local artifact_dir="$ROOT_DIR/e2e/artifacts/$version/$logger_mode"
  local runner_log="$artifact_dir/runner.log"
  local project="storagesign-e2e-${version//./-}-${logger_mode}"
  local result=0

  rm -rf "$runtime_dir" "$artifact_dir"
  mkdir -p "$runtime_dir/data" "$runtime_dir/plugins" "$artifact_dir"
  : >"$runner_log"
  cp "$ROOT_DIR/e2e/config/spigot.yml" "$runtime_dir/data/spigot.yml"
  copy_e2e_plugins "$runtime_dir/plugins" "$logger_mode" || result=1

  export MC_VERSION="$version"
  export PAPER_BUILD="$build"
  export MC_SERVER_IMAGE="$(minecraft_server_image "$version")"
  export E2E_DATA_DIR="$runtime_dir/data"
  export E2E_PLUGIN_DIR="$runtime_dir/plugins"
  export E2E_PORT="${E2E_PORT:-25565}"
  export LOGGER_MODE="$logger_mode"
  export COMPOSE_PROJECT_NAME="$project"

  if [ "$result" -eq 0 ]; then
    run_step "$runner_log" docker compose -f "$COMPOSE_FILE" build bot || result=1
  fi
  if [ "$result" -eq 0 ]; then
    echo "WAIT_HINT scope=e2e stage=minecraft-startup version=$version logger=$logger_mode estimate_seconds=60 wait_seconds=60 source=fixed"
    run_step "$runner_log" docker compose -f "$COMPOSE_FILE" up -d --build server || result=1
  fi
  if [ "$result" -eq 0 ] && ! wait_for_server; then
    echo "Paper server did not become healthy" >>"$runner_log"
    result=1
  fi

  if [ "$result" -eq 0 ]; then
    E2E_PHASE=main docker compose -f "$COMPOSE_FILE" run --rm bot \
      >"$artifact_dir/bot-main.log" 2>&1 || result=1
  fi

  if [ "$result" -eq 0 ]; then
    run_step "$runner_log" docker compose -f "$COMPOSE_FILE" restart server || result=1
    if [ "$result" -eq 0 ] && ! wait_for_server; then result=1; fi
  fi

  if [ "$result" -eq 0 ]; then
    E2E_PHASE=restart docker compose -f "$COMPOSE_FILE" run --rm bot \
      >"$artifact_dir/bot-restart.log" 2>&1 || result=1
  fi

  capture_server_log "$artifact_dir/paper.log"
  if [ "$logger_mode" = "with-logger" ] \
     && grep -q "外部 Logger の初期化に失敗" "$artifact_dir/paper.log"; then
    echo "External Logger initialization failed on Paper $version" >&2
    result=1
  fi
  if [ "$logger_mode" = "with-logger" ] \
     && ! grep -Eq '\[StorageSignPlugin(Bootstrap)?#onEnable\] StorageSign enabled\.' "$artifact_dir/paper.log"; then
    echo "StorageSign message did not reach the external Logger sink on Paper $version" >&2
    result=1
  fi
  run_step "$runner_log" docker compose -f "$COMPOSE_FILE" down -v --remove-orphans || true

  if [ "$result" -ne 0 ]; then
    echo "FAIL e2e version=$version logger=$logger_mode; artifacts=$artifact_dir" >&2
    echo "--- runner log, last $FAILURE_TAIL_LINES lines ---" >&2
    tail -n "$FAILURE_TAIL_LINES" "$runner_log" >&2 || true
    echo "diagnose: $artifact_dir/bot-main.log $artifact_dir/bot-restart.log $artifact_dir/paper.log" >&2
  else
    local logger_state="absent"
    [ "$logger_mode" = "with-logger" ] && logger_state="registered"
    echo "PASS e2e version=$version logger=$logger_mode externalLogger=$logger_state phases=main,restart"
  fi
  return "$result"
}

run_e2e() {
  local requested="${1:-}"
  local requested_mode="${2:-both}"
  local versions=("${DEFAULT_E2E_VERSIONS[@]}")
  local modes=()
  local timing_keys=("prepare:e2e")
  local completed=0
  local failed=0

  if [ -n "$requested" ]; then
    paper_build "$requested" >/dev/null || usage
    versions=("$requested")
  fi
  case "$requested_mode" in
    both) modes=(without-logger with-logger) ;;
    with-logger|without-logger) modes=("$requested_mode") ;;
    *) usage ;;
  esac

  local version logger_mode
  for version in "${versions[@]}"; do
    for logger_mode in "${modes[@]}"; do
      timing_keys+=("e2e:$version:$logger_mode")
    done
  done

  initialize_timing_cache
  local total="${#timing_keys[@]}"
  emit_wait_hint e2e initial 0 "$total" "$TIMING_INITIAL_BUFFER_SECONDS" "${timing_keys[@]}"

  local started actual result
  started="$(date +%s)"
  prepare_e2e_jars || return 1
  actual=$(($(date +%s) - started))
  timing_record "${timing_keys[0]}" "$actual"
  completed=1
  emit_wait_hint e2e remaining "$completed" "$total" 0 "${timing_keys[@]:$completed}"

  for version in "${versions[@]}"; do
    for logger_mode in "${modes[@]}"; do
      started="$(date +%s)"
      result=0
      run_e2e_version "$version" "$(paper_build "$version")" "$logger_mode" || result=$?
      actual=$(($(date +%s) - started))
      timing_record_if_success "e2e:$version:$logger_mode" "$actual" "$result"
      if [ "$result" -ne 0 ]; then
        failed=1
      fi
      completed=$((completed + 1))
      emit_wait_hint e2e remaining "$completed" "$total" 0 "${timing_keys[@]:$completed}"
    done
  done
  return "$failed"
}

run_banner_compat() {
  local versions=("${DEFAULT_E2E_VERSIONS[@]}")
  case "${1:-1.21.11}" in
    1.21.11) ;;
    all) versions=("${SUPPORTED_E2E_VERSIONS[@]}") ;;
    *) usage ;;
  esac
  local timing_keys=("prepare:e2e")
  local runtime_dir="$ROOT_DIR/e2e/runtime/banner-upgrade"
  local artifact_root="$ROOT_DIR/e2e/artifacts/banner-upgrade"
  local project="storagesign-banner-upgrade"
  local completed=0
  local result=0

  local version
  for version in "${versions[@]}"; do
    timing_keys+=("banner:$version")
  done
  initialize_timing_cache
  local total="${#timing_keys[@]}"
  emit_wait_hint banner-compat initial 0 "$total" "$TIMING_INITIAL_BUFFER_SECONDS" "${timing_keys[@]}"

  local started actual
  started="$(date +%s)"
  prepare_e2e_jars || return 1
  actual=$(($(date +%s) - started))
  timing_record "${timing_keys[0]}" "$actual"
  completed=1
  emit_wait_hint banner-compat remaining "$completed" "$total" 0 "${timing_keys[@]:$completed}"

  rm -rf "$runtime_dir" "$artifact_root"
  mkdir -p "$runtime_dir/data" "$runtime_dir/plugins" "$artifact_root"
  cp "$ROOT_DIR/e2e/config/spigot.yml" "$runtime_dir/data/spigot.yml"
  copy_e2e_plugins "$runtime_dir/plugins" without-logger || return 1

  export E2E_DATA_DIR="$runtime_dir/data"
  export E2E_PLUGIN_DIR="$runtime_dir/plugins"
  export E2E_PORT="${E2E_PORT:-25565}"
  export LOGGER_MODE="without-logger"
  export COMPOSE_PROJECT_NAME="$project"

  for version in "${versions[@]}"; do
    started="$(date +%s)"
    local artifact_dir="$artifact_root/$version"
    local runner_log="$artifact_dir/runner.log"
    local phase="banner-upgrade"
    [ "$version" = "1.21.4" ] && phase="banner-seed"
    mkdir -p "$artifact_dir"
    : >"$runner_log"
    export MC_VERSION="$version"
    export PAPER_BUILD="$(paper_build "$version")"
    export MC_SERVER_IMAGE="$(minecraft_server_image "$version")"

    run_step "$runner_log" docker compose -f "$COMPOSE_FILE" build bot || result=1
    if [ "$result" -eq 0 ]; then
      echo "WAIT_HINT scope=banner-compat stage=minecraft-startup version=$version estimate_seconds=60 wait_seconds=60 source=fixed"
      run_step "$runner_log" docker compose -f "$COMPOSE_FILE" up -d --build server || result=1
    fi
    if [ "$result" -eq 0 ] && ! wait_for_server; then result=1; fi
    if [ "$result" -eq 0 ]; then
      E2E_PHASE="$phase" docker compose -f "$COMPOSE_FILE" run --rm bot \
        >"$artifact_dir/bot.log" 2>&1 || result=1
    fi
    capture_server_log "$artifact_dir/paper.log"
    run_step "$runner_log" docker compose -f "$COMPOSE_FILE" down -v --remove-orphans || true

    if [ "$result" -ne 0 ]; then
      echo "FAIL banner-compat version=$version; artifacts=$artifact_dir" >&2
      echo "--- runner log, last $FAILURE_TAIL_LINES lines ---" >&2
      tail -n "$FAILURE_TAIL_LINES" "$runner_log" >&2 || true
      echo "diagnose: $artifact_dir/bot.log $artifact_dir/paper.log" >&2
      return 1
    fi
    actual=$(($(date +%s) - started))
    timing_record "banner:$version" "$actual"
    completed=$((completed + 1))
    if [ "$phase" = "banner-seed" ]; then
      echo "PASS banner-compat version=$version phase=$phase persisted=passed"
    else
      echo "PASS banner-compat version=$version phase=$phase import=passed reexport=passed"
    fi
    emit_wait_hint banner-compat remaining "$completed" "$total" 0 "${timing_keys[@]:$completed}"
  done
}

main() {
  case "${1:-}" in
    unit) run_unit ;;
    integration) run_integration ;;
    coverage) run_coverage ;;
    e2e) run_e2e "${2:-}" "${3:-both}" ;;
    banner-compat) run_banner_compat "${2:-1.21.11}" ;;
    all)
      run_unit && run_integration && run_coverage && run_e2e "" both && run_banner_compat
      ;;
    *) usage ;;
  esac
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
