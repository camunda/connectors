#!/usr/bin/env bash
# Minimal test helpers for the babysit-pr scripts. Sourced by test_*.sh.
TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC2034 # used by tests that source this file
SCRIPT_DIR="$(cd "$TEST_DIR/.." && pwd)"
PASS=0
FAIL=0

setup() {
  FAKE_GH_DIR="$(mktemp -d)"
  export FAKE_GH_DIR
  export PATH="$TEST_DIR/fake-bin:$PATH"
  trap 'rm -rf "$FAKE_GH_DIR"' EXIT
}

assert_eq() { # EXPECTED ACTUAL LABEL
  if [ "$1" = "$2" ]; then
    PASS=$((PASS + 1))
  else
    FAIL=$((FAIL + 1))
    printf 'FAIL: %s\n  expected: %s\n  actual:   %s\n' "$3" "$1" "$2" >&2
  fi
}

assert_contains() { # HAYSTACK NEEDLE LABEL
  case "$1" in
    *"$2"*) PASS=$((PASS + 1)) ;;
    *)
      FAIL=$((FAIL + 1))
      printf 'FAIL: %s\n  missing: %s\n  in:      %s\n' "$3" "$2" "$1" >&2
      ;;
  esac
}

finish() {
  printf '%s: %d passed, %d failed\n' "$(basename "$0")" "$PASS" "$FAIL"
  [ "$FAIL" -eq 0 ]
}
