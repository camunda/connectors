# Copilot verdict

Copilot never submits an `APPROVED` review. The "Copilot review for default branch" ruleset
re-reviews on every push, and the verdict is the first `###` heading of its latest review body
(`<!-- ccr-overview-v2 -->`). `scripts/copilot-verdict.sh` parses it.

| Heading | `verdict` | Meaning for the loop |
|---|---|---|
| 🟢 Approval recommended | `green` | nothing to change |
| 🔵 Needs a closer look | `blue` | a human review would be nice — not a blocker; answer any thread it opened |
| 🟡 Changes recommended | `changes` | triage its threads; fix what's real |
| 🔴 … | `changes` | same (not seen in practice yet) |
| anything else | `unknown` | treat as `changes`, mention it in the report |
| (no review yet) | `none` | wait |

Only a verdict with `current: true` (its review is on the PR's current head commit) counts. After
every push the old verdict is stale until Copilot re-reviews; wait for it instead of acting on it.
Blue is the normal outcome for PRs that touch element templates or validation suppressions — it
asks for a human, so don't loop trying to turn it green.

The verdict JSON also carries `summary`: Copilot's own paragraph under the heading. Read it — it can raise
points that have no inline thread.
