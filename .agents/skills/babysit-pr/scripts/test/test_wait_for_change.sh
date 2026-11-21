#!/usr/bin/env bash
# shellcheck source=testlib.sh
source "$(dirname "$0")/testlib.sh"
setup

# Stub pr-state: call N for PR P prints $FAKE_GH_DIR/state-P-N.json (falling back to the highest
# existing one); a file state-P-N.fail makes that call exit 1.
cat > "$FAKE_GH_DIR/state.sh" <<'SH'
#!/usr/bin/env bash
n=$(($(cat "$FAKE_GH_DIR/count-$1" 2>/dev/null || echo 0) + 1))
echo "$n" > "$FAKE_GH_DIR/count-$1"
[ -e "$FAKE_GH_DIR/state-$1-$n.fail" ] && exit 1
while [ "$n" -gt 1 ] && [ ! -e "$FAKE_GH_DIR/state-$1-$n.json" ]; do n=$((n - 1)); done
cat "$FAKE_GH_DIR/state-$1-$n.json"
SH
chmod +x "$FAKE_GH_DIR/state.sh"
export BABYSIT_PR_STATE="$FAKE_GH_DIR/state.sh" BABYSIT_POLL_SECONDS=0 BABYSIT_TIMEOUT_SECONDS=30

BASE='{"state":"OPEN","reviewDecision":null,"head":"h","headOid":"o1","isDraft":false,"mergeable":"MERGEABLE","mergeStateStatus":"CLEAN","autoMerge":false,"queue":null,"checks":{"failed":[],"pending":2,"passed":1},"copilot":{"verdict":"none","commit":null,"current":false},"threads":{"human":0,"bot":0}}'
reset() { rm -f "$FAKE_GH_DIR"/count-* "$FAKE_GH_DIR"/state-*; }
put() { printf '%s' "$3" > "$FAKE_GH_DIR/state-$1-$2.json"; } # PR N JSON
changed() { bash "$SCRIPT_DIR/wait-for-change.sh" "$@" | jq -c .changed; }

# 1. only the changed PR is reported
reset; put 1 1 "$BASE"; put 2 1 "$BASE"; put 1 2 "$(printf '%s' "$BASE" | jq -c '.checks.pending=0')"
assert_eq '[1]' "$(changed 1 2)" "pending change reported for PR 1 only"

# 2. each state the loop must wake on triggers on its own
for patch in '.mergeable="CONFLICTING"' '.mergeStateStatus="BEHIND"' '.headOid="o2"' '.autoMerge=true' \
  '.isDraft=true' '.queue={"state":"QUEUED"}' '.copilot.current=true'; do
  reset; put 1 1 "$BASE"; put 1 2 "$(printf '%s' "$BASE" | jq -c "$patch")"
  assert_eq '[1]' "$(changed 1)" "wakes on $patch"
done

# 3. a transient pr-state failure does not abort the wait
reset; put 1 1 "$BASE"; : > "$FAKE_GH_DIR/state-1-2.fail"; put 1 3 "$(printf '%s' "$BASE" | jq -c '.checks.pending=0')"
assert_eq '[1]' "$(changed 1)" "survives a failed poll and still reports the change"

# 4. nothing ever changes -> timeout, exit 3
reset; put 1 1 "$BASE"
BABYSIT_POLL_SECONDS=1 BABYSIT_TIMEOUT_SECONDS=2 bash "$SCRIPT_DIR/wait-for-change.sh" 1 > "$FAKE_GH_DIR/out.json"
assert_eq 3 "$?" "timeout exits 3"
assert_eq '[]' "$(jq -c .changed "$FAKE_GH_DIR/out.json")" "timeout reports no change"

finish
