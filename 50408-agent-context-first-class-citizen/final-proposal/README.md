# Final Proposal: Agent Context as a First-Class Citizen in Camunda

> **Status:** Proposal  
> **Scope:** Camunda Platform 8 — Zeebe engine, Connector Runtime, Operate  
> **Last updated:** 2026-04-08

---

## 1. Executive Summary

This proposal introduces two new first-class concepts in the Camunda engine to provide structured observability and auditability for agentic AI workloads:

| Concept | Purpose | Lifecycle |
|---------|---------|-----------|
| **`AGENT_CONTEXT`** | Committed execution state — the canonical snapshot of what an agent has decided, its accumulated metrics, and current tool catalog | Written on successful job completion; represents engine-committed truth |
| **`AGENT_TRAIL_EVENT`** | Execution trail — a time-ordered log of individual agent actions: LLM calls, tool invocations, tool results, errors, and decision points | Published per-attempt, including attempts that never reach job completion |

Together they solve a fundamental gap: today, agentic AI execution data is either trapped inside the connector runtime (invisible to the engine) or crammed into process variables (unstructured, hard to query, impossible to audit). Neither path gives Operate, exporters, or compliance systems the structured, memory-type-independent view they need.

**Key architectural decision:** `AgentContext` carries only committed state that the engine has accepted through a successful `CompleteJob`. `AgentTrailEvent` carries the execution trail including data from attempts that may be rejected, retried, or superseded. These two concepts are published through separate paths and must not be conflated.

---

## 2. Problem Statement

### 2.1 Current Architecture

Today's agentic AI connector (the `agentic-ai` module in `camunda/connectors`) executes a multi-turn loop:

```
Zeebe activates job
  → Connector loads AgentContext from process variables
  → Connector calls LLM (one or more turns)
  → Connector executes tool calls via ad-hoc sub-process activation
  → Connector stores updated AgentContext back through CompleteJob variables
  → Process continues
```

The `AgentContext` record currently contains:

```java
record AgentContext(
    AgentState state,               // INITIALIZING, TOOL_DISCOVERY, READY
    AgentMetadata metadata,         // processDefinitionKey, processInstanceKey
    AgentMetrics metrics,           // modelCalls, tokenUsage
    List<ToolDefinition> toolDefinitions,
    ConversationContext conversation,
    Map<String, Object> properties  // e.g., MCP client state
)
```

This is serialized as a process variable and round-tripped through `CompleteJob`.

### 2.2 Why This Is Insufficient

| Problem | Impact |
|---------|--------|
| **No execution trail visibility** | Individual LLM calls, tool invocations, and agent decisions are invisible to the engine, Operate, and exporters. You cannot answer "what did the agent do?" from Camunda's perspective. |
| **Memory-type coupling** | Conversation history may live in process variables (inline), Camunda documents, or external systems. Operate would need to understand each storage backend to render a conversation. |
| **No attempt-level data** | If a job fails after the LLM has executed (e.g., completion failure, stale job, timeout), the execution data from that attempt is lost. The engine only sees the failure — not what happened. |
| **Unstructured process variables** | Agent context stored as a JSON blob in a process variable is not indexed, not typed, and not queryable by exporters in a standardized way. |
| **No non-happy-path observability** | Retries, event-subprocess supersession, and stale-job scenarios produce no structured record of the agent work that was performed before the interruption. |
| **Downstream blindness** | Operate, audit logs, and analytics systems cannot render agent execution timelines without deep coupling to connector internals and memory backends. |

### 2.3 Design Goals

1. **Structured, first-class agent execution data** in the Zeebe protocol
2. **Memory-type independence** — downstream systems see a canonical projection regardless of how conversation memory is stored
3. **Attempt-level trail capture** — execution data from failed or superseded attempts is not lost
4. **Clean separation of committed state vs. execution trail**
5. **Minimal engine coupling** — the engine does not interpret agent semantics; it stores and forwards structured records

---

## 3. Recommended Architecture

### 3.1 Two-Concept Model

