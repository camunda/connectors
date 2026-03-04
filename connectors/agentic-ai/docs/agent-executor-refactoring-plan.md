# Agent Executor Refactoring: Implementation Plan

Depends on the [Conversation Storage Refactoring](conversation-storage-refactoring-plan.md) being
completed first.

## TL;DR

This refactoring makes the agent's core processing pipeline — the part that loads conversation
history, constructs messages, calls the LLM, and produces a response — replaceable. Users who want
fundamentally different agent behavior (a different LLM framework, multi-step reasoning, custom
tool execution strategies) can swap in their own implementation while keeping the full Camunda
integration (ad-hoc sub-process completion, tool activation, conversation storage, job lifecycle).

**Why this is needed:**

The agent's orchestration logic lives in `BaseAgentRequestHandler`, an abstract class that
hard-codes the processing pipeline. Individual steps are already behind clean interfaces (message
handling, LLM invocation, response formatting, etc.), but the way they're composed is not
pluggable. Users who need different orchestration have to replace the entire handler chain,
including the Camunda-specific parts they want to keep.

The LLM framework adapter also receives the agent's internal context-window abstraction as a
parameter, coupling it to orchestration details it doesn't need. It only needs the messages to
send to the LLM.

**Key changes:**

- **New `AgentExecutor` SPI**: A single-method interface that encapsulates the full agent processing
  pipeline between "agent initialized" and "response ready." The default implementation composes
  all existing interfaces and contains the standard pipeline: load conversation, check for
  reconciliation, manage context window, construct messages, call LLM, transform tool calls, store
  conversation, format response. Behavior is unchanged — the logic moves, it doesn't change.
- **Handler becomes a thin coordinator**: The request handler no longer contains orchestration
  logic. It initializes the agent, delegates to the executor, and completes the job. This makes it
  easy to understand and unlikely to need changes.
- **LLM adapter receives plain messages**: The framework adapter receives a pre-filtered message
  list instead of the runtime memory object. A clean "messages in, response out" contract with
  no knowledge of memory management internals.
- **Runtime memory becomes an internal detail**: The context window management is no longer part of
  any public interface. Custom executors can manage messages however they want — they aren't forced
  to use the sliding window approach.
- **Spring-based extensibility**: The default executor is registered as a conditional bean. Users
  provide their own executor as a Spring bean to replace the entire pipeline. The handler handles
  initialization, job completion, and session lifecycle — the executor focuses purely on agent logic.

## Context and Problem Statement

The current agent orchestration logic lives in `BaseAgentRequestHandler`, a concrete abstract class
that tightly bundles the full agent processing pipeline: initialization, memory management, message
construction, LLM invocation, response handling, and job completion. While individual steps are
factored into interfaces (`AgentInitializer`, `AgentMessagesHandler`, `AiFrameworkAdapter`,
`AgentResponseHandler`), the overall orchestration is not pluggable.

Users who want fundamentally different agent behavior — a different LLM orchestration framework, a
multi-step reasoning pipeline, a custom tool execution strategy, or an entirely custom agent
implementation — cannot plug in their own logic without replacing the entire handler chain.

Additionally, the `AiFrameworkAdapter` interface receives `RuntimeMemory` as a parameter, leaking
an internal orchestration concern into the framework abstraction layer. The adapter only needs
`List<Message>` to do its job (convert messages to framework types and call the LLM).

### What Works Well Today

The existing decomposition into focused interfaces is well-designed:

| Interface | Responsibility | Status |
|-----------|---------------|--------|
| `AgentInitializer` | Context setup, tool discovery | Good — keep as-is |
| `AgentMessagesHandler` | System/user/tool message construction | Good — keep as-is |
| `AgentLimitsValidator` | Iteration/token limit enforcement | Good — keep as-is |
| `AgentResponseHandler` | Response formatting (text/JSON/message) | Good — keep as-is |
| `AiFrameworkAdapter` | LLM invocation | Good concept, needs RuntimeMemory decoupling |
| `GatewayToolHandlerRegistry` | Tool call transformation (MCP/A2A) | Good — keep as-is |

