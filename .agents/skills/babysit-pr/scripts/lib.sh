#!/usr/bin/env bash
# Shared helpers for the babysit-pr scripts. Source it; don't execute it.
# shellcheck disable=SC2034 # used by scripts that source this file
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="${BABYSIT_REPO:-camunda/connectors}"

# die MSG [CODE=1]: print MSG to stderr and exit.
die() {
  printf '%s\n' "$1" >&2
  exit "${2:-1}"
}

# pr_number ARG: print the PR number from `123`, `#123` or a PR URL of $REPO.
pr_number() {
  local arg="${1:-}"
  case "$arg" in
    "https://github.com/$REPO/pull/"*)
      arg="${arg#"https://github.com/$REPO/pull/"}"
      arg="${arg%%[!0-9]*}"
      ;;
    http*) die "PR URL is not in $REPO: $arg" 2 ;;
    \#*) arg="${arg#\#}" ;;
  esac
  case "$arg" in
    '' | *[!0-9]*) die "Not a PR number or $REPO PR URL: ${1:-}" 2 ;;
  esac
  printf '%s\n' "$arg"
}
