# Agent instance metrics and status reporting

* Deciders: Agentic AI Team
* Date: May 22, 2026

## Status

**Implemented** on branch `agentic-ai/update-agent-instance-metrics-on-engine`.

## Context and Problem Statement

The Camunda engine exposes an agent instance resource that tracks the runtime state of an AI Agent execution.
Connectors can push status and metric updates via a PATCH endpoint wrapped by `AgentInstanceClient.update()`.
Before this work, the agent instance received no updates during execution — its metrics were always zero and its
status was always stale.

The question is: **which metrics to report, when to report them, and from which source to derive the values**.

Three counters are in scope: `modelCalls`, `tokenUsage`, and `toolCalls`, corresponding to the fields of
`AgentMetrics`. Two status transitions are in scope: `THINKING` (LLM call in progress) and `TOOL_CALLING` /
`IDLE` (LLM response received with / without tool call requests).

## Decision Drivers

* **Accuracy**: Metrics must reflect what the agent _initiated_, not what it received as input.
* **Fairness under supersession**: The job-worker flavor's jobs are superseded when tool calls complete. A
  superseded job's metrics must not inflate counters in the engine.
* **Observability**: Status updates should allow the engine/UI to show meaningful agent state (thinking, calling
  tools, idle) without polling.
* **Simplicity**: The PATCH cadence should be easy to reason about and test without adding new external surface
  area.

## Considered Options

### A — `toolCalls` metric source

1. **Partition of incoming tool-call results** — count the resolved results fed into the current LLM call.
2. **LLM response (`assistantMessage.toolCalls()`)** — count the new tool-call requests emitted by the LLM.

### B — `toolCalls` reporting timing

1. **Immediate (post-LLM PATCH)** — include `toolCalls` in the same PATCH that reports `modelCalls`/`tokenUsage`.
2. **Deferred (job-completion callback)** — report `toolCalls` only after Zeebe has accepted the job completion
   command, via `AgentJobCompletionListener.onJobCompleted()`.

### C — Tool-call-result partition surface

1. **Public surface on `AgentMessagesHandler`** — expose `AddedUserMessagesResult` containing a
   `ToolCallResultsPartition` as the return type of `addUserMessages()`.
2. **Internal detail of `AgentMessagesHandlerImpl`** — keep partition matching inline in
   `createToolCallResultMessage`; return `List<Message>` from the interface.

### D — `compose` method location

1. **Private static on `BaseAgentRequestHandler`** — two-argument null-check cascade.
2. **Static on `AgentJobCompletionListener` (varargs, exception-safe)** — null-filtering, handles 0/1/N listeners,
   each call wrapped in try-catch so one failing listener cannot suppress the rest.

## Decision Outcome

**Option A2** (LLM output), **B2** (deferred), **C2** (internal), **D2** (interface static).

### A2 — Count `toolCalls` from the LLM response

`toolCalls` is incremented by `assistantMessage.toolCalls().size()` _after_ the chat call. This is symmetric with
`modelCalls` (incremented per LLM invocation) and accurately reflects what the agent requested on the current
turn. Option A1 counted tool-call _results_ that arrived as _input_ — attributing prior-iteration tool work to
the current PATCH and never counting the last iteration's requests.

### B2 — Defer `toolCalls` to job completion

The post-LLM PATCH reports `status + {modelCalls, tokenUsage}` only, with `toolCalls` explicitly zeroed out
(`metricsDelta.withToolCalls(0)`). The `toolCalls` delta is instead pushed inside
`AgentJobCompletionListener.onJobCompleted()`. Superseded or failed jobs call `onJobCompletionFailed()` instead,
which logs a warning and skips the PATCH — so only jobs whose completion Zeebe actually accepted contribute to
the tool-call count.

**PATCH cadence per LLM turn:**

| Moment | Status | Delta |
|---|---|---|
| Before LLM call | `THINKING` | — |
| After LLM call | `TOOL_CALLING` or `IDLE` | `{modelCalls, tokenUsage}` |
| Job completion accepted | — | `{toolCalls}` |
| Job completion failed/superseded | (no PATCH) | — |

### C2 — Partition stays internal to `AgentMessagesHandlerImpl`

Once A2 was adopted, the only external consumer of the partition (the metric source) was removed. Keeping
`AddedUserMessagesResult` / `ToolCallResultsPartition` / `ToolCallResultsPartitioner` as public surface would
add dead API with no consumer. All three classes were deleted; `AgentMessagesHandler.addUserMessages()` returns
`List<Message>` again; partitioning logic remains inline in `createToolCallResultMessage`.

### D2 — `compose` moved to `AgentJobCompletionListener`

The method is the natural owner of composition logic for its own type. Moving it there makes it reusable without
importing an unrelated handler class, and the varargs signature cleanly replaces the two-argument null-chain. Each
listener in the composed chain is called inside its own try-catch: a throwing listener is logged at `ERROR` and
skipped, but the remaining listeners still execute. This is critical because once a job is completed or failed
there is no Zeebe incident to raise for a failed metric update.

### Positive Consequences

- Engine UI can show real-time agent status (thinking / tool-calling / idle) per turn.
- `toolCalls` count is exact and fair: superseded jobs contribute nothing.
- `AgentMessagesHandler` public API is back to its minimal pre-extraction shape.
- `AgentJobCompletionListener` is self-contained and safe to compose with any number of listeners.

### Negative Consequences

- Three PATCH calls per LLM turn (THINKING + post-LLM + job completion) instead of one, adding engine round
  trips.
- The post-LLM `TOOL_CALLING`/`IDLE` status decision is driven by `assistantMessage.toolCalls()` size — if the
  framework ever transforms tool calls before returning (e.g., filters them out), the status could be misleading.

## Out of Scope

- **Idempotency keys** on PATCH calls — a parallel follow-up will assign a per-turn key derived from the job key
  and turn counter so that retried PATCH calls are safe.
- **Partition extraction as a public API** — `ToolCallResultsPartitioner` logic is deliberately kept inline for
  now. If the idempotency-key follow-up needs to correlate results with outgoing requests at the handler level,
  re-extracting the partition at that point is the right time.
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

`BaseAgentRequestHandler.createToolCallsCompletionListener` returns `null` when there are no tool calls
(`toolCallsDelta ≤ 0`), so `compose` receives a null entry and filters it out — no no-op listener is allocated.
The `onJobCompleted` implementation also wraps its `agentInstanceClient.update()` in try-catch for the same
reason as `compose`: the metric PATCH runs after Zeebe accepted the job completion command, and there is nowhere
to propagate a failure.
