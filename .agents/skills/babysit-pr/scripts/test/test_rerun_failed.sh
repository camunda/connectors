#!/usr/bin/env bash
# shellcheck source=testlib.sh
source "$(dirname "$0")/testlib.sh"
setup

cat > "$FAKE_GH_DIR/state.sh" <<'SH'
#!/usr/bin/env bash
cat <<'JSON'
{"checks":{"failed":[{"name":"a","runId":111},{"name":"a2","runId":111},{"name":"b","runId":222},{"name":"ext/ci","runId":null}],"pending":0,"passed":3}}
JSON
SH
chmod +x "$FAKE_GH_DIR/state.sh"
export BABYSIT_PR_STATE="$FAKE_GH_DIR/state.sh"
echo '{"attempt":1}' > "$FAKE_GH_DIR/run-111.json"
echo '{"attempt":4}' > "$FAKE_GH_DIR/run-222.json"

out=$(bash "$SCRIPT_DIR/rerun-failed.sh" 7)
f() { printf '%s' "$out" | jq -c "$1"; }
assert_eq '[{"runId":111,"attempt":2}]' "$(f .rerun)" "run below cap is rerun (attempt+1)"
assert_eq '[{"runId":222,"attempt":4}]' "$(f .capped)" "run at attempt 4 is capped"
assert_eq '["ext/ci"]' "$(f .unrerunnable)" "check without run is unrerunnable"
assert_eq "1" "$(grep -c '^run rerun' "$FAKE_GH_DIR/calls.log")" "exactly one rerun call (deduped, capped skipped)"
assert_contains "$(grep '^run rerun' "$FAKE_GH_DIR/calls.log")" "111 --failed" "reruns failed jobs only"

finish
