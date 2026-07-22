#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/storagesign-runner-test.XXXXXX")"
trap 'rm -rf "$TEMP_DIR"' EXIT

export STORAGESIGN_TIMING_FILE="$TEMP_DIR/e2e-timings.tsv"
# shellcheck source=test.sh
source "$ROOT_DIR/scripts/test.sh"

fail() {
  echo "runner self-test failed: $*" >&2
  exit 1
}

assert_equals() {
  [ "$1" = "$2" ] || fail "expected '$2', got '$1'"
}

initialize_timing_cache
assert_equals "${DEFAULT_E2E_VERSIONS[*]}" "1.21.4 1.21.8 1.21.11"
assert_equals "${SUPPORTED_E2E_VERSIONS[*]}" "1.21.4 1.21.8 1.21.11 26.1.2 26.2"
timing_record first 10
timing_record first 20
timing_record second 5
assert_equals "$(timing_lookup first)" "15"
assert_equals "$(awk -F '\t' '$1 == "first" { print $3 }' "$TIMING_FILE")" "2"
assert_equals "$(timing_estimate first second)" "20 saved"
assert_equals "$(timing_estimate first unknown)" "195 fallback"

hint="$(emit_wait_hint e2e initial 0 1 30 first)"
case "$hint" in
  *"remaining=1 estimate_seconds=15 wait_seconds=45 source=saved"*) ;;
  *) fail "unexpected wait hint: $hint" ;;
esac

unknown_hint="$(emit_wait_hint e2e initial 0 1 30 unknown)"
case "$unknown_hint" in
  *"estimate_seconds=180 wait_seconds=210 source=fallback"*) ;;
  *) fail "unexpected unknown wait hint: $unknown_hint" ;;
esac

before_failure="$(cat "$TIMING_FILE")"
timing_record_if_success failed-case 99 1
assert_equals "$(cat "$TIMING_FILE")" "$before_failure"
timing_record_if_success success-case 7 0
assert_equals "$(timing_lookup success-case)" "7"

printf 'broken\n' >"$TIMING_FILE"
warning="$(initialize_timing_cache 2>&1)"
case "$warning" in
  *"WARN timing-cache invalid="*"fallback_seconds=180"*) ;;
  *) fail "invalid cache warning missing: $warning" ;;
esac
[ ! -s "$TIMING_FILE" ] || fail "invalid cache was not replaced with an empty cache"
invalid_count="$(find "$TEMP_DIR" -name 'e2e-timings.tsv.invalid.*' | wc -l | tr -d ' ')"
assert_equals "$invalid_count" "1"

echo "PASS runner-selftest"
