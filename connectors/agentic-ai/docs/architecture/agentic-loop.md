# Agentic loop

This document provides a comprehensive, code-level reference for the AI Agent implementation within the Camunda
Connectors `agentic-ai` module. It covers concepts, interaction patterns, data flow, concurrency challenges, and all
nuances of the distributed agent loop.

## Foundational Concepts

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

## The Agentic Loop – Distributed Execution Model

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

2. **Job activation**: The `AgentSubProcessV1Function` picks up the job with `fetchVariables = [adHocSubProcessElements, agentContext, toolCallResults, provider, data]`.

3. **Agent initialization** (`AgentInitializerImpl`): the agent is initialized with state `INITIALIZING` on the first job, `TOOL_DISCOVERY` while gateway tool discovery results are missing and `READY` afterwards. Tool definitions are resolved from `adHocSubProcessElements`. See the [agent state machine](../../connector-aiagent/docs/agent-state-machine.md) for the transitions and the per-state behavior.

4. **LLM interaction** (when state = `READY`, handled by `BaseAgentRequestHandler`):
   - Load the stored flat message list (via `ConversationSession`) and reconstruct it into turns (`TurnReconstructor.reconstruct`)
   - Resolve the next turn (`AgentConversationTurnInputComposer.resolve`) → `CompositionResult` (`Deferred` / `NoInput` /
     `Ready`). The composer resolves the results of the process tool calls only. When they are complete (or the calls
     without a result are cancelled by an interrupting event), `Ready` holds the tool calls of the previous turn and the process
     results as a `ToolCallBatch`; a rejected call has its rejection as its result from the start. On `Ready`, the
     handler first checks the model-call limit, so a job at the limit executes no runtime tool. Then it executes
     the unanswered calls, the calls of runtime tools (`LocalToolCallExecutor`), adds their results to the batch and
     assembles the input (`AgentConversationTurnInputComposer.assemble` → `TurnInput`), with all results in call order
   - Compose the system message and `AgentConversation.rehydrate` into a pending turn
   - Resolve a `ChatModel` for the configured provider via `ChatModelRegistry` and call
     `execute(ChatRequest)` with a windowed `ConversationSnapshot`; `ingest` the assistant response
     into the turn. Before the ingest, `ToolCallAdmission` admits the tool calls of the assistant message: each gets the kind
     of its tool, or a rejection when the tool is not offered (see [What Happens When Tools Complete](#what-happens-when-tools-complete)). If the result is a `ChatResult.Continuation`
     rather than `Completed`, the handler loops — persisting the round as its own turn and re-calling the model with no new
     input — until a `Completed` result ends the turn (see [`chat-model.md`](../../connector-aiagent/docs/chat-model.md#turn-based-continuation-loop)). The same happens when every
     tool call of the message is a local tool call: the handler checks the model-call limit, produces their tool call
     results the same way, from a `Ready` of the batch, and opens the next round with the assembled input
     (`nextRuntimeToolRound`)
   - Store the updated conversation back to the memory store (via `ConversationSession`) and reduce it back to `AgentContext`
   - Transform the process tool calls and create the response

5. **Job completion** (`AgentSubProcessConnectorResponse`):
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

## Job Completion – The Heart of the Distributed Loop

### Job Worker Completion Flow

`AgentSubProcessV1Function` is an `OutboundConnectorFunction` wrapped by `SpringConnectorJobHandler` at runtime. The flow:

```
SpringConnectorJobHandler.handle(jobClient, job)
  │
  ├─ Creates OutboundConnectorContext from job variables
  │
  ├─ AgentSubProcessV1Function.execute(context)
  │    ├─ Binds variables to AgentSubProcessV1Request
  │    └─ agentRequestHandler.handleRequest(executionContext)
  │         └─ Returns AgentSubProcessConnectorResponse (AdHocSubProcessConnectorResponse)
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

4. **`cancelRemainingInstances`**: Used when event handling interrupts tool calls — cancels all still-running tool instances. Determined in `AgentSubProcessRequestHandler.buildResponse()` from `conversation.hasInterruptedToolCallResultsInInvocation()`, which inspects the input messages of every turn of this invocation (the first round and the rounds that follow it in the same job) for any `ToolCallResult` carrying the `PROPERTY_INTERRUPTED` flag.

5. **Async execution**: The complete command is sent asynchronously via `CommandWrapper` with up to 3 retries. This is important because:
   - The job may have been superseded (NOT_FOUND)
   - Network issues may occur

### No-Op Completion (Waiting for More Results)

When the agent cannot proceed (e.g., not all tool call results are present yet, or discovery is in progress):

```java
return AgentSubProcessConnectorResponse.builder()
    .completionConditionFulfilled(false)
    .cancelRemainingInstances(false)
    .build();
// No agentResponse, no variables, no element activations
// Just complete the job without doing anything → wait for next job
```

## What Happens When Tools Complete

### Single Tool Completion

1. The tool activity (e.g., a script task, connector, user task) completes within the AHSP scope
2. The tool is expected to have produced a `toolCallResult` variable in its local scope
3. Zeebe evaluates the `outputElement` expression:
   ```
   ={id: toolCall._meta.id, name: toolCall._meta.name, content: toolCallResult, completedAt: now()}
   ```
   `completedAt` (v11+ templates) is this tool's own engine completion timestamp, not the turn's
   (ADR 008).
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

**The critical mechanism: `resolveProcessToolCallResults`**

In `AgentConversationTurnInputComposerImpl.resolve`, when the last reconstructed turn ended with an
`AssistantMessage` that has tool calls (`history.turns().getLast().hasToolCalls()`):

```java
final var processResults =
    resolveProcessToolCallResults(
        agentContext, previousTurn.processToolCalls(), agentInput.toolCallResults(), interruptMissingToolCalls);

// either we have all results or we interrupted the missing tool calls
// if incomplete, we wait on further tool call results to be added
if (!processResults.complete()) {
    return new CompositionResult.Deferred(processResults.arrivedResults());
}
```

The method correlates and gateway (MCP/A2A) transforms the process results, then checks each process tool call of the last
assistant message against them (`ProcessToolCallResults(arrivedResults, complete)`):
- If all present: `complete` is `true`. `resolve` returns `CompositionResult.Ready` with a `ToolCallBatch` of all tool
  calls of the turn that holds the process results and the rejections of the rejected calls (`ToolCallBatch.of`). The
  calls of runtime tools are its unanswered calls; the handler executes them (`LocalToolCallExecutor`), and `assemble` builds the `ToolCallResultMessage` from the batch in
  call order (`ToolCallResultsInputBuilder`). The builder does not know where a result came from
- If missing and NOT interrupting: `complete` is `false`; `arrivedResults` holds only the process
  results that arrived so far → `resolve` returns `CompositionResult.Deferred(arrivedResults)`
  (ADR 011) → handler reports them to agent instance history and completes as a no-op. The agent adds the tool call results of
  the local tool calls only when the results of the process are complete, so an incomplete job executes no runtime tool
  and writes nothing about it
- If interrupting (due to event): `complete` is `true`; every call of the batch without a result gets a cancelled
  result: the missing process tool calls and the calls of runtime tools, which were not executed yet. No runtime tool
  runs in the job of the interrupt. A rejected call keeps its rejection

**Admission: tool call kind and rejection.** Every `ToolCall` carries a `kind` (`ToolKind`): `PROCESS` for a tool that
is a BPMN element, `RUNTIME` for a tool the agent executes itself ([Add a runtime tool](extension-points.md#add-a-runtime-tool)). `ToolCallAdmission.admit` sets the kind of a
call to the kind of the tool of its name in the tool list of the job (the tools of the process and the enabled runtime
tools; for an instance without the feature, the tools of the process). A call whose name is not in the list gets a
`rejection`, the error text that is its tool call result (`The tool '<name>' does not exist.`); its kind stays
`PROCESS`. A rejection can apply to a call of any kind. The agent admits the calls once, between the model result and
`AgentConversation.ingest`, for every agent instance, with or without the `runtimeTools` feature, persists
them with the message and never computes them again, so they may appear in the state of any agent instance. The JSON of a
tool call holds `kind` (`"runtime"`) only for `RUNTIME` and `rejection` only when set; a missing `kind` reads as `PROCESS`
and a missing `rejection` as none, so state written before they exist loads unchanged. A conversation store must keep
both; a store that drops them turns a runtime tool call or a rejected call into a process tool call that never gets a
result. A change of what a runtime tool enables therefore can neither send a local tool call to the process, nor
execute a rejected call, also not when a runtime tool of that name is enabled by the time the tool call result is
composed.

`ToolCall.resultSource()` derives who produces the tool call result of a call: `REJECTED` for a call with a rejection,
whatever its kind, else `RUNTIME` or `PROCESS` after the kind. `ToolCall.isLocal()` is true for the local tool calls,
`RUNTIME` and `REJECTED`, whose tool call result the agent produces itself. A rejected call gets its rejection as its
tool call result (`ToolCallResultContent.forRejectedToolCall`) when its `ToolCallBatch` is created, and is never
executed. `LocalToolCallExecutor` executes the rest, the calls of runtime tools, through `RuntimeTools` and fails for
any other result source; a call whose tool is no longer enabled when it is executed gets the error result `The tool
'<name>' is not available.` Neither goes to the process. The error is plain text in the tool call
result; the tool call result has no error flag.

Only the process tool calls are activated and handed to the process (`AgentResponse.toolCalls`, built from
`AgentConversationTurn.processToolCalls()`). What happens to the local tool calls (`localToolCalls()`) depends
on the message:
- *Only local tool calls.* The handler checks the model-call limit, produces their tool call results in the same job,
  assembles the input of the next round from them the same way as for the next job (`CompositionResult.Ready.of`,
  `assemble`) and calls the model again. The round counts against the model-call limit.
- *Process tool calls and local tool calls (a mixed batch).* The job activates the process tool calls only. The agent
  adds the tool call results of the local tool calls in the job that composes the next turn, as described above, and the
  model receives them with the next request, together with the process results. A process call that never completes holds that request back, as
  it holds back every other result of the batch.

`RuntimeTools.execute` executes the calls in call order, one after the other. Its contract allows the calls of one
message to be executed concurrently, with the results applied in call order. Every execution (`RuntimeToolExecution`) keeps the
call, the `RuntimeToolResult` and the time the call started and completed; the tool call result for the conversation is
derived from them. No state about the local tool calls is
persisted between jobs apart from the `kind` and the `rejection` of the calls. See [ADR 016](../adr/016-runtime-tools.md).

### No-Op Detection in BaseAgentRequestHandler

`BaseAgentRequestHandler.converse` switches on the `CompositionResult` returned by
`AgentConversationTurnInputComposer.resolve`:

```java
return switch (agentInputComposer.resolve(...)) {
    case CompositionResult.Deferred(var arrivedResults) -> {
        reportArrivedToolCallResults(..., arrivedResults, ...); // ADR 011 — report what's arrived so far
        yield handleNoOp(executionContext);    // wait for more tool results
    }
    case CompositionResult.NoInput ignored ->
        handleNoInput(executionContext);       // nothing to add; handler decides
    case CompositionResult.Ready ready ->
        proceed(..., answerLocalToolCalls(..., ready).messages(), ...); // local results, assemble, call the LLM
};
```

This is the key gate: when tool results were incomplete the composer returns `CompositionResult.Deferred`,
and `handleNoOp` completes the job as a no-op (the job worker waits for the next job, which will have
more results). When no input (user prompt, documents or events) is available at all, the composer returns
`CompositionResult.NoInput`. This variant carries no error semantics — each handler decides: the job
worker's `handleNoInput` completes without a response, while the outbound connector's `handleNoInput`
throws a `ConnectorException` with `ERROR_CODE_NO_USER_MESSAGE_CONTENT`.

### Streaming Arrived Results to Agent Instance History (ADR 011)

`CompositionResult.Deferred` carries `arrivedResults`: the tool call results correlated and
gateway-transformed by `AgentConversationTurnInputComposerImpl.resolveProcessToolCallResults`
before it determined the batch was still incomplete. `BaseAgentRequestHandler.reportArrivedToolCallResults`
reports these to agent instance history via `AgentInstanceClient.applyToolCallResults`
before completing the job, so history shows progress before the slowest tool call in a turn
finishes — instead of only once the whole batch completes and `proceed()` runs. See
[ADR 011](../adr/011-stream-tool-call-results-to-agent-instance-history.md) for the full design.

- **Correlation and gateway (MCP/A2A) transformation happen once**, in the composer, for both the
  `Deferred` and `Ready` paths. A stray or redelivered id that doesn't correlate to a tool call
  the turn is waiting on never makes it into `arrivedResults`.
- **Written once per job that still observes it.** `arrivedResults` is the full cumulative
  correlated set on every call, since Zeebe's `toolCallResults` variable is cumulative across jobs
  ([What Happens When Tools Complete](#what-happens-when-tools-complete)) and there is no state to track what an earlier job already reported. A result that arrives
  while other tool calls in the same turn are still outstanding is reported by every subsequent
  incomplete job that still observes it, plus once more by `applyTurnStart` once
  the batch completes — for a turn with `N` staggered tool calls, up to `N(N+1)/2` item writes in total.
  The engine deduplicates the repeats by `historyItemId`, so the history holds each result once.
- **No new persisted state.** The `Deferred` path touches neither `AgentContext` nor the
  `ConversationStore` — reporting to agent instance history is its only side effect.
- **The tool call results of the local tool calls are not part of `arrivedResults`.** They are not awaited and not
  written by an incomplete job; they are reported with the turn start that carries them (see [`agent-instance.md`](../../connector-aiagent/docs/agent-instance.md#conversation-history-items)).
- **Strict failure.** This call runs before job completion, synchronously, like every other
  `AgentInstanceClient` write since [ADR 013](../adr/013-unified-agent-instance-turn-updates.md) — a
  failure fails the job.

## Concurrency Challenges & Race Conditions

### Challenge 1: Job Supersession (NOT_FOUND)

**Problem**: When a tool completes, Zeebe creates a new job. The previous job may still be processing. Completing the old job results in `NOT_FOUND`.

**Mitigation**:
- The `JobCallbackCommandWrapperFactory` retries up to 3 times with backoff
- The no-op completion pattern means most superseded jobs were doing nothing anyway
- Superseded jobs produce a `CommandIgnored` outcome — the conversation store receives `onJobCompletionFailed` with a `CommandIgnored` failure

**Job leasing**: All AI Agent connectors (both flavors, v1 and v2) opt into job leasing; the gateway/MCP/A2A tool connectors do not. Each activation carries a per-activation lease token. The engine rejects a job completion from a superseded activation of the same job, and an agent-instance `create` with a stale lease. An agent-instance update with a stale lease is accepted, and the engine skips its status change. An update for a job that is no longer active is rejected and treated as supersession rather than retried (see [`agent-instance.md`](../../connector-aiagent/docs/agent-instance.md)). v1 is leased alongside v2 because a custom v1 template can also carry an agent definition and emit visibility data, so it needs the same lease.

A lease token is required for every agent-instance write, and the connector fails the write when one is absent rather than skipping the fencing. In steady state a token is always present: job leasing and the agent-instance API are introduced together in 8.10, and a settled cluster's broker and gateway are on the same version, so leasing is active wherever the agent-instance endpoints are. The one gap is a transient rolling upgrade (for example on SaaS), where for a brief window a job may be activated without a lease while the already-upgraded agent-instance endpoints are reachable; those writes fail and follow the connector's configured retry handling, so the impact is bounded to the upgrade window. The same check also surfaces a connector misconfigured without leasing. For the underlying job-lease mechanism, see the monorepo ADR [0005-810-job-lease](https://github.com/camunda/camunda/blob/main/zeebe/docs/adr/0005-810-job-lease.md).

### Challenge 2: Conversation Store Ahead of Zeebe

**Problem**: The conversation is written to storage **before** the job completion command is sent to Zeebe. If job completion fails, the store has data that Zeebe doesn't know about.

**Mitigation**: This is safe by design. All stores follow the [Storage Contract](../../aiagent-api/docs/conversation-store-spi.md#storage-contract): they write to a new location each turn, and the `ConversationContext` pointer in the old `AgentContext` still resolves to the old data. The newly written data becomes an orphan — harmless to correctness. Stores can use the `onJobCompletionFailed` callback to proactively clean up orphaned data.

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

## Event Handling

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
- If events are present:
  - Every tool call without a result gets a synthetic "cancelled" result (`ToolCallResult.forCancelledToolCall(id, name)`):
    the missing process tool calls and the calls of runtime tools that were not executed yet; a rejected call keeps its
    rejection
  - The `cancelRemainingInstances` flag is set to `true` on the completion command
  - Active tool instances are terminated by Zeebe
- Example message sequence: `[Tool A cancelled, Tool B result, Event message]`
- The `PROPERTY_INTERRUPTED` flag on cancelled results triggers `cancelRemainingInstances` in `AgentSubProcessRequestHandler.buildResponse()` (via `AgentConversation.hasInterruptedToolCallResultsInInvocation()`, which reads the turns of the invocation)

### Event Payload

Events create their payload in `toolCallResult`:
- If non-empty: added as a user message (text or object content)
- If empty/null/blank: a generic message is generated:
  - With interrupt: "An event was triggered but no content was returned. All in-flight tool executions were canceled."
  - Without interrupt: "An event was triggered but no content was returned. Execution waited for all in-flight tool executions to complete before proceeding."