```
┌─────────────────────────────────────────────────────────────────┐
│                        Zeebe Engine                             │
│                                                                 │
│  ┌───────────────────┐        ┌────────────────────────────┐   │
│  │   AGENT_CONTEXT    │        │    AGENT_TRAIL_EVENT        │   │
│  │                   │        │                            │   │
│  │  Committed state  │        │  Time-ordered execution    │   │
│  │  per element inst │        │  trail per attempt         │   │
│  │                   │        │                            │   │
│  │  Written on       │        │  Published during/after    │   │
│  │  CompleteJob      │        │  each attempt              │   │
│  └────────┬──────────┘        └─────────────┬──────────────┘   │
│           │                                  │                  │
│           ▼                                  ▼                  │
│     Exporter record                   Exporter record           │
│           │                                  │                  │
│           ▼                                  ▼                  │
│     Operate / Analytics / Audit                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### AGENT_CONTEXT — Committed State

`AGENT_CONTEXT` represents the **engine-accepted truth** about an agent's execution at a given element instance. It is written as part of a successful `CompleteJob` and reflects only state that the engine has committed.

**When it is emitted:** As part of processing a `CompleteJob` command that includes agent context data.

**What it contains:**

| Field | Type | Description |
|-------|------|-------------|
| `elementInstanceKey` | `long` | The element instance this context belongs to |
| `processInstanceKey` | `long` | Parent process instance |
| `processDefinitionKey` | `long` | Process definition reference |
| `agentState` | `enum` | Current agent lifecycle state (INITIALIZING, READY, COMPLETED) |
| `cumulativeMetrics` | `object` | Accumulated metrics across all committed iterations (model calls, token usage) |
| `toolDefinitions` | `list` | Canonical tool catalog available to the agent |
| `conversationSummary` | `object` | Memory-type-independent summary of conversation state (message count, last message role, context window size) — **not** the full conversation |
| `iterationCount` | `int` | Number of committed iterations |
| `properties` | `map` | Extensible key-value properties |

**Why committed state only:** If the engine wrote `AGENT_CONTEXT` for every attempt (including failed ones), the context would represent a state the process never actually reached. Downstream systems would see "ghost" states from retried or superseded executions. By tying `AGENT_CONTEXT` to `CompleteJob`, we guarantee it reflects reality.

#### AGENT_TRAIL_EVENT — Execution Trail

`AGENT_TRAIL_EVENT` represents **individual actions taken during agent execution**, published as a time-ordered log. Trail events are emitted per-attempt and include data from attempts that may never reach job completion.

**When it is emitted:** Via a separate publication path (see Section 6), independently of `CompleteJob`.

**Event categories:**

| Category | Examples |
|----------|----------|
| `LLM_REQUEST` | Model call initiated — includes model name, message count, token estimate |
| `LLM_RESPONSE` | Model response received — includes token usage, finish reason, tool call count |
| `TOOL_INVOCATION` | Tool call dispatched — includes tool name, arguments (sanitized) |
| `TOOL_RESULT` | Tool call completed — includes tool name, result summary, duration |
| `TOOL_CANCELLATION` | Tool call cancelled (e.g., due to event interruption) |
| `AGENT_ERROR` | Error during agent execution — includes error code, message |
| `AGENT_DECISION` | Agent reached a decision point — includes chosen action, alternatives considered |
| `ITERATION_START` | New agent iteration started |
| `ITERATION_END` | Agent iteration completed — includes outcome (tool calls, response, error) |

**Status model for trail events:**

| Status | Meaning |
|--------|---------|
| `ATTEMPTED` | Action was performed during an attempt (default on publication) |
| `COMMITTED` | Action belongs to an attempt that successfully completed (updated when `CompleteJob` succeeds) |
| `REJECTED` | Action belongs to an attempt that was explicitly rejected (job failure, validation error) |
| `SUPERSEDED` | Action belongs to an attempt that was superseded (event subprocess, timer, newer attempt) |

**Causal linking:** Each trail event carries:

| Field | Purpose |
|-------|---------|
| `trailEventKey` | Unique event identifier |
| `jobKey` | The Zeebe job this event belongs to |
| `attemptIndex` | Which attempt of this job (0-based) |
| `elementInstanceKey` | The element instance |
| `parentTrailEventKey` | Causal parent (e.g., `LLM_RESPONSE` is parent of resulting `TOOL_INVOCATION` events) |
| `timestamp` | When the action occurred |

### 3.2 Why AgentContext Alone Is Not Enough

A single `AGENT_CONTEXT` record cannot serve both purposes because:

1. **Timing:** Committed state is available only after `CompleteJob`. Execution trail data exists *during* execution, before completion is known.

2. **Granularity:** `AGENT_CONTEXT` is a snapshot. Trail events are a sequence. You cannot reconstruct "what happened in what order" from a snapshot.

3. **Non-happy-path:** When a job fails after the LLM has run and tool calls have been made, `AGENT_CONTEXT` is never written (no `CompleteJob`). Without `AGENT_TRAIL_EVENT`, that execution is invisible.

4. **Cardinality:** One `AGENT_CONTEXT` per element instance vs. many `AGENT_TRAIL_EVENT` records per attempt. Mixing them would overload the context record and make it unbounded.

---

## 4. Scope and Lifecycle

### 4.1 Scope: Element Instance (AHSP)

Both `AGENT_CONTEXT` and `AGENT_TRAIL_EVENT` are scoped to the **element instance** level, specifically the ad-hoc sub-process (AHSP) that hosts the agentic loop.

**Rationale:**
- An agent execution corresponds to one AHSP instance
- Multiple iterations (job activations) within the same AHSP share one `AGENT_CONTEXT`
- Trail events reference the element instance as their scope anchor
- This aligns with how Operate renders process instance details (by element)

### 4.2 Lifecycle

```
AHSP activated
  │
  ├── Iteration 1 (job activated → agent runs → CompleteJob)
  │     ├── AGENT_TRAIL_EVENTs published (status: ATTEMPTED → COMMITTED)
  │     └── AGENT_CONTEXT written (iteration 1 state)
  │
  ├── Iteration 2 (tool results arrive → agent runs → CompleteJob)
  │     ├── AGENT_TRAIL_EVENTs published (status: ATTEMPTED → COMMITTED)
  │     └── AGENT_CONTEXT updated (iteration 2 state)
  │
  ├── Iteration 3 (agent returns final response → CompleteJob)
  │     ├── AGENT_TRAIL_EVENTs published (status: ATTEMPTED → COMMITTED)
  │     └── AGENT_CONTEXT updated (final state, agentState=COMPLETED)
  │
  └── AHSP completed
