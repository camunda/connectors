#!/usr/bin/env bash
# shellcheck source=testlib.sh
source "$(dirname "$0")/testlib.sh"
setup

# The PR's current head (pushed after the readiness check) differs from the checked one.
echo '{"id":"PR_node","headRefOid":"newerpush"}' > "$FAKE_GH_DIR/pr-view.json"
out=$(bash "$SCRIPT_DIR/enqueue.sh" 9 checkedsha)
calls=$(cat "$FAKE_GH_DIR/calls.log")
assert_contains "$calls" "enablePullRequestAutoMerge" "uses the auto-merge mutation"
assert_contains "$calls" "sha=checkedsha" "bound to the head the caller checked, not a fresh read"
case "$calls" in
  *sha=newerpush*) assert_eq "no fresh head" "fresh head used" "must not re-read the head" ;;
  *) assert_eq ok ok "must not re-read the head" ;;
esac
assert_contains "$calls" "id=PR_node" "uses the PR node id"
assert_contains "$calls" "m=SQUASH" "explicit merge method"
assert_eq "checkedsha" "$(printf '%s' "$out" | jq -r .headOid)" "reports the head it enqueued"

bash "$SCRIPT_DIR/enqueue.sh" 9 2>/dev/null
assert_eq 2 "$?" "missing head oid exits 2"

finish
