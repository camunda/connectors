---
name: babysit-pr
description: Drive one or more camunda/connectors PRs (a merge plus its backports) to ready-to-merge — green CI, Copilot's review settled, every review thread handled — and arm auto-merge so they land as soon as a human approves. Use when asked to babysit, shepherd, watch, drive, or "get merged" PRs, or to fix CI and review comments until they land.
---

# Babysit PR(s) to merge

Ported from the camunda/camunda `babysit-pr` skill, extended: this one also **fixes** problems
(real CI failures in the PR's own diff, review threads) instead of only rerunning flakes.

Goal per PR: **green CI** + **Copilot settled** + **every review thread handled** → then arm auto-merge
(it enqueues the PR once the human approval is in) and stop. Never wait on a human review; the skill
is re-invocable.

Owns its own loop. Invoke once; it tends the PR(s) across turns. No `/loop` wrapper.

## Inputs

PR numbers or URLs in `camunda/connectors`. Single (`babysit #123`) or a set: the original plus its
`backport stable/X.Y` PRs (discover via
`gh pr list --repo camunda/connectors --search "<orig> in:body is:open"`). No PR → ask; don't infer.
Another repo → ask first; these rules assume connectors' merge queue and backport bot.

Prerequisites: `gh auth status` works, `jq` runs (`jq --version`), CWD = a checkout of the repo. To
push fixes, check out the PR's branch in its own worktree.

## Scripts

All in `scripts/`, JSON on stdout. Use them instead of hand-writing `gh` / GraphQL calls.

| Script | Purpose |
|---|---|
| `pr-state.sh <pr>` | snapshot: state, draft, mergeable, reviewDecision, autoMerge, checks (failed with run ids / pending / passed), Copilot verdict, unresolved threads |
| `copilot-verdict.sh <pr>` | Copilot's latest verdict and whether it covers the current head |
| `review-threads.sh <pr>` | unresolved threads with their replies (`kind` human/bot; a human reply makes it human) |
| `resolve-thread.sh <id> [--reply T] [--no-resolve]` | reply and/or resolve a thread |
| `rerun-failed.sh <pr>` | rerun failed jobs, ≤3 reruns per run |
| `enqueue.sh <pr> <headOid>` | enable auto-merge (enqueues once approved), bound to the head commit the readiness check saw |
| `wait-for-change.sh <pr>...` | block until CI, Copilot, threads or state change |

## Hard rules

1. **Rerun = unblock, not fix.** Log every rerun; a check that flakes repeatedly → stop retrying,
   report it as a flake to harden.
2. **Fix only what's cheap, real, and inside the PR's diff.** Everything else is reported or becomes
   a follow-up ([thread-triage](references/thread-triage.md)).
3. **Never** bypass the merge queue, bump a timeout, disable a test, hand-edit a generated element
   template (regenerate it, see `AGENTS.md`), amend/force-push, rebase someone's branch, change a PR
   title, or run `ready-for-review` (squashing clashes with fix commits; the queue squashes anyway).
4. **Fix commits are new commits**, conventional-commit style (`fix: …`), verified locally first
   (the "Verify locally before pushing" list in `AGENTS.md`).
5. **Cancelled = real until proven otherwise.** Read the run's annotations first (usually timeouts).
6. **Caps.** ≤3 reruns per run; ≤2 fix rounds per PR (a round = one push addressing CI/Copilot/
   thread findings), then stop and ask the user. Don't harden against every edge case a reviewer
   raises once the change is clearly better than before.

## Flaky (rerun) vs real (fix or stop)

Unsure → **real**.

- **Rerun:** failure unrelated to the diff and green on the base branch; infra noise (network,
  registry, runner provisioning); merge-queue failure on the temp branch, not the PR head.
- **Fix:** compile/test/format/lint/element-template failure in code the PR touches. Reproduce
  locally, fix, push.
- **Stop (⛔ needs author):** same check fails again after a rerun *and* the cause isn't in the
  diff; `CONFLICTING`/`BEHIND`; queue removal caused by a concurrently merged change; gates such as
  `ENFORCE_QA_APPROVAL`, the PR title check, `check-pr-conflicts`.

## Loop

One row per PR; repeat passes until every PR is terminal, then report.

**Terminal:** ✅ merged · 🟢 ready, auto-merge armed, awaiting human approval · ⛔ needs author/user · 🔁✋ cap hit.
**Not terminal:** 📥 enqueued (approved, in the queue) — keep passing until it is ✅ merged or gets removed from the queue.

**Each pass, per non-terminal PR:**

1. **State** — `scripts/pr-state.sh <pr>`.
   - `MERGED` → ✅. `CLOSED` (unmerged) → ⛔ closed. `isDraft` → conflicted backport or not-ready PR: hand conflicted backports to the
     `fix-backports` skill → ⛔.
   - `mergeable: UNKNOWN` → GitHub is still computing; wait a pass, don't call it a conflict.
   - `CONFLICTING` / `BEHIND` → ⛔ author.
   - `reviewDecision == CHANGES_REQUESTED` → a human asked for changes: handle their threads per
     triage, and if the request can't be satisfied by those threads → ⛔ "changes requested by <who>"
     (never 🟢).
   - Backport branch (`backport-<n>-to-<branch>`): rerun flaky checks only. On a real failure or an
     open thread, **don't push** (any extra commit stops the bot from auto-approving) → ⛔ and ask.
     Never enqueue one yourself; the bot does it.
2. **CI** — `checks.pending > 0` → skip to step 6. `checks.failed` non-empty → classify (above):
   flaky → `scripts/rerun-failed.sh <pr>` (log "rerun N/3"); real → fix and push (counts as a fix
   round); `capped`/`unrerunnable` entries → ⛔ with run links.
3. **Copilot** — `copilot.current == false` → it will re-review the new head; wait (step 6). Verdict
   rules in [copilot-verdict](references/copilot-verdict.md). Also read `copilot.summary`: it can
   raise points that have no inline thread — treat each as a finding to fix or answer in your report.
4. **Threads** — for each of `scripts/review-threads.sh <pr>`, apply
   [thread-triage](references/thread-triage.md). Batch all fixes into one push.
5. **Done?** CI green (nothing failed/pending), Copilot `current` and `green` — or `blue`/`changes`
   with all of its points answered — and no unresolved thread except human ones awaiting the user.
   - Not already armed/enqueued, and no human thread left open awaiting the user → `scripts/enqueue.sh <pr> <headOid>`
     with the `headOid` of the snapshot you just judged ready. Do it **whether or not the PR is approved**:
     it enables auto-merge bound to that commit, and GitHub waits for the approval and the required
     checks, then enqueues (on branches under the "Use Merge Queue" ruleset — `main` and the recent
     `stable/*`) or merges. Don't use `gh pr merge --auto`: it needs an explicit strategy on non-queue
     branches. Armed/enqueued = `queue != null` **or** `autoMerge`: a queued PR may report
     `autoMerge: false`. A new push can drop auto-merge: re-arm it on the next pass.
   - Then `reviewDecision == APPROVED` → 📥 enqueued; otherwise → 🟢 ready, auto-merge armed, awaiting
     human approval (terminal).
   - Human thread still open awaiting the user → don't arm; 🟢 ready, awaiting human approval, and list the thread under "Needs you".
   - Backports never take this step: the bot approves and auto-merges them
     (`AUTO_MERGE_BACKPORT_PR.yml`) while it is the sole pusher and tests pass.
   - **Enqueued PRs stay under watch.** If it later shows `OPEN`, approved, green, not enqueued (and
     `autoMerge: false`), it was removed from the queue: look at its merge-group run
     (`gh run list --repo camunda/connectors --event merge_group --limit 10`, then the run's failed
     jobs). Flaky on the temp branch → re-enqueue (≤3× per PR; count them in the report). Real
     failure or removal by a concurrently merged change → ⛔.
6. **Between passes** — `scripts/wait-for-change.sh <pr>...` polls every 2 min and times out after
   1 h (exit 3 = timeout: re-check once, then report). It outlives a foreground tool call, so run it
   with `run_in_background: true` and continue when its exit notification arrives. It wakes on CI,
   Copilot, thread, merge-state, head, draft and queue changes. Don't busy-wait.

## Reporting

See [report](references/report.md): live status line per PR while running, final summary at the end.