The issue is not with these individual pieces, but with the **glue** — `BaseAgentRequestHandler`
hard-codes how they're composed.

### What We Want to Enable

1. **Full custom agent implementations**: Users bring their own orchestration (e.g., LangGraph,
   CrewAI adapter, custom multi-agent patterns) while reusing the Camunda integration layer
   (AHSP completion, tool activation, conversation storage)
2. **Composable default pipeline**: The standard agent logic should be decomposable so users can
   override individual steps without replacing everything
3. **Clean framework adapter contract**: `AiFrameworkAdapter` receives messages, returns a response.
   No knowledge of memory internals.

## Proposed Architecture

### New: AgentExecutor SPI

A new `AgentExecutor` interface that encapsulates the agent processing pipeline (everything
between "agent context initialized" and "agent response ready"):

```java
/**
 * Executes the agent logic for a single iteration.
 *
 * Receives the initialized context and tool call results, returns an AgentExecutionResult
 * containing the AgentResponse and a ConversationSession for lifecycle hooks.
 *
 * The executor is responsible for:
 * - Loading/storing conversation from the ConversationStore
 * - Handling store-as-truth reconciliation (when the store is ahead of Zeebe)
 * - Managing the runtime memory (context window)
 * - Constructing messages (system, user, tool results)
 * - Invoking the LLM via AiFrameworkAdapter
 * - Handling gateway tool call transformations
 *
 * The executor is NOT responsible for:
 * - Agent initialization (AgentInitializer handles this)
 * - Job completion (the caller handles this)
 * - Job-level error handling (the caller handles this)
 * - Conversation session lifecycle hooks (the caller handles this via the returned session)
 */
public interface AgentExecutor {
    AgentExecutionResult execute(
        AgentExecutionContext executionContext,
        AgentContext agentContext,
        List<ToolCallResult> toolCallResults);
}

/**
 * Result of agent execution, containing the response and the conversation session
 * for post-completion lifecycle hooks.
 */
public record AgentExecutionResult(
    @Nullable AgentResponse agentResponse,
    @Nullable ConversationSession session
) {
    public static AgentExecutionResult noOp() {
        return new AgentExecutionResult(null, null);
    }

    public static AgentExecutionResult of(AgentResponse response, ConversationSession session) {
        return new AgentExecutionResult(response, session);
    }
}
```

The executor returns an `AgentExecutionResult` that bundles the `AgentResponse` with the
`ConversationSession`. The handler needs the session to call `session.onJobCompleted()` after
`completeJob` succeeds — this happens outside the executor's scope (in the success callback of
`CommandWrapper`). Bundling them in a result record keeps the flow explicit without making the
executor stateful.

Custom executors that manage their own storage return `new AgentExecutionResult(response, null)`.
The handler only calls lifecycle hooks when `session` is non-null.

This is **framework-agnostic** — it works with our own `Message` model, `AgentContext`,
`AgentResponse`, and `ToolCallResult` types. No LangChain4J or other framework types leak through.

### Default Implementation: DefaultAgentExecutor

The existing logic from `BaseAgentRequestHandler.handleRequest()` moves into a
`DefaultAgentExecutor` that composes the existing interfaces:

