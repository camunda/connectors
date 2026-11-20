#!/usr/bin/env bash
# Usage: enqueue.sh <pr> <head-oid>
# Enables auto-merge for a PR, bound to <head-oid>: the `headOid` of the pr-state.sh snapshot the
# readiness decision was made on (never a fresh read, or a push after the check would be enqueued). On branches under the
# merge-queue ruleset this enqueues the PR once requirements are met; elsewhere it arms auto-merge.
# Calls the GraphQL mutation directly (like AUTO_MERGE_BACKPORT_PR.yml) because `gh pr merge --auto`
# needs an explicit strategy on non-queue branches and can fail queue detection with some tokens
# (https://github.com/cli/cli/issues/8352). Fails if the branch moved since that snapshot: run a new pass.
# Prints {"pr":N,"headOid":"..."}.
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

pr=$(pr_number "${1:-}")
head_oid="${2:-}"
[ -n "$head_oid" ] || die "Usage: enqueue.sh <pr> <head-oid> (head-oid = headOid from pr-state.sh)" 2
node_id=$(gh pr view "$pr" --repo "$REPO" --json id | jq -r .id)

# shellcheck disable=SC2016 # GraphQL variables, not shell expansions
gh api graphql \
  -f query='mutation($id:ID!,$sha:GitObjectID!,$m:PullRequestMergeMethod!){enablePullRequestAutoMerge(input:{pullRequestId:$id,expectedHeadOid:$sha,mergeMethod:$m}){clientMutationId}}' \
  -f id="$node_id" -f sha="$head_oid" -f m=SQUASH > /dev/null
jq -n --argjson pr "$pr" --arg h "$head_oid" '{pr:$pr,headOid:$h}'
