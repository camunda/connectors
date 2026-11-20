#!/usr/bin/env bash
# Usage: rerun-failed.sh <pr>
# Reruns the failed jobs of every failed Actions run of a PR, at most 3 reruns per run
# (GitHub's run `attempt` counter is the cap: attempt >= 4 means 3 reruns are used up).
# Prints {"rerun":[{runId,attempt}],"capped":[{runId,attempt}],"unrerunnable":[checkName]}.
# Classify failures as flaky BEFORE calling this: a rerun is an unblock, not a fix.
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

pr=$(pr_number "${1:-}")
state_script="${BABYSIT_PR_STATE:-$SCRIPT_DIR/pr-state.sh}"
failed=$("$state_script" "$pr" | jq -c '.checks.failed')

rerun='[]'
capped='[]'
for run_id in $(printf '%s' "$failed" | jq -r '[.[].runId | select(. != null)] | unique | .[]'); do
  attempt=$(gh run view "$run_id" --repo "$REPO" --json attempt | jq -r '.attempt')
  if [ "$attempt" -ge 4 ]; then
    capped=$(printf '%s' "$capped" | jq -c --argjson r "$run_id" --argjson a "$attempt" '. + [{runId:$r,attempt:$a}]')
  else
    gh run rerun "$run_id" --failed --repo "$REPO" > /dev/null
    rerun=$(printf '%s' "$rerun" | jq -c --argjson r "$run_id" --argjson a "$((attempt + 1))" '. + [{runId:$r,attempt:$a}]')
  fi
done

printf '%s' "$failed" | jq --argjson rerun "$rerun" --argjson capped "$capped" '
  {rerun: $rerun, capped: $capped, unrerunnable: [.[] | select(.runId == null) | .name]}'
