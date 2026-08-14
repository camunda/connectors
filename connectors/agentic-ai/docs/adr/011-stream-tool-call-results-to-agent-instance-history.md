# Stream tool call results into agent instance history as they arrive

* Deciders: Agentic AI Team
* Date: August 14, 2026

## Status

**Implemented**

## Context and Problem Statement

Tool call results for a turn arrive as separate Zeebe jobs — one per AHSP tool element completion
(`AGENTS.md` gotchas). The worker only proceeds to call the LLM once
`AgentConversationTurnInputComposerImpl.compose` sees all expected results for the turn; until
then it returns `CompositionResult.Deferred`, and `BaseAgentRequestHandler.converse` routes this to
`handleNoOp` — a job completion with no engine call, no `ConversationStore` write, nothing
persisted (`ai-agent.md` §9). Only once the full batch is present does `proceed()` run, which calls
`agentInstanceClient.createHistoryForInputMessages(...)` once for the turn's entire input,
including every tool result at once. Partial results are invisible in agent instance history until
the slowest tool call finishes — a turn with one 2-second tool call and one 4-hour user-task tool
call shows nothing until the 4-hour one completes.

`AgentInstanceHistoryMapper` already splits a `ToolCallResultMessage` into one history item per
`ToolCallResultContent` (`AgentInstanceHistoryMapper.java:86-89`), so per-result granularity exists
at the mapping layer — it's just never invoked until the whole batch is ready.

ADR 008 already identified this as "Eager push," rejected there only because it was out of scope
for that ADR's fix, and deferred to this epic (#7595): "This is heavier (needs history-item
idempotency ... deferred to parent epic #7595)."

## Decision Drivers

* **Visibility**: partial tool-call progress should be visible in agent instance history as soon
  as it happens, not only once the whole turn's batch completes.
* **No new persisted state**: the `Deferred`/no-op path currently touches neither `AgentContext`
  nor the `ConversationStore`; adding a new dependency there should not change that.
* **Correctness under supersession**: a job on this path can observe more than one new result at
  once (job supersession under concurrent tool completions, `ai-agent.md` §10), and can observe a
  stray/redelivered result id that doesn't correlate to any tool call this turn is waiting on —
  the implementation must not crash on either.
* **No dependency on the redesigned API**: the current agent instance history wire API
  (`CreateAgentHistoryItemCommandStep1`) has no id/idempotency parameter at all — confirmed via the
  `camunda-client-java` sources. Whatever this ships cannot rely on server-side dedup existing yet.

## Considered Options

| Dimension | A — report on arrival, accept duplicates | B — local dedup tracking | C — wait for the redesigned API |
|---|---|---|---|
| Visibility gain | Immediate | Immediate | None until API lands |
| New persisted state | None | New `AgentContext` field (reported-ids), persisted on every no-op job | None |
| Duplicate history items | Yes, until API dedups | No | N/A |
| Extra store round-trips | None beyond the new engine call | One per tool-call arrival (store write to persist tracking) | None |
| Complexity | Low | Medium-high | None (no-op) |
| Ships now | Yes | Yes | No |

**B (local dedup tracking) was considered and rejected.** It would turn every currently-free no-op
job into a store round-trip solely to persist "already reported" bookkeeping — a meaningful cost
and failure surface (keep-earliest correctness, pruning) for state that becomes redundant the
moment the redesigned API's own dedup lands. See the companion ADR 012 (stable message ids): the
per-role id assignment section there documents which id a future dedup mechanism would use per
history-item role, which is the actual long-term fix for the duplication this ADR accepts.

**C (wait for the API) was rejected** per this epic's stated goal: prepare the lifecycle ahead of
the redesign, not block on it.

## Decision Outcome

**Option A** — report each turn's currently-arrived, currently-relevant tool call results to agent
instance history immediately in the `Deferred` branch, accepting that the same result will be
written again by the batch-complete path in `proceed()` once the turn completes. This duplication
is temporary and expected to be resolved once the redesigned agent instance history API supports
dedup by id.

### Mechanism

- New `AgentInstanceClient.createHistoryForToolCallResults(executionContext, agentInstanceKey,
  toolCallResults, previousTurn)`. `CamundaAgentInstanceClient`'s implementation builds a synthetic
  `ToolCallResultMessage` from the given `ToolCallResult`s (`ToolCallResultContent::from`) and reuses
  the existing `AgentInstanceHistoryMapper.inputHistoryItems` / `createHistoryItem` path — no new
  mapping logic.
