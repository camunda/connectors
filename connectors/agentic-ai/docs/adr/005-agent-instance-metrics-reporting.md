# Agent instance metrics and status reporting

* Deciders: Agentic AI Team
* Date: May 22, 2026

## Status

**Implemented**

## Context and Problem Statement

The Camunda engine exposes an agent instance resource that tracks the runtime state of an AI Agent execution.
Connectors can push status and metric updates via a PATCH endpoint wrapped by `AgentInstanceClient.update()`.
Before this work, there did not exist any first-class primitive on the engine side to populate agent data.

The question is: **when to report metrics, and from which source to derive the values**.

Three counters are in scope: `modelCalls`, `tokenUsage`, and `toolCalls`, corresponding to the fields of
`AgentMetrics`. Two status transitions are in scope: `THINKING` (LLM call in progress) and `TOOL_CALLING` /
`IDLE` (LLM response received with / without tool call requests).

## Decision Drivers

* **Accuracy**: Metrics must reflect what the agent actually _initiated_ (i.e. tool calls) and consumed tokens
* **Fairness under supersession**: The job-worker flavor's jobs are superseded when tool calls complete. A
  superseded job's metrics must not inflate counters in the engine.
* **Observability**: Status updates should allow the engine/UI to show meaningful agent state (thinking, calling
  tools, idle) as soon as possible.
* **Simplicity**: The PATCH cadence should be easy to reason about and test without adding new external surface
  area.

## Considered Options

### A — `toolCalls` metric source

1. **Partition of incoming tool-call results** — count the resolved results fed into the current LLM call.
2. **LLM response (`assistantMessage.toolCalls()`)** — count the new tool-call requests emitted by the LLM.

### B — Metrics reporting timing

1. **Immediate (post-LLM PATCH)** — push `{modelCalls, tokenUsage, toolCalls}` and the outcome status
   (`TOOL_CALLING` or `IDLE`) as soon as the LLM responds, before completing the job.
2. **Deferred (job-completion callback)** — report all post-LLM metrics and status only after Zeebe has
   resolved the job completion command, via `AgentJobCompletionListener`. Superseded or failed jobs report
   partial metrics (no `toolCalls`) or skip reporting entirely depending on the failure type.

### C — `compose` method location

1. **Private static on `BaseAgentRequestHandler`** — two-argument null-check cascade.
2. **Static on `AgentJobCompletionListener` (varargs, exception-safe)** — null-filtering, handles 0/1/N listeners,
   each call wrapped in try-catch so one failing listener cannot suppress the rest.

## Decision Outcome

**Option A2** (LLM output), **B2** (deferred), **C2** (interface static).

### A2 — Count `toolCalls` from the LLM response

`toolCalls` is incremented by `assistantMessage.toolCalls().size()` _after_ the chat call. This is symmetric with
`modelCalls` (incremented per LLM invocation) and accurately reflects what the agent requested on the current
turn. Option A1 counted tool-call _results_ that arrived as _input_ — attributing prior-iteration tool work to
the current PATCH and never counting the last iteration's requests.

### B2 — Defer all post-LLM metrics and status to job completion

All post-LLM metrics and status are deferred to `AgentJobCompletionListener` callbacks rather than pushed
eagerly after the LLM call. A job that is superseded or fails before Zeebe accepts its completion command
will not have contributed a complete set of actions (no tool elements activated, no final status reached),
so reporting as if it had would inflate counters and mislead status. Deferring lets the outcome of the
Zeebe command determine what — if anything — is safe to report:

- `onJobCompleted()` — reports `status + {modelCalls, tokenUsage, toolCalls}` together.
- `onJobCompletionFailed(CommandIgnored)` — job was superseded (NOT_FOUND); reports
  `{modelCalls, tokenUsage}` without a status change so the new job's status is not overwritten.
- `onJobCompletionFailed(other)` — execution or command error; reports
  `{modelCalls, tokenUsage}` with `status=IDLE` (tokens were consumed, tools were not activated).

**PATCH cadence per LLM turn:**

| Moment | Status | Delta |
|---|---|---|
| Before LLM call | `THINKING` | — |
| Job completion accepted | `TOOL_CALLING` or `IDLE` | `{modelCalls, tokenUsage, toolCalls}` |
| Job completion superseded (`CommandIgnored`) | — | `{modelCalls, tokenUsage}` |
| Job completion failed (other) | `IDLE` | `{modelCalls, tokenUsage}` |

### C2 — `compose` moved to `AgentJobCompletionListener`

The method is the natural owner of composition logic for its own type. Moving it there makes it reusable without
importing an unrelated handler class, and the varargs signature cleanly replaces the two-argument null-chain. Each
listener in the composed chain is called inside its own try-catch: a throwing listener is logged at `ERROR` and
skipped, but the remaining listeners still execute. This is critical because once a job is completed or failed
there is no Zeebe incident to raise for a failed metric update.

### Positive Consequences

- Engine UI can show real-time agent status (thinking / tool-calling / idle) per turn.
- `toolCalls` count is exact and fair: superseded jobs contribute nothing.
- `AgentJobCompletionListener` is self-contained and safe to compose with any number of listeners.

### Negative Consequences

- Two PATCH calls per LLM turn (THINKING + job completion) instead of one, adding engine round trips.
- The `TOOL_CALLING`/`IDLE` status decision is driven by `assistantMessage.toolCalls()` size — if the
  framework ever transforms tool calls before returning (e.g., filters them out), the status could be misleading.

## Out of Scope

- **Idempotency keys** on PATCH calls — a parallel follow-up will assign a per-turn key derived from the job key
  and turn counter so that retried PATCH calls are safe.
- **`TOOL_DISCOVERY` status** — agent instance status during the gateway tool discovery phase is managed by
  `AgentInitializerImpl` independently of this work; it was already emitting `TOOL_DISCOVERY` patches before
  this change.
- **Metrics persistence across process migration** — `AgentMetrics.minus()` computes a delta relative to the
  snapshot taken before the LLM call; if the agent context is migrated between turns the accumulated counters in
  the engine may diverge from what the current job reported.

## Implementation Notes

`AgentInstanceUpdateRequest` is built with a `@Builder` and a `statusOnly(status)` factory for the THINKING
PATCH. The `delta` field accepts an `AgentMetrics` instance; `null` delta means status-only. `AgentMetrics` uses
RecordBuilder's `with*` methods — `withToolCalls(0)` strips the tool-calls component from a delta without
touching `modelCalls` or `tokenUsage`.

`BaseAgentRequestHandler.createMetricsCompletionListener` is only created when `agentResponse != null`
(i.e., when an LLM call actually occurred). The returned listener always wraps `agentInstanceClient.update()`
in try-catch — the PATCH runs after Zeebe has already accepted or rejected the job, so there is nowhere to
propagate a failure. On `onJobCompletionFailed`, `toolCalls` are stripped from the delta via
`metricsDelta.withToolCalls(0)` since unactivated tool calls must not inflate the counter.
