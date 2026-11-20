#!/usr/bin/env bash
# shellcheck source=testlib.sh
source "$(dirname "$0")/testlib.sh"
setup

cat > "$FAKE_GH_DIR/pr-view.json" <<'JSON'
{
  "number": 42, "title": "feat: x", "state": "OPEN", "isDraft": false,
  "mergeable": "UNKNOWN", "mergeStateStatus": "BLOCKED", "reviewDecision": "REVIEW_REQUIRED",
  "autoMergeRequest": null, "headRefName": "feat/x", "baseRefName": "main", "headRefOid": "abc",
  "reviews": [{"author":{"login":"copilot-pull-request-reviewer"},"submittedAt":"2026-01-01T00:00:00Z",
               "commit":{"oid":"abc"},"body":"### 🟢 Approval recommended\n"}],
  "statusCheckRollup": [
    {"__typename":"CheckRun","name":"ok","status":"COMPLETED","conclusion":"SUCCESS","detailsUrl":"https://github.com/camunda/connectors/actions/runs/111/job/1"},
    {"__typename":"CheckRun","name":"skipped","status":"COMPLETED","conclusion":"SKIPPED","detailsUrl":"https://github.com/camunda/connectors/actions/runs/112/job/1"},
    {"__typename":"CheckRun","name":"boom","status":"COMPLETED","conclusion":"FAILURE","detailsUrl":"https://github.com/camunda/connectors/actions/runs/222/job/2"},
    {"__typename":"CheckRun","name":"slow","status":"COMPLETED","conclusion":"TIMED_OUT","detailsUrl":"https://github.com/camunda/connectors/actions/runs/333/job/3"},
    {"__typename":"CheckRun","name":"running","status":"IN_PROGRESS","conclusion":null,"detailsUrl":"https://github.com/camunda/connectors/actions/runs/444/job/4"},
    {"__typename":"StatusContext","context":"ext/ci","state":"FAILURE","targetUrl":"https://ci.example.com/1"},
    {"__typename":"StatusContext","context":"ext/wait","state":"PENDING","targetUrl":null}
  ]
}
JSON
cat > "$FAKE_GH_DIR/graphql-1.json" <<'JSON'
{"data":{"repository":{"pullRequest":{"reviewThreads":{"pageInfo":{"hasNextPage":false,"endCursor":""},"nodes":[
 {"id":"A","isResolved":false,"isOutdated":false,"path":"p","line":1,"comments":{"nodes":[{"author":{"login":"alice"},"body":"b","url":"u"}]}},
 {"id":"B","isResolved":false,"isOutdated":false,"path":"p","line":1,"comments":{"nodes":[{"author":{"login":"github-actions"},"body":"b","url":"u"}]}},
 {"id":"C","isResolved":false,"isOutdated":false,"path":"p","line":1,"comments":{"nodes":[{"author":{"login":"copilot-pull-request-reviewer"},"body":"b","url":"u"}]}}
]}}}}}
JSON

cat > "$FAKE_GH_DIR/graphql-2.json" <<'JSON'
{"data":{"repository":{"pullRequest":{"mergeQueueEntry":{"state":"QUEUED","position":1}}}}}
JSON
out=$(bash "$SCRIPT_DIR/pr-state.sh" 42 2>&1)
f() { printf '%s' "$out" | jq -r "$1"; }
assert_eq "42 OPEN main feat/x" "$(f '"\(.number) \(.state) \(.base) \(.head)"')" "basics"
assert_eq "UNKNOWN" "$(f .mergeable)" "mergeable UNKNOWN passes through"
assert_eq "REVIEW_REQUIRED" "$(f .reviewDecision)" "reviewDecision"
assert_eq "false" "$(f .autoMerge)" "no auto-merge"
assert_eq "abc" "$(f .headOid)" "head commit oid exposed"
assert_eq "QUEUED" "$(f .queue.state)" "merge queue entry exposed"
assert_eq "boom:222 slow:333 ext/ci:null" "$(f '[.checks.failed[]|"\(.name):\(.runId)"]|join(" ")')" "failed checks with run ids"
assert_eq "2" "$(f .checks.pending)" "pending count"
assert_eq "2" "$(f .checks.passed)" "passed count (success + skipped)"
assert_eq "green true" "$(f '"\(.copilot.verdict) \(.copilot.current)"')" "copilot embedded"
assert_eq "1 2" "$(f '"\(.threads.human) \(.threads.bot)"')" "thread counts"

jq '.autoMergeRequest={"enabledAt":"x"}' "$FAKE_GH_DIR/pr-view.json" > "$FAKE_GH_DIR/p2" && mv "$FAKE_GH_DIR/p2" "$FAKE_GH_DIR/pr-view.json"
rm -f "$FAKE_GH_DIR/.n"
echo '{"data":{"repository":{"pullRequest":{"mergeQueueEntry":null}}}}' > "$FAKE_GH_DIR/graphql-2.json"
assert_eq "null" "$(bash "$SCRIPT_DIR/pr-state.sh" 42 | jq -c .queue)" "no queue entry -> null"
rm -f "$FAKE_GH_DIR/.n"
assert_eq "true" "$(bash "$SCRIPT_DIR/pr-state.sh" 42 | jq -r .autoMerge)" "auto-merge enabled"

finish
