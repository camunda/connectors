# Agent Execution Tracing — Metrics Coverage

* Authors: Agentic AI Team
* Date: April 23, 2026
* Status: **Proposed** (PoC)
* Related: [Design Document](design.md), [Metrics Reference](https://github.com/camunda/camunda-hub-design-prototype/blob/main/docs/drafts/agent-visibility-metrics-reference.md)

---

Comprehensive cross-reference between the [Agent Visibility Metrics Reference](https://github.com/camunda/camunda-hub-design-prototype/blob/main/docs/drafts/agent-visibility-metrics-reference.md)
and the agent execution tracing event stream. For each metric, this document classifies the
**data source** (event stream, BPMN execution, or combined) and describes exactly how the server
derives the metric value.

For the event model, API contract, and examples, see [design.md](design.md).

---

## Table of Contents

1. [Tool Call Scope](#1-tool-call-scope)
2. [Agent Scope](#2-agent-scope)
3. [Process Instance Scope](#3-process-instance-scope)
4. [Process Definition Scope](#4-process-definition-scope)
5. [Cost Metrics](#5-cost-metrics)
6. [Cluster Scope](#6-cluster-scope)
7. [Testing Scope](#7-testing-scope)
8. [Business Value KPIs](#8-business-value-kpis)
9. [Feature Gaps](#9-feature-gaps)

---

## 1. Tool Call Scope (element instance)

| # | Metric | Source | Server derivation | Status |
|---|--------|--------|-------------------|--------|
| 4 | Tool call duration | **Events** | Pair `TOOL_CALLS_EMITTED` timestamp (≈ startedAt) with `TOOL_CALL_RESULT_RECEIVED.completedAt` by `toolCallId`. Duration = `completedAt - emittedTimestamp`. Falls back to result event timestamp if `completedAt` is missing (pre-upgrade element templates). | **PoC** (requires element template change) |
| 5 | Tool call execution history | **BPMN** | Zeebe tracks job retries, failures, and incidents per element instance. The agent has no visibility into whether a tool's underlying job was retried. | Outside agent scope |
| D1 | Tool call name/type | **Events** | Read from `TOOL_CALLS_EMITTED.toolCalls[].llmToolName` and `.elementId`. For gateway tools these differ (e.g., `MCP_Files___readFile` vs `MCP_Files`); for regular tools they are identical. | **PoC** |
| D2 | Tool call input payload | **Events** | Read from `TOOL_CALLS_EMITTED.toolCalls[].arguments`. This is the full argument map as the LLM produced it. | **PoC** |
| D3 | Tool call output payload | **Events** | Read from `TOOL_CALL_RESULT_RECEIVED.content`. This is the raw tool output as returned by the BPMN element. | **PoC** |

---

## 2. Agent Scope (AHSP element instance)

### Token usage

| # | Metric | Source | Server derivation | Status |
|---|--------|--------|-------------------|--------|
| 6 | Tokens in (input) | **Events** | `SUM(LLM_CALL_COMPLETED.tokenUsage.inputTokenCount)` across all events for this `agentExecutionKey`. Includes tokens from failed job attempts (each retry produces a separate `LLM_CALL_COMPLETED` event with a unique dedup key). | **PoC** |
| 7 | Tokens out (output) | **Events** | `SUM(LLM_CALL_COMPLETED.tokenUsage.outputTokenCount)` across all events. Same aggregation as #6. | **PoC** |
| 8 | Reasoning tokens | **Events** (future) | `SUM(LLM_CALL_COMPLETED.tokenUsage.reasoningTokenCount)` — requires extending `TokenUsageInfo` with `reasoningTokenCount`. Depends on LLM provider support via Langchain4j. Not all providers expose reasoning tokens separately. | **Not started** (Medium) |
| — | Caching tokens | **Events** (future) | `SUM(LLM_CALL_COMPLETED.tokenUsage.cachedTokenCount)` — requires extending `TokenUsageInfo`. Same provider dependency. | **Not started** (Low) |

> **Note on token accuracy across retries**: Because each job retry generates a new
> `LLM_CALL_COMPLETED` event (with a unique dedup key), the server's sum naturally includes
> tokens from all attempts — including failed ones that consumed real tokens. This is more
> accurate than the agent's internal metrics counter, which only persists on successful job
> completion and loses failed attempt data.

### Timing and iterations

| # | Metric | Source | Server derivation | Status |
|---|--------|--------|-------------------|--------|
| 9 | Total agent run time | **BPMN + Events** | Primary: Zeebe AHSP element instance duration (start to completion). Alternative from events: `AGENT_COMPLETED.timestamp - createExecution.timestamp`. The BPMN source is authoritative as it includes Zeebe scheduling overhead. | **PoC** (events provide it; BPMN is authoritative) |
| 10 | LLM call duration | **Events** | Read from `LLM_CALL_COMPLETED.durationMs`. Wall-clock time of the LLM API call. Per-call granularity — the server can compute avg/p50/p95 across iterations. | **PoC** |
| 11 | Agent iterations | **Events + BPMN** | Count of `TURN_COMPLETED` events from jobs that Zeebe confirmed as completed AND that have a matching `LLM_CALL_COMPLETED` event with the same `jobKey`. This naturally excludes: (a) no-op turns (no LLM call), (b) failed job attempts (engine knows they failed — their `TURN_COMPLETED` is ignored), (c) auxiliary LLM calls (future compaction/discovery — these produce `LLM_CALL_COMPLETED` but not within a primary agent turn). The `jobKey` on every event groups them per turn. | **PoC** |

### Tool calls and errors

| # | Metric | Source | Server derivation | Status |
|---|--------|--------|-------------------|--------|
| 12 | Tool call count | **Events** | `SUM(TOOL_CALLS_EMITTED.toolCalls.size())` across all events. Counts every tool call the LLM requested, including across retries. | **PoC** |
| 13 | Execution count | **BPMN** | Count of AHSP element instance activations for this element within the process instance. This is a Zeebe-level metric — the agent cannot see how many times the AHSP was entered. | Outside agent scope |
| 14 | Limit hits | **Events** | `COUNT(LIMIT_HIT)` events for this `agentExecutionKey`. Each event carries `limitType`, `configuredThreshold`, and `actualValue`. | **PoC** |
| 15 | Tool call incident count | **Events + BPMN** | The event stream provides correlation keys: `TOOL_CALLS_EMITTED` carries `elementId` for each tool call. The server joins Zeebe incident data on those element IDs within the AHSP scope to count tool-level incidents. The connector also sees tool failure content in `TOOL_CALL_RESULT_RECEIVED.content`, which provides error context for incidents. | **PoC** (correlation keys); BPMN (incident lifecycle) |
| 16 | Agent incident count | **Events + BPMN** | The event stream provides `elementInstanceKey` (from `CreateAgentExecutionRequest`) and error context (`LLM_CALL_FAILED` events). The server joins Zeebe incidents on the AHSP element instance key to this agent execution. `LLM_CALL_FAILED` events capture the error that led to the incident (e.g., the specific LLM error before retries were exhausted). | **PoC** (correlation + error context); BPMN (incident count) |
| 17 | Error type classification | **Events + BPMN** | `LLM_CALL_FAILED.errorClass` provides agent-level error classification (e.g., `RateLimitException`, `AuthenticationException`). Zeebe provides infrastructure-level errors (timeout, connection refused). The server combines both, correlated by `elementInstanceKey` and `jobKey`. **Proposal**: define an error taxonomy enum that maps common exception types to categories: `LLM_ERROR`, `RATE_LIMIT`, `AUTHENTICATION`, `TIMEOUT`, `PARSING_ERROR`, `LIMIT_VIOLATION`. | Partially covered (agent errors in PoC) |

### Agent behavior

| # | Metric | Source | Server derivation | Status |
|---|--------|--------|-------------------|--------|
| — | Human touch | **BPMN** | Binary: did this agent execution result in a designed handoff to a human user task? Requires process model awareness — checking if a user task follows the AHSP in the BPMN flow and whether it was activated. The agent has no visibility into what happens after it completes. | Outside agent scope |
| — | Escalation to human | **BPMN** | Binary: did the agent escalate because it could not fulfill its goal? Requires detecting escalation events or error boundary events on the AHSP that route to human tasks. **Proposal**: the agent could emit an `AGENT_ESCALATED` event if it hits a configured escalation condition. This requires the agent to be aware of escalation semantics, which is not yet designed. | Outside agent scope (future consideration) |

### Data requirements

| # | Data point | Source | Server derivation | Status |
|---|-----------|--------|-------------------|--------|
| D4 | Agent status | **Events** | Derived from the event timeline as a state machine: no events = `initializing`, `TOOL_DISCOVERY_STARTED` = `discovering`, `LLM_CALL_STARTED` = `reasoning`, `TOOL_CALLS_EMITTED` = `calling_tool` / `waiting_for_results`, `AGENT_COMPLETED` = `completed`, `LLM_CALL_FAILED` (terminal) = `failed`. For live status, the latest event determines the current state. | **PoC** |
| D5 | Tool call sequence | **Events** | Ordered list constructed from `TOOL_CALLS_EMITTED` and `TOOL_CALL_RESULT_RECEIVED` events, sorted by timestamp. Joined by `toolCallId`. | **PoC** |
| D6 | Decision reasoning (chain of thought) | **Events** (future) | Requires extracting reasoning/thinking text from the LLM response into a dedicated list of content blocks, separate from the assistant message content. Provider-dependent: Anthropic returns thinking blocks inline, OpenAI exposes reasoning as a separate field. **Proposal**: add a `List<Content> reasoningContent` field to `LlmCallCompleted` and populate from the framework adapter. Coupled with reasoning token tracking (#8). | **Not started** (Low) |
| D7 | System prompt | **Events** | Read from `CreateAgentExecutionRequest.systemPrompt`. Static for the execution's lifetime. Future: `SYSTEM_PROMPT_CHANGED` event carries updates. | **PoC** |
| D8 | Conversation history | **Events** | Server reconstructs the full conversation by replaying delta events in order. `TOOL_CALL_RESULT_RECEIVED` → tool result messages, `LLM_CALL_STARTED` → user/event messages, `LLM_CALL_COMPLETED` → assistant messages. `CONVERSATION_SNAPSHOT` events serve as reset points. `firstIncludedMessageId` in `LLM_CALL_STARTED` tells the server which portion the LLM actually saw per call. | **PoC** |
| D9 | Model provider and name | **Events** | Read from `CreateAgentExecutionRequest.provider`. Currently assumed static per execution. **Proposal for multi-model**: include `provider` in each `LLM_CALL_COMPLETED` or emit `MODEL_CHANGED`. | **PoC** |
| D10 | Agent error messages/codes | **Events** | Read from `LLM_CALL_FAILED.errorClass` and `.errorMessage`. Job completion callback ensures error events from failed processing are persisted via `flushAll()`. | **PoC** |
| D11 | Limit configuration | **Events** | Read from `CreateAgentExecutionRequest.limits`. Currently `maxModelCalls` only. **Note**: `maxTokens` is a planned limit type. | **PoC** (partial) |

---

## 3. Process Instance Scope

Metrics at this scope aggregate across all agent executions within a single process instance.
The server joins by `processInstanceKey` from `CreateAgentExecutionRequest`.

| # | Metric | Source | Server derivation | Status |
|---|--------|--------|-------------------|--------|
| 18 | Total input tokens | **Events** (aggregated) | `SUM(#6)` across all agent executions where `processInstanceKey` matches. **Note**: called activities create new process instances — the server needs Zeebe's call activity hierarchy to aggregate across parent + child instances. | Events provide per-agent data; Zeebe provides hierarchy |
| 19 | Total output tokens | **Events** (aggregated) | `SUM(#7)` across all agent executions. Same as #18. | Same |
| 20 | Total reasoning tokens | **Events** (future) | `SUM(#8)` across all agent executions. Requires reasoning token tracking. | Not started |
| — | Total caching tokens | **Events** (future) | `SUM(cachedTokenCount)` across all agent executions. Requires cached token tracking. | Not started |
| 21 | Total process instance duration | **BPMN** | Zeebe process instance start-to-end wall-clock time. | BPMN (exists today) |
| 22 | Total incidents across instance | **BPMN** | Zeebe incident count across all elements. | BPMN (exists today) |
| 23 | Agent execution count | **Events** (aggregated) | `COUNT(DISTINCT agentExecutionKey)` where `processInstanceKey` matches. | **PoC** (naturally available) |
| 24 | Human touch | **BPMN** | Binary: did any user task execute? Requires Zeebe user task tracking. | Outside agent scope |
| — | Escalation | **BPMN** | Binary: did any agent escalate? | Outside agent scope |

---

## 4. Process Definition Scope (version)

Metrics aggregate across all instances of a process definition version. The server groups by
`processDefinitionKey` from `CreateAgentExecutionRequest`.

| # | Metric | Source | Server derivation | Status |
|---|--------|--------|-------------------|--------|
| 25 | Total runs | **BPMN** | Count of process instances. Zeebe data. | BPMN (exists today) |
| 26 | Total agent executions | **Events** (aggregated) | `COUNT(DISTINCT agentExecutionKey)` grouped by `processDefinitionKey`. | **PoC** |
| 27 | Avg / median tokens per run | **Events** (aggregated) | Group by `processInstanceKey`, sum tokens per instance, compute avg/median. | **PoC** |
| 28 | Token outlier bands (p5/p95) | **Events** (aggregated) | Same grouping as #27, compute percentiles. | **PoC** |
| 29 | Token trend over time | **Events + BPMN** | Token sums plotted against process instance end time (from Zeebe, per Assumption #4 in metrics ref). | **PoC** (events); BPMN provides end time |
| 30 | Avg / p50 / p95 duration | **BPMN** | Zeebe process instance duration distribution. | BPMN (exists today) |
| 31 | Duration trend | **BPMN** | Duration plotted over time. | BPMN (exists today) |
| 32 | Incident / failure rate | **BPMN** | Percentage of instances with incidents. | BPMN (exists today) |
| 33 | Tool call frequency / distribution | **Events** (aggregated) | Group `TOOL_CALLS_EMITTED.toolCalls` by `elementId` (or `llmToolName`) across executions. Count per tool type. | **PoC** |
| 34 | Tool call failure rate | **Events + BPMN** | Tool requests from `TOOL_CALLS_EMITTED`, results from `TOOL_CALL_RESULT_RECEIVED`. A "failure" could mean: (a) tool returned error content, or (b) Zeebe incident on the tool element. **Proposal**: define a convention for error content (e.g., `ToolCallResult.properties.error = true`) or add `isError` flag to `ToolCallResultReceived`. | Partially covered — needs convention |
| 35 | Limit hit rate | **Events** | `COUNT(instances with at least one LIMIT_HIT) / COUNT(instances)`. | **PoC** |
| 36 | Limit hit patterns | **Events** | Group `LIMIT_HIT` events by `limitType` over time. | **PoC** |
| 37 | Escalation rate | **BPMN** | Percentage of instances where an agent escalated. | Outside agent scope |
| 38 | Human touch rate | **BPMN** | Percentage of instances with human task activation. | Outside agent scope |

---

## 5. Cost Metrics (Optimize only)

Cost metrics are **derived** from token usage events combined with customer-configured pricing
(via DMN decision table). The agent produces raw token counts; cost calculation is entirely
server-side.

| # | Metric | Source | Server derivation | Status |
|---|--------|--------|-------------------|--------|
| 39 | Agent execution cost | **Events** (derived) | `(inputTokens × inputCostPerToken) + (outputTokens × outputCostPerToken) + (reasoningTokens × reasoningCostPerToken)` per agent execution. Pricing from DMN by `provider.type` + `provider.model`. | **PoC** (input/output; reasoning future) |
| 40 | Automated step cost | **BPMN** (derived) | Cost per connector execution from DMN. Not agent-specific. | Outside agent scope |
| 41 | User task cost | **BPMN** (derived) | Fixed cost per user task from DMN. | Outside agent scope |
| 42 | Instance cost (avg/median/p90) | **Events + BPMN** (derived) | Sum of #39 + #40 + #41 per instance. | Partially covered |
| 43 | Total cost per process | **Events + BPMN** (derived) | Sum of #42 across all instances in period. | Partially covered |
| 44 | Cost trend | **Events + BPMN** (derived) | #43 plotted over time. | Partially covered |

---

## 6. Cluster Scope

| # | Metric | Source | Server derivation | Status |
|---|--------|--------|-------------------|--------|
| 45 | Total LLM spend | **Events** (derived) | `SUM(#39)` across all agent executions in the cluster. | Available when #39 is available |
| 46 | Overall error rate | **BPMN** (extended) | Aggregate incident/error rate. Agent contributes `LLM_CALL_FAILED` events for classification. | Events contribute; BPMN is primary |
| 47 | Overall escalation rate | **BPMN** | Aggregate escalation rate. | Outside agent scope |

---

## 7. Testing Scope (Task Tester, Play, Agentic Testing)

| # | Metric | Source | Server derivation | Status |
|---|--------|--------|-------------------|--------|
| 53 | LLM-as-judge score | **External** | Generated by a secondary LLM. Not produced by agent tracing. | Outside agent scope |
| 54 | Pass rate + threshold | **Testing framework** | Events provide raw data the judge evaluates. | Events provide input data |
| 55 | BPMN path coverage | **BPMN + Testing framework** | Requires process model analysis. | Outside agent scope |
| 56 | Eval score per case | **Testing framework** | Same as #53 per case. | Outside agent scope |
| 57 | Token assertion results | **Events** | Testing framework reads `SUM(LLM_CALL_COMPLETED.tokenUsage)` per test case vs. threshold. | **PoC** (events provide data) |
| 58 | Avg agent execution duration | **Events** | `AVG(AGENT_COMPLETED.timestamp - createExecution.timestamp)` per test case. | **PoC** |
| 59 | Failure patterns | **Events + BPMN** | Categorize `LLM_CALL_FAILED` by `errorClass` across cases. | Partially covered |
| 60 | Flakiness rate | **Testing framework** | Compare outcomes across repeated runs. Events provide data for comparison. | Events provide input data |

---

## 8. Business Value KPIs (Hub)

| # | KPI | Source | Server derivation | Status |
|---|-----|--------|-------------------|--------|
| 48 | Automation rate | **BPMN** | `COUNT(instances with 0 user tasks) / COUNT(total instances)`. | Outside agent scope |
| 49 | Cycle time reduction | **BPMN** | Avg duration compared to historical baseline. | Outside agent scope |
| 50 | Execution cost per instance | **Events + BPMN** (derived) | = #42 (avg instance cost). | Partially covered |
| 51 | Estimated time saved | **BPMN** (derived) | Requires configured baseline cost. | Outside agent scope |
| 52 | Total cost saved | **BPMN** (derived) | Requires configured baseline cost. | Outside agent scope |

---

## 9. Feature Gaps

### 9.1 Agent runtime features needed for full metrics coverage

| Feature | Metrics unlocked | Status |
|---------|-----------------|--------|
| **Reasoning token tracking** | #8, #20, #27-29 (reasoning component), D6 | Not started. Two parts: (1) Extend `TokenUsageInfo` with `reasoningTokenCount` — requires Langchain4j provider support. (2) Extract reasoning/thinking text into dedicated content blocks. Provider-dependent. |
| **Cached token tracking** | Caching tokens (pending #) | Not started. Two parts: (1) Extend `TokenUsageInfo` with `cachedTokenCount`. (2) Track caching configuration to correlate with cached token counts. |
| **Max tokens limit** | Extends #14 (limit hits), D11 (limit config) | Not started. New limit type in `LimitsConfiguration`. |
| **LLM call duration timing** | #10 | **In PoC scope.** |
| **Element template `completedAt`** | #4 (tool call duration) | **In PoC scope.** Add `completedAt: now()` to `outputElement`. |
| **Limit hit event** | #14, #35, #36 | **In PoC scope.** |
| **System prompt change detection** | `SYSTEM_PROMPT_CHANGED` event | Event type defined, wiring deferred. |

### 9.2 Planned features that interact with tracing

| Feature | Interaction with tracing |
|---------|------------------------|
| **Conversation compaction** | Replacing older messages with a summary. Emits `CONVERSATION_SNAPSHOT` with compacted messages. Compaction involves an auxiliary LLM call (produces `LLM_CALL_COMPLETED` for token tracking but no `TURN_COMPLETED` — not counted as iteration). |
| **Multi-model support** | If the agent switches models mid-execution, include `provider` in each `LLM_CALL_COMPLETED` or emit `MODEL_CHANGED`. Currently assumed static. |