```

**Non-happy-path lifecycle:**

```
Iteration N (job activated → agent runs → job becomes stale)
  ├── AGENT_TRAIL_EVENTs published (status: ATTEMPTED)
  ├── No CompleteJob → no AGENT_CONTEXT update
  └── Trail events remain with status ATTEMPTED (or later marked SUPERSEDED)

Retry (new job activated → agent runs → CompleteJob)
  ├── AGENT_TRAIL_EVENTs published (new attemptIndex, status: ATTEMPTED → COMMITTED)
  └── AGENT_CONTEXT written (reflects retried state)
```

---

## 5. Connector to Engine Data Flow

### 5.1 What Data Is Produced in the Connector/Runtime

During agent execution, the connector runtime produces two categories of data:

**Category A — Committed state (for `AGENT_CONTEXT`):**
- Updated agent state (lifecycle phase)
- Cumulative metrics (total model calls, total token usage)
- Current tool catalog
- Conversation summary (message count, window state)
- Iteration count

**Category B — Execution trail (for `AGENT_TRAIL_EVENT`):**
- Individual LLM request/response pairs with timing and token usage
- Individual tool call dispatches and results
- Error events
- Decision points
- Iteration boundaries

### 5.2 Publication Paths

```
Connector Runtime
  │
  ├── Path 1: CompleteJob (existing)
  │     ├── Process variables (existing behavior, unchanged)
  │     └── AGENT_CONTEXT payload (new, structured field on CompleteJob)
  │
  └── Path 2: Trail Publication (new)
        └── AGENT_TRAIL_EVENT records
            Published independently of job completion
            Via new Zeebe command or gateway endpoint
