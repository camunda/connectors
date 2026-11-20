# Review thread triage

Input: `scripts/review-threads.sh <pr>` (unresolved threads; `kind` = `bot` for Copilot,
`github-actions` and `*[bot]` accounts, `human` for everyone else).

Pick exactly one outcome per thread:

1. **Fix** — cheap and clearly real (a realistic bug, wrong doc, missing test in the diff). Edit,
   verify locally, include in the round's push, then
   `scripts/resolve-thread.sh <id> --reply "Fixed in <short-sha>: <one line>"`.
2. **Decline with reason** — a once-in-a-thousand-years edge, a nitpick, a stale/outdated point, or
   already addressed elsewhere. Reply with the reason, then resolve.
3. **Follow-up** — a real point that is too big for this PR. Create an issue with the
   `create-issue` skill (or open a follow-up PR), reply with the link, then resolve.

Don't over-engineer: once the change is clearly better than before, answer remaining edge cases
with outcome 2 instead of hardening for each one. After 2 fix rounds, stop and ask the user.

## Human threads

Resolve only when you fixed the point (outcome 1) or opened a follow-up the reviewer can see
(outcome 3). If you disagree, reply with the reason using `--no-resolve`, leave the thread open, and
list it in the report for the user — the reviewer decides, not the skill.

## Replies

Short, plain, no implementation tour. Never reply with text that merely says "done" — say what
changed or why nothing will.
