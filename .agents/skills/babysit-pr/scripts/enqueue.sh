#!/usr/bin/env bash
# Usage: enqueue.sh <pr>
# Enables auto-merge for a PR, bound to the head commit just inspected. On branches under the
# merge-queue ruleset this enqueues the PR once requirements are met; elsewhere it arms auto-merge.
# Calls the GraphQL mutation directly (like AUTO_MERGE_BACKPORT_PR.yml) because `gh pr merge --auto`
# needs an explicit strategy on non-queue branches and can fail queue detection with some tokens
# (https://github.com/cli/cli/issues/8352). Fails if the branch moved since it was inspected.
# Prints {"pr":N,"headOid":"..."}.
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

pr=$(pr_number "${1:-}")
view=$(gh pr view "$pr" --repo "$REPO" --json id,headRefOid)
node_id=$(printf '%s' "$view" | jq -r .id)
head_oid=$(printf '%s' "$view" | jq -r .headRefOid)

# shellcheck disable=SC2016 # GraphQL variables, not shell expansions
gh api graphql \
  -f query='mutation($id:ID!,$sha:GitObjectID!,$m:PullRequestMergeMethod!){enablePullRequestAutoMerge(input:{pullRequestId:$id,expectedHeadOid:$sha,mergeMethod:$m}){clientMutationId}}' \
  -f id="$node_id" -f sha="$head_oid" -f m=SQUASH > /dev/null
jq -n --argjson pr "$pr" --arg h "$head_oid" '{pr:$pr,headOid:$h}'