```java
public class DefaultAgentExecutor implements AgentExecutor {

    private final ConversationStoreRegistry conversationStoreRegistry;
    private final AgentLimitsValidator limitsValidator;
    private final AgentMessagesHandler messagesHandler;
    private final GatewayToolHandlerRegistry gatewayToolHandlers;
    private final AiFrameworkAdapter<?> framework;
    private final AgentResponseHandler responseHandler;

    @Override
    public AgentExecutionResult execute(
            AgentExecutionContext executionContext,
            AgentContext agentContext,
            List<ToolCallResult> toolCallResults) {

        var conversationStore = conversationStoreRegistry.getConversationStore(...);

        // Create session — pure factory, no resources opened, no callback nesting.
        var session = conversationStore.createSession(executionContext, agentContext);

        // 1. Load conversation (with reconciliation check)
        var loadResult = session.loadMessages(agentContext);

        // 2. Handle reconciliation — store is ahead of Zeebe
        if (loadResult.reconciledFromStore()) {
            // A previous iteration wrote to the store but failed completeJob.
            // The conversation already contains the LLM response.
            // Derive AgentResponse from conversation state — no LLM call needed.
            var messages = loadResult.messages();
            var lastAssistantMessage = findLastAssistantMessage(messages);

            // Re-run response pipeline (tool call transformation + formatting)
            var toolCalls = gatewayToolHandlers.transformToolCalls(
                lastAssistantMessage.toolCalls(), ...);
            var processVariableToolCalls = toolCalls.stream()
                .map(ToolCallProcessVariable::from).toList();

            // Store to sync agentContext version with store
            agentContext = session.storeMessages(agentContext, messages);

            var response = responseHandler.createResponse(agentContext,
                lastAssistantMessage, processVariableToolCalls);
            return AgentExecutionResult.of(response, session);
        }

        // 3. Normal path — populate runtime memory
        var runtimeMemory = new MessageWindowRuntimeMemory(contextWindowSize);
        runtimeMemory.addMessages(loadResult.messages());

        // 4. Validate limits
        limitsValidator.validateConfiguredLimits(executionContext, agentContext);

        // 5. Add messages
        messagesHandler.addSystemMessage(...);
        var userMessages = messagesHandler.addUserMessages(...);

        // 6. Check prerequisites
        if (!modelCallPrerequisitesFulfilled(userMessages)) {
            // No-op: return session so handler can close it
            return new AgentExecutionResult(null, session);
        }

        // 7. Call LLM (with filtered messages, not RuntimeMemory)
        // No DB connection held during this call — per-operation transactions
        // keep connections short-lived.
        var chatResponse = framework.executeChatRequest(
            executionContext, agentContext, runtimeMemory.filteredMessages());
        agentContext = chatResponse.agentContext();
        runtimeMemory.addMessage(chatResponse.assistantMessage());

        // 8. Transform tool calls
        var toolCalls = gatewayToolHandlers.transformToolCalls(...);
        var processVariableToolCalls = toolCalls.stream()
            .map(ToolCallProcessVariable::from).toList();

        // 9. Store conversation (short write transaction for RDBMS stores)
        agentContext = session.storeMessages(agentContext, runtimeMemory.allMessages());

        // 10. Create response — return both response and session
        // The caller invokes session.onJobCompleted(agentContext) after completeJob succeeds.
        var response = responseHandler.createResponse(...);
        return AgentExecutionResult.of(response, session);
    }
}
```

This is essentially a refactoring of the existing `BaseAgentRequestHandler.handleRequest()` logic
into a standalone, replaceable component. The code is no longer nested inside a callback — the
session is created as a local variable and used throughout the method.

The reconciliation check at step 2 is the key addition from the store-as-truth architecture — it
allows external stores to short-circuit the entire processing pipeline when they detect they're
ahead of Zeebe's `agentContext`.

**No spanning transactions**: Note that steps 1 (load), 7 (LLM call), and 9 (store) are now
independent operations. For RDBMS stores, steps 1 and 9 are each wrapped in a short transaction
internally by the session, while step 7 runs with no DB connection held. This prevents connection
pool exhaustion during LLM calls.

### Changed: AiFrameworkAdapter Signature

Current:
```java
public interface AiFrameworkAdapter<R extends AiFrameworkChatResponse<?>> {
    R executeChatRequest(
        AgentExecutionContext executionContext,
        AgentContext agentContext,
        RuntimeMemory runtimeMemory);
}
```

