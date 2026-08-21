# Unified agent instance updates during conversation turns

* Deciders: Agentic AI Team
* Date: August 20, 2026

## Status

**Implemented**

Supersedes the status/metrics reporting design in [ADR 005](005-agent-instance-metrics-reporting.md)
and the history-write mechanics in [ADR 011](011-stream-tool-call-results-to-agent-instance-history.md).
Both ADRs remain otherwise valid (metrics semantics, streamed-early motivation); only the transport —
how many calls, how they are batched, and how failure is classified — changes.

## Context and Problem Statement

Every conversation turn produced up to six independent engine round-trips: a status/tools update, one
history-item create per input message (including one per tool-call result), an assistant history-item
create, and a final status/metrics update. Each was retried independently via `CamundaApiRetry` with no
all-or-nothing guarantee, so a mid-turn failure could leave the agent instance with some items
committed and others not, with no recovery path. Metrics and the assistant history item derive from the
same LLM response and were reported through two unrelated code paths, sometimes in the same call and
sometimes deferred to a job-completion listener depending on which connector flavor and turn shape was
in play — a distinction that existed only to decide whether a PATCH was safe to defer, not because the
data itself differed.

The engine gained a batched `history[]` list on the `UPDATE` command plus `jobKey`/`jobLease` and
per-item `historyItemId` (camunda/camunda#58789, camunda/camunda#59714, camunda/camunda#59756), making it possible to collapse
a turn's writes into one all-or-nothing, lease-fenced request. The question is how to restructure the
connector's agent-instance client and handler around that primitive.

## Decision Drivers

* **Consistency**: a turn's status, metrics, and history items should commit together or not at all,
  so a superseded or failed activation can't leave the agent instance half-updated.
* **Simplicity**: one client method per turn-shaped operation, not one per item; the handler should not
  need to reason about which updates are safe to defer.
* **Correctness under supersession**: a stale activation's write must be rejected by the engine, not
  silently accepted or blindly retried.
* **No regression on the metrics/status semantics** already established by ADR 005, or the streamed
  early tool-result visibility established by ADR 011.

## Considered Options

1. Keep the per-item client methods and the deferred-PATCH distinction, and layer the new batched
   `update()` call on top only for the request-level status/metrics report.
2. Replace the per-item methods with turn-shaped methods (`applyTurnStart`, `applyTurnCompletion`,
   `applyToolCallResults`) that each build one batched `update()` call, and delete the deferred-PATCH
   path entirely — every agent-instance write happens synchronously, inline in the handler.

## Decision Outcome

Chosen option: **Option 2 — turn-shaped methods, always synchronous**, because the deferred-PATCH
distinction it removes existed purely to decide when a PATCH was safe to send, not because any turn
shape needed different data; once every write is a single batched, lease-fenced call, sending it
inline is no more expensive than sending it from a completion listener; and a leftover per-item
API next to a batched one would keep two ways to write the same data, working against Decision Driver
2.

Each turn issues exactly two calls: `applyTurnStart`, before the LLM call, batches the `THINKING`
status with the turn's input-message history items and — only when the system prompt or tool list
changed since the previous turn — a `CONFIGURATION` history item; `applyTurnCompletion`, once per LLM
round (including intermediate provider continuations), batches that round's status with its assistant
history item and metrics. A continuation round re-asserts `THINKING`; only the final round sends the
terminal status, since the engine allows exactly one `status` per batched update. `applyToolCallResults`
keeps ADR 011's streamed-early write, now also batched.

`historyItemId` is derived per role so a retried batch dedups against its own prior attempt:
`USER`/`ASSISTANT` from the message's own self-generated id (ADR 012), `TOOL_RESULT` from the
originating tool-call id (so a streamed-early report and the later batch write for the same result
collapse into one row once the engine's per-item dedup lands), and `CONFIGURATION` from a content hash
over system prompt and tools — which doubles as change detection: a `CONFIGURATION` item is only
emitted when that hash differs from the one recorded for the previous turn.

A `404` on a batched `update()` is no longer classified the same as one on a batch-less `update()` or
on `create()`: it means the job activation that issued it has been superseded, so it is raised as a
non-retryable `AGENT_INSTANCE_SUPERSEDED` failure instead of the ordinary permanent-failure path. The
`applyTurnStart` batch doubles as the fence probe for the whole turn — a superseded activation is
rejected before any model tokens are spent.

### Positive Consequences

* A turn's agent-instance writes are all-or-nothing and lease-fenced; a superseded activation's writes
  are rejected by the engine rather than partially applied.
* Two calls per turn instead of up to six; no per-item retry sequencing to reason about.
* One code path for every write — no flavor-specific or turn-shape-specific deferral logic in the
  handler.

### Negative Consequences

* `model`/`provider`/`limits` changes are not currently re-pushed via a `CONFIGURATION` item (see
  Deferred below) even though the engine allows it, since only system prompt and tool list are tracked
  by the change-detection fingerprint today.
* `create()` sends its configuration as a `CONFIGURATION` history item and is lease-fenced via
  `jobKey`/`jobLease`, consistent with the turn updates. The separate create call still precedes the
  first turn's `applyTurnStart`, so that turn's `CONFIGURATION` item remains (see Deferred below).

## Deferred

**First-turn configuration redundancy (Req 1, partly closed).** `create()` now sends its
configuration as a `CONFIGURATION` history item (model/provider/system prompt/tools) and forwards
`jobKey`/`jobLease`, using the `history[]` batch the engine's create-instance API accepts
(camunda/camunda#59784) — the direct-fields create path is gone. Creation is kept as a standalone
call, though: it is not folded into the first turn's `applyTurnStart`. `AgentMetadata` starts with
an empty `configurationFingerprintHistory`, so the first turn's `applyTurnStart` finds no previous
fingerprint and emits its own `CONFIGURATION` item — redundant with what `create()` sent moments
earlier, but harmless. The two items usually carry different `historyItemId`s, since `create()` runs
before tools are resolved and the fingerprint folds in the tool list; for a tool-less agent, both
items see an empty tool list and the fingerprints (and thus `historyItemId`s) coincide, which is
still harmless since it's an idempotent dedup key over identical content. Folding creation into the
first turn to remove the redundancy was declined to keep the change low-risk.

**Per-item dedup.** camunda/camunda#58792 (engine) has no PR yet. `historyItemId` values are already chosen so
dedup works transparently once it lands; until then, the ADR 011 streamed-early duplicate row (one from
`applyToolCallResults`, one written again by the next `applyTurnStart`) persists.

**Reasoning/cache token counts on history items.** Blocked on camunda/camunda#59627. No regression: the
request-level update this design replaces never carried them either.
