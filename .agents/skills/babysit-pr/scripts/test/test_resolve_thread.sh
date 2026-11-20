#!/usr/bin/env bash
# shellcheck source=testlib.sh
source "$(dirname "$0")/testlib.sh"
setup

out=$(bash "$SCRIPT_DIR/resolve-thread.sh" T1 --reply "done; thanks")
assert_eq "true true" "$(printf '%s' "$out" | jq -r '"\(.replied) \(.resolved)"')" "reply and resolve"
calls=$(cat "$FAKE_GH_DIR/calls.log")
assert_contains "$calls" "addPullRequestReviewThreadReply" "reply mutation called"
assert_contains "$calls" "resolveReviewThread" "resolve mutation called"
assert_contains "$calls" "body=done; thanks" "reply text passed as variable"

: > "$FAKE_GH_DIR/calls.log"
out=$(bash "$SCRIPT_DIR/resolve-thread.sh" T1 --reply "disagree" --no-resolve)
assert_eq "true false" "$(printf '%s' "$out" | jq -r '"\(.replied) \(.resolved)"')" "reply only"
case "$(cat "$FAKE_GH_DIR/calls.log")" in
  *resolveReviewThread*) assert_eq "no resolve call" "resolve call" "--no-resolve must not resolve" ;;
  *) assert_eq ok ok "--no-resolve must not resolve" ;;
esac

out=$(bash "$SCRIPT_DIR/resolve-thread.sh" T1)
assert_eq "false true" "$(printf '%s' "$out" | jq -r '"\(.replied) \(.resolved)"')" "resolve only"

bash "$SCRIPT_DIR/resolve-thread.sh" T1 --no-resolve 2>/dev/null
assert_eq 2 "$?" "--no-resolve alone exits 2"

finish