Proposed:
```java
public interface AiFrameworkAdapter<R extends AiFrameworkChatResponse<?>> {
    R executeChatRequest(
        AgentExecutionContext executionContext,
        AgentContext agentContext,
        List<Message> messages);
}
```

The adapter receives **pre-filtered messages** (the context window) and returns a response. It does
not need to know about `RuntimeMemory`, full history, or windowing — those are the executor's
concerns.

The `Langchain4JAiFrameworkAdapter` change is minimal:
```java
// Before:
final var messages = chatMessageConverter.map(runtimeMemory.filteredMessages());

// After:
final var messages = chatMessageConverter.map(messages);
```

### Changed: BaseAgentRequestHandler Simplified

`BaseAgentRequestHandler` becomes a thin coordinator:

```java
public abstract class BaseAgentRequestHandler<C extends AgentExecutionContext, R>
        implements AgentRequestHandler<C, R> {

    private final AgentInitializer agentInitializer;
    private final AgentExecutor agentExecutor;

    @Override
    public R handleRequest(C executionContext) {
        var initResult = agentInitializer.initializeAgent(executionContext);

        return switch (initResult) {
            case AgentResponseInitializationResult(AgentResponse response) ->
                completeJob(executionContext, response, null);

            case AgentDiscoveryInProgressInitializationResult ignored ->
                completeJob(executionContext, null, null);

            case AgentContextInitializationResult(AgentContext ctx, List<ToolCallResult> tcr) -> {
                var result = agentExecutor.execute(executionContext, ctx, tcr);
                yield completeJob(executionContext, result.agentResponse(), result.session());
            }
        };
    }

    protected abstract R completeJob(C executionContext,
        @Nullable AgentResponse agentResponse, @Nullable ConversationSession session);
}
```

The handler only does initialization → delegate to executor → completion. All orchestration logic
lives in the executor.

**Lifecycle hooks**: The `AgentExecutionResult` bundles the `ConversationSession` alongside the
`AgentResponse`. The handler extracts the session and passes it to `completeJob`. In
`JobWorkerAgentRequestHandler`, the success callback of `CommandWrapper` calls
`session.onJobCompleted(agentContext)`. This ensures cleanup (e.g., deleting previous document
versions) only happens after the job is durably completed. The `completeJob` signature already
takes `ConversationSession` (from the storage refactoring), so no additional signature change is
needed.

### Unchanged: Everything Else

- `AgentInitializer`, `AgentMessagesHandler`, `AgentLimitsValidator`, `AgentResponseHandler`,
  `GatewayToolHandlerRegistry` — unchanged interfaces
- `JobWorkerAgentRequestHandler`, `OutboundConnectorAgentRequestHandler` — only the constructor
  changes (takes `AgentExecutor` instead of individual components). The `completeJob` signature
  already takes `ConversationSession` (from the storage refactoring).
- `ConversationStore` / `ConversationSession` — unchanged (already cleaned up by storage
  refactoring: `createSession()` factory, `loadMessages`/`storeMessages`, lifecycle hooks,
  `AutoCloseable`, version tracking)
- All model classes — unchanged

### Where modelCallPrerequisitesFulfilled Goes

Currently `modelCallPrerequisitesFulfilled` is an abstract method on `BaseAgentRequestHandler`,
overridden differently by the job worker handler (returns false if no messages → no-op) and the
outbound connector handler (throws exception if no messages).

Options:
1. **Move into AgentExecutor**: The `DefaultAgentExecutor` calls a prerequisite check. Users
   implementing custom executors handle this themselves.
2. **Inject as a strategy**: The `DefaultAgentExecutor` receives a prerequisite check function/
   interface as a constructor parameter.
3. **Keep on the handler**: The handler calls `agentExecutor.execute()` and the executor returns
   null when prerequisites aren't met. The handler decides what to do with null (no-op for job
   worker, exception for outbound connector).

