# Camunda Agentic AI – AI Agent Core Architecture Deep Dive

This document provides a comprehensive, code-level analysis of the AI Agent implementation within the Camunda Connectors `agentic-ai` module. It covers concepts, interaction patterns, data flow, concurrency challenges, and all nuances of the distributed agent loop.

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
12. [Framework Abstraction (LangChain4J)](#12-framework-abstraction)
13. [Response Handling](#13-response-handling)
14. [Process Instance Migration](#14-migration)
15. [Key Code Paths Reference](#15-code-paths)
16. [Gateway Tool Pattern](#16-gateway-tool-pattern)
17. [MCP Integration Deep Dive](#17-mcp-deep-dive)
18. [A2A Integration Deep Dive](#18-a2a-deep-dive)
19. [Examples Directory Reference](#19-examples)

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

The AI Agent Sub-process (job worker flavor) uses `autoComplete = false` because it needs to send custom completion commands that include ad-hoc sub-process element activation instructions.

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

## 2. Two Flavors: AI Agent Task vs AI Agent Sub-process {#2-two-flavors}

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
- **Class**: `AiAgentJobWorker` with `@JobWorker(autoComplete = false)`
- **Type**: `io.camunda.agenticai:aiagent-job-worker:1`
- **Execution context**: `JobWorkerAgentExecutionContext`
- **Request handler**: `JobWorkerAgentRequestHandler`
- **Tool resolution**: Tools come directly from the `adHocSubProcessElements` variable (populated by Zeebe) — no API call needed
- **Feedback loop**: **Implicit** — the job worker completes the job with element activation commands, and Zeebe automatically creates a new job when those elements complete
- **Agent context**: Stored as `agentContext` variable within the ad-hoc sub-process scope
- **Event handling**: Supports non-interrupting event sub-processes with configurable behavior
- **Job completion**: Manual via `jobClient.newCompleteCommand(job).withResult(...)` including ad-hoc sub-process directives

### Key Differences Summary

| Aspect | AI Agent Task | AI Agent Sub-process |
|---|---|---|
| BPMN element | Service task | Ad-hoc sub-process |
| Feedback loop | Explicit (modeled) | Implicit (engine-managed) |
| Tool resolution source | Camunda API (XML fetch) | `adHocSubProcessElements` variable |
| Agent context management | Via process variable wiring | Scoped within sub-process |
| Event sub-process support | No | Yes (non-interrupting) |
| Config re-evaluation per iteration | Yes (input mappings per task execution) | No (input mappings evaluated once on AHSP entry) |
| Process migration config changes | Supported | Not supported (frozen at entry) |
| Job completion | Auto (connector runtime) | Manual (custom command) |

---

## 3. The Agentic Loop – Distributed Execution Model {#3-the-agentic-loop}

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

4. **LLM interaction** (when state = `READY`):
   - Load conversation from memory store into runtime memory
   - Validate limits (max model calls)
   - Add system prompt, user prompt / tool call results to memory
   - Call LLM via `AiFrameworkAdapter`
   - Store updated conversation back to memory store
   - Transform tool calls and create response

5. **Job completion** (`AiAgentJobWorkerHandlerImpl.prepareCompleteCommand`):
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

## 4. Agent State Machine & Initialization {#4-agent-state-machine}

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

## 5. Data Model & Agent Context {#5-data-model}

### AgentContext (the persistent state)

```java
record AgentContext(
    AgentState state,           // INITIALIZING, TOOL_DISCOVERY, READY
    AgentMetadata metadata,     // processDefinitionKey, processInstanceKey
    AgentMetrics metrics,       // modelCalls count, tokenUsage
    List<ToolDefinition> toolDefinitions,  // resolved tools
    ConversationContext conversation,       // memory storage reference
    Map<String, Object> properties         // extensible properties (e.g., gateway tool state)
)
```

This is the **central piece of persisted state**. It is:
- Stored as the `agentContext` process variable (Sub-process) or part of `agent.context` (Task)
- Serialized/deserialized as JSON via Jackson
- Passed between every job activation
- Updated and written back on every job completion

### AgentExecutionContext (the transient request context)

Contains everything needed for a single execution:
- `jobContext()`: Job metadata (process definition key, element ID, etc.)
- `initialAgentContext()`: The `AgentContext` from input variables
- `initialToolCallResults()`: Tool call results from the current invocation
- `provider()`: LLM provider configuration
- `systemPrompt()`, `userPrompt()`: Prompt configurations
- `memory()`, `limits()`, `events()`, `response()`: Various configurations

### AgentResponse (the output)

```java
record AgentResponse(
    AgentContext context,                    // updated agent context
    List<ToolCallProcessVariable> toolCalls, // tool calls to execute
    AssistantMessage responseMessage,         // optional full message
    String responseText,                      // optional text response
    Object responseJson                       // optional parsed JSON response
)
```

### JobWorkerAgentCompletion (job worker specific)

Wraps the response with job completion control:
```java
record JobWorkerAgentCompletion(
    AgentResponse agentResponse,
    boolean completionConditionFulfilled,   // true = AHSP done
    boolean cancelRemainingInstances,        // true = cancel active tools
    Map<String, Object> variables,           // variables to set
    Consumer<Throwable> onCompletionError   // error compensation callback
)
```

### ToolCallProcessVariable (tool call format for process variables)

```java
record ToolCallProcessVariable(
    @JsonProperty("_meta") ToolCallMetadata metadata,  // {id, name}
    @JsonAnySetter @JsonAnyGetter Map<String, Object> arguments  // flattened at root
)
```

Arguments are **flattened to the top level** so BPMN expressions can access them directly as `toolCall.myParameter` rather than `toolCall.arguments.myParameter`. The `_meta` object holds the tool call ID and name.

---

## 6. Conversation Memory & Storage {#6-conversation-memory}

### Architecture

Memory is managed through a layered architecture:

```
RuntimeMemory (in-process, transient)
    ▲ load         │ store
    │              ▼
ConversationSession (per-invocation)
    ▲ create       │ persist
    │              ▼
ConversationStore (registered backend)
    │
    ├── InProcessConversationStore
    ├── CamundaDocumentConversationStore
    └── Custom implementations
```

### RuntimeMemory

`RuntimeMemory` is the in-process working memory for a single agent execution:
- `DefaultRuntimeMemory`: Simple list of messages
- `MessageWindowRuntimeMemory`: Wraps a delegate with a sliding window filter:
  - Keeps at most `maxMessages` (default: 20) messages
  - System message is never evicted
  - When evicting an `AssistantMessage` with tool calls, also evicts the follow-up `ToolCallResultMessage` entries
  - `allMessages()` returns the full history (for persistence)
  - `filteredMessages()` returns the windowed view (for LLM API calls)

### ConversationStore Implementations

**InProcessConversationStore** (`type = "in-process"`):
- Stores entire message history inside `AgentContext.conversation` as `InProcessConversationContext`
- Messages are serialized as part of the `agentContext` process variable
- Simple, but subject to Zeebe variable size limits (default: ~4MB)
- No transactional behavior, no compensation needed

**CamundaDocumentConversationStore** (`type = "camunda-document"`):
- Stores messages as a JSON document in Camunda Document Storage
- `AgentContext.conversation` only contains a `CamundaDocumentConversationContext` with a document reference
- On load: fetches document content, deserializes messages
- On store: creates a **new document** each time (immutable documents), keeps references to previous documents
- **Previous document retention**: Keeps the last 2 previous documents as a safety net for recovery, purges older ones
- Supports configurable TTL and custom properties
- **Compensation on failure**: The `compensateFailedJobCompletion` hook exists but current implementation does not actively compensate — the document is already written before job completion is attempted

**Custom implementations**: Pluggable via `ConversationStoreRegistry` — Self-Managed users can register custom stores.

### ConversationSession Lifecycle

1. `ConversationStore.executeInSession()` creates a session
2. `session.loadIntoRuntimeMemory()`: Loads previous conversation into RuntimeMemory
3. Agent logic adds messages, calls LLM, gets response
4. `session.storeFromRuntimeMemory()`: Persists updated conversation
5. Job completion sends the updated `AgentContext` (with new conversation reference) back to Zeebe

**Critical insight**: The conversation is stored **before** job completion. If job completion fails (e.g., job was superseded), the stored conversation state may be ahead of what Zeebe knows. This is the `onCompletionError` callback's purpose — to allow stores to compensate (though current implementations don't actively roll back).

---

## 7. Tool Resolution & Ad-Hoc Sub-Process Integration {#7-tool-resolution}

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
4. Results are cached (`CachingProcessDefinitionAdHocToolElementsResolver`) with configurable TTL

### Tool Schema Generation

`AdHocToolSchemaGeneratorImpl` converts `fromAi()` parameter definitions into JSON Schema:
- Input parameters become schema properties
- Types map to JSON Schema types
- Required/optional status is preserved
- Descriptions become schema descriptions

---

## 8. Job Completion – The Heart of the Distributed Loop {#8-job-completion}

### Job Worker Completion Flow

`AiAgentJobWorkerHandlerImpl` handles the complete flow:

```
handle(jobClient, job)
  │
  ├─ executionContextFactory.createExecutionContext(jobClient, job)
  │    └─ Binds job variables to JobWorkerAgentRequest
  │
  ├─ agentRequestHandler.handleRequest(executionContext)
  │    └─ Returns JobWorkerAgentCompletion
  │
  ├─ connectorResultHandler.examineErrorExpression(...)
  │    └─ Checks for error expressions (BPMN error handling)
  │
  └─ completeJob / failJob / throwBpmnError
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

4. **`cancelRemainingInstances`**: Used when event handling interrupts tool calls — cancels all still-running tool instances.

5. **Async execution**: The complete command is sent asynchronously via `CommandWrapper` with up to 3 retries. This is important because:
   - The job may have been superseded (NOT_FOUND)
   - Network issues may occur
   - The `onCompletionError` callback handles failure compensation

### No-Op Completion (Waiting for More Results)

When the agent cannot proceed (e.g., not all tool call results are present yet, or discovery is in progress):

```java
return JobWorkerAgentCompletion.builder()
    .completionConditionFulfilled(false)
    .cancelRemainingInstances(false)
    .build();
// No agentResponse, no variables, no element activations
// Just complete the job without doing anything → wait for next job
```

---

## 9. What Happens When Tools Complete {#9-tool-completion}

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
                                        → "modelCallPrerequisitesFulfilled" returns false
                                        → No LLM call made
                                        → completeJob with null agentResponse (no-op)
                                        → Job #1 completion may get NOT_FOUND
                                        → (or succeeds as no-op if Job #2 not created yet)

t4                                      Job #2 picked up
                                        Sees: toolCallResults = [{A}, {B}]
                                        → Both results present!
                                        → Calls LLM with all results
                                        → Proceeds normally
```

**The critical mechanism: `createToolCallResultMessage`**

In `AgentMessagesHandlerImpl`, when the last message in memory is an `AssistantMessage` with tool calls:

```java
if (lastChatMessage instanceof AssistantMessage assistantMessage
    && assistantMessage.hasToolCalls()) {
    ToolCallResultMessage toolCallResultMessage =
        createToolCallResultMessage(
            agentContext, assistantMessage.toolCalls(),
            actualToolCallResults, interruptMissingToolCalls);
    
    if (toolCallResultMessage != null) {
        messages.add(toolCallResultMessage);
    }
    // If null → not all results present → no messages added
}
```

The method checks each tool call from the last assistant message against the available results:
- If all present: creates a `ToolCallResultMessage` with results ordered to match the original tool call order
- If missing and NOT interrupting: returns `null` → no messages → `modelCallPrerequisitesFulfilled` returns `false` → no-op
- If missing and interrupting (due to event): creates cancelled results for missing tools

### Job Worker `modelCallPrerequisitesFulfilled`

```java
// JobWorkerAgentRequestHandler
protected boolean modelCallPrerequisitesFulfilled(...) {
    return !CollectionUtils.isEmpty(addedUserMessages);
}
```

This is the key gate: if no user messages were added (because tool results were incomplete), the agent simply does nothing — completes the job as a no-op, and waits for the next job (which will have more results).

---

## 10. Concurrency Challenges & Race Conditions {#10-concurrency}

### Challenge 1: Job Supersession (NOT_FOUND)

**Problem**: When a tool completes, Zeebe creates a new job. The previous job may still be processing. Completing the old job results in `NOT_FOUND`.

**Mitigation**: 
- The `CommandWrapper` retries up to 3 times via `CommandExceptionHandlingStrategy`
- The `onCompletionError` callback on `JobWorkerAgentCompletion` allows conversation stores to compensate
- The no-op completion pattern means most superseded jobs were doing nothing anyway

### Challenge 2: Conversation Store Ahead of Zeebe

**Problem**: The conversation is written to storage (document store or in-process context) **before** the job completion command is sent. If the job completion fails:
- In-process store: The updated `agentContext` (including conversation) is in the completion variables that failed to send — so both fail together. Safe.
- Document store: The document was already written. The `agentContext` variable in the completion command references this new document. If completion fails, the next job will use the OLD `agentContext` which references the OLD document. The new document becomes an orphan, but the agent state is consistent because it re-uses the old context.

**Mitigation**:
- The `compensateFailedJobCompletion` hook exists on `ConversationStore`
- Previous document retention (last 2 documents kept) provides a recovery path
- The "document ahead of Zeebe" scenario is benign for correctness: the old conversation is a valid subset, the agent simply re-does the LLM call

### Challenge 3: Duplicate LLM Calls on Rapid Tool Completion

**Problem**: If tools A and B complete almost simultaneously, Job #1 (with only A's result) and Job #2 (with both results) may both attempt LLM calls.

**Mitigation**: This CANNOT happen due to the missing-results check. Job #1 will see that B's result is missing, return `null` for `addedUserMessages`, and `modelCallPrerequisitesFulfilled` returns `false`. Only Job #2 (with complete results) will call the LLM.

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

## 11. Event Handling {#11-event-handling}

Event handling is **exclusive to the Sub-process flavor**. In the AHSP, non-interrupting event sub-processes can fire while tools are running.

### How Events Arrive

1. An event sub-process fires within the AHSP (e.g., timer, message)
2. The event handler flow runs to completion
3. Optionally creates a `toolCallResult` variable (without an ID — this is what distinguishes it from a tool result)
4. The event flow completing triggers a new AHSP job

### Event Result Identification

In `AgentMessagesHandlerImpl.addUserMessages()`:

```java
// Partition tool call results: those WITH an id are tool results, 
// those WITHOUT are event results
final var partitionedByToolCallId = toolCallResults.stream()
    .collect(Collectors.partitioningBy(result -> result.id() != null));
final List<ToolCallResult> actualToolCallResults = partitionedByToolCallId.get(true);
final List<Message> eventMessages = partitionedByToolCallId.get(false).stream()
    .map(eventResult -> createEventMessage(eventResult, interruptToolCallsOnEventResults))
    .toList();
```

Events produce `ToolCallResult` entries with `id = null`, which are separated from actual tool results.

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
- The `PROPERTY_INTERRUPTED` flag on cancelled results triggers `cancelRemainingInstances` in `handleAddedUserMessages()`

### Event Payload

Events create their payload in `toolCallResult`:
- If non-empty: added as a user message (text or object content)
- If empty/null/blank: a generic message is generated:
  - With interrupt: "An event was triggered but no content was returned. All in-flight tool executions were canceled."
  - Without interrupt: "An event was triggered but no content was returned. Execution waited for all in-flight tool executions to complete before proceeding."

---

## 12. Framework Abstraction (LangChain4J) {#12-framework-abstraction}

### Interface

```java
public interface AiFrameworkAdapter<R extends AiFrameworkChatResponse<?>> {
    R executeChatRequest(
        AgentExecutionContext executionContext,
        AgentContext agentContext,
        RuntimeMemory runtimeMemory);
}
```

The agent core is framework-agnostic. `AiFrameworkAdapter` abstracts:
- Converting internal message models to framework-specific formats
- Calling the LLM
- Converting the framework response back to internal models
- Updating `AgentContext` with metrics (model calls, token usage)

### LangChain4J Implementation

The current (and only) implementation uses LangChain4J:
- Configured via `AgenticAiLangchain4JFrameworkConfiguration`
- Supports multiple providers: Anthropic, OpenAI, AWS Bedrock, Google Vertex AI, Azure OpenAI, OpenAI Compatible
- Converts `RuntimeMemory.filteredMessages()` to LangChain4J chat messages
- Converts tool definitions to LangChain4J tool specifications
- **Does NOT use LangChain4J's built-in tool execution** — tool calls are returned as data, execution happens via BPMN

---

## 13. Response Handling {#13-response-handling}

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

## 14. Process Instance Migration {#14-migration}

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

## 15. Key Code Paths Reference {#15-code-paths}

### Entry Points
- `AiAgentFunction.execute()` → Connector (Task) entry point
- `AiAgentJobWorker.execute()` → Job worker (Sub-process) entry point
- `AiAgentJobWorkerHandlerImpl.handle()` → Job worker processing logic

### Core Agent Logic
- `BaseAgentRequestHandler.handleRequest()` → Main orchestration method
- `AgentInitializerImpl.initializeAgent()` → State machine / initialization
- `AgentMessagesHandlerImpl.addUserMessages()` → Message assembly (tool results, events, user prompt)
- `AgentMessagesHandlerImpl.createToolCallResultMessage()` → Tool result matching & missing detection
- `AgentResponseHandlerImpl.createResponse()` → Response formatting

### Job Completion
- `AiAgentJobWorkerHandlerImpl.prepareCompleteCommand()` → AHSP completion command with tool activations
- `JobWorkerAgentRequestHandler.completeJob()` → Job worker completion logic (no-op vs response)
- `JobWorkerAgentCompletion.onCompletionError()` → Failure compensation

### Memory
- `ConversationStoreRegistryImpl.getConversationStore()` → Store resolution
- `InProcessConversationSession.loadIntoRuntimeMemory()` / `storeFromRuntimeMemory()` → In-process persistence
- `CamundaDocumentConversationSession.loadIntoRuntimeMemory()` / `storeFromRuntimeMemory()` → Document persistence
- `MessageWindowRuntimeMemory.filteredMessages()` → Context window sliding

### Tool Resolution
- `AgentToolsResolverImpl.loadAdHocToolsSchema()` → Tool schema loading
- `AgentToolsResolverImpl.updateToolDefinitions()` → Migration tool refresh
- `AdHocToolsSchemaResolverImpl` → Schema generation from tool elements

### Configuration
- `AgenticAiConnectorsAutoConfiguration` → Spring Boot bean definitions
- `AiAgentJobWorkerValueCustomizer` → Job worker type/timeout overrides

### E2E Tests
- `connectors-e2e-test/connectors-e2e-test-agentic-ai/` → Full integration tests
- `BaseAiAgentJobWorkerTest` → Job worker test base
- `BaseAiAgentConnectorTest` → Connector test base
- `L4JAiAgentJobWorkerFeedbackLoopTests` → Feedback loop tests
- `L4JAiAgentJobWorkerToolCallingTests` → Tool calling tests

---

## 16. Gateway Tool Pattern {#16-gateway-tool-pattern}

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

The discovery flow integrates with the agent state machine from §4:

```
INITIALIZING
  ├─ AdHocToolsSchemaResolver → identifies gateway elements
  ├─ GatewayToolHandlerRegistry.initiateToolDiscovery()
  │   ├─ MCP handler: creates ToolCall("MCP_toolsList_<elementId>", "<elementId>", {method: "listTools"})
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
**Process sees** (element activation): `MyFilesystem` with variables `{toolCall: {operation: "callTool", params: {name: "readFile", arguments: {...}}}}`

This mapping happens in `transformToolCalls()` (LLM → process) and `transformToolCallResults()` (process → LLM).

### Agent Context Properties

Gateway handlers store per-handler state in `AgentContext.properties`:

- MCP: `properties.mcpClients = ["elementId1", "elementId2"]` — list of MCP client element IDs
- A2A: `properties.a2aClients = ["elementId1", "elementId2"]` — list of A2A client element IDs

These are used during discovery checking and tool call result transformation.

---

## 17. MCP Integration Deep Dive {#17-mcp-deep-dive}

### Overview

MCP (Model Context Protocol) integration enables the AI Agent to use tools exposed by MCP servers. The integration is bidirectional:

1. **Agent Integration**: MCP Client elements inside the AHSP act as gateway tools, exposing MCP server tools to the LLM
2. **Standalone**: MCP Client connector used independently (not as an AI Agent tool)

### Connector Types

**MCP Client** (`McpClientFunction`):
- Type: `io.camunda.agenticai:mcpclient:1`
- Uses pre-configured MCP connections on the runtime (via Spring properties)
- Client lifecycle managed by `McpClientRegistry`
- Supports STDIO and HTTP/SSE transports

**MCP Remote Client** (`McpRemoteClientFunction`):
- Type: `io.camunda.agenticai:mcpremoteclient:1`
- Connects to remote MCP servers via HTTP (Streamable HTTP or SSE)
- Connection configured per connector instance (URL, auth, etc.)
- Client lifecycle managed by `McpRemoteClientRegistry` with Caffeine cache
- Cache key: `(processDefinitionKey, elementId)` — scoped per deployment + element
- Supports Basic, Bearer, and OAuth authentication

### MCP Client Architecture

```
McpClientFunction / McpRemoteClientFunction
  │
  ├── McpClientHandler / McpRemoteClientHandler
  │     └── resolves client from registry, creates operation
  │
  ├── McpClientExecutor
  │     ├── routes to McpClientDelegate method by operation
  │     └── McpClientResultDocumentHandler (binary → document conversion)
  │
  ├── McpClientDelegate (interface)
  │     ├── listTools(filter)
  │     ├── callTool(params, filter)
  │     ├── listResources(filter), readResource(params, filter)
  │     └── listPrompts(filter), getPrompt(params, filter)
  │
  └── Implementation: MCP SDK (mcpsdk package)
        └── McpSdkClientFactory → creates MCP SDK client instances
```

### MCP Operations

| Operation | Method | Used By |
|---|---|---|
| `LIST_TOOLS` | `listTools()` | Discovery (agent integration) and standalone |
| `CALL_TOOL` | `callTool(name, arguments)` | Tool execution (agent integration) and standalone |
| `LIST_RESOURCES` | `listResources()` | Standalone only |
| `LIST_RESOURCE_TEMPLATES` | `listResourceTemplates()` | Standalone only |
| `READ_RESOURCE` | `readResource(uri)` | Standalone only |
| `LIST_PROMPTS` | `listPrompts()` | Standalone only |
| `GET_PROMPT` | `getPrompt(name)` | Standalone only |

### MCP Tool Name Convention (`McpToolCallIdentifier`)

```
MCP_<elementName>___<mcpToolName>
```

- Prefix: `MCP_`
- Element name: BPMN element ID of the MCP Client in the AHSP
- Separator: `___` (triple underscore)
- MCP tool name: the tool's name on the MCP server

Example: `MCP_MyFilesystem___readFile`

Pattern: `^MCP_(?<elementName>.+)___(?<mcpToolName>.+)$`

### MCP Discovery Flow (Sequence)

```
1. Agent enters INITIALIZING state
2. AdHocToolsSchemaResolver identifies MCP Client element via gateway type extension
3. McpClientGatewayToolHandler.initiateToolDiscovery():
   - Stores MCP client element IDs in agentContext.properties.mcpClients
   - Creates tool call: id="MCP_toolsList_<elementId>", name="<elementId>",
     args={method: "listTools"}
4. Agent completes job with activateElement("<elementId>") and
   variables {toolCall: {method: "listTools"}, toolCallResult: ""}
5. MCP Client connector picks up the tool call, calls listTools on MCP server
6. Result (McpClientListToolsResult with tool definitions) flows back via outputElement
7. Agent receives result in TOOL_DISCOVERY state
8. McpClientGatewayToolHandler.handleToolDiscoveryResults():
   - Converts each McpToolDefinition → ToolDefinition
   - Name: MCP_<elementId>___<mcpToolName>
   - Schema: inputSchema from MCP server
9. Tools merged into agentContext.toolDefinitions
10. State → READY, tools available to LLM
```

### MCP Tool Call Execution (Sequence)

```
1. LLM requests tool call: "MCP_MyFilesystem___readFile" with args {path: "/foo"}
2. McpClientGatewayToolHandler.transformToolCalls():
   - Parses McpToolCallIdentifier: elementName="MyFilesystem", mcpToolName="readFile"
   - Transforms to: ToolCall(id=<original>, name="MyFilesystem",
     args={method: "callTool", params: {name: "readFile", arguments: {path: "/foo"}}})
3. Agent completes job with activateElement("MyFilesystem")
4. MCP Client connector executes callTool("readFile", {path: "/foo"})
5. Result flows back as toolCallResult (McpClientCallToolResult)
6. McpClientGatewayToolHandler.transformToolCallResults():
   - Extracts tool name from result, rebuilds fully qualified name
   - Maps content: if single McpTextContent → use string directly, else use list
   - Returns ToolCallResult with name="MCP_MyFilesystem___readFile"
7. Agent presents result to LLM with the original fully qualified tool name
```

### MCP Client Lifecycle

**Pre-configured clients** (`McpClientRegistry`):
- Clients registered as suppliers during Spring context initialization
- Lazy instantiation: supplier called on first `getClient()` access
- Long-lived, cached for entire application lifecycle
- Closed when registry is closed (application shutdown)

**Remote clients** (`McpRemoteClientRegistry`):
- Cached via Caffeine with configurable TTL and max size
- Cache key: `McpRemoteClientIdentifier(processDefinitionKey, elementId)`
- Non-cacheable mode for process-specific credentials (caller must close)
- Eviction listener auto-closes evicted clients

### MCP Filters

Both MCP Client types support allow/deny list filtering:
- **Tool filters**: restrict which tools are listed/callable
- **Resource filters**: restrict resource listing/reading
- **Prompt filters**: restrict prompt listing/retrieval

Filters are configured per connector instance via the element template.

### MCP Result Document Handling

`McpClientResultDocumentHandler` converts binary MCP content into Camunda documents:
- Checks if result implements `McpClientResultWithStorableData`
- If yes, calls `convertStorableMcpResultData(documentFactory)` to create documents
- This handles MCP resources that return binary data (images, files)

### Key MCP Source Files

| File | Purpose |
|---|---|
| `McpClientFunction.java` | Pre-configured MCP client connector entry point |
| `McpRemoteClientFunction.java` | Remote MCP client connector entry point |
| `McpClientGatewayToolHandler.java` | Gateway handler: discovery, name transform, result transform |
| `McpToolCallIdentifier.java` | Tool name parsing/construction (`MCP_<element>___<tool>`) |
| `McpClientGatewayToolDefinitionResolver.java` | Identifies MCP elements in AHSP |
| `McpClientRegistry.java` | Pre-configured client lifecycle management |
| `McpRemoteClientRegistry.java` | Remote client cache with Caffeine |
| `McpClientExecutor.java` | Routes operations to McpClientDelegate methods |
| `McpClientDelegate.java` | Abstract MCP client interface |
| `McpSdkClientFactory.java` | Creates MCP SDK client instances |
| `McpClientResultDocumentHandler.java` | Binary content → Camunda document conversion |
| `McpDiscoveryConfiguration.java` | Spring config for MCP gateway beans |

---

## 18. A2A Integration Deep Dive {#18-a2a-deep-dive}

### Overview

A2A (Agent-to-Agent) integration enables the AI Agent to interact with remote autonomous agents via the A2A protocol. Unlike MCP (which exposes discrete tools), A2A exposes **entire agents** as tools. Each A2A Client element in the AHSP becomes a single tool the LLM can send messages to.

### Key Differences from MCP

| Aspect | MCP | A2A |
|---|---|---|
| What it exposes | Multiple discrete tools per server | One agent per remote server |
| Discovery | `listTools` → N tool definitions | `fetchAgentCard` → 1 tool definition |
| Tool name format | `MCP_<element>___<mcpTool>` | `A2A_<element>` (no sub-tool) |
| Input schema | Per-tool (from MCP server) | Fixed (text + taskId + contextId + referenceTaskIds) |
| Tool description | Per-tool (from MCP server) | Serialized agent card JSON |
| Multi-turn | Not applicable (stateless tools) | Supported (taskId/contextId for continuation) |
| System prompt | None | A2A protocol instructions injected automatically |
| Response handling | Simple content return | Task lifecycle (submitted/working/input-required/completed/failed) |

### Connector Types

**A2A Client Outbound** (`A2aClientOutboundConnectorFunction`):
- Type: `io.camunda.agenticai:a2aclient:0`
- Sends messages to remote A2A agents and fetches agent cards
- Two modes: standalone and AI Agent tool

**A2A Client Polling Inbound** (`A2aClientPollingExecutable`):
- Type: `io.camunda.agenticai:a2aclient:polling:0`
- Intermediate catch event / receive task
- Polls remote A2A server for task completion (async tasks)
- Used after outbound send message returns `submitted`/`working` status

**A2A Client Webhook Inbound** (`A2aClientWebhookExecutable`):
- Type: `io.camunda.agenticai:a2aclient:webhook:0`
- Intermediate catch event / receive task
- Receives push notifications from A2A servers
- HMAC signature verification for security

### A2A Tool Name Convention (`A2aToolCallIdentifier`)

```
A2A_<elementName>
```

- Prefix: `A2A_`
- Element name: BPMN element ID of the A2A Client in the AHSP

Example: `A2A_WeatherAgent`

Simpler than MCP because there's only one "tool" per A2A element (the agent itself).

### A2A Discovery Flow

```
1. Agent enters INITIALIZING state
2. AdHocToolsSchemaResolver identifies A2A Client element via gateway type extension
3. A2aGatewayToolHandler.initiateToolDiscovery():
   - Stores A2A client element IDs in agentContext.properties.a2aClients
   - Creates tool call: id="A2A_fetchAgentCard_<elementId>", name="<elementId>",
     args={operation: "fetchAgentCard"}
4. Agent completes job with activateElement("<elementId>")
5. A2A Client connector fetches agent card from remote server
6. Result (agent card JSON) flows back via outputElement
7. Agent receives result in TOOL_DISCOVERY state
8. A2aGatewayToolHandler.handleToolDiscoveryResults():
   - Creates single ToolDefinition per A2A element:
     name: "A2A_<elementId>"
     description: serialized agent card JSON (the LLM reads agent capabilities from this)
     inputSchema: fixed schema from a2a/tool-input-schema.json
9. Tools merged into agentContext.toolDefinitions
10. State → READY
```

### A2A Tool Call Execution

```
1. LLM requests: "A2A_WeatherAgent" with args {text: "What's the weather?"}
2. A2aGatewayToolHandler.transformToolCalls():
   - Parses A2aToolCallIdentifier: elementName="WeatherAgent"
   - Transforms to: ToolCall(name="WeatherAgent",
     args={operation: "sendMessage", params: {text: "What's the weather?"}})
3. Agent completes job with activateElement("WeatherAgent")
4. A2A Client connector sends message to remote agent
5. Result (A2aSendMessageResult — either A2aTask or A2aMessage) flows back
6. A2aGatewayToolHandler.transformToolCallResults():
   - Rebuilds fully qualified name: "A2A_WeatherAgent"
   - Returns ToolCallResult with the send message result content
7. LLM receives the response and decides next action based on task state
```

### A2A Fixed Tool Input Schema

All A2A tools share a single input schema (`a2a/tool-input-schema.json`):

```json
{
  "type": "object",
  "properties": {
    "text":             { "type": "string", "description": "The request or follow-up message" },
    "taskId":           { "type": "string", "description": "Existing task ID (for continuation)" },
    "contextId":        { "type": "string", "description": "Context ID (from first response)" },
    "referenceTaskIds": { "type": "array", "description": "Prior task IDs for context" }
  },
  "required": ["text"]
}
```

This fixed schema allows the LLM to have multi-turn conversations with remote agents via `taskId` and `contextId` management.

### A2A System Prompt Contribution

`A2aSystemPromptContributor` automatically injects A2A protocol instructions into the system prompt when A2A tools are detected. This is loaded from `a2a/a2a-system-prompt.md` and covers:

- How to interpret `kind: "message"` vs `kind: "task"` responses
- Task state handling (input-required, completed, submitted/working, failed)
- ID management rules (taskId, contextId, referenceTaskIds)
- Multi-turn flow examples
- Common error patterns to avoid

The contributor is ordered (`ORDER = 100`) and only activates when `agentContext.properties.a2aClients` is non-empty.

### A2A Response Handling

`A2aSendMessageResponseHandlerImpl` handles two event types from the A2A SDK:
- **MessageEvent**: Direct message response → `A2aMessage`
- **TaskEvent**: Task-based response → `A2aTask` (with status: submitted, working, input-required, completed, failed, cancelled, rejected)
- `AUTH_REQUIRED` status is not supported yet

The outbound connector also prepares response metadata:
- **Polling mode**: Returns `pollingData` with task/message ID for the polling inbound connector
- **Notification mode**: Returns `pushNotificationData` with token for the webhook connector

### A2A Async Patterns

For asynchronous A2A interactions (when remote agent returns `submitted`/`working`):

**Polling pattern**:
```
A2A Client Outbound (sendMessage) → get taskId
  ↓
A2A Client Polling (intermediate catch event) → polls until completed
  ↓
Result correlation
```

**Push notification pattern**:
```
A2A Client Outbound (sendMessage) → get notification token
  ↓
A2A Client Webhook (intermediate catch event) → waits for callback
  ↓
Result correlation
```

### Key A2A Source Files

| File | Purpose |
|---|---|
| `A2aClientOutboundConnectorFunction.java` | Outbound connector entry point |
| `A2aClientRequestHandlerImpl.java` | Handles fetchAgentCard and sendMessage operations |
| `A2aGatewayToolHandler.java` | Gateway handler: discovery, name transform, result transform |
| `A2aToolCallIdentifier.java` | Tool name parsing/construction (`A2A_<element>`) |
| `A2aGatewayToolDefinitionResolver.java` | Identifies A2A elements in AHSP |
| `A2aSystemPromptContributor.java` | Injects A2A protocol instructions into system prompt |
| `A2aClientPollingExecutable.java` | Polling inbound connector |
| `A2aClientWebhookExecutable.java` | Webhook inbound connector |
| `A2aSendMessageResponseHandlerImpl.java` | Handles message/task events from A2A SDK |
| `A2aMessageSenderImpl.java` | Sends messages to remote A2A agents |
| `A2aAgentCardFetcherImpl.java` | Fetches agent cards from remote servers |
| `a2a/tool-input-schema.json` | Fixed tool input schema for all A2A tools |
| `a2a/a2a-system-prompt.md` | A2A protocol instructions for LLM |
| `A2aClientAgenticToolConfiguration.java` | Spring config for A2A gateway beans |

---

## 19. Examples Directory Reference {#19-examples}

The `examples/` directory contains reference BPMN processes and configurations:

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
