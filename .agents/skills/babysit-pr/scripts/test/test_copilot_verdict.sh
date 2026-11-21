#!/usr/bin/env bash
# shellcheck source=testlib.sh
source "$(dirname "$0")/testlib.sh"
setup

BOT=copilot-pull-request-reviewer
HEAD=headsha

# review LOGIN HEADING COMMIT TIME -> one reviews[] element
review() {
  jq -n --arg l "$1" --arg h "$2" --arg c "$3" --arg t "$4" \
    '{author:{login:$l},submittedAt:$t,commit:{oid:$c},body:("<!-- ccr-overview-v2 -->\n\n### " + $h + "\n\nthe summary line\n\n<details>\nlong table\n</details>")}'
}
verdict() { # reviews... -> verdict JSON
  jq -n --arg head "$HEAD" '{headRefOid:$head,reviews:($ARGS.positional|map(fromjson))}' --args "$@" > "$FAKE_GH_DIR/pr-view.json"
  bash "$SCRIPT_DIR/copilot-verdict.sh" 1
}
field() { printf '%s' "$1" | jq -r "$2"; }

out=$(verdict "$(review $BOT '🟢 Approval recommended' $HEAD 2026-01-01T10:00:00Z)")
assert_eq green "$(field "$out" .verdict)" "green verdict"
assert_eq true "$(field "$out" .current)" "green is current"
assert_eq "🟢 Approval recommended" "$(field "$out" .heading)" "heading kept"
assert_eq "the summary line" "$(field "$out" .summary)" "summary is the text between heading and <details>"

out=$(verdict "$(review $BOT '🔵 Needs a closer look' $HEAD 2026-01-01T10:00:00Z)")
assert_eq blue "$(field "$out" .verdict)" "blue verdict"

out=$(verdict "$(review $BOT '🟡 Changes recommended' $HEAD 2026-01-01T10:00:00Z)")
assert_eq changes "$(field "$out" .verdict)" "yellow is changes"

out=$(verdict "$(review $BOT '🔴 Anything' $HEAD 2026-01-01T10:00:00Z)")
assert_eq changes "$(field "$out" .verdict)" "red is changes"

out=$(verdict "$(review $BOT '⚪ Whatever' $HEAD 2026-01-01T10:00:00Z)")
assert_eq unknown "$(field "$out" .verdict)" "unknown heading"

out=$(verdict "$(review $BOT '🟢 Approval recommended' oldsha 2026-01-01T10:00:00Z)")
assert_eq false "$(field "$out" .current)" "review on older commit is not current"
assert_eq green "$(field "$out" .verdict)" "stale verdict still reported"

# latest wins regardless of array order
out=$(verdict "$(review $BOT '🟡 Changes recommended' $HEAD 2026-01-02T10:00:00Z)" \
  "$(review $BOT '🟢 Approval recommended' $HEAD 2026-01-01T10:00:00Z)")
assert_eq changes "$(field "$out" .verdict)" "latest review wins"

out=$(verdict "$(review someone '🟢 Approval recommended' $HEAD 2026-01-01T10:00:00Z)")
assert_eq none "$(field "$out" .verdict)" "no copilot review"
assert_eq false "$(field "$out" .current)" "none is not current"
assert_eq "$HEAD" "$(field "$out" .head)" "head reported"

# heading-only body (no summary, no details) must not crash
out=$(verdict "$(jq -n --arg h "$HEAD" '{author:{login:"copilot-pull-request-reviewer"},submittedAt:"2026-01-01T00:00:00Z",commit:{oid:$h},body:"### 🟢 Approval recommended\n"}')")
assert_eq green "$(field "$out" .verdict)" "heading-only body: verdict"
assert_eq "" "$(field "$out" .summary)" "heading-only body: empty summary"

finish
