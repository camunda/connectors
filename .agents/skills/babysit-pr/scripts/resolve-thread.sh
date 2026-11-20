#!/usr/bin/env bash
# Usage: resolve-thread.sh <thread-id> [--reply TEXT] [--no-resolve]
# Replies to a review thread (when --reply is given) and resolves it (unless --no-resolve).
# Prints {"id":...,"replied":bool,"resolved":bool}.
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

id="${1:-}"
[ -n "$id" ] || die "Usage: resolve-thread.sh <thread-id> [--reply TEXT] [--no-resolve]" 2
shift
reply=""
resolve=true
while [ $# -gt 0 ]; do
  case "$1" in
    --reply) reply="${2:?--reply needs TEXT}"; shift 2 ;;
    --no-resolve) resolve=false; shift ;;
    *) die "Unknown argument: $1" 2 ;;
  esac
done
{ [ -n "$reply" ] || [ "$resolve" = true ]; } || die "Nothing to do: --no-resolve without --reply" 2

replied=false
resolved=false
if [ -n "$reply" ]; then
  # shellcheck disable=SC2016 # GraphQL variables, not shell expansions
  gh api graphql -f query='mutation($t:ID!,$body:String!){addPullRequestReviewThreadReply(input:{pullRequestReviewThreadId:$t,body:$body}){comment{id}}}' \
    -f t="$id" -f body="$reply" > /dev/null
  replied=true
fi
if [ "$resolve" = true ]; then
  # shellcheck disable=SC2016 # GraphQL variables, not shell expansions
  gh api graphql -f query='mutation($t:ID!){resolveReviewThread(input:{threadId:$t}){thread{isResolved}}}' -f t="$id" > /dev/null
  resolved=true
fi
jq -n --arg id "$id" --argjson r "$replied" --argjson s "$resolved" '{id:$id,replied:$r,resolved:$s}'
