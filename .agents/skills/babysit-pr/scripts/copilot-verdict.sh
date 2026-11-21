#!/usr/bin/env bash
# Usage: copilot-verdict.sh <pr>
# Prints the verdict of Copilot's latest review:
#   {"verdict":"green|blue|changes|unknown|none","heading":str|null,"summary":str|null,"commit":str|null,"head":str,"current":bool}
# summary = the text between the heading and the first <details> block (Copilot's own one-paragraph
# assessment, which can carry points that have no inline thread).
# Copilot never submits APPROVED; the verdict is the heading of its review body.
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

pr=$(pr_number "${1:-}")

gh pr view "$pr" --repo "$REPO" --json reviews,headRefOid | jq '
  .headRefOid as $head
  | ([.reviews[] | select(.author.login == "copilot-pull-request-reviewer")]
     | sort_by(.submittedAt) | last) as $r
  | ($r.body // "" | capture("### (?<h>[^\n]*)").h? // null) as $heading
  | ($r.body // null | if . == null then null
       else sub("^[\\s\\S]*?### [^\n]*\n"; "") | split("<details>") | (.[0] // "") | gsub("^\\s+|\\s+$"; "") end) as $summary
  | {
      verdict: (if $r == null then "none"
                elif $heading == null then "unknown"
                elif ($heading | startswith("🟢")) then "green"
                elif ($heading | startswith("🔵")) then "blue"
                elif ($heading | startswith("🟡") or startswith("🔴")) then "changes"
                else "unknown" end),
      heading: $heading,
      summary: $summary,
      commit: ($r.commit.oid // null),
      head: $head,
      current: ($r != null and $r.commit.oid == $head)
    }'
