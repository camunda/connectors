#!/usr/bin/env bash
# Usage: pr-state.sh <pr>
# One-shot snapshot of a PR:
#   {number,title,state,isDraft,mergeable,mergeStateStatus,reviewDecision,autoMerge,queue,base,head,headOid,
#    checks:{failed:[{name,runId}],pending,passed}, copilot:<copilot-verdict.sh>, threads:{human,bot}}
# queue = the merge queue entry ({state,position}) or null. A queued PR may report autoMerge=false:
# treat "queue != null or autoMerge" as enqueued.
# mergeable can be "UNKNOWN" right after a push: re-poll instead of treating it as a conflict.
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

pr=$(pr_number "${1:-}")

view=$(gh pr view "$pr" --repo "$REPO" --json number,title,state,isDraft,mergeable,mergeStateStatus,reviewDecision,autoMergeRequest,headRefName,baseRefName,headRefOid,statusCheckRollup)
copilot=$("$SCRIPT_DIR/copilot-verdict.sh" "$pr")
threads=$("$SCRIPT_DIR/review-threads.sh" "$pr")
# shellcheck disable=SC2016 # GraphQL variables, not shell expansions
queue=$(gh api graphql -f query='query($o:String!,$r:String!,$n:Int!){repository(owner:$o,name:$r){pullRequest(number:$n){mergeQueueEntry{state position}}}}' \
  -f o="${REPO%%/*}" -f r="${REPO#*/}" -F n="$pr" | jq -c '.data.repository.pullRequest.mergeQueueEntry')

jq -n --argjson v "$view" --argjson c "$copilot" --argjson t "$threads" --argjson q "$queue" '
  def is_failed:
    if .__typename == "StatusContext" then (.state == "ERROR" or .state == "FAILURE")
    else (.conclusion | IN("FAILURE", "TIMED_OUT", "CANCELLED", "STARTUP_FAILURE", "ACTION_REQUIRED")) end;
  def is_pending:
    if .__typename == "StatusContext" then (.state == "PENDING" or .state == "EXPECTED")
    else (.status != "COMPLETED") end;
  ($v.statusCheckRollup // []) as $checks
  | {
      number: $v.number, title: $v.title, state: $v.state, isDraft: $v.isDraft,
      mergeable: $v.mergeable, mergeStateStatus: $v.mergeStateStatus,
      reviewDecision: $v.reviewDecision,
      autoMerge: ($v.autoMergeRequest != null), queue: $q,
      base: $v.baseRefName, head: $v.headRefName, headOid: $v.headRefOid,
      checks: {
        failed: [$checks[] | select(is_failed)
                 | {name: (.name // .context),
                    runId: ((.detailsUrl // "") | [match("/actions/runs/([0-9]+)").captures[0].string] | first | if . then tonumber else null end)}],
        pending: ([$checks[] | select(is_failed | not) | select(is_pending)] | length),
        passed: ([$checks[] | select((is_failed or is_pending) | not)] | length)
      },
      copilot: $c,
      threads: {human: ([$t[] | select(.kind == "human")] | length),
                bot: ([$t[] | select(.kind == "bot")] | length)}
    }'