**Recommendation**: Option 3. The executor returns null for "nothing to do," and the handler
interprets it. This keeps the executor simple and the flavor-specific behavior in the handler where
it already lives. The handler's `completeJob(executionContext, null, null)` path already handles
the null case correctly for both flavors.

However, this means the `DefaultAgentExecutor` needs to know when prerequisites are NOT met. Today,
the check is: "were any user messages added?" This is intrinsic to the message handling logic, so
the executor can check `addedUserMessages.isEmpty()` internally and return
`AgentExecutionResult.noOp()`. The outbound connector handler then interprets a null response as an
error:

```java
// In OutboundConnectorAgentRequestHandler:
case AgentContextInitializationResult(AgentContext ctx, List<ToolCallResult> tcr) -> {
    var result = agentExecutor.execute(executionContext, ctx, tcr);
    if (result.agentResponse() == null) {
        throw new ConnectorException(ERROR_CODE_NO_USER_MESSAGE_CONTENT, ...);
    }
    yield completeJob(executionContext, result.agentResponse(), result.session());
}
```

### RuntimeMemory Becomes Internal

After this refactoring, `RuntimeMemory` is an internal implementation detail of
`DefaultAgentExecutor`. It is not part of any SPI interface:

- `ConversationSession` → works with `List<Message>` (storage refactoring)
- `AiFrameworkAdapter` → works with `List<Message>` (this refactoring)
- `AgentExecutor` → works with `AgentContext` + `List<ToolCallResult>` → `AgentResponse`

Custom `AgentExecutor` implementations don't need to use `RuntimeMemory` at all. They can manage
messages however they want.

The `RuntimeMemory` interface and `MessageWindowRuntimeMemory` implementation remain as internal
utilities — they're useful for the default executor's context window management.

## Extension Points Summary

After both refactorings (storage + executor), the pluggability model is:

| Layer | Interface | What Users Can Replace |
|-------|-----------|----------------------|
| **Agent Orchestration** | `AgentExecutor` | Full control over the agent processing pipeline |
| **Conversation Storage** | `ConversationStore` + `ConversationSession` | Custom storage backend (JDBC, Redis, etc.) |
| **LLM Framework** | `AiFrameworkAdapter` | Different LLM library (not just LangChain4J) |
| **LLM Provider** | `ChatModelFactory` (LangChain4J-specific) | Custom model providers within LangChain4J |
| **Gateway Tools** | `GatewayToolHandler` | Custom tool integration patterns (beyond MCP/A2A) |
| **Message Handling** | `AgentMessagesHandler` | Custom message construction logic |
| **Response Format** | `AgentResponseHandler` | Custom response formatting |
| **Limits** | `AgentLimitsValidator` | Custom limit enforcement |

All registered via Spring beans. The default implementations cover the standard use case; users
override only what they need.

## Tradeoffs

### What We Gain

- **Full custom agent pluggability**: Users implement `AgentExecutor` for completely custom agents
  while reusing Camunda integration (AHSP, job completion, tool activation)
- **Clean framework adapter**: `AiFrameworkAdapter` is a pure "messages in → response out" contract
- **RuntimeMemory internalized**: No longer leaks into SPI interfaces
- **Simpler BaseAgentRequestHandler**: Thin coordinator instead of monolithic orchestrator
- **Composable default**: `DefaultAgentExecutor` composes existing interfaces, each independently
  replaceable
- **Reconciliation in the right place**: The `DefaultAgentExecutor` owns the reconciliation logic
  (check `reconciledFromStore`, derive response from conversation state, skip LLM call). This is
  orchestration logic — exactly where it belongs. Custom executors can implement their own
  reconciliation strategy or ignore it entirely.

### What We Accept

- **One more interface**: `AgentExecutor` adds a new abstraction layer. The existing interfaces
  remain — this is additive, not a replacement.
- **Migration effort**: Tests that mock `BaseAgentRequestHandler` internals need updating. But the
  public contract (`AgentRequestHandler.handleRequest()`) is unchanged.