```

#### Path 1: What Goes Through CompleteJob

The existing `CompleteJob` command is extended with an optional `agentContext` field:

```
CompleteJob {
    jobKey: long,
    variables: Map<String, Object>,     // existing — unchanged
    agentContext: AgentContextPayload    // new — optional structured payload
}
```

The engine, upon processing `CompleteJob` with an `agentContext` payload, writes an `AGENT_CONTEXT` record to the log. This record flows through exporters to Operate and other downstream systems.

**Why through CompleteJob:** This ties context to commitment. The engine guarantees that `AGENT_CONTEXT` is written if and only if the job actually completes. No separate coordination needed.

#### Path 2: What Goes Through the Separate Trail Publication Path

Trail events are published via a **new Zeebe command** (proposed: `PublishAgentTrailEvents`):

```
PublishAgentTrailEvents {
    jobKey: long,
    attemptIndex: int,
    events: List<AgentTrailEventPayload>
}
```

**When this is called:**
- During agent execution, the connector runtime batches trail events
- At natural boundaries (after each LLM call, after each tool result), the runtime publishes accumulated events
- On job completion, any remaining unpublished events are flushed
- On job failure, accumulated events are published before the failure is reported

**Why a separate path:** Trail events must be published even when `CompleteJob` never happens (stale jobs, timeouts, crashes). If trail data only flowed through `CompleteJob`, non-happy-path executions would be invisible.

### 5.3 Data Flow Diagram

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         Connector Runtime                                │
│                                                                          │
│  Agent Execution Loop                                                    │
│    │                                                                     │
│    ├─ LLM Call ──→ capture trail event (LLM_REQUEST, LLM_RESPONSE)      │
│    │                │                                                    │
│    │                ▼                                                    │
│    │         Trail Event Buffer                                          │
│    │                │                                                    │
│    │                ├──→ PublishAgentTrailEvents (Path 2)                │
│    │                │    (batched, periodic, or on boundary)             │
│    │                                                                     │
│    ├─ Tool Call ──→ capture trail event (TOOL_INVOCATION)               │
│    ├─ Tool Result ─→ capture trail event (TOOL_RESULT)                  │
│    │                                                                     │
│    └─ Final Response                                                     │
│         │                                                                │
│         ├─ Flush remaining trail events → PublishAgentTrailEvents        │
│         │                                                                │
│         └─ CompleteJob (Path 1)                                          │
│              ├─ variables: { agentContext, agent, ... }  (existing)      │
│              └─ agentContext: { committed state snapshot } (new)         │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
         │                           │
         ▼                           ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          Zeebe Engine                                    │
│                                                                          │
│  On CompleteJob with agentContext:                                        │
│    → Write AGENT_CONTEXT record to log                                   │
│    → Mark associated trail events as COMMITTED                           │
│                                                                          │
│  On PublishAgentTrailEvents:                                             │
│    → Write AGENT_TRAIL_EVENT records to log (status: ATTEMPTED)          │
│                                                                          │
│  On job failure/timeout:                                                 │
│    → Trail events remain ATTEMPTED (or marked REJECTED/SUPERSEDED)       │
│                                                                          │
│  Exporter                                                                │
│    → Exports both AGENT_CONTEXT and AGENT_TRAIL_EVENT                   │
│    → Downstream systems consume structured records                       │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
         │                           │
         ▼                           ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    Operate / Analytics / Audit                            │
│                                                                          │
│  AGENT_CONTEXT  → Render current agent state, metrics, tool catalog      │
│  AGENT_TRAIL_EVENT → Render execution timeline, debug failed attempts    │
│                                                                          │
│  No need to call Documents API or external memory systems                │
│  All data is self-contained in the exported records                      │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Proposed Zeebe Changes

### 6.1 New Record/Value Types

#### `AgentContextRecord` (new record type)

```
RecordType: AGENT_CONTEXT

AgentContextRecordValue {
    elementInstanceKey: long
    processInstanceKey: long
    processDefinitionKey: long
    agentState: String                    // "INITIALIZING", "READY", "COMPLETED"
    iterationCount: int
    cumulativeMetrics: AgentMetricsValue  // { modelCalls, inputTokens, outputTokens }
    toolDefinitionCount: int              // number of available tools
    conversationSummary: ConversationSummaryValue  // { messageCount, contextWindowSize, lastMessageRole }
    properties: Map<String, String>       // extensible key-value pairs
}
```

#### `AgentTrailEventRecord` (new record type)

```
RecordType: AGENT_TRAIL_EVENT