- `BaseAgentRequestHandler.converse`'s `Deferred` branch calls this before `handleNoOp`, using
  **`agentInput.toolCallResults()`** (already built at that point in `converse()`), not the raw
  combined `toolCallResults` parameter — `AgentInput.from` partitions on `id() != null`
  (`AgentInput.java`), which excludes event results (`ai-agent.md` §11 gotcha) and is exactly the
  set `AgentInstanceHistoryMapper.argumentsForResult` can resolve without throwing.
- **Correlation filter, load-bearing:** results are further filtered to those whose id is a key in
  the previous turn's `toolCallsById()` (`previousConversation.turns().getLast()`) before being
  reported. `argumentsForResult` throws `IllegalArgumentException` for a non-null-id result with no
  matching originating tool call (`AgentInstanceHistoryMapper.java:141-145`); combined with this
  ADR's strict-failure decision (below), an unfiltered stray or redelivered result id would turn
  the currently-unconditional no-op path into a failing job. The filter is what keeps that path
  safe. `Deferred` is only produced by this composer implementation when a previous turn with tool
  calls exists, but the filter degrades safely (reports nothing) if that invariant doesn't hold for
  some future `AgentConversationTurnInputComposer` implementation.
- **Gateway (MCP/A2A) transformation is intentionally skipped** on this path —
  `gatewayToolHandlers.transformToolCallResults` is only invoked inside
  `AgentConversationTurnInputComposerImpl.resolveOrderedToolCallResults`, not here. Adding
  `GatewayToolHandlerRegistry` to `BaseAgentRequestHandler`/`CamundaAgentInstanceClient` for this
  would cascade into Spring wiring and every handler test, for a cosmetic gain on one tool class.
  Verified safe to skip:
  - `completedAt` is resolved unconditionally for every result in `AgentInitializerImpl`
    (`completedAtResolver.resolve(initialToolCallResults)`, before any gateway transformation ever
    runs), so `AgentInstanceHistoryMapper.requireCompletedAt` never sees a null value here.
  - `elementId` has its own independent fallback inside the mapper itself
    (`AgentInstanceHistoryMapper.elementIdFor`), regardless of whether the composer's transform
    step has run.
  - The one real difference: a gateway tool's result content is reported in its raw,
    pre-unwrap envelope shape on this early path, versus the gateway-unwrapped shape on the final
    batch write. Accepted — this is supplementary/duplicate telemetry, not the source of truth.
- **Iteration key** on the early-reported items is `previousTurn.iterationKey() + 1` — a best-effort
  approximation (matches the documented reconstruction-count fallback), not authoritative. The
  batch write's iteration key (computed by `AgentConversation.rehydrate`, including
  `AgentMetadata.lastIterationKey` cross-validation) is authoritative.
- **Failure handling is strict**, unlike the metrics-update listener pattern (which fires *after*
  job completion and deliberately swallows failures). This call runs before job completion and a
  failure propagates and fails the job — same retry semantics as the existing
  `agentInstanceClient` calls via `CamundaApiRetry`.

### Positive Consequences

- Agent instance history shows tool-call progress as it happens, not only once the slowest tool in
  a turn finishes.
- No new persisted state, no change to job-completion cadence, no change to when the LLM is
  invoked — only history reporting becomes non-blocking on batch completeness.

### Negative Consequences

- Every early-reported tool result is written to history twice: once here, once again by the
  batch-complete write in `proceed()`. Until the redesigned API supports dedup by id, anyone
  reading agent instance history will see each tool result appear twice for a turn that was ever
  `Deferred`.
- Gateway (MCP/A2A) tool results show their pre-unwrap envelope shape on the early write, not the
  final unwrapped shape — a cosmetic divergence, not a data-loss one.

## Out of Scope

- Any dedup mechanism — the redesigned agent instance history API (camunda/camunda#58789) owns
  that; this ADR only prepares the connector to report early.
- Gateway (MCP/A2A) result transformation on this path (see above).
- Changes to `AgentConversationTurnInputComposer`, job-completion cadence, or when the LLM is
  invoked — the "no-op unless full batch ready" behavior for the *LLM call* is untouched.