- **modelCallPrerequisitesFulfilled**: Moves from being an abstract template method to a
  null-check in the handler. Slight change in error semantics for the outbound connector case.

### What We Explicitly Don't Do

- **Don't change the Message model**: Our `Message`, `AssistantMessage`, `ToolCallResultMessage`
  types remain the lingua franca. Custom executors work with these types.
- **Don't change AgentContext**: The context structure, state machine, and metrics tracking remain.
- **Don't change job completion**: `JobWorkerAgentRequestHandler.completeJob()` and the AHSP
  integration are unchanged.
- **Don't remove existing interfaces**: `AgentMessagesHandler`, `AgentLimitsValidator`, etc.
  remain. They're composed by `DefaultAgentExecutor` but are independently useful.

## Implementation Plan

### Prerequisites

The [Conversation Storage Refactoring](conversation-storage-refactoring-plan.md) must be completed
first because:
1. The `ConversationSession` API change (`loadMessages`/`storeMessages`) is needed for the
   executor to work cleanly with `List<Message>`
2. `RuntimeMemory` is removed from the session interface, which is a prerequisite for removing it
   from the framework adapter

### Phase 1: Extract AgentExecutor

#### Task 1.1: Create AgentExecutor Interface and AgentExecutionResult

**New files**: `AgentExecutor.java` and `AgentExecutionResult.java` in
`io.camunda.connector.agenticai.aiagent.agent`

```java
public interface AgentExecutor {
    AgentExecutionResult execute(
        AgentExecutionContext executionContext,
        AgentContext agentContext,
        List<ToolCallResult> toolCallResults);
}

public record AgentExecutionResult(
    @Nullable AgentResponse agentResponse,
    @Nullable ConversationSession session
) {
    public static AgentExecutionResult noOp() { ... }
    public static AgentExecutionResult of(AgentResponse response, ConversationSession session) { ... }
}
```

#### Task 1.2: Create DefaultAgentExecutor

**New file**: `DefaultAgentExecutor.java`

Move the orchestration logic from `BaseAgentRequestHandler.handleRequest(C, AgentContext,
List<ToolCallResult>, ConversationSession)` into `DefaultAgentExecutor.execute()`.

Key changes vs current code:
- Constructor takes all the current dependencies: `ConversationStoreRegistry`,
  `AgentLimitsValidator`, `AgentMessagesHandler`, `GatewayToolHandlerRegistry`,
  `AiFrameworkAdapter`, `AgentResponseHandler`
- `execute()` method contains the full pipeline (load → messages → LLM → store → response)
- Returns `AgentExecutionResult.noOp()` when `addedUserMessages` is empty (no-op case)
- Returns `AgentExecutionResult.of(response, session)` on normal and reconciliation paths
- No abstract methods — this is a concrete class

#### Task 1.3: Simplify BaseAgentRequestHandler

**File**: `BaseAgentRequestHandler.java`

- Remove all dependencies except `AgentInitializer` and `AgentExecutor`
- Remove `handleRequest(C, AgentContext, List<ToolCallResult>)` and
  `handleRequest(C, AgentContext, List<ToolCallResult>, ConversationSession)` private methods
- Keep `handleRequest(C)` which does: init → switch → execute → complete
- Remove `modelCallPrerequisitesFulfilled` abstract method
- Remove `handleAddedUserMessages` hook method
- The `completeJob` abstract method remains

#### Task 1.4: Update JobWorkerAgentRequestHandler

**File**: `JobWorkerAgentRequestHandler.java`

- Remove `modelCallPrerequisitesFulfilled` override
- Move `handleAddedUserMessages` logic (interrupted tool call detection) into an appropriate
  location. Options:
  - A callback/hook on `DefaultAgentExecutor`
  - A pre-processing step in the handler before calling `execute()`
  - Part of a custom `AgentMessagesHandler` wrapper
