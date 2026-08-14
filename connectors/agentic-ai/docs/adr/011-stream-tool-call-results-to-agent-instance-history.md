# Stream tool call results into agent instance history as they arrive

* Deciders: Agentic AI Team
* Date: August 14, 2026

## Status

**Implemented**

## Context and Problem Statement

Tool call results for a turn arrive as separate jobs, one per tool. Today, agent instance history
is only written once all results for the turn have arrived; until then the worker no-ops. A turn
with one fast tool call and one long-running tool call shows nothing in history until the slow one
finishes.

## Decision Drivers

* **Visibility**: partial tool-call progress should appear in history as soon as it happens, not
  only once the whole turn completes.
* **No new persisted state**: the no-op path currently persists nothing; adding a dependency here
  should not change that.
* **Correctness under supersession**: this path can see more than one new result at once, or a
  stray/redelivered result that doesn't belong to this turn, and must not crash on either.
* **No dependency on the redesigned API**: the current agent instance history API has no
  id/idempotency support yet, so this cannot rely on server-side dedup existing.

## Considered Options

| Dimension | A — report on arrival, accept duplicates | B — local dedup tracking | C — wait for the redesigned API |
|---|---|---|---|
| Visibility gain | Immediate | Immediate | None until API lands |
| New persisted state | None | New tracking field, persisted on every no-op job | None |
| Duplicate history items | Yes, until API dedups | No | N/A |
| Ships now | Yes | Yes | No |

**B was rejected**: it would turn every currently-free no-op job into a store round-trip solely to
persist "already reported" bookkeeping, for state that becomes redundant once the redesigned API's
dedup lands (see [ADR 012](012-stable-self-generated-message-ids.md)).

**C was rejected**: this epic's goal is to prepare the lifecycle ahead of the redesign, not block
on it.

## Decision Outcome

**Option A** — report each turn's currently-arrived, currently-relevant tool call results to agent
instance history immediately on the no-op path, every time this path runs for the turn. A result
that arrives while other tool calls are still outstanding is written again by every later job that
still observes it, plus once more when the turn completes; see [Follow-up](#follow-up).

Results are only reported once correlated to a tool call the agent is actually waiting on and
gateway (MCP/A2A) transformed, matching the shape of the eventual batch write; uncorrelated results
(stray or redelivered) are silently skipped rather than failing the job. Correlation and
transformation happen once, in the composer that already produces the deferred/proceed decision,
so both the early and final writes go through the same logic. Failure on this path is strict and
fails the job, matching the semantics of the other agent instance client calls it sits alongside.

### Positive Consequences

* Agent instance history shows tool-call progress as it happens, not only once the slowest tool in
  a turn finishes, in the same shape as the eventual batch write.
* No new persisted state, no change to job-completion cadence, no change to when the LLM is
  invoked.

### Negative Consequences

* Write volume is O(N²) in the number of tool calls per turn: for `N` staggered tool calls, up to
  `N(N+1)/2` history-item writes occur before dedup; see [Follow-up](#follow-up).

## Out of Scope

* Changes to job-completion cadence or when the LLM is invoked.

## Follow-up

Once the redesigned agent instance history API
([camunda/camunda#58789](https://github.com/camunda/camunda/issues/58789)) supports dedup by id,
wire both this path and the batch-complete write to supply that id, using the stable per-role
message ids from [ADR 012](012-stable-self-generated-message-ids.md). That closes the duplicate-write
gap without further design changes here.
