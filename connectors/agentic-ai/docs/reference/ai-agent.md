# Camunda Agentic AI – AI Agent Reference

This document provides a comprehensive, code-level reference for the AI Agent implementation within the Camunda
Connectors `agentic-ai` module. It covers concepts, interaction patterns, data flow, concurrency challenges, and all
nuances of the distributed agent loop.

For MCP integration details, see [`mcp.md`](mcp.md).
For A2A integration details, see [`a2a.md`](a2a.md).

> **Read by section.** This is a long reference. Use the table of contents anchors (or `Read` with
> `offset`/`limit`) to jump to the one section you need, and avoid ingesting the whole file. The
> module [`AGENTS.md`](../../AGENTS.md) router maps common tasks to the exact section here.

---

## Table of Contents

1. [Foundational Concepts](#1-foundational-concepts)
2. [Two Flavors: AI Agent Task vs AI Agent Sub-process](#2-two-flavors)
3. [The Agentic Loop – Distributed Execution Model](#3-the-agentic-loop)
4. [Agent State Machine & Initialization](#4-agent-state-machine)
5. [Data Model & Agent Context](#5-data-model)
6. [Conversation Memory & Storage](#6-conversation-memory)
7. [Tool Resolution & Ad-Hoc Sub-Process Integration](#7-tool-resolution)
8. [Job Completion – The Heart of the Distributed Loop](#8-job-completion)
9. [What Happens When Tools Complete](#9-tool-completion)
10. [Concurrency Challenges & Race Conditions](#10-concurrency)
11. [Event Handling](#11-event-handling)
12. [Framework Abstraction & Converter Chain (LangChain4J)](#12-framework-abstraction)
13. [System Prompt Composition](#13-system-prompt-composition)
14. [Response Handling](#14-response-handling)
15. [Error Codes](#15-error-codes)
16. [Spring Auto-Configuration](#16-spring-auto-configuration)
17. [Process Instance Migration](#17-migration)
18. [Key Code Paths Reference](#18-code-paths)
19. [Gateway Tool Pattern](#19-gateway-tool-pattern)
20. [MCP Integration](#20-mcp-integration)
21. [A2A Integration](#21-a2a-integration)
22. [Examples Directory Reference](#22-examples)
23. [Agent Instance Integration](#23-agent-instance-integration)
24. [Architectural Invariants](#24-architectural-invariants)
25. [Extension Points](#25-extension-playbooks)

---

## 1. Foundational Concepts

### What is a Camunda Connector?

A **connector** is a reusable integration component that runs within the Camunda connector runtime. It implements `OutboundConnectorFunction` and is invoked as a **service task** in a BPMN process. The runtime:

1. Subscribes to Zeebe jobs of the connector's declared `type`
2. Activates a job when a process instance reaches the service task
3. Calls `execute(OutboundConnectorContext)` on the connector
4. Completes the job with the return value

Connectors are stateless from their own perspective — all state lives in process variables. The runtime handles job activation, secret injection, variable binding, and job completion.

### What is a Job Worker?

A **job worker** is a lower-level construct. While connectors use the job worker mechanism under the hood (the runtime is a job worker), a raw job worker gives direct control over:

- Which variables to fetch (`fetchVariables`)
- Whether to auto-complete (`autoComplete = false`)
- The exact `CompleteJobCommand` sent back to Zeebe, including ad-hoc sub-process control commands

### AdHocSubProcessConnectorResponse — Custom Job Completion via the SDK

The AI Agent Sub-process flavor needs custom job completion (ad-hoc sub-process directives) but is implemented as a standard `OutboundConnectorFunction`. This is enabled by the `ConnectorResponse` sealed interface hierarchy in the Connectors SDK:

- The connector returns an `AdHocSubProcessConnectorResponse` from `execute()`
- The runtime translates the response's `elementActivations()`, `completionConditionFulfilled()`, and `cancelRemainingInstances()` into the Zeebe complete command with `.withResult().forAdHocSubProcess()` configuration
- Completion variables are provided via `variables()`, and result expression evaluation is skipped by the runtime for `AdHocSubProcessConnectorResponse`
- The SDK continues to handle error expressions, retries, and metrics

This avoids duplicating SDK concerns in the connector. See [ADR 002](../adr/002-consolidate-job-worker-into-sdk.md) for the decision rationale.

### What is an Ad-Hoc Sub-Process?

An **ad-hoc sub-process** is a BPMN construct where inner elements (activities) are **not connected** to start/end events. Each element can be:

- Activated independently, in any order, multiple times, or skipped
- Connected internally via sequence flows for structured sub-sequences

**BPMN implementation** (default): Elements are activated via an `activeElementsCollection` expression evaluated on entry. A `completionCondition` expression controls when the sub-process completes.

**Job worker implementation**: The ad-hoc sub-process creates a **job** that a worker must handle. The worker controls:
1. Which elements to activate (via the completion command's `adHocSubProcess` result)
2. Whether the completion condition is fulfilled
3. Whether to cancel remaining active instances

Critical behavioral contract of the job worker ad-hoc sub-process:
- **One active job at a time**: Zeebe creates exactly one job for the ad-hoc sub-process
- **Job recreation on inner flow completion**: When any inner element/flow completes, Zeebe creates a **new** job for the ad-hoc sub-process
- **Job supersession**: A new job may be created while the worker is still processing the previous one. The old job becomes stale — completing it results in a `NOT_FOUND` rejection
- **`adHocSubProcessElements` variable**: Auto-created by Zeebe, contains metadata about activatable elements (ID, name, documentation, `fromAi()` parameters)

### Variable Scoping in Ad-Hoc Sub-Processes

Variables inside an ad-hoc sub-process have their own scope:
- **Input mappings** are evaluated once on entry (this matters for the Sub-process flavor — configuration is fixed for the lifetime of the sub-process)
- **Output collection**: `outputCollection` + `outputElement` expressions are evaluated when inner flows complete, collecting results into a local list variable
- **Variable propagation**: Local variables only propagate to the parent scope when the sub-process completes, unless explicitly mapped via output mappings

---

<a id="2-two-flavors"></a>

## 2. Two Flavors: AI Agent Task vs AI Agent Sub-process

### AI Agent Task (Outbound Connector)

- **BPMN element**: Service task with connector template applied
- **Class**: `AiAgentFunction` implements `OutboundConnectorFunction`
- **Type**: `io.camunda.agenticai:aiagent:1`
- **Execution context**: `OutboundConnectorAgentExecutionContext`
- **Request handler**: `OutboundConnectorAgentRequestHandler`
- **Tool resolution**: Fetches process definition XML via the Camunda API to resolve tool elements from a referenced ad-hoc sub-process (eventually consistent — can fail on first deploy)
- **Feedback loop**: Must be **modeled explicitly** in BPMN — the process must route tool calls to a multi-instance ad-hoc sub-process and route results back to the AI Agent task
- **Agent context**: Flows through process variables — the modeler must wire `agent.context` back as input for the next iteration
- **No event handling support**: Does not support event sub-processes
- **Job completion**: Handled by the connector runtime (auto-complete) — returns `AgentResponse` directly

### AI Agent Sub-process (Job Worker)

- **BPMN element**: Ad-hoc sub-process with job worker element template applied
- **Class**: `AiAgentJobWorker` with `@OutboundConnector`
- **Type**: `io.camunda.agenticai:aiagent-job-worker:1`
- **Execution context**: `JobWorkerAgentExecutionContext`
- **Request handler**: `JobWorkerAgentRequestHandler`
- **Tool resolution**: Tools come directly from the `adHocSubProcessElements` variable (populated by Zeebe) — no API call needed
- **Feedback loop**: **Implicit** — the job worker completes the job with element activation commands, and Zeebe automatically creates a new job when those elements complete
- **Agent context**: Stored as `agentContext` variable within the ad-hoc sub-process scope
- **Event handling**: Supports non-interrupting event sub-processes with configurable behavior
- **Job completion**: Manual via `jobClient.newCompleteCommand(job).withResult(...)` including ad-hoc sub-process directives

### Key Differences Summary

| Aspect                               | AI Agent Task                            | AI Agent Sub-process                                |
|--------------------------------------|------------------------------------------|-----------------------------------------------------|
| BPMN element                         | Service task                             | Ad-hoc sub-process                                  |
| Feedback loop                        | Explicit (modeled)                       | Implicit (engine-managed)                           |
| Tool resolution source               | Camunda API (XML fetch)                  | `adHocSubProcessElements` variable                  |
| Agent context management             | Via process variable wiring              | Scoped within sub-process                           |
| Event sub-process support            | No                                       | Yes (non-interrupting)                              |
| Config re-evaluation per iteration   | Yes (input mappings per task execution)  | No (input mappings evaluated once on AHSP entry)    |
| Process migration config changes     | Supported                                | Not supported (frozen at entry)                     |
| Job completion                       | Auto (connector runtime)                 | Custom (`AdHocSubProcessConnectorResponse`)         |

---

<a id="3-the-agentic-loop"></a>

## 3. The Agentic Loop – Distributed Execution Model

### AI Agent Sub-process Loop (Primary Focus)

The loop operates as a distributed state machine between the connector runtime and the Zeebe engine:

```
┌─────────────────────────────────────────────────────────────────┐
│                    Ad-Hoc Sub-Process Scope                      │
│                                                                  │
│  ┌──────┐    ┌──────────┐    ┌─────────┐    ┌───────────────┐  │
│  │Zeebe │───>│Job Worker│───>│   LLM   │───>│Complete Job + │  │
│  │creates│    │activates │    │ request │    │activate tools │  │
│  │ job   │    │& handles │    │         │    │ in AHSP       │  │
│  └──────┘    └──────────┘    └─────────┘    └───────┬───────┘  │
│       ▲                                             │           │
│       │         ┌──────────────────────┐            │           │
│       │         │ Tools execute within │            │           │
│       └─────────│ AHSP scope. On each  │◄───────────┘           │
│    (new job     │ completion, result   │                        │
│     created)    │ added to             │                        │
│                 │ toolCallResults via  │                        │
│                 │ outputCollection     │                        │
│                 └──────────────────────┘                        │
│                                                                  │
│  Loop terminates when: completionConditionFulfilled = true      │
│  (LLM returns no tool calls → agent has reached its goal)       │
└─────────────────────────────────────────────────────────────────┘
```

**Step-by-step flow:**

1. **Process enters AHSP**: Zeebe creates the first job for the ad-hoc sub-process. Input mappings are evaluated (provider config, prompts, memory config, etc. become local variables).

2. **Job activation**: The `AiAgentJobWorker` picks up the job with `fetchVariables = [adHocSubProcessElements, agentContext, toolCallResults, provider, data]`.

3. **Agent initialization** (`AgentInitializerImpl`):
   - First invocation: `agentContext` is null → state = `INITIALIZING`
   - Resolves tool definitions from `adHocSubProcessElements`
   - If no gateway tools → state transitions to `READY`
   - If gateway tools (MCP) → state = `TOOL_DISCOVERY`, returns discovery tool calls

4. **LLM interaction** (when state = `READY`, handled by `BaseAgentRequestHandler`):
   - Load the stored flat message list (via `ConversationSession`) and reconstruct it into turns (`TurnReconstructor.reconstruct`)
   - Compose the next turn input (`AgentConversationTurnInputComposer.compose`) → `AgentInput` (`None` / `Cancellation` / `NextTurn`)
   - On `NextTurn`: compose the system message, `AgentConversation.rehydrate` into a pending turn, check the model-call limit
   - Call LLM via `AiFrameworkAdapter` with a windowed `ConversationSnapshot`; `ingest` the assistant response into the turn
   - Store the updated conversation back to the memory store (via `ConversationSession`) and reduce it back to `AgentContext`
   - Transform tool calls and create response

5. **Job completion** (`AiAgentSubProcessConnectorResponse`):
   - Sets `agentContext` variable with updated state
   - If tool calls present:
     - `completionConditionFulfilled = false`
     - For each tool call: `activateElement(toolName)` with variables `{toolCall: <data>, toolCallResult: ""}`
     - Clears `toolCallResults = []` for the next iteration
   - If no tool calls:
     - `completionConditionFulfilled = true`
     - Sets `agent` response variable
     - AHSP completes, output propagates to parent scope

6. **Tool execution**: Activated elements run within the AHSP scope. Each tool receives `toolCall` variable with `_meta.id`, `_meta.name`, and the LLM-provided arguments at the top level. The tool is expected to produce a `toolCallResult` variable.

7. **Tool completion → new job**: When an inner flow completes, Zeebe:
   - Evaluates the `outputElement` expression: `={id: toolCall._meta.id, name: toolCall._meta.name, content: toolCallResult}`
   - Appends the result to the `toolCallResults` output collection
   - Creates a new job for the AHSP

8. **Loop continues**: The new job is picked up by the worker with the updated `toolCallResults`. The agent processes tool call results as `ToolCallResultMessage` entries and calls the LLM again.

### AI Agent Task Loop

The Task variant requires explicit BPMN modeling:

```
Start → AI Agent Task → Gateway (tool calls?)
           ▲                    │ yes          │ no
           │         Multi-Instance AHSP       ▼
           └─── (toolCallResults) ◄────    End/User Task
```

- The multi-instance ad-hoc sub-process executes all tool calls in parallel
- Results are collected into `toolCallResults` via the multi-instance output collection
- The process loops back to the AI Agent Task with the updated `toolCallResults` and `agent.context`

---

<a id="4-agent-state-machine"></a>

## 4. Agent State Machine & Initialization

The agent context tracks state via `AgentState` enum:

```
INITIALIZING ──────────────────────────────────> READY
      │                                            ▲
      │ (if gateway tools present)                 │
      ▼                                            │
TOOL_DISCOVERY ──(all results present)────────────┘
      │
      │ (not all results yet)
      ▼
  AgentDiscoveryInProgressInitializationResult
  (complete job as no-op, wait for more results)
```

**`AgentInitializerImpl.initializeAgent()`** is the entry point:

- **INITIALIZING**: First execution. Loads ad-hoc tool schema, determines tool definitions. If gateway tools exist, initiates discovery (returns tool calls to activate MCP/A2A clients). Otherwise transitions to READY.
- **TOOL_DISCOVERY**: Waiting for gateway tool discovery results. Checks if all expected results are present. If not, returns `AgentDiscoveryInProgressInitializationResult` (no-op completion). If yes, processes results and transitions to READY.
- **READY** (or any other state): Normal operation. Validates that the process definition hasn't changed (for migration detection). Updates tool definitions if needed.

---

<a id="5-data-model"></a>

## 5. Data Model & Agent Context

The **central piece of persisted state**: a snapshot of the agent carried as the `agentContext`
process variable (Sub-process) or via `agent.context` (Task). It holds the lifecycle state, the
resolved tools, a pointer to the stored conversation, cumulative metrics, and the metadata used to
detect process migration. Serialized as JSON, it is passed into every job activation and written back
on every completion. Authoritative definition:
[`AgentContext.java`](../../connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/model/AgentContext.java).

### AgentExecutionContext (the transient request context)

The transient per-invocation request context, assembled fresh for each job activation and never
persisted. It carries the job metadata, the inbound `AgentContext` and tool-call results, the LLM
provider configuration, the system- and user-prompt configurations, and the memory, limits, event, and
response settings that shape the turn. Authoritative definition:
[`AgentExecutionContext.java`](../../connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/model/AgentExecutionContext.java).

### AgentResponse (the output)

The result of an agent turn: the updated `AgentContext` plus either the tool calls to execute next or
a final response to emit (text, JSON, or the full assistant message). Authoritative definition:
[`AgentResponse.java`](../../connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/model/AgentResponse.java).

### AiAgentSubProcessConnectorResponse (job worker specific)

The job-worker completion directive: wraps the `AgentResponse` and tells the runtime how to complete
the ad-hoc sub-process job (whether the completion condition is fulfilled, whether to cancel
still-running tool instances, and which variables to set). Implements `AdHocSubProcessConnectorResponse`.
Authoritative definition:
[`AiAgentSubProcessConnectorResponse.java`](../../connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/AiAgentSubProcessConnectorResponse.java).

### ToolCallProcessVariable (tool call format for process variables)

Definition: [`ToolCallProcessVariable.java`](../../connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/model/tool/ToolCallProcessVariable.java).
Arguments are **flattened to the top level** so BPMN expressions can access them directly as
`toolCall.myParameter` rather than `toolCall.arguments.myParameter`. The `_meta` object holds the tool
call ID and name.

---

<a id="6-conversation-memory"></a>

## 6. Conversation Memory & Storage

### Architecture

Memory is managed through a layered architecture:

```
AgentConversation (immutable turn aggregate, transient)
    ▲ reconstruct            │ allMessages / window
    │                        ▼
ConversationSession (per-invocation, AutoCloseable)
    ▲ createSession          │ persist
    │                        ▼
ConversationStore (registered backend)
    │
    ├── InProcessConversationStore
    ├── CamundaDocumentConversationStore
    ├── AwsAgentCoreConversationStore
    └── Custom implementations
```

### AgentConversation, ConversationSnapshot & MessageWindowFilter

The in-process working representation of a conversation is the immutable `AgentConversation`
aggregate (see [ADR 007](../adr/007-agent-conversation-turn-aggregate.md)). It is built once per
invocation and transformed through copy-on-write methods.

`AgentConversation` (package `...aiagent.model`):
- Holds an optional `SystemMessage` (`null` when the composed system prompt is blank), a list of
  previous `AgentConversationTurn`s, the pending current turn, the durable base `AgentContext`, and the
  static `AgentConfiguration`.
- `rehydrate(history, systemMessage, inputMessages, agentContext, configuration)`: builds from the
  reconstructed history plus the composed system message and the current-turn input as a *pending*
  turn (`assistantMessage == null`).
- `ingest(assistantMessage, tokenUsage)`: completes the pending turn, recording per-turn metrics
  (1 model call, the token usage, and the tool-call count).
- `withStoredConversation(ref)`: updates the base context's persistence cursor after storing.
- `window(int size)`: applies `MessageWindowFilter.apply(allMessages(), size)` and returns a
  read-only `ConversationSnapshot`.
- `toAgentContext()`: reduces back to the serialized `AgentContext`, incrementing the durable
  `AgentContext.metrics` by the current turn's delta.
- `totalMetrics()`: returns the durable `AgentContext.metrics()` plus the current turn's delta —
  **not** a sum over the reconstructed turns, which always carry `AgentMetrics.empty()`. The
  model-call limit check (`BaseAgentRequestHandler.throwIfLimitsReached`) relies on this cumulative
  counter.

`AgentConversationTurn` (record, `...aiagent.model`) is one LLM call:
`(int iterationKey, List<Message> inputMessages, @Nullable AssistantMessage assistantMessage,
AgentMetrics metrics)`. `iterationKey` is 1-based across the agent lifetime; the turn is pending
while `assistantMessage == null`.

`TurnReconstructor.reconstruct(messages)` (`...aiagent.model`) rebuilds the turn list and the
optional system message from the persisted flat message list: the leading `SystemMessage` (if any)
is split off, and the remaining body is grouped by `AssistantMessage` boundaries. All reconstructed
turns carry `AgentMetrics.empty()` — per-invocation metrics are computed live from the current
turn, not read from history. This provides backward compatibility with existing conversations
without a data migration.

`ConversationSnapshot` (record, `...aiagent.memory`) is the transient, windowed, read-only view sent
to the LLM: `(List<Message> messages, List<ToolDefinition> toolDefinitions)`.

`MessageWindowFilter.apply(List<Message>, int maxMessages)` (`...aiagent.memory.runtime`) is a pure
static window filter:
  - Keeps at most `maxMessages` (default: 20, from `AgentConfiguration.contextWindowSize()`) messages
  - Tool-call-document user messages are excluded from the count
  - The system message is never evicted
  - When evicting an `AssistantMessage` with tool calls, also evicts the follow-up
    `ToolCallResultMessage` entries (some providers error on orphaned tool results)
  - Also evicts follow-up tool-call-document user messages attached to evicted results

### ConversationStore Implementations

**InProcessConversationStore** (`type = "in-process"`):
- Stores entire message history inside `AgentContext.conversation` as `InProcessConversationContext`
- Messages are serialized as part of the `agentContext` process variable
- **Durable**: Once job completion succeeds, all data is persisted by the Zeebe engine and survives runtime restarts
- Simple, but subject to Zeebe variable size limits — conversation growth inflates the process variable
- No transactional behavior, no compensation needed

**CamundaDocumentConversationStore** (`type = "camunda-document"`):
- Stores messages as a JSON document in Camunda Document Storage
- `AgentContext.conversation` only contains a `CamundaDocumentConversationContext` with a document reference and `previousDocuments` list
- On load: fetches document content, deserializes the stored flat message list
- On store: creates a **new document** each time (immutable documents), adds the previous reference to `previousDocuments`
- Supports configurable TTL and custom properties
- Supports transparent migration from `InProcessConversationContext`: if the context is in-process, it reads messages directly (no document to load)

**AwsAgentCoreConversationStore** (`type = "aws-agentcore"`):
- Stores messages as events in AWS Bedrock AgentCore Memory
- Uses a **branch-per-turn** strategy for isolation: each agent turn writes to a fresh branch, so failed job completions leave orphaned branches that are invisible on retry
- `AgentContext.conversation` contains `AwsAgentCoreConversationContext` with branch pointer (`branchName`, `lastEventId`) and system message
- On load: `ListEvents` with branch filter + `includeParentBranches=true` returns the full conversation chain
- On store: new messages written to a new branch forked from the previous turn's last event
- Conversational payloads feed AWS long-term memory extraction; structured data (tool calls, results) stored as versioned blob envelopes
- System messages preserved in context (AgentCore has no SYSTEM role)
- See [AWS AgentCore Memory reference](aws-agentcore-memory.md) for full details

**Custom implementations**: Fully pluggable via `ConversationStoreRegistry`. Users can register custom stores by:
1. Implementing `ConversationStore` (with `createSession` factory method), `ConversationSession` (with `loadMessages`/`storeMessages`), and `ConversationContext`
2. Annotating the custom `ConversationContext` with `@JsonTypeName("my-type")` and registering the subtype with the runtime `ObjectMapper` (e.g., via a Spring `Jackson2ObjectMapperBuilderCustomizer` calling `registerSubtypes()`)
3. Selecting "Custom Implementation" as memory storage type in the element template and specifying the implementation type string
4. Registering the store as a Spring component

See the [`CustomMemoryStorageConfiguration`](../../connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/model/request/MemoryStorageConfiguration.java) type for configuration, and [camunda-agentic-ai-customizations](https://github.com/maff/camunda-agentic-ai-customizations) for a working example with a JPA-backed store.

### Storage Contract

Every `ConversationStore` implementation must follow the **write-ahead with pointer-based visibility** pattern to guarantee correctness across retries.

#### The fundamental invariant

The `AgentContext` stored in Zeebe is the **sole source of truth** for which conversation data to read. The `ConversationContext` inside it acts as a pointer (storage cursor) to the data. The conversation store may write data ahead of Zeebe — but that data only becomes "committed" when Zeebe accepts the job completion containing the updated `AgentContext`.

If job completion fails (e.g., the job was superseded), Zeebe retries with the **old** `AgentContext`, which contains the **old** pointer. The newly written data is invisible to the retry and becomes an orphan.

#### Rules for implementations

1. **`storeMessages` must always write to a new location.** Never mutate or overwrite the data that the current `ConversationContext` points to. Create a new version, snapshot, branch, or record — then return a new `ConversationContext` pointing to it. This ensures the old pointer always resolves to the old data.

2. **`loadMessages` must be guided by the pointer.** Load only the data that the `ConversationContext` (from `agentContext.conversation()`) references. Do not load "latest" or "most recent" — that would break retry safety.

3. **Orphaned writes are expected.** When job completion fails after `storeMessages`, the written data is never pointed to. Implementations should tolerate orphans and may clean them up via the `onJobCompleted` / `onJobCompletionFailed` completion callbacks (see below).

4. **`ConversationContext` must be serializable and self-contained.** It is persisted as part of the `agentContext` process variable in Zeebe. It must contain everything needed to locate the conversation data (e.g., a document reference, a version number, a branch pointer) — without relying on external state that could change between turns.

#### How each built-in store satisfies this contract

| Store | Write target | Pointer | Orphan on failure |
|-------|-------------|---------|-------------------|
| **InProcess** | `agentContext` variable itself (messages in `ConversationContext`) | The variable *is* the data | No orphan — variable update and job completion fail together |
| **CamundaDocument** | New immutable document per turn | `document` reference in context | Orphaned document (tracked in `previousDocuments` for cleanup) |
| **AwsAgentCore** | New branch per turn (events forked from previous turn's last event) | `branchName` + `lastEventId` | Orphaned branch (invisible without pointer, no parent-chain traversal reaches it) |

### ConversationSession Lifecycle

`ConversationStore.createSession()` returns an `AutoCloseable` session. The handler manages it via try-with-resources:

```java
try (var session = store.createSession(executionContext, agentContext)) {
    var loaded = session.loadMessages(agentContext).messages();   // load flat history
    var history = TurnReconstructor.reconstruct(loaded);          // rebuild turns
    // [compose input → rehydrate → call LLM → ingest]
    var cursor = session.storeMessages(                           // persist
        conversation.toAgentContext(),
        ConversationStoreRequest.of(conversation.allMessages()));
    conversation = conversation.withStoredConversation(cursor);   // assemble
}
```

1. `ConversationStore.createSession()` creates and returns a session (the caller owns its lifecycle)
2. `session.loadMessages(agentContext)` returns a `ConversationLoadResult` containing the stored flat
   message history, which `TurnReconstructor.reconstruct` rebuilds into an `AgentConversation` history
3. Agent logic composes the turn input, rehydrates the conversation, calls the LLM, and `ingest`s the response
4. `session.storeMessages(...)` persists the full message list (`conversation.allMessages()`) and returns
   only the `ConversationContext` (storage cursor)
5. The caller folds the cursor back in via `conversation.withStoredConversation(cursor)`
6. `session.close()` handles resource cleanup (e.g., closing AWS clients)
7. Job completion sends the updated `AgentContext` (`conversation.toAgentContext()`) back to Zeebe

**Critical insight**: The conversation is stored **before** job completion. This is safe because all stores follow the write-ahead with pointer-based visibility contract — see [Storage Contract](#storage-contract) above.

### Completion Callbacks

After Zeebe accepts or rejects the job-completion command, the runtime notifies the connector
function via `JobCompletionListener` (from the connector SDK). Both `AiAgentFunction` and
`AiAgentJobWorker` implement this interface (via the shared `AgentConnectorFunction` mixin) and
delegate to an internal `AgentJobCompletionListener` carried by the response, which in turn
invokes the conversation store's `onJobCompleted` / `onJobCompletionFailed` hooks.

```
storeMessages(...)  →  Zeebe command sent  →  command future resolves  →  callback fires
                                                                           ├─ accepted  →  store.onJobCompleted(executionContext, context)
                                                                           └─ rejected  →  store.onJobCompletionFailed(executionContext, context, failure)
```

Carrying the callback on the response is what keeps the conversation store and agent context in
scope: both are captured during request handling and dispatched once the Zeebe command resolves.

**Callback timing**: All paths are asynchronous — callbacks fire only after the corresponding
Zeebe command (`completeJob`, `failJob`, or `throwBpmnError`) resolves. This applies uniformly
to successful completion, error-expression paths, and pre-response failures.

**Best-effort guarantee**: Callbacks may never fire (e.g., runtime crash before command dispatch). They are optimizations for cleanup (e.g., orphan removal), not correctness mechanisms. The [Storage Contract](#storage-contract) ensures correctness without callbacks.

**`JobCompletionFailure` hierarchy:**

- `CommandFailure` (sealed) — Zeebe rejected the command we sent:
  - `CommandFailed(cause)` — server-side rejection (network, internal error after retries)
  - `CommandIgnored(cause)` — the job was superseded (`NOT_FOUND`)
- `ExecutionFailed(cause, commandFailure?)` — connector or runtime hit an error before/while
  producing a response (function exception, error-expression evaluation, IgnoreError used by an
  unsupported connector type)
- `BpmnErrorThrown(errorCode, errorMessage, variables, commandFailure?)` — `throwBpmnError`
  dispatched via error expression
- `JobErrorRaised(errorMessage, variables, commandFailure?)` — `failJob` dispatched via error
  expression

The optional `commandFailure` field on `ExecutionFailed`, `BpmnErrorThrown`, and `JobErrorRaised`
surfaces the case where the failJob/throwBpmnError command sent in response was itself rejected
by Zeebe. It is `null` when Zeebe accepted the response command.

`CamundaDocumentConversationStore` overrides `onJobCompletionFailed` to clean up orphaned documents. Other built-in stores use the default no-op implementations. Custom stores can override either hook for cleanup or bookkeeping.

---

<a id="7-tool-resolution"></a>

## 7. Tool Resolution & Ad-Hoc Sub-Process Integration

### Job Worker Flavor

Tools come from the `adHocSubProcessElements` special variable populated by Zeebe:

```json
[
  {
    "elementId": "check_weather",
    "elementName": "Check Weather",
    "documentation": "Retrieves current weather data",
    "properties": { ... },
    "parameters": { ... }  // from fromAi() FEEL function
  }
]
```

The `AdHocToolsSchemaResolver` processes these into:
1. **ToolDefinitions**: Regular tools with name, description, and JSON schema for input parameters
2. **GatewayToolDefinitions**: Special tools (MCP clients, A2A) that need discovery

### Connector (Task) Flavor

The `ProcessDefinitionAdHocToolElementsResolver` fetches the BPMN XML from Camunda's API:
1. `ProcessDefinitionClient` calls `GET /process-definitions/{key}/xml` (with retries for eventual consistency)
2. `CamundaClientProcessDefinitionAdHocToolElementsResolver` parses the XML to find the ad-hoc sub-process by element ID
3. Extracts element metadata similar to the Zeebe-provided `adHocSubProcessElements`
4. Results are cached by `CachingProcessDefinitionAdHocToolElementsResolver` (Caffeine cache, default: max 100 entries, 10min TTL, configurable via `camunda.connector.agenticai.tools.process-definition.cache.*`)

### FEEL Parameter Extraction

`CamundaClientProcessDefinitionAdHocToolElementsResolver` extracts tool parameters from BPMN input/output mappings that use FEEL expressions with the `fromAi()` tagging function:

1. For each flow node in the AHSP, reads `ZeebeIoMapping` extension elements
2. For each mapping with a FEEL expression source (starts with `=`), calls `AdHocToolElementParameterExtractor.extractParameters()`
3. `AdHocToolElementParameterExtractorImpl` uses `FeelEngineApi` to parse the expression and `TaggedParameterExtractor` to extract `fromAi()` tagged parameters
4. Each `AdHocToolElementParameter` has: `name`, `description` (nullable), `type` (nullable), `schema` (nullable `Map<String,Object>`), `options` (nullable — contains `required` flag)

### Tool Schema Generation

`AdHocToolSchemaGeneratorImpl` converts extracted parameters into JSON Schema:
- Parameter names are stripped of `toolCall.` prefix and validated (no dots, no `_meta`)
- `schema` map from the parameter is used as base, with `type` and `description` overlaid
- If `type` is unset, defaults to `"string"`
- Required unless `options.required == false`
- Output: `{type: "object", properties: {...}, required: [...]}`

---

<a id="8-job-completion"></a>

## 8. Job Completion – The Heart of the Distributed Loop

### Job Worker Completion Flow

`AiAgentJobWorker` is an `OutboundConnectorFunction` wrapped by `SpringConnectorJobHandler` at runtime. The flow:

```
SpringConnectorJobHandler.handle(jobClient, job)
  │
  ├─ Creates OutboundConnectorContext from job variables
  │
  ├─ AiAgentJobWorker.execute(context)
  │    ├─ Binds variables to JobWorkerAgentRequest
  │    └─ agentRequestHandler.handleRequest(executionContext)
  │         └─ Returns AiAgentSubProcessConnectorResponse (AdHocSubProcessConnectorResponse)
  │
  ├─ SpringConnectorJobHandler examines error expression
  │    └─ Checks for error expressions (BPMN error handling)
  │
  └─ SpringConnectorJobHandler builds Zeebe command from response / failJob / throwBpmnError
       └─ Asynchronous command execution via CommandWrapper
```

### The Complete Command Structure

```java
jobClient.newCompleteCommand(job)
    .variables(completion.variables())    // {agentContext, toolCallResults/agent}
    .withResult(result -> {
        var adHocSubProcess = result.forAdHocSubProcess()
            .completionConditionFulfilled(...)  // true = done, false = continue
            .cancelRemainingInstances(...);      // true = cancel active tools

        for (toolCall : agentResponse.toolCalls()) {
            adHocSubProcess = adHocSubProcess
                .activateElement(toolCall.metadata().name())
                .variables(Map.of(
                    "toolCall", toolCall,
                    "toolCallResult", ""    // empty default to scope the variable locally
                ));
        }

        return adHocSubProcess;
    });
```

**Key design decisions in the completion command:**

1. **`toolCallResult` = ""**: An empty `toolCallResult` variable is created for each activated tool element. This prevents the variable from "bubbling up" to the parent AHSP scope during variable merging. Each tool writes its own `toolCallResult` in its local scope.

2. **`toolCallResults = []`**: When tool calls are present, the agent clears the results array so the next iteration starts fresh. This is set as a variable on the AHSP scope.

3. **`completionConditionFulfilled`**: Directly controls whether the AHSP terminates. When `true`, the AHSP completes and output mappings propagate results to the parent process.

4. **`cancelRemainingInstances`**: Used when event handling interrupts tool calls — cancels all still-running tool instances. Determined in `JobWorkerAgentRequestHandler.buildResponse()` from `conversation.currentTurn().hasInterruptedToolCallResults()`, which inspects the current turn's input messages for any `ToolCallResult` carrying the `PROPERTY_INTERRUPTED` flag.

5. **Async execution**: The complete command is sent asynchronously via `CommandWrapper` with up to 3 retries. This is important because:
   - The job may have been superseded (NOT_FOUND)
   - Network issues may occur

### No-Op Completion (Waiting for More Results)

When the agent cannot proceed (e.g., not all tool call results are present yet, or discovery is in progress):

```java
return AiAgentSubProcessConnectorResponse.builder()
    .completionConditionFulfilled(false)
    .cancelRemainingInstances(false)
    .build();
// No agentResponse, no variables, no element activations
// Just complete the job without doing anything → wait for next job
```

---

<a id="9-tool-completion"></a>

## 9. What Happens When Tools Complete

### Single Tool Completion

1. The tool activity (e.g., a script task, connector, user task) completes within the AHSP scope
2. The tool is expected to have produced a `toolCallResult` variable in its local scope
3. Zeebe evaluates the `outputElement` expression:
   ```
   ={id: toolCall._meta.id, name: toolCall._meta.name, content: toolCallResult}
   ```
4. The result is **appended** to the `toolCallResults` output collection list
5. Zeebe creates a **new job** for the AHSP

### Multiple Tools Completing Simultaneously / In Quick Succession

This is where the distributed nature creates interesting dynamics:

**Scenario: LLM requests tools A and B simultaneously**

```
Time    Zeebe                           Job Worker
─────────────────────────────────────────────────────
t0      Activates tools A, B
        (from previous job completion)

t1      Tool A completes
        → outputElement evaluated
        → toolCallResults = [{A result}]
        → Creates Job #1                Job #1 picked up
                                        Sees: toolCallResults = [{A result}]
                                        But LLM requested A AND B
                                        → Missing B result!

t2      Tool B completes                Job #1 still processing...
        → toolCallResults = [{A}, {B}]
        → Creates Job #2                (Job #1 is now STALE)

t3                                      Job #1: Determines B is missing
                                        → compose returns AgentInput.None
                                        → handleNoOp (no agentResponse)
                                        → No LLM call made
                                        → Job #1 completion may get NOT_FOUND
                                        → (or succeeds as no-op if Job #2 not created yet)

t4                                      Job #2 picked up
                                        Sees: toolCallResults = [{A}, {B}]
                                        → Both results present!
                                        → Calls LLM with all results
                                        → Proceeds normally
```

**The critical mechanism: `createToolCallResultMessage`**

In `AgentConversationTurnInputComposerImpl.compose`, when the last reconstructed turn ended with an
`AssistantMessage` that has tool calls (`history.turns().getLast().hasToolCalls()`):

```java
final var toolCallResultMessage =
    createToolCallResultMessage(
        agentContext,
        toolCalls,
        invocationInput.toolCallResults(),
        interruptMissingToolCalls);

// if empty, we wait on further tool call results to be added
if (toolCallResultMessage.isEmpty()) {
    return new AgentInput.None();
}
```

The method checks each tool call from the last assistant message against the available results
(`Optional<ToolCallResultMessage>`):
- If all present: creates a `ToolCallResultMessage` with results ordered to match the original tool call order
- If missing and NOT interrupting: returns `Optional.empty()` → `compose` returns `CompositionResult.Deferred` → handler completes as a no-op
- If missing and interrupting (due to event): creates cancelled results for missing tools

### No-Op Detection in BaseAgentRequestHandler

`BaseAgentRequestHandler.converse` switches on the `CompositionResult` returned by
`AgentConversationTurnInputComposer.compose`:

```java
return switch (compositionResult) {
    case CompositionResult.Deferred ignored ->
        handleNoOp(executionContext);          // wait for more tool results
    case CompositionResult.NoInput ignored ->
        handleNoInput(executionContext);       // nothing to add; handler decides
    case CompositionResult.NextTurn(var newMessages) ->
        proceed(...);                          // call the LLM
};
```

This is the key gate: when tool results were incomplete the composer returns `CompositionResult.Deferred`,
and `handleNoOp` completes the job as a no-op (the job worker waits for the next job, which will have
more results). When no input (user prompt, documents or events) is available at all, the composer returns
`CompositionResult.NoInput`. This variant carries no error semantics — each handler decides: the job
worker's `handleNoInput` completes without a response, while the outbound connector's `handleNoInput`
throws a `ConnectorException` with `ERROR_CODE_NO_USER_MESSAGE_CONTENT`.

---

<a id="10-concurrency"></a>

## 10. Concurrency Challenges & Race Conditions

### Challenge 1: Job Supersession (NOT_FOUND)

**Problem**: When a tool completes, Zeebe creates a new job. The previous job may still be processing. Completing the old job results in `NOT_FOUND`.

**Mitigation**:
- The `JobCallbackCommandWrapperFactory` retries up to 3 times with backoff
- The no-op completion pattern means most superseded jobs were doing nothing anyway
- Superseded jobs produce a `CommandIgnored` outcome — the conversation store receives `onJobCompletionFailed` with a `CommandIgnored` failure

### Challenge 2: Conversation Store Ahead of Zeebe

**Problem**: The conversation is written to storage **before** the job completion command is sent to Zeebe. If job completion fails, the store has data that Zeebe doesn't know about.

**Mitigation**: This is safe by design. All stores follow the [Storage Contract](#storage-contract): they write to a new location each turn, and the `ConversationContext` pointer in the old `AgentContext` still resolves to the old data. The newly written data becomes an orphan — harmless to correctness. Stores can use the `onJobCompletionFailed` callback to proactively clean up orphaned data.

### Challenge 3: Duplicate LLM Calls on Rapid Tool Completion

**Problem**: If tools A and B complete almost simultaneously, Job #1 (with only A's result) and Job #2 (with both results) may both attempt LLM calls.

**Mitigation**: This CANNOT happen due to the missing-results check. Job #1 will see that B's result is missing, so `AgentConversationTurnInputComposer.compose` returns `AgentInput.None` and the handler completes as a no-op. Only Job #2 (with complete results) will call the LLM.

### Challenge 4: Variable Scoping and Merging

**Problem**: When multiple tools run in parallel within the AHSP, their variables could conflict.

**Mitigation**:
- Each activated element gets its own scoped variables (`toolCall` and `toolCallResult` are created per-activation)
- The `toolCallResult = ""` empty default prevents the variable from bubbling up from a parent scope
- The `outputCollection`/`outputElement` mechanism properly collects results into a list

### Challenge 5: Stale Agent Context After Migration

**Problem**: After a process instance migration, the `agentContext` may reference tools from the old process definition.

**Mitigation**:
- `AgentInitializerImpl.handleReadyState()` checks if `processDefinitionKey` in the agent metadata matches the current execution's key
- If different: calls `toolsResolver.updateToolDefinitions()` which re-resolves from the new process definition
- Validates that no gateway tools were added/removed (not supported)
- Validates that no tools were removed (not supported)
- Updated/new tool definitions are merged

---

<a id="11-event-handling"></a>

## 11. Event Handling

Event handling is **exclusive to the Sub-process flavor**. In the AHSP, non-interrupting event sub-processes can fire while tools are running.

### How Events Arrive

1. An event sub-process fires within the AHSP (e.g., timer, message)
2. The event handler flow runs to completion
3. Optionally creates a `toolCallResult` variable (without an ID — this is what distinguishes it from a tool result)
4. The event flow completing triggers a new AHSP job

### Event Result Identification

The raw engine tool call results are pre-partitioned in `AgentInvocationInput.from`:

```java
// Partition tool call results: those WITH an id are tool results,
// those WITHOUT are event results
var partitioned =
    engineToolCallResults.stream().collect(Collectors.partitioningBy(r -> r.id() != null));
return new AgentInvocationInput(userPrompt, partitioned.get(true), partitioned.get(false));
```

Events produce `ToolCallResult` entries with `id = null`. `AgentConversationTurnInputComposerImpl.compose`
reads them as `invocationInput.eventMessages()` (separate from `invocationInput.toolCallResults()`)
and renders each via `createEventMessage`.

### Two Behaviors

**WAIT_FOR_TOOL_CALL_RESULTS** (default):
- Event messages are queued but the agent still waits for all tool call results
- Only after all tools complete, the event messages are added as user messages after the tool results
- Example message sequence: `[Tool A result, Tool B result, Event message]`

**INTERRUPT_TOOL_CALLS**:
- If events are present AND tool results are missing:
  - Missing tools get synthetic "cancelled" results: `ToolCallResult.forCancelledToolCall(id, name)`
  - The `cancelRemainingInstances` flag is set to `true` on the completion command
  - Active tool instances are terminated by Zeebe
- Example message sequence: `[Tool A cancelled, Tool B result, Event message]`
- The `PROPERTY_INTERRUPTED` flag on cancelled results triggers `cancelRemainingInstances` in `JobWorkerAgentRequestHandler.buildResponse()` (via `AgentConversationTurn.hasInterruptedToolCallResults()`)

### Event Payload

Events create their payload in `toolCallResult`:
- If non-empty: added as a user message (text or object content)
- If empty/null/blank: a generic message is generated:
  - With interrupt: "An event was triggered but no content was returned. All in-flight tool executions were canceled."
  - Without interrupt: "An event was triggered but no content was returned. Execution waited for all in-flight tool executions to complete before proceeding."

---

<a id="12-framework-abstraction"></a>

## 12. Framework Abstraction & Converter Chain (LangChain4J)

### Interface

```java
public interface AiFrameworkAdapter<R extends AiFrameworkChatResponse<?>> {
    R executeChatRequest(AgentExecutionContext executionContext, ConversationSnapshot snapshot);
}
```

The adapter receives a read-only `ConversationSnapshot` (the windowed message list plus the tool
definitions) and returns a response. `BaseAgentRequestHandler` builds the snapshot via
`conversation.window(configuration.contextWindowSize())` and passes it to the adapter.

The agent core is framework-agnostic. `AiFrameworkAdapter` abstracts:
- Converting internal message models to framework-specific formats
- Calling the LLM
- Converting the framework response back to internal models
- Updating `AgentContext` with metrics (model calls, token usage)

### LangChain4J Implementation

The current (and only) implementation uses LangChain4J:
- Configured via `AgenticAiLangchain4JFrameworkConfiguration`
- Supports multiple providers: Anthropic, OpenAI, AWS Bedrock, Google Vertex AI, Azure OpenAI, OpenAI Compatible
- **Does NOT use LangChain4J's built-in tool execution** — tool calls are returned as data, execution happens via BPMN

### Converter Chain Architecture

The module maintains its own domain model (framework-agnostic `Message`, `ToolCall`, `Content` types) separate from LangChain4J types. The converter chain translates between them:

```
Langchain4JAiFrameworkAdapter
  ├── ChatMessageConverter         # Message ↔ LangChain4J ChatMessage
  │     ├── ContentConverter       # Content → LangChain4J Content (for user messages)
  │     │     └── DocumentToContentConverter  # Camunda Document → LangChain4J Content
  │     └── ToolCallConverter      # ToolCall ↔ ToolExecutionRequest, ToolCallResult → ToolExecutionResultMessage
  ├── ToolSpecificationConverter   # ToolDefinition ↔ LangChain4J ToolSpecification
  │     └── JsonSchemaConverter    # Map<String,Object> ↔ LangChain4J JsonSchemaElement
  │           └── JsonSchemaElementModule  # Jackson module for JsonSchemaElement round-trip
  └── ChatModelFactory             # creates LangChain4J ChatModel per provider config
```

**Key converters:**

- **`ChatMessageConverter`**: Top-level converter. `map(Message)` dispatches on sealed type (System/User/Assistant/ToolCallResult). `toAssistantMessage(ChatResponse)` converts back, attaching metadata (timestamp, finishReason, tokenUsage).
- **`ContentConverter`**: Converts `TextContent` → text, `DocumentContent` → delegates to `DocumentToContentConverter`, `ObjectContent` → JSON string. Uses a copy of `ObjectMapper` with `DocumentToContentModule` for nested document serialization.
- **`DocumentToContentConverter`**: Dispatches on MIME type: `text/*` → `TextContent`; `application/pdf` → `PdfFileContent`; images → `ImageContent`; throws `DocumentConversionException` for unsupported types.
- **`ToolSpecificationConverter`**: Uses `JsonSchemaConverter` to convert between `Map<String,Object>` (domain) and `JsonObjectSchema` (LangChain4J). Throws `ParseSchemaException` if schema is not an object.
- **`JsonSchemaElementModule`**: Custom Jackson module needed because LangChain4J doesn't expose standard polymorphic annotations on `JsonSchemaElement`. Serializer/deserializer handle all concrete types (`JsonObjectSchema`, `JsonEnumSchema`, `JsonStringSchema`, `JsonArraySchema`, `JsonAnyOfSchema`, `JsonReferenceSchema`, etc.).
- **`DocumentToContentModule`**: Jackson module registering `DocumentToContentSerializer` for Camunda `Document` objects in tool call result content — serializes to `{type, media_type, data}` structure.

All converter beans are `@ConditionalOnMissingBean`, activated when `camunda.connector.agenticai.framework=langchain4j` (default).

---

<a id="13-system-prompt-composition"></a>

## 13. System Prompt Composition

### SystemPromptContributor Interface

```java
public interface SystemPromptContributor {
    String contribute(AgentExecutionContext executionContext, AgentContext agentContext);
    default int getOrder() { return 0; }  // lower values sort earlier
}
```

Spring auto-wires all `SystemPromptContributor` beans into the composer.

### SystemPromptComposerImpl

`compose(AgentExecutionContext executionContext, AgentContext agentContext)`:
1. Starts with the base system prompt from `SystemPromptConfiguration.prompt()` (via `AgentConfiguration.systemPrompt()`)
2. Iterates contributors sorted by `getOrder()` ascending
3. Non-blank contributions are collected and all parts joined with `"\n\n"`

### Known Implementations

| Implementation               | Order | Activation Condition                                  |
|------------------------------|-------|-------------------------------------------------------|
| `A2aSystemPromptContributor` | 100   | `agentContext.properties["a2aClients"]` is non-empty  |

The architecture supports adding more contributors by creating a Spring bean implementing the interface — the composer picks them up automatically.

### Usage Site

The core request handler invokes the composer once per turn while assembling the conversation. A blank
composed prompt produces no system message (nothing is added or persisted); a non-blank one becomes a
`SystemMessage` placed at the head of the conversation when it is rehydrated for the LLM.

---

<a id="14-response-handling"></a>

## 14. Response Handling

### AgentResponseHandlerImpl

Creates the `AgentResponse` from the LLM's assistant message:

1. **Response format**:
   - `TextResponseFormatConfiguration`: Response text returned as `responseText`. Optionally parse JSON from it.
   - `JsonResponseFormatConfiguration`: Response text must be valid JSON, parsed into `responseJson`. Fails with `FAILED_TO_PARSE_RESPONSE_CONTENT` if invalid.

2. **Markdown stripping**: Before JSON parsing, markdown code fences (` ```json ... ``` `) are stripped to handle models that wrap JSON in code blocks.

3. **Full message**: If `includeAssistantMessage` is true, the raw `AssistantMessage` is included in the response.

### Job Worker Response Variables

On final completion (no tool calls):
- `agent` variable = `JobWorkerAgentResponse` containing:
  - `responseText`, `responseJson`, `responseMessage`
  - Optionally `context` (if `includeAgentContext` is true)
- `agentContext` variable = updated agent context

On tool call iteration (tool calls present):
- `agentContext` = updated agent context
- `toolCallResults` = `[]` (cleared for next iteration)

---

<a id="15-error-codes"></a>

## 15. Error Codes

### AgentErrorCodes

The codes are defined in
[`AgentErrorCodes`](../../connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/agent/AgentErrorCodes.java)
and thrown as `ConnectorException(errorCode, message)`. To find a code's current throw site, search the
codebase for its constant. `Value` is the externally visible error code (what BPMN error handling keys
on).

| Constant                                                | Value                                          | When                                                                   |
|---------------------------------------------------------|------------------------------------------------|------------------------------------------------------------------------|
| `ERROR_CODE_NO_USER_MESSAGE_CONTENT`                    | `NO_USER_MESSAGE_CONTENT`                      | user messages list is empty (task connector)                            |
| `ERROR_CODE_TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT`         | `TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT`           | tool results arrive but there is no prior conversation                  |
| `ERROR_CODE_MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED`      | `MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED`        | model calls reached the configured maximum                              |
| `ERROR_CODE_FAILED_TO_PARSE_RESPONSE_CONTENT`           | `FAILED_TO_PARSE_RESPONSE_CONTENT`             | JSON parse failure (explicit JSON response format only)                 |
| `ERROR_CODE_FAILED_MODEL_CALL`                          | `FAILED_MODEL_CALL`                            | the chat model call threw                                               |
| `ERROR_CODE_MIGRATION_MISSING_TOOLS`                    | `MIGRATION_MISSING_TOOLS`                      | existing tools were removed after a process migration                   |
| `ERROR_CODE_MIGRATION_GATEWAY_TOOL_DEFINITIONS_CHANGED` | `MIGRATION_GATEWAY_TOOL_DEFINITIONS_CHANGED`   | gateway tools were added or removed after a process migration           |
| `ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED`             | `AGENT_INSTANCE_CREATION_FAILED`               | agent instance creation failed (retries exhausted or non-retryable)     |
| `ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED`               | `AGENT_INSTANCE_UPDATE_FAILED`                 | agent instance update failed (retries exhausted or non-retryable)       |
| `ERROR_CODE_AGENT_INSTANCE_HISTORY_ITEM_FAILED`         | `AGENT_INSTANCE_HISTORY_ITEM_FAILED`           | agent instance history item write failed (retries exhausted or non-retryable) |

Additional errors from `CamundaClientProcessDefinitionAdHocToolElementsResolver`:
- `AD_HOC_SUB_PROCESS_NOT_FOUND` — element ID doesn't resolve to an `AdHocSubProcess` in BPMN
- `AD_HOC_TOOL_DEFINITION_INVALID` — FEEL expression on a mapping fails to parse

All errors are thrown as `ConnectorException(errorCode, message)`.

For MCP error codes, see [mcp.md §15](mcp.md#15-error-codes).
For A2A error codes, see [a2a.md §15](a2a.md#15-error-codes).

---

<a id="16-spring-auto-configuration"></a>

## 16. Spring Auto-Configuration

### AgenticAiConnectorsAutoConfiguration

Master configuration class. Activated by `@ConditionalOnBooleanProperty("camunda.connector.agenticai.enabled", matchIfMissing=true)` — on by default.

Imports:
- `AgenticAiLangchain4JFrameworkConfiguration` — LangChain4J converter chain and adapter
- `McpDiscoveryConfiguration`, `McpClientConfiguration`, `McpRemoteClientConfiguration` — MCP (see [mcp.md §14](mcp.md#14-spring-configuration))
- `A2aClientOutboundConnectorConfiguration`, `A2aClientAgenticToolConfiguration`, `A2aClientPollingConfiguration`, `A2aClientWebhookConfiguration` — A2A (see [a2a.md §14](a2a.md#14-spring-configuration))

### Key Differences from Standard Connectors

1. **Dual activation modes**: Both an outbound connector (`AiAgentFunction`) and a job worker (`AiAgentJobWorker`) are registered. The job worker bypasses the standard connector runtime, handling variable resolution, secret injection, and exception handling directly.
2. **Pluggable AI framework**: `AiFrameworkAdapter<?>` SPI allows the LangChain4J stack to be replaced. The LangChain4J config is guarded by `@ConditionalOnProperty(camunda.connector.agenticai.framework)` (default: `langchain4j`).
3. **Pluggable system prompt contributors**: All `SystemPromptContributor` beans are auto-collected into `SystemPromptComposerImpl`.
4. **Pluggable gateway tool handlers**: All `GatewayToolHandler` beans are auto-collected into `GatewayToolHandlerRegistryImpl`.
5. **Caffeine caching of BPMN resolution**: Process definition fetch (API + XML parse + FEEL extraction) is cached with configurable TTL and max size.
6. **Dual conversation stores**: `InProcessConversationStore` and `CamundaDocumentConversationStore` are both registered; `ConversationStoreRegistry` selects based on request config.

### Feature Toggles

| Property                                                          | Default      | Controls                           |
|-------------------------------------------------------------------|--------------|------------------------------------|
| `camunda.connector.agenticai.enabled`                             | `true`       | Master switch                      |
| `camunda.connector.agenticai.aiagent.outbound-connector.enabled`  | `true`       | AI Agent Task connector            |
| `camunda.connector.agenticai.aiagent.job-worker.enabled`          | `true`       | AI Agent Sub-process job worker    |
| `camunda.connector.agenticai.ad-hoc-tools-schema-resolver.enabled` | `true`     | Ad-Hoc Tools Schema connector     |
| `camunda.connector.agenticai.framework`                           | `langchain4j` | AI framework implementation      |

### Key Configuration Defaults

Configurable under the `camunda.connector.agenticai.*` prefix, in three main areas: process-definition
fetch retries and the BPMN-resolution Caffeine cache (`tools.processDefinition.*`), and the chat-model
API timeout (`aiagent.chatModel.api.*`). For the current defaults, see
[`AgenticAiConnectorsConfigurationProperties`](../../connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/autoconfigure/AgenticAiConnectorsConfigurationProperties.java);
each property carries its own `@DefaultValue`.

---

<a id="17-migration"></a>

## 17. Process Instance Migration

### How Detection Works

`AgentInitializerImpl.handleReadyState()`:

```java
if (agentMetadata == null
    || !executionMetadata.processDefinitionKey().equals(agentMetadata.processDefinitionKey())) {
    agentContext = toolsResolver.updateToolDefinitions(executionContext, agentContext)
        .withMetadata(executionMetadata);
}
```

If the `processDefinitionKey` stored in the agent context doesn't match the current job's key, tool definitions are refreshed.

### What's Allowed

- **Adding new tools**: Merged into existing definitions
- **Changing tool descriptions/parameters**: Updated in-place
- **Changing tool implementation** (without changing definition): Transparent — agent doesn't see the change

### What's Blocked

- **Removing/renaming tools**: `ERROR_CODE_MIGRATION_MISSING_TOOLS`
- **Adding/removing gateway tools (MCP/A2A)**: `ERROR_CODE_MIGRATION_GATEWAY_TOOL_DEFINITIONS_CHANGED`

### Task vs Sub-process Migration Difference

- **Task**: Input mappings are re-evaluated each loop iteration, so config changes (system prompt, model, etc.) are picked up immediately
- **Sub-process**: Input mappings are evaluated once on AHSP entry. Config changes via migration are **not** picked up for running instances

---

<a id="18-code-paths"></a>

## 18. Key Code Paths Reference

### Entry Points
- `AiAgentFunction.execute()` → Connector (Task) entry point
- `AiAgentJobWorker.execute()` → Job worker (Sub-process) entry point
- `AiAgentJobWorker.execute()` wraps into `AiAgentSubProcessConnectorResponse` → handled by `SpringConnectorJobHandler`

### Core Agent Logic
- `BaseAgentRequestHandler.handleRequest()` → Core orchestrator: init → load + reconstruct → compose → rehydrate → LLM → ingest → persist → complete
- `AgentInitializerImpl.initializeAgent()` → State machine / initialization
- `TurnReconstructor.reconstruct()` → Rebuilds turns + system message from the stored flat message list
- `AgentConversationTurnInputComposerImpl.compose()` → Turn input assembly (tool results, events, user prompt) → `AgentInput`
- `AgentConversationTurnInputComposerImpl.createToolCallResultMessage()` → Tool result matching & missing detection
- `AgentConversation.rehydrate()` / `ingest()` / `window()` / `toAgentContext()` → Immutable turn aggregate lifecycle
- `BaseAgentRequestHandler.throwIfLimitsReached()` → Model-call limit check (reads `totalMetrics().modelCalls()`)
- `AgentResponseHandlerImpl.createResponse()` → Response formatting

### Job Completion
- `AiAgentSubProcessConnectorResponse.elementActivations()` → AHSP element activations from tool calls
- `JobWorkerAgentRequestHandler.buildConnectorResponse()` → Job worker response assembly (no-op vs response)

### Memory
- `ConversationStoreRegistryImpl.getConversationStore()` → Store resolution
- `InProcessConversationSession.loadMessages()` / `storeMessages()` → In-process persistence
- `CamundaDocumentConversationSession.loadMessages()` / `storeMessages()` → Document persistence
- `MessageWindowFilter.apply()` → Context window sliding (called by `AgentConversation.window()`)

### Tool Resolution
- `AgentToolsResolverImpl.loadAdHocToolsSchema()` → Tool schema loading
- `AgentToolsResolverImpl.updateToolDefinitions()` → Migration tool refresh
- `AdHocToolsSchemaResolverImpl` → Schema generation from tool elements
- `AdHocToolElementParameterExtractorImpl` → FEEL expression parameter extraction
- `AdHocToolSchemaGeneratorImpl` → Parameter → JSON Schema conversion

### System Prompt
- `SystemPromptComposerImpl.compose()` → Aggregates base prompt + contributions
- `A2aSystemPromptContributor` → A2A protocol instructions (order 100)

### Framework (LangChain4J)
- `Langchain4JAiFrameworkAdapter.executeChatRequest()` → Main LLM call path
- `ChatMessageConverterImpl` → Message conversion chain
- `ToolSpecificationConverterImpl` → Tool definition conversion
- `ChatModelFactoryImpl` → Provider-specific ChatModel creation

### Configuration
- `AgenticAiConnectorsAutoConfiguration` → Spring Boot bean definitions
- `ConnectorConfigurationOverrides` (connector-runtime-core) → Type/timeout overrides via env vars

### Class Diagram

```mermaid
classDiagram
    direction TB

    %% --- Entry points ---
    class AiAgentFunction {
        <<OutboundConnectorFunction>>
    }
    class AiAgentJobWorker {
        <<OutboundConnectorFunction>>
    }

    %% --- Request handling ---
    class AgentRequestHandler~C, R~ {
        <<interface>>
        +handleRequest(C) R
    }
    class BaseAgentRequestHandler~C, R~ {
        <<abstract>>
    }
    class OutboundConnectorAgentRequestHandler
    class JobWorkerAgentRequestHandler

    BaseAgentRequestHandler ..|> AgentRequestHandler
    OutboundConnectorAgentRequestHandler --|> BaseAgentRequestHandler
    JobWorkerAgentRequestHandler --|> BaseAgentRequestHandler

    AiAgentFunction --> OutboundConnectorAgentRequestHandler
    AiAgentJobWorker --> JobWorkerAgentRequestHandler

    %% --- Core orchestration dependencies ---
    class AgentInitializer {
        <<interface>>
    }
    class AgentConversationTurnInputComposer {
        <<interface>>
    }
    class AgentResponseHandler {
        <<interface>>
    }

    BaseAgentRequestHandler --> AgentInitializer
    BaseAgentRequestHandler --> AgentConversationTurnInputComposer
    BaseAgentRequestHandler --> AiFrameworkAdapter
    BaseAgentRequestHandler --> AgentResponseHandler
    BaseAgentRequestHandler --> ConversationStoreRegistry
    BaseAgentRequestHandler --> SystemPromptComposer
    BaseAgentRequestHandler ..> AgentConversation : builds

    %% --- Gateway tool SPI ---
    class GatewayToolCallTransformer {
        <<interface>>
    }
    class GatewayToolHandler {
        <<interface>>
    }
    class GatewayToolHandlerRegistry {
        <<interface>>
    }

    GatewayToolHandler --|> GatewayToolCallTransformer
    GatewayToolHandlerRegistry --|> GatewayToolCallTransformer
    GatewayToolHandlerRegistry o-- GatewayToolHandler : manages *

    %% --- Tool resolution ---
    class AgentToolsResolver {
        <<interface>>
    }
    class AdHocToolsSchemaResolver {
        <<interface>>
    }

    AgentInitializer --> AgentToolsResolver
    AgentInitializer --> GatewayToolHandlerRegistry
    AgentToolsResolver --> AdHocToolsSchemaResolver

    %% --- Memory architecture ---
    class ConversationStoreRegistry {
        <<interface>>
    }
    class ConversationStore {
        <<interface>>
    }
    class ConversationSession {
        <<interface>>
    }
    class InProcessConversationStore
    class CamundaDocumentConversationStore

    ConversationStoreRegistry o-- ConversationStore : manages *
    InProcessConversationStore ..|> ConversationStore
    CamundaDocumentConversationStore ..|> ConversationStore
    ConversationStore ..> ConversationSession : creates

    %% --- Turn aggregate ---
    class AgentConversation
    class AgentConversationTurn
    class TurnReconstructor
    class ConversationSnapshot
    class MessageWindowFilter

    AgentConversation o-- AgentConversationTurn : turns *
    TurnReconstructor ..> AgentConversationTurn : reconstructs
    AgentConversation ..> ConversationSnapshot : window()
    AgentConversation ..> MessageWindowFilter : uses
    AiFrameworkAdapter ..> ConversationSnapshot : consumes

    %% --- System prompt composition ---
    class SystemPromptComposer {
        <<interface>>
    }
    class SystemPromptContributor {
        <<interface>>
    }

    AgentConversationTurnInputComposer --> GatewayToolHandlerRegistry
    SystemPromptComposer o-- SystemPromptContributor : aggregates *

    %% --- Framework abstraction ---
    class AiFrameworkAdapter~R~ {
        <<interface>>
    }
    class Langchain4JAiFrameworkAdapter

    Langchain4JAiFrameworkAdapter ..|> AiFrameworkAdapter
```

### E2E Tests
- `connectors-e2e-test/connectors-e2e-test-agentic-ai/` — Full integration tests
- `BaseAiAgentJobWorkerTest` — Job worker test base
- `BaseAiAgentConnectorTest` — Connector test base
- `L4JAiAgentJobWorkerFeedbackLoopTests` — Feedback loop tests
- `L4JAiAgentJobWorkerToolCallingTests` — Tool calling tests

---

<a id="19-gateway-tool-pattern"></a>

## 19. Gateway Tool Pattern

The Gateway Tool Pattern is the extensibility mechanism that allows the AI Agent to integrate with external tool providers that expose **multiple tools behind a single BPMN element**. MCP and A2A are both implementations of this pattern.

### Core Concept

A regular tool in the ad-hoc sub-process is a 1:1 mapping: one BPMN element = one tool the LLM can call. A **gateway tool** breaks this: one BPMN element = a **gateway** to many tools that are discovered at runtime.

For example, an MCP Client element "MyFilesystem" might expose `readFile`, `writeFile`, and `listDirectory`. The LLM sees three separate tools, but the BPMN only has one element.

### Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│ Tool Schema Resolution (AdHocToolsSchemaResolverImpl)                │
│                                                                      │
│  adHocSubProcessElements → for each element:                         │
│    1. Check GatewayToolDefinitionResolvers                           │
│       - McpClientGatewayToolDefinitionResolver (type = "mcpClient")  │
│       - A2aGatewayToolDefinitionResolver (type = "a2aClient")        │
│    2. If match → GatewayToolDefinition (not a regular tool)          │
│    3. If no match → ToolDefinition (regular tool with JSON schema)   │
│                                                                      │
│  Result: AdHocToolsSchemaResponse {                                  │
│    toolDefinitions: [...],          // regular tools                 │
│    gatewayToolDefinitions: [...]    // gateway tools needing discovery│
│  }                                                                   │
└──────────────────────────────────────────────────────────────────────┘
```

### Gateway Detection

Gateway elements are identified via BPMN **extension properties**. The element template sets:

```
extensionProperties = {
  @ExtensionProperty(
    name = "io.camunda.agenticai.gateway.type",   // GatewayToolDefinitionResolver.GATEWAY_TYPE_EXTENSION
    value = "mcpClient",                            // or "a2aClient"
    condition = @PropertyCondition(property = "data.connectorMode.type", equals = "aiAgentTool"))
}
```

`TypePropertyBasedGatewayToolDefinitionResolver` checks each element's properties for a matching `io.camunda.agenticai.gateway.type` value. Both `McpClientGatewayToolDefinitionResolver` and `A2aGatewayToolDefinitionResolver` extend this base class.

### Gateway Tool Handler Interface

`GatewayToolHandler` is the core interface that each gateway type implements:

```java
public interface GatewayToolHandler extends GatewayToolCallTransformer {
    String type();                              // e.g., "mcpClient", "a2aClient"
    boolean isGatewayManaged(String toolName);  // tool name prefix check

    // Discovery lifecycle
    GatewayToolDiscoveryInitiationResult initiateToolDiscovery(agentContext, gatewayToolDefinitions);
    boolean allToolDiscoveryResultsPresent(agentContext, toolCallResults);
    boolean handlesToolDiscoveryResult(toolCallResult);
    List<ToolDefinition> handleToolDiscoveryResults(agentContext, toolCallResults);

    // Migration
    GatewayToolDefinitionUpdates resolveUpdatedGatewayToolDefinitions(agentContext, gatewayToolDefinitions);

    // Document extraction (default falls back to ContentTreeDocumentWalker for raw content trees)
    default List<Document> extractDocuments(ToolCallResult toolCallResult);
}

// From GatewayToolCallTransformer:
public interface GatewayToolCallTransformer {
    List<ToolCall> transformToolCalls(agentContext, toolCalls);             // LLM → process
    List<ToolCallResult> transformToolCallResults(agentContext, results);   // process → LLM
}
```

### Gateway Tool Handler Registry

`GatewayToolHandlerRegistryImpl` wraps multiple `GatewayToolHandler` instances and distributes operations:

- **Discovery initiation**: Iterates all handlers, collects discovery tool calls, merges agent context updates
- **Discovery result check**: All handlers must report all results present
- **Discovery result processing**: Groups results by handler type, merges discovered tool definitions into agent context
- **Tool call transformation**: Chains through all handlers (each transforms its own tool calls, passes others through)
- **Tool call result transformation**: Same chaining for results

### Discovery Flow (State Machine Integration)

The discovery flow integrates with the agent state machine from [§4](#4-agent-state-machine):

```
INITIALIZING
  ├─ AdHocToolsSchemaResolver → identifies gateway elements
  ├─ GatewayToolHandlerRegistry.initiateToolDiscovery()
  │   ├─ MCP handler: creates ToolCall("MCP_toolsList_<elementId>", "<elementId>", {method: "tools/list"})
  │   └─ A2A handler: creates ToolCall("A2A_fetchAgentCard_<elementId>", "<elementId>", {operation: "fetchAgentCard"})
  │
  ├─ If discovery calls exist:
  │   └─ Return AgentResponseInitializationResult with tool calls
  │      State → TOOL_DISCOVERY
  │      Job completed with: activateElement for each discovery call
  │
  └─ If no discovery calls:
      └─ State → READY (proceed normally)

TOOL_DISCOVERY
  ├─ Tool results arrive (e.g., MCP listTools response, A2A agent card)
  ├─ GatewayToolHandlerRegistry.allToolDiscoveryResultsPresent()
  │   ├─ Not all present → AgentDiscoveryInProgressInitializationResult (no-op completion)
  │   └─ All present:
  │       ├─ GatewayToolHandlerRegistry.handleToolDiscoveryResults()
  │       │   ├─ MCP: each tool from listTools → ToolDefinition("MCP_<elementId>___<mcpToolName>")
  │       │   └─ A2A: agent card → single ToolDefinition("A2A_<elementId>")
  │       ├─ Merge into agentContext.toolDefinitions
  │       └─ State → READY
```

### Tool Call Name Transformation

The gateway pattern uses namespaced tool names to maintain uniqueness:

**LLM sees** (fully qualified): `MCP_MyFilesystem___readFile`
**Process sees** (element activation): `MyFilesystem` with variables `{toolCall: {method: "tools/call", params: {name: "readFile", arguments: {...}}}}`

This mapping happens in `transformToolCalls()` (LLM → process) and `transformToolCallResults()` (process → LLM).

### Agent Context Properties

Gateway handlers store per-handler state in `AgentContext.properties`:

- MCP: `properties.mcpClients = ["elementId1", "elementId2"]` — list of MCP client element IDs
- A2A: `properties.a2aClients = ["elementId1", "elementId2"]` — list of A2A client element IDs

These are used during discovery checking and tool call result transformation.

### Document Extraction from Tool Call Results

Tool call results may contain Camunda `Document` instances — at the root, nested in maps/lists, or
embedded inside typed gateway responses (e.g. `McpDocumentContent`, A2A artifacts). The agent
extracts those documents into a synthetic follow-up `UserMessage` with native `DocumentContent`
blocks so LLMs can interpret them; see [ADR-004](../adr/004-document-handling-in-tool-call-results.md).

`ToolCallResultDocumentExtractor` is the entrypoint, called from `AgentConversationTurnInputComposerImpl` after
the `ToolCallResultMessage` is built. For each result it asks the registry which handler manages
the tool name (`GatewayToolHandlerRegistry.handlerForToolDefinition`) and either:

- delegates to that handler's `extractDocuments(ToolCallResult)` — handlers walk their own typed
  content (sealed-type `switch` over `McpContent` / `A2aSendMessageResult`), so documents inside
  typed records remain discoverable;
- falls back to `ContentTreeDocumentWalker` — a stateless static utility that recursively walks
  `Map`, `Collection`, `Object[]` and `Document` nodes. Used for plain BPMN tools whose content is
  the raw FEEL tree from the engine.

The default `GatewayToolHandler.extractDocuments` implementation also delegates to
`ContentTreeDocumentWalker`, so third-party handlers that return raw maps work without overriding.
Handlers whose typed content embeds raw user-generated subtrees can call the walker directly on
those subtrees.

```
ToolCallResultDocumentExtractor.extractDocuments(results)
  ├─ for each result:
  │    GatewayToolHandlerRegistry.handlerForToolDefinition(result.name())
  │      ├─ Some(handler) → handler.extractDocuments(result)        ── typed walk
  │      └─ None          → ContentTreeDocumentWalker.extractDocumentsFromContent(content)
  └─ groups documents by ToolCallDocuments(toolCallId, toolCallName, documents)
```

---

<a id="20-mcp-integration"></a>

## 20. MCP Integration

MCP (Model Context Protocol) enables the AI Agent to discover and call tools from MCP servers. Two connector types:

- **MCP Client** (`McpClientFunction`, type `io.camunda.agenticai:mcpclient:1`): Pre-configured MCP connections on the runtime
- **MCP Remote Client** (`McpRemoteClientFunction`, type `io.camunda.agenticai:mcpremoteclient:1`): On-demand remote connections

Tool naming: `MCP_<elementName>___<mcpToolName>` — one MCP server = many tools, triple-underscore separates gateway element from tool name.

For the complete MCP reference including data model, client lifecycle, transport/auth configuration, filtering, Spring configuration, and error codes, see **[`mcp.md`](mcp.md)**.

---

<a id="21-a2a-integration"></a>

## 21. A2A Integration

A2A (Agent-to-Agent) enables the AI Agent to interact with remote autonomous agents. Unlike MCP (discrete tools), A2A exposes **entire agents** as single tools.

- **A2A Client Outbound** (`A2aClientOutboundConnectorFunction`, type `io.camunda.agenticai:a2aclient:0`)
- **A2A Client Polling Inbound** (`A2aClientPollingExecutable`, type `io.camunda.agenticai:a2aclient:polling:0`)
- **A2A Client Webhook Inbound** (`A2aClientWebhookExecutable`, type `io.camunda.agenticai:a2aclient:webhook:0`)

Tool naming: `A2A_<elementName>`. `A2aSystemPromptContributor` injects protocol instructions when A2A tools are detected.

For the complete A2A reference including data model, connector modes, SDK client layer, async patterns, Spring configuration, and error codes, see **[`a2a.md`](a2a.md)**.

---

<a id="22-examples"></a>

## 22. Examples Directory Reference

The `examples/` directory contains reference BPMN processes and configurations. When making code changes that affect
connector behavior, element template properties, or data model shapes, update the relevant examples to stay in sync.

### AI Agent Examples

- **`ai-agent/ad-hoc-sub-process/`**: Recommended approach — AI Agent as job worker on AHSP
  - Shows tool elements inside AHSP, event handling, and the implicit feedback loop
- **`ai-agent/service-task/`**: AI Agent as connector on service task
  - Shows explicit BPMN loop with gateway checking for tool calls
  - Multi-instance AHSP for parallel tool execution

### MCP Examples

- **`mcp/standalone/`**: MCP Client used independently (not as AI Agent tool)
  - Shows direct tool listing, tool calling, resource and prompt operations

### A2A Examples

- **`a2a/a2a-agent-integration/`**: A2A Client integrated as AI Agent gateway tool
  - Shows A2A Client element inside AHSP alongside regular tools
  - Demonstrates multi-turn interaction with remote agents
- **`a2a/a2a-polling/`**: A2A Client with polling for async tasks
  - Shows outbound → intermediate catch event pattern for async task completion
- **`a2a/a2a-push-notification/`**: A2A Client with webhook push notifications
  - Shows outbound → webhook intermediate catch event pattern

### Ad-Hoc Tools Schema Example

- **`ad-hoc-tools-schema/`**: Direct use of the Ad-Hoc Tools Schema Resolver
  - For custom LLM connectors that want to leverage AHSP tool metadata without the full AI Agent

---

<a id="23-agent-instance-integration"></a>

## 23. Agent Instance Integration

The agent reports its lifecycle to the engine's **agent instance** API via `AgentInstanceClient`
(`CamundaAgentInstanceClient`). All calls silently skip when the `agentInstanceKey` is `null` (agents
that pre-date the feature) and retry transient failures via `CamundaApiRetry`. A `404` is treated as
**retryable** for updates and history items, because a freshly created agent instance may not yet be
visible to follow-up calls (eventual consistency).

### Status & metrics

- **Status** (`AgentInstanceUpdateStatus`): `THINKING` is sent synchronously before the LLM call;
  `IDLE`/`TOOL_CALLING` after, `TOOL_DISCOVERY` on the discovery path.
- **Metrics delta**: per-turn `modelCalls`, `inputTokens`, `outputTokens`, `toolCalls` are sent on or
  after job completion (synchronously on terminal turns, deferred via a completion listener on
  intermediate tool-call turns).

### Conversation history items

During `BaseAgentRequestHandler.proceed`, the agent appends conversation history items around the LLM
call (`POST /v2/agent-instances/{key}/history` via `newCreateAgentHistoryItemCommand`):

- **Before the chat request** — `createHistoryForInputMessages(turn)` emits history items for the
  current turn's new input messages: a `UserMessage` → one `USER` item (covers the user prompt, event
  messages, and virtual document-reference messages); a `ToolCallResultMessage` → **one `TOOL_RESULT`
  item per result**, each with that result's content block(s) and a single-entry `toolCalls` array
  `{toolCallId: result.id(), toolName: result.name(), elementId, arguments}` correlating it back to
  the originating tool call. The `arguments` are the originating request arguments, looked up by
  tool-call id from the previous turn's assistant message via
  `AgentConversation.previousTurnToolCallsById()` (so each `TOOL_RESULT` item is self-contained for
  the paginated history API and need not be joined back to the `ASSISTANT` item); they fall back to
  `{}` for events (`id == null`), unmatched ids, or calls without arguments.
- **After the chat request** — `createHistoryForAssistantMessage(turn)` emits one `ASSISTANT` item with
  the assistant text, the assistant's `toolCalls`, and per-call `metrics` (input/output tokens +
  `durationMs`, measured via `AiFrameworkAdapter.executeMeasuringTime` and carried on the turn's
  `AgentMetrics.executionTime`). Empty assistant content (tool-only turns) falls back to a single
  `"No content"` text block, since the API rejects empty content.

Each `toolCalls` entry carries the BPMN **`elementId`** alongside the (LLM-visible, possibly
namespaced) `toolName`. For tool results it is resolved once on the model in
`AgentConversationTurnInputComposerImpl` (`ToolCallResult.elementId`) and read directly; for assistant
tool calls the mapper resolves it from the namespaced name via `GatewayToolHandlerRegistry.resolveElementId`.
For ad-hoc tools the element id equals the tool name; for gateway tools (MCP/A2A) it is the BPMN
gateway element id parsed from the namespaced name.

Content blocks map by type: `TextContent` → text, `ObjectContent` → object (or JSON text), and
`DocumentContent` → a document reference block (Camunda documents only; external document references
currently fall back to an object/text block — see follow-ups). The item carries the current turn's
`iterationKey` and the active `jobKey`; the engine discards superseded/non-completed items by
observing job completion (`jobLease` enforcement is a planned follow-up, camunda/camunda#55033).

Failures to write a history item are **fatal** to the turn (propagated after retries), unlike the
best-effort metrics updates.

---

<a id="24-architectural-invariants"></a>

## 24. Architectural Invariants

These are the boundaries that keep the module maintainable and framework-pluggable. They hold today
and **must not be broken** by changes. They are documented here as the single source of truth; a
planned ArchUnit suite (epic [#7537](https://github.com/camunda/connectors/issues/7537)) will encode
them so violations fail the build. Until then, enforcement is by review, so respect them deliberately.

### I1. The agent core is framework-agnostic

Only the LangChain4J adapter package may depend on LangChain4J.

- **Rule**: nothing outside `io.camunda.connector.agenticai.aiagent.framework.langchain4j.**` may
  import `dev.langchain4j.*`. The agent core (`aiagent/agent`, `aiagent/model`, `aiagent/memory`, the
  root `model/`, `adhoctoolsschema/`, `tool/`) stays framework-neutral.
- **Why**: the LLM framework is an SPI (`AiFrameworkAdapter`, see [§12](#12-framework-abstraction)).
  Keeping LangChain4J behind the adapter means it can be replaced without touching orchestration,
  memory, or the data model.
- **Verify**: `grep -rl "import dev.langchain4j" --include="*.java"
  connector-agentic-ai/src/main/java/io/camunda/connector/agenticai | grep -v /framework/langchain4j/` returns nothing.

### I2. Domain types never leak framework types

The module owns a framework-agnostic domain model in `io.camunda.connector.agenticai.model.*` (the
`Message`, `Content`, `ToolCall`, and `ToolDefinition` sealed types).

- **Rule**: these types must not expose LangChain4J types in their API. Conversion to/from
  `dev.langchain4j` types happens **only** in the converter chain (see [§12](#12-framework-abstraction)
  for the converters).
- **Why**: a leak here re-couples the whole codebase to the framework and defeats I1.

### I3. Interface in package root, single `*Impl` alongside

Public collaborators are interfaces; the single implementation is named `<Interface>Impl` in the same
package (e.g. `ConversationStoreRegistry` / `ConversationStoreRegistryImpl`). The convention is
pervasive, so follow the nearest existing pair.

- **Rule**: depend on the interface (constructor injection); register the `Impl` as a Spring bean
  (typically `@ConditionalOnMissingBean` so it can be overridden). Don't inject `*Impl` directly.

### I4. Extension points are plug-in SPIs

Add capability by implementing an SPI and registering a bean. Do not modify the core to special-case
a new provider, store, or contributor. The SPIs:

| SPI                       | Add a…                          | Where to start      |
|---------------------------|---------------------------------|---------------------|
| `ChatModelProvider<T>`    | LLM provider                    | [§25.1](#251-add-an-llm-provider) |
| `SystemPromptContributor` | system-prompt contribution      | [§25.2](#252-add-a-systempromptcontributor) |
| `ConversationStore`       | conversation memory backend     | [§25.3](#253-add-a-conversationstore) |
| `GatewayToolHandler`      | multi-tool gateway (MCP/A2A)    | [§19](#19-gateway-tool-pattern) |

### I5. Conversation stores follow the write-ahead, pointer-based contract

Any `ConversationStore` must write each turn to a **new** location and load **only** what the
`AgentContext` pointer references; never overwrite data the current pointer resolves to. This is the
correctness foundation for retry/supersession safety; full statement in
[§6 (Storage Contract)](#6-conversation-memory).

---

<a id="25-extension-playbooks"></a>

## 25. Extension Points

Where to start for each SPI from [§24 I4](#24-architectural-invariants). The pattern is always the
same: implement the interface, copy the nearest existing implementation, and register a Spring bean.
This section points at code rather than transcribing the steps, so it cannot drift; read the named
reference implementation and its wiring for the current exact procedure, and respect the invariants in
[§24](#24-architectural-invariants).

<a id="251-add-an-llm-provider"></a>

### 25.1 Add an LLM provider

Implement `ChatModelProvider<T extends ProviderConfiguration>` and add the matching
`ProviderConfiguration` subtype. The `ChatModelProviderRegistry` selects providers by `type()`.
Reference implementation: `AnthropicChatModelProvider` with `AnthropicProviderConfiguration`. The
strategy is the only place that may touch `dev.langchain4j` (invariant I1).

<a id="252-add-a-systempromptcontributor"></a>

### 25.2 Add a `SystemPromptContributor`

Implement `SystemPromptContributor` (see [§13](#13-system-prompt-composition)).
`SystemPromptComposerImpl` auto-collects every contributor bean and orders them by `getOrder()`.
Reference implementation: `A2aSystemPromptContributor`.

<a id="253-add-a-conversationstore"></a>

### 25.3 Add a `ConversationStore`

Implement `ConversationStore`, `ConversationSession`, and `ConversationContext`, following the
write-ahead, pointer-based storage contract in [§6](#6-conversation-memory) (invariant I5).
`ConversationStoreRegistryImpl` selects the store by `type()`. Reference implementation: the in-process
store (`InProcessConversationStore` and its session/context). For a custom store outside this module,
see [camunda-agentic-ai-customizations](https://github.com/maff/camunda-agentic-ai-customizations).