- Update constructor to take `AgentExecutor` instead of individual components
- Unpack `AgentExecutionResult` — extract `agentResponse()` and `session()`, pass both to
  `completeJob(executionContext, result.agentResponse(), result.session())`
- Handle null `agentResponse` from result (no-op completion — existing logic)
- The `completeJob` signature already takes `ConversationSession` from the storage refactoring —
  no signature change needed

#### Task 1.5: Update OutboundConnectorAgentRequestHandler

**File**: `OutboundConnectorAgentRequestHandler.java`

- Remove `modelCallPrerequisitesFulfilled` override
- Unpack `AgentExecutionResult` — check for null `agentResponse()` and throw `ConnectorException`
- Pass `result.session()` to `completeJob`
- Update constructor

### Phase 2: Decouple AiFrameworkAdapter from RuntimeMemory

#### Task 2.1: Change AiFrameworkAdapter Signature

**File**: `AiFrameworkAdapter.java`

```java
// Before:
R executeChatRequest(AgentExecutionContext ctx, AgentContext agentCtx, RuntimeMemory memory);

// After:
R executeChatRequest(AgentExecutionContext ctx, AgentContext agentCtx, List<Message> messages);
```

#### Task 2.2: Update Langchain4JAiFrameworkAdapter

**File**: `Langchain4JAiFrameworkAdapter.java`

```java
// Before:
final var messages = chatMessageConverter.map(runtimeMemory.filteredMessages());

// After:
final var l4jMessages = chatMessageConverter.map(messages);
```

#### Task 2.3: Update DefaultAgentExecutor to Pass Filtered Messages

**File**: `DefaultAgentExecutor.java`

```java
// The executor passes the filtered view to the adapter:
var chatResponse = framework.executeChatRequest(
    executionContext, agentContext, runtimeMemory.filteredMessages());
```

### Phase 3: Wire Up and Auto-Configuration

#### Task 3.1: Update AutoConfiguration

**File**: `AgenticAiConnectorsAutoConfiguration.java`

- Add `DefaultAgentExecutor` bean (or `AgentExecutor` bean)
- Allow users to provide their own `AgentExecutor` bean that replaces the default
  (`@ConditionalOnMissingBean`)
- Update `JobWorkerAgentRequestHandler` and `OutboundConnectorAgentRequestHandler` bean
  definitions to inject `AgentExecutor`

#### Task 3.2: Handle handleAddedUserMessages Migration

The `handleAddedUserMessages` hook in `JobWorkerAgentRequestHandler` currently sets
`cancelRemainingInstances` when interrupted tool calls are detected. This needs a home:

**Recommended approach**: Add a `postProcessUserMessages` callback to `DefaultAgentExecutor` that
the job worker handler can set. Or make it a step in the executor pipeline that the handler
configures.

Alternatively, move the interrupted tool call detection to `JobWorkerAgentRequestHandler` — before
calling `agentExecutor.execute()`, scan the tool call results for interrupted flags and set the
cancel flag on the execution context. This keeps the executor clean and moves flavor-specific
behavior to the handler.

### Phase 4: Tests

#### Task 4.1: Create AgentExecutor Tests

- Unit test `DefaultAgentExecutor` in isolation: mock all dependencies, verify the pipeline steps
  are called in order, verify null return on empty user messages
- **Reconciliation tests**: Verify `DefaultAgentExecutor` handles `ConversationLoadResult` with
  `reconciledFromStore = true` correctly — skips LLM call, derives response from conversation
  state, calls `storeMessages()` to sync version. See Task 2.4 in the
  [storage refactoring plan](conversation-storage-refactoring-plan.md) for detailed test scenarios.
- Test custom `AgentExecutor` integration: verify a minimal custom executor can be wired up

#### Task 4.2: Update Existing Tests

- `JobWorkerAgentRequestHandlerTest` — adapt for new constructor, mock `AgentExecutor`
- `OutboundConnectorAgentRequestHandlerTest` — adapt similarly
- `Langchain4JAiFrameworkAdapterTest` — update to pass `List<Message>` instead of `RuntimeMemory`
- `AgenticAiConnectorsAutoConfigurationTest` — verify `AgentExecutor` bean creation

