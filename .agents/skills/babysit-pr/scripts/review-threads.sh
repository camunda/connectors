#!/usr/bin/env bash
# Usage: review-threads.sh <pr>
# Prints the UNRESOLVED review threads of a PR as a JSON array:
#   [{id,path,line,outdated,author,kind:"human"|"bot",url,body,replies:[{author,kind,body}]}]
# body/author/url are the first comment; replies are the later ones (first 50 comments per thread).
# A bot-started thread with a human reply is kind=human.
# kind=bot when the first comment's author is a GitHub Bot account (GraphQL __typename "Bot";
# bot logins carry no "[bot]" suffix there) or one of github-actions / copilot-pull-request-reviewer.
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

pr=$(pr_number "${1:-}")
owner="${REPO%%/*}"
name="${REPO#*/}"

# Kept on one line so call logs stay one line per call.
# shellcheck disable=SC2016 # GraphQL variables, not shell expansions
query='query($o:String!,$r:String!,$n:Int!,$after:String){repository(owner:$o,name:$r){pullRequest(number:$n){reviewThreads(first:100,after:$after){pageInfo{hasNextPage endCursor} nodes{id isResolved isOutdated path line comments(first:50){nodes{author{__typename login} body url}}}}}}}'

pages=""
cursor=""
while :; do
  args=(-f query="$query" -f o="$owner" -f r="$name" -F n="$pr")
  [ -n "$cursor" ] && args+=(-f after="$cursor")
  page=$(gh api graphql "${args[@]}")
  pages="$pages$page"$'\n'
  if [ "$(printf '%s' "$page" | jq -r '.data.repository.pullRequest.reviewThreads.pageInfo.hasNextPage')" != "true" ]; then
    break
  fi
  cursor=$(printf '%s' "$page" | jq -r '.data.repository.pullRequest.reviewThreads.pageInfo.endCursor')
done

printf '%s' "$pages" | jq -s '
  def kind($c):
    if ($c.author.__typename == "Bot" or $c.author.login == "github-actions"
        or $c.author.login == "copilot-pull-request-reviewer") then "bot" else "human" end;
  [.[].data.repository.pullRequest.reviewThreads.nodes[]
   | select(.isResolved | not)
   | (.comments.nodes[0] // {}) as $c
   | [(.comments.nodes[1:] // [])[] | {author: (.author.login // "ghost"), kind: kind(.), body}] as $replies
   | {id, path, line, outdated: .isOutdated, author: ($c.author.login // "ghost"),
      kind: (if kind($c) == "human" or ($replies | any(.kind == "human")) then "human" else "bot" end),
      url: $c.url, body: $c.body, replies: $replies}]'
