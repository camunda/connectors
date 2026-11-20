#!/usr/bin/env bash
# shellcheck source=testlib.sh
source "$(dirname "$0")/testlib.sh"
setup

# thread ID RESOLVED AUTHOR(or "null") -> one reviewThreads node
thread() { # ID RESOLVED AUTHOR [TYPENAME]
  jq -n --arg id "$1" --argjson res "$2" --arg a "$3" --arg tn "${4:-User}" '{
    id:$id, isResolved:$res, isOutdated:false, path:"a/B.java", line:7,
    comments:{nodes:[{author:(if $a=="null" then null else {login:$a,__typename:$tn} end), body:("body "+$id), url:("https://x/"+$id)}]}}'
}
# page HASNEXT CURSOR nodes... -> GraphQL response
page() {
  local next=$1 cursor=$2
  shift 2
  jq -n --argjson next "$next" --arg c "$cursor" '{data:{repository:{pullRequest:{reviewThreads:{
    pageInfo:{hasNextPage:$next,endCursor:$c}, nodes:($ARGS.positional|map(fromjson))}}}}}' --args "$@"
}

page false "" \
  "$(thread T1 true someone)" "$(thread T2 false alice)" "$(thread T3 false github-actions)" \
  "$(thread T4 false copilot-pull-request-reviewer)" "$(thread T5 false null)" "$(thread T6 false renovate Bot)" \
  > "$FAKE_GH_DIR/graphql-1.json"
out=$(bash "$SCRIPT_DIR/review-threads.sh" 1)
assert_eq "T2 T3 T4 T5 T6" "$(printf '%s' "$out" | jq -r '[.[].id]|join(" ")')" "only unresolved threads"
assert_eq "human bot bot human bot" "$(printf '%s' "$out" | jq -r '[.[].kind]|join(" ")')" "kinds"
assert_eq "ghost" "$(printf '%s' "$out" | jq -r '.[3].author')" "null author is ghost"
assert_eq "a/B.java" "$(printf '%s' "$out" | jq -r '.[0].path')" "path"
assert_eq "https://x/T2" "$(printf '%s' "$out" | jq -r '.[0].url')" "url"
assert_eq "false" "$(printf '%s' "$out" | jq -r '.[0].outdated')" "outdated"

# pagination
rm -f "$FAKE_GH_DIR/.n" "$FAKE_GH_DIR/calls.log"
page true CUR1 "$(thread P1 false alice)" > "$FAKE_GH_DIR/graphql-1.json"
page false "" "$(thread P2 false bob)" > "$FAKE_GH_DIR/graphql-2.json"
out=$(bash "$SCRIPT_DIR/review-threads.sh" 1)
assert_eq "P1 P2" "$(printf '%s' "$out" | jq -r '[.[].id]|join(" ")')" "both pages returned"
assert_eq "2" "$(grep -c 'api graphql' "$FAKE_GH_DIR/calls.log")" "two graphql calls"
assert_contains "$(sed -n 2p "$FAKE_GH_DIR/calls.log")" "after=CUR1" "second call passes cursor"

finish
