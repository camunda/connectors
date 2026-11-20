#!/usr/bin/env bash
# Usage: wait-for-change.sh <pr>...
# Blocks until the state of any PR changes (state, draft, mergeable, merge state, review decision,
# head commit, auto-merge, merge queue entry, pending/failed checks, Copilot verdict/commit/currency,
# thread counts), then prints {"changed":[pr...]}. On timeout prints {"changed":[]} and exits 3.
# A failed poll (gh/network hiccup) is skipped, not fatal; a failed first snapshot is fatal.
# It can run longer than a foreground tool call allows: start it in the background.
# Env: BABYSIT_POLL_SECONDS (default 120), BABYSIT_TIMEOUT_SECONDS (default 3600).
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

[ $# -gt 0 ] || die "Usage: wait-for-change.sh <pr>..." 2
state_script="${BABYSIT_PR_STATE:-$SCRIPT_DIR/pr-state.sh}"
poll="${BABYSIT_POLL_SECONDS:-120}"
timeout="${BABYSIT_TIMEOUT_SECONDS:-3600}"

prs=()
for arg in "$@"; do prs+=("$(pr_number "$arg")"); done

signature() { # <pr>
  "$state_script" "$1" | jq -cS '{state, isDraft, mergeable, mergeStateStatus, reviewDecision, headOid,
    autoMerge, queue, pending: .checks.pending, failed: [.checks.failed[].name],
    copilot: {verdict: .copilot.verdict, commit: .copilot.commit, current: .copilot.current}, threads}'
}

# Parallel arrays (bash 3.2 has no associative arrays): prs[i] <-> before[i]
before=()
for pr in "${prs[@]}"; do
  sig=$(signature "$pr") || die "Could not read the state of PR $pr" 1
  before+=("$sig")
done

start=$SECONDS
while :; do
  sleep "$poll"
  changed='[]'
  i=0
  for pr in "${prs[@]}"; do
    if now=$(signature "$pr") && [ "$now" != "${before[$i]}" ]; then changed=$(printf '%s' "$changed" | jq -c --argjson p "$pr" '. + [$p]')
    fi
    i=$((i + 1))
  done
  if [ "$changed" != "[]" ]; then
    jq -n --argjson c "$changed" '{changed:$c}'
    exit 0
  fi
  if [ $((SECONDS - start)) -ge "$timeout" ]; then
    echo '{"changed":[]}'
    exit 3
  fi
done
