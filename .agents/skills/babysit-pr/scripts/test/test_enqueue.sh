#!/usr/bin/env bash
# shellcheck source=testlib.sh
source "$(dirname "$0")/testlib.sh"
setup

echo '{"id":"PR_node","headRefOid":"deadbeef","state":"OPEN"}' > "$FAKE_GH_DIR/pr-view.json"
out=$(bash "$SCRIPT_DIR/enqueue.sh" 9)
calls=$(cat "$FAKE_GH_DIR/calls.log")
assert_contains "$calls" "enablePullRequestAutoMerge" "uses the auto-merge mutation"
assert_contains "$calls" "sha=deadbeef" "bound to the inspected head SHA"
assert_contains "$calls" "id=PR_node" "uses the PR node id"
assert_contains "$calls" "SQUASH" "explicit merge method"
assert_eq "deadbeef" "$(printf '%s' "$out" | jq -r .headOid)" "reports the head it enqueued"

finish