### Phase 5: Documentation

#### Task 5.1: Update AGENTS.md

- Add AgentExecutor to the extension points table
- Document how to provide a custom executor via Spring bean

#### Task 5.2: Update Architecture Deep-Dive

- Update the orchestration flow diagram to show the executor layer
- Document the new extension points
- Update code examples

## Open Questions

1. **handleAddedUserMessages**: Where does the interrupted-tool-call detection live after
   the refactoring? Recommended: move to `JobWorkerAgentRequestHandler` as a pre-processing step
   before calling `execute()`. But this means the execution context needs to be mutable (to set
   `cancelRemainingInstances`), which it already is for the job worker variant.

2. ~~**ConversationSession in completeJob signature**~~ **Resolved**: The executor returns an
   `AgentExecutionResult` record containing both `@Nullable AgentResponse` and
   `@Nullable ConversationSession`. The handler unpacks both and passes the session to
   `completeJob(ctx, agentResponse, session)`. The `completeJob` signature already takes
   `ConversationSession` from the storage refactoring. Custom executors that manage their own
   storage return `AgentExecutionResult(response, null)` — the handler only calls lifecycle hooks
   when session is non-null.

3. **Should AgentExecutor be a functional interface?** It has a single method, which would allow
   lambda implementations. This is nice for simple cases but might be confusing for complex
   implementations. Recommendation: keep it as a regular interface for clarity.

4. **Versioning and backward compatibility**: Is `AgentExecutor` part of the public API from day
   one, or should it be marked as experimental/incubating? Custom executor implementations would
   depend on the stability of `AgentResponse`, `AgentContext`, and `ToolCallResult` — which are
   already public.

5. **Reconciliation and custom executors**: The `DefaultAgentExecutor` handles reconciliation
   transparently. But if a user provides a custom `AgentExecutor`, should they be responsible for
   handling `ConversationLoadResult.reconciledFromStore()`? Options:
   - (a) Yes — custom executors get the full `ConversationLoadResult` and decide what to do. This is
     consistent with "full control over the agent processing pipeline."
   - (b) No — reconciliation is handled by the caller (the handler) before delegating to the
     executor. This makes custom executors simpler but removes their ability to customize
     reconciliation behavior.
   - Recommendation: (a). The executor owns the full pipeline including conversation loading. A
     custom executor that calls `session.loadMessages()` naturally gets the `ConversationLoadResult`
     and can handle it however it wants. Forcing reconciliation at the handler level would re-couple
     the handler to conversation internals.

6. **`findLastAssistantMessage` utility**: The reconciliation path needs to find the last
   `AssistantMessage` in the loaded conversation. This should be a utility method on a helper class
   (e.g., `ConversationUtils`) rather than inline in the executor. It needs to handle edge cases:
   what if the conversation has no assistant messages? (Should not happen if reconciliation is
   correctly reported, but defensive coding is warranted.)

## References

- Current `BaseAgentRequestHandler`:
  `connectors/agentic-ai/src/main/java/.../aiagent/agent/BaseAgentRequestHandler.java`
- Current `AiFrameworkAdapter`:
  `connectors/agentic-ai/src/main/java/.../aiagent/framework/AiFrameworkAdapter.java`
- Current `Langchain4JAiFrameworkAdapter`:
  `connectors/agentic-ai/src/main/java/.../framework/langchain4j/Langchain4JAiFrameworkAdapter.java`
- `AgentExecutionContext`:
  `connectors/agentic-ai/src/main/java/.../aiagent/model/AgentExecutionContext.java`
- `AgentResponse`:
  `connectors/agentic-ai/src/main/java/.../aiagent/model/AgentResponse.java`
- Storage refactoring plan:
  `connectors/agentic-ai/docs/conversation-storage-refactoring-plan.md`
