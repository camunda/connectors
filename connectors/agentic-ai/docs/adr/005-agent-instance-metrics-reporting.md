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

**Option A2** (LLM output), **B3** (hybrid — deferred when safe, immediate otherwise), **C2** (interface static).

### A2 — Count `toolCalls` from the LLM response

`toolCalls` is incremented by `assistantMessage.toolCalls().size()` _after_ the chat call. This is symmetric with
`modelCalls` (incremented per LLM invocation) and accurately reflects what the agent requested on the current
turn. Option A1 counted tool-call _results_ that arrived as _input_ — attributing prior-iteration tool work to
the current PATCH and never counting the last iteration's requests.

### B3 (hybrid) — Deferred when safe, immediate when the element instance won't survive job completion

`UpdateAgentInstanceCommand.elementInstanceKey()` requires a live element instance. The instance dies when
its containing BPMN element closes:

- **Job worker (AHSP) — intermediate turn** (`completionConditionFulfilled=false`): the AHSP stays open after
  job completion, so the element instance survives → deferred PATCH via `onJobCompleted()` is safe.
- **Job worker (AHSP) — final turn** (`completionConditionFulfilled=true`): the AHSP closes → element
  instance dies → synchronous PATCH required before the complete command.
- **Outbound connector (task)**: every job completion closes the service task → element instance always dies
  → synchronous PATCH always required.

`BaseAgentRequestHandler` dispatches via the abstract method `shouldUpdateAgentInstanceBeforeJobCompletion(AgentResponse)`:
- `OutboundConnectorAgentRequestHandler` returns `true` unconditionally.
- `JobWorkerAgentRequestHandler` returns `agentResponse.toolCalls().isEmpty()` — mirrors
  `completionConditionFulfilled`.

**Deferred path** (`shouldUpdateAgentInstanceBeforeJobCompletion = false`): a metrics completion listener is
composed with the store listener via `AgentJobCompletionListener.compose(...)`. On `onJobCompleted()` the
full delta is reported. On `onJobCompletionFailed()` the delta is reported with `toolCalls=0` and status
`IDLE` — tool elements were never activated so inflating the counter would be incorrect.

**Immediate path** (`shouldUpdateAgentInstanceBeforeJobCompletion = true`): `notifyMetrics()` fires
synchronously before `buildConnectorResponse()`; no metrics completion listener is created.

**Supersession fairness restored for intermediate turns**: a superseded intermediate-turn job reports the LLM
cost (modelCalls, tokens) but not toolCalls, because the deferred `onJobCompletionFailed()` strips them.

**PATCH cadence per LLM turn:**

| Moment | Status | Delta | Flavor / condition |
|---|---|---|---|
| Before LLM call | `THINKING` | — | all |
| After LLM call, before job complete command | `TOOL_CALLING` or `IDLE` | `{modelCalls, tokenUsage, toolCalls}` | outbound; or job-worker final turn |
| Job completion accepted | `TOOL_CALLING` | `{modelCalls, tokenUsage, toolCalls}` | job-worker intermediate turn |
| Job completion failed/superseded | `IDLE` | `{modelCalls, tokenUsage, toolCalls=0}` | job-worker intermediate turn |

**PATCH cadence for gateway tool discovery (unchanged):**

| Moment | Status | Delta |
|---|---|---|
| Job completion accepted | `TOOL_DISCOVERY` | — |
| Job completion failed/superseded | (no PATCH) | — |

### C2 — `compose` moved to `AgentJobCompletionListener`

The method is the natural owner of composition logic for its own type. Moving it there makes it reusable without
importing an unrelated handler class, and the varargs signature cleanly replaces the two-argument null-chain. Each
listener in the composed chain is called inside its own try-catch: a throwing listener is logged at `ERROR` and
skipped, but the remaining listeners still execute.

### Positive Consequences

- Engine UI can show real-time agent status (thinking / tool-calling / idle / tool-discovery) per turn.
- Supersession fairness restored for intermediate job-worker turns: `toolCalls` counter not inflated when
  tool elements were never activated.
- Metrics PATCH always targets a live element instance: synchronous path fires while the instance is still
  valid; deferred path fires while the AHSP is still open.

### Negative Consequences

- Two PATCH calls per LLM turn (THINKING + post-LLM) instead of one, adding engine round trips.
- Immediate path (outbound connector; job-worker final turn) still reports metrics even if the job completion
  command ultimately fails — but this is unavoidable without an API change.
- The `TOOL_CALLING`/`IDLE` status decision is driven by `assistantMessage.toolCalls()` size — if the
  framework ever transforms tool calls before returning (e.g., filters them out), the status could be misleading.

## Out of Scope

- **Idempotency keys** on PATCH calls — a parallel follow-up will assign a per-turn key derived from the job key
  and turn counter so that retried PATCH calls are safe.
- **Metrics persistence across process migration** — `AgentMetrics.minus()` computes a delta relative to the
  snapshot taken before the LLM call; if the agent context is migrated between turns the accumulated counters in
  the engine may diverge from what the current job reported.

## Implementation Notes

`AgentInstanceUpdateRequest` is built with a `@Builder` and a `statusOnly(status)` factory for the THINKING
PATCH. The `delta` field accepts an `AgentMetrics` instance; `null` delta means status-only.

`BaseAgentRequestHandler.shouldUpdateAgentInstanceBeforeJobCompletion(AgentResponse)` is an abstract method
that each handler implements to express when its element instance will survive job completion.

`BaseAgentRequestHandler.notifyMetrics()` wraps `agentInstanceClient.update()` in try-catch — a failure is
logged at `ERROR` but does not propagate, ensuring the job can still be completed.

`BaseAgentRequestHandler.createMetricsCompletionListener(...)` creates the deferred listener. It is only
created when `agentResponse != null` (i.e., when an LLM call occurred) and when
`shouldUpdateAgentInstanceBeforeJobCompletion` returns `false`. On `onJobCompleted`, the full delta is
reported. On `onJobCompletionFailed`, `toolCalls` are stripped via `metricsDelta.withToolCalls(0)` and status
is set to `IDLE` — unactivated tool calls must not inflate the counter.

The deferred metrics listener is composed with the store completion listener via
`AgentJobCompletionListener.compose(metricsListener, storeListener)`, so both always fire together.