AgentTrailEventRecordValue {
    trailEventKey: long
    jobKey: long
    attemptIndex: int
    elementInstanceKey: long
    processInstanceKey: long
    category: String                      // "LLM_REQUEST", "TOOL_INVOCATION", etc.
    status: String                        // "ATTEMPTED", "COMMITTED", "REJECTED", "SUPERSEDED"
    parentTrailEventKey: long             // causal parent (-1 if root)
    timestamp: long                       // epoch millis
    payload: String                       // JSON — category-specific data
}
```

### 6.2 New Intents/Events

#### For AGENT_CONTEXT:

| Intent | When |
|--------|------|
| `AGENT_CONTEXT.CREATED` | First `CompleteJob` with agent context for this element instance |
| `AGENT_CONTEXT.UPDATED` | Subsequent `CompleteJob` updates to an existing agent context |
| `AGENT_CONTEXT.COMPLETED` | Final `CompleteJob` where `agentState=COMPLETED` |

#### For AGENT_TRAIL_EVENT:

| Intent | When |
|--------|------|
| `AGENT_TRAIL_EVENT.PUBLISHED` | Trail events received from connector runtime |
| `AGENT_TRAIL_EVENT.COMMITTED` | Associated job completed successfully — events promoted |
| `AGENT_TRAIL_EVENT.REJECTED` | Associated job failed — events marked as rejected |
| `AGENT_TRAIL_EVENT.SUPERSEDED` | Element instance superseded (event subprocess) — events marked |

### 6.3 New Commands

#### `PublishAgentTrailEvents` (new command)

```
PublishAgentTrailEventsRequest {
    jobKey: long
    attemptIndex: int
    events: List<AgentTrailEventPayload> {
        category: String
        parentTrailEventKey: long
        timestamp: long
        payload: String  // JSON
    }
}
```

**Authorization:** The command is authorized by the job key — only the worker that activated the job can publish trail events for it.

**Idempotency:** Trail events are append-only. Duplicate publications (same jobKey + attemptIndex + timestamp + category) are deduplicated by the engine.

### 6.4 Protocol/Gateway/Client Implications

| Component | Change |
|-----------|--------|
| **Zeebe protocol (gRPC)** | New `PublishAgentTrailEvents` RPC; extended `CompleteJob` request with optional `agentContext` field |
| **Zeebe gateway** | Route new command to appropriate partition (by job key) |
| **Zeebe Java client** | New `publishAgentTrailEvents()` builder method; extended `completeCommand()` with `.agentContext()` |
| **Connector SDK** | New `AgentTrailPublisher` interface in outbound context |
| **Exporters** | New record type handlers for `AGENT_CONTEXT` and `AGENT_TRAIL_EVENT` |

---

## 7. Proposed Connector Changes

### 7.1 Where Data Is Captured

Data capture happens at well-defined boundaries in the existing agent execution loop:

| Capture point | Trail event(s) | Location in current code |
|---------------|----------------|--------------------------|
| Before LLM call | `ITERATION_START`, `LLM_REQUEST` | `BaseAgentRequestHandler` — before `executeChatRequest()` |
| After LLM response | `LLM_RESPONSE` | `BaseAgentRequestHandler` — after `executeChatRequest()` |
| Tool call dispatch | `TOOL_INVOCATION` | `AgentResponseHandlerImpl` — when creating `ToolCallProcessVariable` |
| Tool result received | `TOOL_RESULT` | `AgentMessagesHandlerImpl` — when processing `ToolCallResult` |
| Tool cancellation | `TOOL_CANCELLATION` | `JobWorkerAgentRequestHandler` — on interrupt handling |
| Error | `AGENT_ERROR` | Exception handlers in `BaseAgentRequestHandler` |
| Iteration end | `ITERATION_END` | `BaseAgentRequestHandler` — after `completeJob()` |

### 7.2 Canonical Projection

The connector runtime produces a **canonical projection** of the conversation — a memory-type-independent summary that is included in both `AGENT_CONTEXT` and relevant trail events.

**Current state (proposal):**
- `ConversationContext` remains the connector's internal memory representation
- On `CompleteJob`, the connector produces a `conversationSummary` for `AGENT_CONTEXT` (message count, roles, window size — not the full conversation)
- Trail events for `LLM_REQUEST` include the message list sent to the model (sanitized of secrets, truncated if large)
- Trail events for `LLM_RESPONSE` include the assistant's response content

**Why this matters:** Operate does not need to call the Documents API to render "what did the agent say." The trail events contain the canonical content. The `AGENT_CONTEXT` contains the summary. Full conversation replay requires the original memory backend, but operational visibility does not.

### 7.3 Tool Call/Result Handling

**Current state of the art:** Tool calls are modeled as ad-hoc sub-process element activations. The connector dispatches `ToolCallProcessVariable` records via `CompleteJob.withResult()`, and results flow back as `ToolCallResult` in the next job activation.

**What changes:**
- Each tool dispatch also emits a `TOOL_INVOCATION` trail event with tool name, arguments, and the associated `ToolCall.id`
- Each tool result also emits a `TOOL_RESULT` trail event with result content, duration, and status
- Cancelled tool calls emit `TOOL_CANCELLATION` trail events
- The existing tool call flow through ad-hoc sub-process activation is **unchanged**

**Pending vs. completed tool activity:**
- **In `AGENT_CONTEXT`**: The current tool catalog and pending tool call state (which tools are awaiting results)
- **In `AGENT_TRAIL_EVENT`**: The full history of tool invocations, results, and cancellations

### 7.4 Memory-Type Independence

The design works identically regardless of how conversation memory is stored:

| Memory type | How it works | What Operate sees |
|-------------|-------------|-------------------|
| **Inline** (`InProcessConversationContext`) | Full conversation in process variable | Same `AGENT_CONTEXT` summary + trail events. Operate doesn't read the variable. |
| **Document-backed** (`CamundaDocumentConversationContext`) | Conversation persisted to Document Store | Same `AGENT_CONTEXT` summary + trail events. Operate doesn't call Documents API. |
| **External/custom** (future) | Conversation in external system (Redis, vector DB, etc.) | Same `AGENT_CONTEXT` summary + trail events. Operate doesn't call external system. |

**Why this works:** The canonical projection in trail events contains the operationally relevant content (what was sent to the LLM, what the LLM responded). The full conversation is the connector's internal concern. Downstream systems consume the structured Zeebe records, not the memory backend.

---

## 8. Exporter / Operate Implications

### 8.1 Exporter Changes

Exporters (Elasticsearch/OpenSearch) need new index templates:

| Index | Source record | Key fields |
|-------|--------------|------------|
| `agent-context` | `AGENT_CONTEXT` | elementInstanceKey, processInstanceKey, agentState, cumulativeMetrics, iterationCount |
| `agent-trail-event` | `AGENT_TRAIL_EVENT` | trailEventKey, jobKey, attemptIndex, category, status, timestamp, payload |

Trail events should be indexed with the `elementInstanceKey` as a routing key to ensure all events for an element instance are co-located for efficient timeline queries.

### 8.2 Operate Changes

| Feature | Data source |
|---------|-------------|
| **Agent state panel** | `AGENT_CONTEXT` — shows current agent state, cumulative metrics, iteration count |
| **Execution timeline** | `AGENT_TRAIL_EVENT` — time-ordered view of LLM calls, tool invocations, results |
| **Failed attempt inspection** | `AGENT_TRAIL_EVENT` with status `REJECTED` or `SUPERSEDED` — shows what happened in failed attempts |
| **Token usage dashboard** | `AGENT_CONTEXT.cumulativeMetrics` — aggregatable across process instances |
| **Tool call detail** | `AGENT_TRAIL_EVENT` (TOOL_INVOCATION + TOOL_RESULT) — shows arguments, results, duration |

### 8.3 What Operate Does NOT Need

- ❌ Access to Documents API to read conversation history
- ❌ Access to external memory systems
- ❌ Knowledge of connector-internal data structures
- ❌ Parsing of process variable JSON blobs

All operationally relevant data arrives as structured, typed Zeebe records through the standard exporter pipeline.

---

## 9. How the Proposal Handles Non-Happy-Path Scenarios

### 9.1 Stale Jobs

**Scenario:** The connector runtime starts executing an agent iteration, makes LLM calls and tool calls, but the job times out before `CompleteJob` is sent.

**Handling:**
1. Trail events were published during execution via `PublishAgentTrailEvents` (Path 2) — they persist with status `ATTEMPTED`
2. No `AGENT_CONTEXT` is written (no `CompleteJob`)
3. When the engine marks the job as timed out, associated trail events can be marked `SUPERSEDED`
4. The next retry starts a new attempt with a new `attemptIndex`

**Result:** The execution work is visible in the trail, even though it didn't commit.

### 9.2 Retries

**Scenario:** A job fails and is retried. Each retry performs a fresh agent iteration.

**Handling:**
1. Each retry produces trail events with an incremented `attemptIndex`
2. Trail events from failed attempts are marked `REJECTED`
3. The successful retry's trail events are marked `COMMITTED`
4. `AGENT_CONTEXT` is written only for the successful attempt

**Result:** All attempts are visible. The committed attempt is clearly distinguished.

### 9.3 Event Subprocess Supersession

**Scenario:** An event subprocess fires while the agent is executing tool calls, interrupting the current iteration.

**Handling:**
1. Trail events published before the interruption persist with status `ATTEMPTED`
2. When the element instance is superseded, trail events are marked `SUPERSEDED`
3. Tool calls that were dispatched but not yet returned emit `TOOL_CANCELLATION` trail events
4. The `AGENT_CONTEXT` from the last successful iteration remains the committed state

**Result:** The interrupted execution is visible in the trail. The event subprocess's impact is traceable.

### 9.4 Completion Failure After LLM Execution

**Scenario:** The LLM returns a response, the connector prepares `CompleteJob`, but the completion fails (e.g., network error, engine rejects the command).

**Handling:**
1. Trail events were published during execution — they persist with status `ATTEMPTED`
2. The connector flushes any remaining trail events before reporting the failure
3. No `AGENT_CONTEXT` is written
4. On retry, the agent re-executes (new attempt, new trail events)

**Result:** The failed attempt's LLM calls and decisions are visible in the trail for debugging.

### 9.5 Summary Table

| Scenario | Trail events | AGENT_CONTEXT | Trail status |
|----------|-------------|---------------|--------------|
| Happy path | Published + committed | Written | COMMITTED |
| Stale job | Published | Not written | ATTEMPTED → SUPERSEDED |
| Retry (failed attempt) | Published | Not written | REJECTED |
| Retry (successful attempt) | Published + committed | Written | COMMITTED |
| Event subprocess interruption | Published | Not updated | SUPERSEDED |
| Completion failure | Published | Not written | ATTEMPTED → REJECTED |

---

## 10. Phased Implementation Recommendation

### Phase 1: Foundation (Engine + Connector SDK)

**Goal:** Establish the new record types and publication paths.

**Deliverables:**
- `AGENT_CONTEXT` record type in Zeebe with CREATED/UPDATED/COMPLETED intents
- `agentContext` field on `CompleteJob` command
- `AGENT_TRAIL_EVENT` record type in Zeebe with PUBLISHED intent
- `PublishAgentTrailEvents` command in Zeebe
- `AgentTrailPublisher` interface in Connector SDK
- Basic exporter support for both record types

**Scope:** Engine changes, protocol changes, SDK interface. No connector-side capture logic yet.

### Phase 2: Connector Integration

**Goal:** Instrument the agentic AI connector to produce structured data.

**Deliverables:**
- Trail event capture at LLM call, tool dispatch, tool result, and error boundaries
- Canonical projection for `AGENT_CONTEXT` (conversation summary, cumulative metrics)
- `PublishAgentTrailEvents` calls at natural boundaries during execution
- `agentContext` payload on `CompleteJob` for committed state
- Trail event flush on job failure

**Scope:** Connector runtime changes, `BaseAgentRequestHandler` instrumentation.

### Phase 3: Operate Visualization

**Goal:** Surface agent execution data in Operate.

**Deliverables:**
- Agent state panel in process instance view
- Execution timeline for agent element instances
- Failed attempt inspection
- Token usage aggregation

**Scope:** Operate UI + backend changes.

### Phase 4: Status Lifecycle Management

**Goal:** Implement the full status model for trail events.

**Deliverables:**
- `COMMITTED` status promotion on successful `CompleteJob`
- `REJECTED` status marking on job failure
- `SUPERSEDED` status marking on element instance supersession
- Trail event status query support in Operate

**Scope:** Engine state management, exporter updates, Operate query changes.

### Phase 5: Advanced Features

**Goal:** Analytics, compliance, and extensibility.

**Deliverables:**
- Token usage dashboards and cost attribution
- Audit log integration for compliance
- Custom trail event categories for connector extensibility
- Trail event retention policies
- Historical trail comparison (across process versions)

**Scope:** Operate analytics, exporter configuration, SDK extensibility.

---

## 11. Open Questions / Follow-Ups

| # | Question | Context |
|---|----------|---------|
| 1 | **Trail event payload size limits** | LLM requests/responses can be large. Should there be a size limit on trail event payloads? Should large payloads be stored as documents with only references in the trail event? |
| 2 | **Trail event batching strategy** | How aggressively should the connector batch trail events? Per-LLM-call? Per-iteration? What's the right trade-off between latency and throughput? |
| 3 | **Trail event retention** | Should trail events have a different retention policy than other Zeebe records? Agent executions can produce orders of magnitude more events than traditional process instances. |
| 4 | **Backward compatibility** | How do existing agentic AI process instances behave when the engine is upgraded? Should the connector detect whether the engine supports the new commands? |
| 5 | **Multi-partition trail events** | If trail events are routed by job key, they land on the same partition as the job. Is this the right partitioning strategy, or should trail events be independently partitioned? |
| 6 | **Sanitization** | What sanitization rules apply to trail event payloads? LLM prompts may contain sensitive data. Should the connector strip secrets before publishing trail events? |
| 7 | **Trail event ordering guarantees** | Are trail events guaranteed to be processed in order within a partition? If not, how does Operate reconstruct the timeline? |
| 8 | **ConversationSummary vs. ConversationContent** | Should `AGENT_CONTEXT` include any conversation content beyond the summary? Or should content always be in trail events only? |
| 9 | **Gateway tool discovery trail** | Should tool discovery (MCP `listTools`, A2A agent card fetch) produce trail events? This is initialization, not execution, but it is operationally relevant. |
| 10 | **Custom connector support** | Should the trail publication API be available to non-agentic connectors? Other long-running connectors might benefit from structured execution trails. |

---

## Appendix A: Glossary

| Term | Definition |
|------|------------|
| **AHSP** | Ad-hoc sub-process — the BPMN construct hosting the agentic loop |
| **Agent iteration** | One cycle of the agent loop: load context → call LLM → dispatch tool calls or return response |
| **Attempt** | One execution of a job. A job may have multiple attempts due to retries. |
| **Committed state** | State that the engine has accepted through a successful `CompleteJob` |
| **Canonical projection** | A memory-type-independent representation of conversation data, suitable for downstream consumption |
| **Trail event** | A single entry in the agent's execution log, representing one action or decision |

## Appendix B: Comparison with Current Implementation

| Aspect | Current (as of `camunda/connectors` main) | Proposed |
|--------|-------------------------------------------|----------|
| Agent state storage | Process variable (`agentContext`) | Process variable (unchanged) + `AGENT_CONTEXT` record |
| Execution visibility | None — data trapped in connector runtime | `AGENT_TRAIL_EVENT` records exported to Operate |
| Failed attempt data | Lost | Preserved in trail events with status markers |
| Conversation access | Requires Documents API or process variable parsing | Summary in `AGENT_CONTEXT`; content in trail events |
| Metrics | Available in `AgentContext.metrics` process variable | Available in `AGENT_CONTEXT.cumulativeMetrics` + per-call detail in trail events |
| Tool call history | Not tracked (only current iteration's calls visible) | Full history in `AGENT_TRAIL_EVENT` records |
| Downstream coupling | Operate would need connector-internal knowledge | Structured, typed records through standard exporter pipeline |

## Appendix C: Relationship to Existing Research Documents

This final proposal consolidates and supersedes the findings from the exploratory research phase:

1. **`agent-context-research.md`** — Established the foundational two-concept model (committed state vs. execution trail). This proposal adopts and refines that model.

2. **`tool-call-results-research.md`** — Analyzed tool call/result representation options. This proposal adopts the recommendation that current/pending tool activity belongs in `AgentContext` while invocation/result history belongs in `AgentTrailEvent`.

3. **`corrected-architecture-followup.md`** — Corrected the earlier "completion-only" design where all data flowed through `CompleteJob`. This proposal adopts the corrected architecture: committed state through `CompleteJob`, execution trail through a separate publication path. **This correction is the authoritative basis for the final architecture.**

Where earlier documents suggested sending trail data through `CompleteJob` only, this proposal explicitly rejects that approach because it loses data on non-happy-path scenarios. The separate trail publication path is essential.
