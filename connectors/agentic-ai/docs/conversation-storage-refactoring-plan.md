# Conversation Storage Refactoring: Implementation Plan

* Deciders: Agentic AI Team
* Date: Feb 20, 2026

## Status

**Proposed** — awaiting team buy-in before implementation.

## Context and Problem Statement

The AI Agent's conversation storage architecture has several fundamental issues that need to be
addressed to ensure correctness, durability, and pluggability in production deployments.

### The Split State Problem

The agent's state is split between two locations:
- **Zeebe process variables** (`agentContext`): persisted atomically via `completeJob`
- **Conversation data**: stored either embedded in `agentContext` (in-process) or in a Camunda
  Document (document store), with the reference/data going through `completeJob`

Both storage backends write conversation data as part of the `completeJob` command. The document
store creates the document before `completeJob` (write-ahead), but the in-process store embeds
messages directly in the `agentContext` variable that's included in `completeJob`.

### The InProcess Storage Limitations

The `InProcessConversationStore` embeds all messages inside
`agentContext.conversation.messages`, which is a process variable persisted only via `completeJob`.

**If `completeJob` fails** (due to supersession, transient network error, or partition leader
change), **the conversation is lost** — it existed only in JVM heap during processing. There is no
external artifact to recover from. The next job activation loads the previous `agentContext`
snapshot, which has the old conversation, and must re-do all work including LLM calls.

In the stateless processing model, this is **correct but not optimal** — the system re-processes
from the last known good state and produces the same outcome. The cost is one repeated LLM call in
a rare timing window. This is acceptable for getting started and simple use cases.

In contrast, the `CamundaDocumentConversationStore` creates a Document **before** attempting
`completeJob`. Even if completion fails, the document exists as a durable artifact. External stores
(RDBMS, Redis, AWS AgentCore) can go further and support reconciliation to avoid the repeated LLM
call entirely (see "Version-Aware Store-as-Truth Reconciliation" below).

**Recommendation**: In-process storage is suitable for getting started, demos, and short
conversations. For production deployments with long conversations, frequent tool calling, or
expensive LLM calls, use Camunda Document storage or a custom store.

### The InProcess Variable Growth Problem

With in-process storage, every message in the conversation is serialized into the `agentContext`
process variable. This variable grows unboundedly with conversation length, causing:
- Increased Zeebe storage costs
- Larger job payloads on every activation
- Potential hitting of variable size limits on long conversations

### The ConversationSession API Coupling

The current `ConversationSession` interface couples storage to `RuntimeMemory`:

```java
public interface ConversationSession {
    void loadIntoRuntimeMemory(AgentContext agentContext, RuntimeMemory memory);
    AgentContext storeFromRuntimeMemory(AgentContext agentContext, RuntimeMemory memory);
}
```

`RuntimeMemory` is an orchestration concern (context windowing, message filtering for LLM calls).
Storage should not need to know about it. This coupling:
- Forces storage implementations to depend on runtime internals
- Makes it harder to swap storage backends independently
- Prevents future `AgentExecutor` refactoring (where `RuntimeMemory` should be an internal detail)

### The Compensation Hook That Does Nothing

`ConversationStore.compensateFailedJobCompletion()` exists as a hook for recovery after failed
`completeJob`, but no implementation overrides the default no-op. In the stateless processing model
we're adopting, compensation is the wrong pattern — the system should be designed for idempotent
re-processing instead.

### Multi-Replica Reality

The connectors runtime runs as multiple replicas in Kubernetes. Any pod can pick up any job. This
means:
- **In-process caching across job executions is not viable** — Job N might be handled by Pod A,
  and Job N+1 (after supersession) by Pod B. Pod B has no knowledge of Pod A's state.
- **Memory-based side channels don't work** — ConcurrentHashMap caches would be empty on different
  pods, making cache misses the norm, not the exception.
- **Memory leaks** — any in-process cache keyed by process instance would grow unboundedly as
  process instances accumulate across the pod's lifetime, requiring complex eviction policies.
- **Each job activation must be fully self-contained** — load from durable storage, process, save
  to durable storage, complete job. No assumptions about previous executions on the same pod.

## Decision

### Recommend Camunda Documents for Production; Keep InProcess as Default

**In-process storage remains the default** for zero-infrastructure getting started. It works
correctly in the stateless processing model and requires no additional setup.

**Camunda Document storage is recommended for production** because it provides:
- **Write-ahead durability**: Document is created before `completeJob`, surviving completion
  failures
- **Small process variables**: `agentContext` holds only a document reference (~100 bytes), not
  the full conversation
- **Native Camunda**: No external infrastructure to manage
- **Audit trail**: The document IS the full conversation trail — all messages, not just the context
  window
- **Single location**: No data scatter across systems. When multiple agents exist in a process, each
  agent's conversation is a separate document, all accessible from the same process instance.
- **Bounded storage via rolling TTL**: Each iteration creates a new document with a fresh
  `expiresAt`; `onJobCompleted()` deletes the previous document. At steady state, exactly 1
  document per conversation exists. After the last iteration, the final document auto-expires via
  TTL — no orphans accumulate.

Users switch to Document storage via the element template dropdown — no code changes needed.

**Document TTL strategy**: Documents use a **rolling TTL** (default: 30 days). Each `storeMessages()`
call creates a new document with `expiresAt = now + configuredTTL`. Since `onJobCompleted()` deletes
the previous document, only 1 document per active conversation exists at any time. When the
conversation ends (AHSP completes, process finishes), the final document auto-expires after the TTL
period. This keeps storage bounded without requiring explicit cleanup.

**Constraint**: The TTL must exceed the longest expected idle period between agent iterations. For
tool-calling agents in AHSPs, idle gaps are typically seconds to minutes (tool execution time), so
30 days is generous. Processes with longer idle periods (e.g., human tasks between agent
invocations that reuse the same conversation) should configure a higher TTL. This constraint should
be documented prominently in the element template and user documentation.

### Stateless Job Processing

Each job activation is fully self-contained, regardless of which store is used:

```
1. Load conversation from store (reference/data from agentContext in job snapshot)
2. Add tool results / user messages to runtime memory
3. Call LLM (if prerequisites met)
4. Write conversation to store (full conversation including all messages)
5. completeJob(agentContext with updated conversation reference/data)
```

On supersession or transient failure:
- Next job loads from the **last successfully completed** agentContext snapshot
- Re-processes tool results and re-calls LLM if needed
- Always correct. Cost: one repeated LLM call in rare scenarios.
- Stores that support reconciliation can avoid this cost (see below).

### Accept Rare Re-Processing Cost

Supersession that causes a repeated LLM call is rare:
- The `modelCallPrerequisitesFulfilled` gate means only the job with ALL tool results calls the LLM
- Intermediate jobs (partial tool results) do fast no-op completions — supersession there costs
  nothing
- The expensive path (full LLM call) is only re-done when "all results present" overlaps with
  "another event during processing" — a narrow window
- For transient `completeJob` failures: `CommandWrapper` retries 3 times before the job times out

The simplicity and correctness of stateless processing far outweigh the cost of rare repeated LLM
calls.

### Version-Aware Store-as-Truth Reconciliation

The stateless processing model accepts re-processing cost when `completeJob` fails. However, for
stores that support querying by conversation key, we can **eliminate wasted LLM calls entirely** by
loading the latest version from the store instead of only loading what `agentContext` references.

**Current reconciliation capability by store type**:
- **In-process**: Cannot reconcile — no external artifact exists (data is embedded in `agentContext`)
- **Camunda Document**: Cannot reconcile **today** — the Document API only supports get-by-ID, not
  query-by-properties. The foundation exists (we already store `conversationId` as a custom
  property), but a document search/query API would be needed. This is a potential future platform
  enhancement.
- **Custom external stores** (RDBMS, Redis, AWS AgentCore): **Can reconcile** — these stores
  support querying by conversation key and can detect when they're ahead of Zeebe.

**The critical insight** (from the brainstorming session): on every job activation, a store that
supports reconciliation loads conversation from the external store **by conversation key**, not by
the specific version/reference in `agentContext`. If a previous iteration's `completeJob` failed
after writing to the store, the store already has the latest state — including the LLM response.
The next iteration picks it up instead of re-doing the work.

**Version tracking**: Every `ConversationContext` implementation includes a `version` field
(monotonically increasing integer). On each `storeMessages()` call, the version increments. The
version in `agentContext.conversation` (persisted via `completeJob`) serves as a reference point —
the store may have a newer version if a previous iteration wrote but failed to complete.

**Reconciliation flow** (for stores that support it):

```
Iteration 1 (fails):
  1. Load from store: messages v5
  2. Process: add tool results, call LLM, get response
  3. Store: write messages v6 (includes LLM response)
  4. completeJob → FAILS (supersession)

Iteration 2 (reconciles):
  1. Load from store by conversation key → finds v6 (ahead of Zeebe's v5)
  2. reconciledFromStore = true
  3. Derive response from last assistant message in loaded conversation
  4. Skip all agent processing (no wasted LLM call)
  5. completeJob with derived response → succeeds
  6. Zeebe updated to v6
```

**Without reconciliation** (in-process, Document store today, or any store that loads by
reference only):
- Iteration 2 loads v5 (from Zeebe's reference/data), re-processes, re-calls LLM → correct but
  wasteful. This is the "accept rare re-processing cost" path.

**The stuck AHSP edge case**: Without reconciliation, if the LLM returned a final response (no
tool calls) and `completeJob` failed, the next iteration loads the old conversation, re-calls the
LLM, and correctly completes. With reconciliation, the store already has the final response — the
executor detects this (last message is an `AssistantMessage` with no tool calls, and
`reconciledFromStore = true`) and replays the completion directly.

**SPI design**: Reconciliation support is **optional per store**. The `ConversationLoadResult`
record includes a `reconciledFromStore` boolean. Stores that can't reconcile today (in-process,
Document without search API) always return `false`. The executor handles both cases transparently.
The API is designed so that stores can adopt reconciliation incrementally — e.g., Document store
could start reconciling if the platform adds a document search API, without any SPI change.

### Decouple ConversationSession from RuntimeMemory

New API:

```java
public interface ConversationSession extends AutoCloseable {
    ConversationLoadResult loadMessages(AgentContext agentContext);
    AgentContext storeMessages(AgentContext agentContext, List<Message> messages);

    /** Called after completeJob succeeds. */
    default void onJobCompleted(AgentContext agentContext) {}

    /** Called when conversation is no longer needed. */
    default void cleanup(AgentContext agentContext) {}

    /** Release any resources held by this session (e.g., SDK clients, connections).
     *  Called by the handler after all lifecycle hooks have completed.
     *  No-op default — most sessions hold no resources. */
    @Override
    default void close() {}
}

public record ConversationLoadResult(
    List<Message> messages,
    boolean reconciledFromStore
) {}
```

- `loadMessages()` returns a `ConversationLoadResult` containing the messages and a flag indicating
  whether the store reconciled from a newer version than `agentContext` references.
- `storeMessages()` writes the full message list and returns an updated `agentContext` with the new
  `ConversationContext` (including incremented version).
- The session internally tracks the loaded version for optimistic concurrency on writes.
- `close()` releases any resources held by the session. This replaces the implicit resource
  management that `executeInSession()` provided via try-with-resources in the callback pattern.
  Stores that allocate per-session resources (e.g., AWS SDK clients) override `close()` to release
  them. The caller is responsible for calling `close()` after all session operations (including
  lifecycle hooks) are complete.

The caller (`BaseAgentRequestHandler` / `DefaultAgentExecutor`) manages `RuntimeMemory` internally:
1. `session = store.createSession(executionContext, agentContext)` — create session (may allocate
   resources)
2. `loadResult = session.loadMessages(agentContext)` — get messages + reconciliation flag
3. If `loadResult.reconciledFromStore()` — derive response from conversation state (see
   reconciliation handling below), skip to step 6
4. Create `RuntimeMemory`, populate with loaded messages, process (add system/user/tool messages,
   LLM call, etc.)
5. `agentContext = session.storeMessages(agentContext, runtimeMemory.allMessages())` — persist all
   messages
6. Return response for job completion (executor returns session alongside response)
7. After `completeJob` succeeds: `session.onJobCompleted(agentContext)` → `session.close()`
8. On `completeJob` failure: `session.close()` (no `onJobCompleted` — reconciliation handles
   recovery on next activation)

**Reconciliation handling** (step 3): When the store is ahead, the loaded messages already contain
the LLM response from the superseded iteration. The executor:
- Extracts the last `AssistantMessage` from the loaded conversation
- Re-runs the response pipeline (gateway tool call transformation, response formatting) to create
  the `AgentResponse`
- Stores the messages (to update the `agentContext` reference to match the store's version)
- Returns the response directly — no LLM call needed

This cleanly separates:
- **Storage** (load/store `List<Message>`, version tracking, reconciliation) — the session's
  responsibility
- **Orchestration** (windowing, filtering, LLM interaction, reconciliation handling) — the
  executor's responsibility

### Replace executeInSession Callback with createSession Factory

The current `ConversationStore` uses a callback pattern:

```java
// Current — callback pattern
public interface ConversationStore {
    String type();
    <T> T executeInSession(AgentExecutionContext ctx, AgentContext agentContext,
                           ConversationSessionHandler<T> handler);
}
```

This was designed to allow RDBMS stores to wrap the entire load→process→store flow in a
`@Transactional` boundary. However, spanning a database transaction across an LLM call is
**actively harmful**:

- **Connection pool exhaustion**: LLM calls take seconds to tens of seconds. Holding a DB connection
  open during that time means one connection per concurrent agent execution. Under load, this
  exhausts the pool and blocks all other database operations.
- **Transaction timeout risk**: Long-running transactions are prone to hitting timeout limits,
  causing unexpected rollbacks mid-processing.
- **Lock contention**: Depending on isolation level, the spanning transaction may hold row-level
  locks on conversation data for the entire LLM call duration.

**The better pattern is per-operation short transactions** with **optimistic concurrency** via
version checks. Each `loadMessages()` and `storeMessages()` call is an independent, short-lived
transaction. The version field provides the same consistency guarantee as a spanning transaction,
without holding resources during the LLM call.

New `ConversationStore` API:

```java
public interface ConversationStore {
    String type();
    ConversationSession createSession(AgentExecutionContext ctx, AgentContext agentContext);
}
```

`createSession()` is a factory method — it creates and returns a `ConversationSession`. Stores that
need per-session resources (e.g., AWS SDK clients) can allocate them here. The session lives for
the full lifecycle of the job iteration, including after `completeJob` succeeds (for the
`onJobCompleted()` hook), and must be closed via `close()` when all operations are complete.

**Why this is better than the callback:**
1. **No spanning transaction**: No DB connection held during LLM calls
2. **Lifecycle hooks work**: `onJobCompleted()` fires **after** `completeJob` succeeds — outside the
   old callback's scope. With `executeInSession`, the callback returns before `completeJob` is
   called, so post-completion hooks couldn't be part of the session.
3. **Resource management via AutoCloseable**: The session extends `AutoCloseable`, so stores that
   allocate per-session resources override `close()` to release them. This replaces the implicit
   try-with-resources in `executeInSession()` with an explicit `close()` by the caller.
4. **Simpler mental model**: Create a session, use it, close it. No callback nesting.

**Per-operation transaction guidance for RDBMS stores:**

```java
public class MyConversationSession implements ConversationSession {
    private final TransactionTemplate txTemplate;
    private final MyConversationRepository repository;
    private long expectedVersion; // set during loadMessages

    @Override
    public ConversationLoadResult loadMessages(AgentContext agentContext) {
        // Short read transaction
        return txTemplate.execute(status -> {
            var record = repository.findByConversationKey(key);
            expectedVersion = record.version();
            return ConversationLoadResult.of(record.messages());
        });
    }

    @Override
    public AgentContext storeMessages(AgentContext agentContext, List<Message> messages) {
        // Short write transaction with optimistic version check
        return txTemplate.execute(status -> {
            int updated = repository.updateWithVersionCheck(key, messages, expectedVersion);
            if (updated == 0) {
                throw new OptimisticLockException("Conversation was modified concurrently");
            }
            expectedVersion++;
            return agentContext.withConversation(newContext);
        });
    }

    @Override
    public void onJobCompleted(AgentContext agentContext) {
        // Short transaction for post-completion cleanup
        txTemplate.executeWithoutResult(status -> {
            repository.pruneHistory(key, maxRetainedVersions);
        });
    }

    // No close() override needed — RDBMS sessions use Spring-managed DataSource
    // (connections are per-transaction, not per-session)
}
```

**AWS AgentCore example** (session that holds an AutoCloseable resource):

```java
public class AwsAgentCoreConversationSession implements ConversationSession {
    private final BedrockAgentCoreClient client; // AutoCloseable SDK client

    // loadMessages, storeMessages use this.client ...

    @Override
    public void close() {
        client.close(); // release AWS SDK client resources
    }
}
```

**Optimistic lock exceptions**: These are **very rare** in our design. They can only occur when two
workers write to the same conversation concurrently — requiring a supersession event where the old
worker's LLM call finishes and races with the new worker's write in a narrow window. In practice,
the old worker almost always either finishes first (its write wins, new worker reconciles) or fails
on `completeJob` with NOT_FOUND (never reaches the conflict). If an optimistic lock exception does
occur, it propagates as a job failure. On the next activation, `loadMessages()` loads the winner's
state, reconciliation detects the store is ahead, and processing continues without a wasted LLM
call. The stateless model handles this gracefully — no data loss, no corruption.

**Migration from @Transactional**: Existing custom stores that use `@Transactional` on
`executeInSession` should migrate to `TransactionTemplate` (or Spring Data `@Version`) on
individual session methods. This is a behavioral improvement, not just an API change — the store
stops holding DB connections during LLM calls.

### Keep InProcess Store as Default with Documented Limitations

The `InProcessConversationStore` remains the default storage backend. It provides the simplest
getting-started experience with no infrastructure requirements. Its known limitations are:

- **No write-ahead**: Conversation data is lost if `completeJob` fails (re-processing cost)
- **Variable growth**: Full conversation serialized into `agentContext` process variable
- **No reconciliation**: Cannot detect or recover from a store-ahead state (no external store)

These are acceptable tradeoffs for getting started, demos, and short conversations. The element
template documentation should guide users toward Document or custom storage for production use
cases with long conversations or expensive LLM calls.

### Replace compensateFailedJobCompletion with Lifecycle Hooks

The current `compensateFailedJobCompletion` is backward-looking ("undo the write after failure")
and no implementation overrides the default no-op. In the stateless model with reconciliation, the
correct strategy is forward-looking: detect and reuse on next load, not undo after failure.

However, the underlying concern — lifecycle management of conversation artifacts — is real. Rather
than removing the hook entirely and losing the lifecycle extension point, we **replace** it with
proper forward-looking lifecycle hooks on `ConversationSession`:

```java
public interface ConversationSession extends AutoCloseable {
    ConversationLoadResult loadMessages(AgentContext agentContext);
    AgentContext storeMessages(AgentContext agentContext, List<Message> messages);

    /** Called after completeJob succeeds. Use for post-success cleanup like deleting
     *  previous document versions or pruning conversation history. */
    default void onJobCompleted(AgentContext agentContext) {}

    /** Called when the conversation is no longer needed (e.g., process completed).
     *  Use for final cleanup of all conversation artifacts. */
    default void cleanup(AgentContext agentContext) {}

    /** Release any resources held by this session (e.g., SDK clients, connections). */
    @Override
    default void close() {}
}
```

**`onJobCompleted()`**: Invoked after `completeJob` succeeds. The session lives beyond the old
`executeInSession` callback scope (because we use `createSession()` instead), so the handler can
call this hook in its success callback. The Document store can use this to delete the previous
document version (currently handled inline in `storeMessages`; this is cleaner). Custom stores can
use it for post-success metadata updates, history pruning, etc.

**`cleanup()`**: Invoked when the conversation is no longer needed. Enables stores to delete all
conversation artifacts. Currently no cleanup mechanism exists — orphaned documents accumulate until
TTL expires.

**`close()`**: Releases any resources held by the session. This replaces the implicit resource
management that `executeInSession()` provided via its callback scope. With the old callback
pattern, stores like the AWS AgentCore store could use `try (var client = ...) { ... }` inside
`executeInSession()` — the callback return guaranteed the client was closed. With `createSession()`,
the session may hold resources (SDK clients, pooled connections) that need explicit release.

The caller (handler) calls `close()` after all session operations are complete — including
lifecycle hooks (`onJobCompleted`, `cleanup`). For the job worker handler, this means `close()` is
called in both the success callback (after `onJobCompleted`) and the error path. For the outbound
connector handler (synchronous), try-with-resources can be used directly.

All hooks have no-op defaults — existing stores work without changes. The hooks are optional
extension points, not mandatory lifecycle requirements.

### Custom Stores Remain Pluggable

The `ConversationStore` SPI continues to support custom implementations (JDBC, Redis, etc.)
registered via Spring beans. Custom stores implement `type()` and `createSession()`, returning a
`ConversationSession` that handles their own durability and consistency guarantees. The
`CustomMemoryStorageConfiguration` element template option is preserved.

**Important for RDBMS stores**: Use per-operation short transactions (`TransactionTemplate` or
Spring Data `@Version`) instead of wrapping the full session in `@Transactional`. See the
"Replace executeInSession Callback with createSession Factory" section above for guidance and
example code.

**Reconciliation guidance for custom store implementors**: Custom stores that support querying by
conversation key (RDBMS, Redis, etc.) should implement store-as-truth reconciliation in
`loadMessages()`:

1. Load the latest version from the store (by conversation key, not by the version in
   `agentContext.conversation`)
2. Compare the loaded version against the version in `agentContext.conversation`
3. If the store's version is newer: return `ConversationLoadResult(messages, true)` — the executor
   will derive the response from the conversation state and skip re-processing
4. If versions match: return `ConversationLoadResult(messages, false)` — normal processing

Custom stores with **append-only/mutable semantics** (like AWS AgentCore) benefit most from
reconciliation because it prevents the "partial writes + re-processing = state corruption" problem.
When the store detects it's ahead, it returns the complete state including the superseded
iteration's work, avoiding the need to re-call the LLM and re-write to the store.

Custom stores that write **immutable snapshots** (similar to the Document store pattern) benefit
from reconciliation for efficiency (avoiding wasted LLM calls) but don't have the state corruption
risk — each write is a new snapshot, and stale references just point to an older (still valid)
snapshot.

## Migration Path (InProcess → Document)

Users who switch an existing process from in-process to Document storage (via the element template
dropdown) get transparent migration:

1. Job activates → `agentContext.conversation` deserializes as `InProcessConversationContext`
2. `CamundaDocumentConversationSession.loadMessages()` detects the type and extracts the embedded
   `messages` list directly (no document to load)
3. Agent processes normally (add messages, call LLM, etc.)
4. `storeMessages()` writes a new Camunda Document with the full conversation
5. Returns `agentContext` with a `CamundaDocumentConversationContext` (document reference)
6. `completeJob` persists the updated `agentContext` → all future iterations use Document storage

**No process redeployment needed.** Migration happens automatically on the next agent iteration
after the user changes the storage type in the element template. Operators see a one-time
transition from embedded messages to document references in their process variables.

### Element Template Changes

The element template retains all three storage options (In Process, Camunda Document, Custom
Implementation) with In Process as the default. The only template change from this refactoring is
the regeneration after the `ConversationSession` API change — the user-facing options are
unchanged.

## Tradeoffs

### What We Gain

- **Clean SPI**: `ConversationSession` works with `List<Message>`, no `RuntimeMemory` coupling.
  `ConversationStore.createSession()` is a factory — no callback nesting. `ConversationSession`
  extends `AutoCloseable` for explicit resource management (replaces the implicit try-with-resources
  that `executeInSession()` provided).
- **No spanning transactions**: `createSession()` replaces `executeInSession()`, eliminating the
  pattern of wrapping LLM calls in database transactions. RDBMS stores use per-operation short
  transactions with optimistic concurrency — no connection pool exhaustion under load.
- **Lifecycle hooks**: `onJobCompleted()` and `cleanup()` replace the broken `compensateFailedJobCompletion`
  with forward-looking extension points. The session lives beyond the old callback scope, so hooks
  fire at the right time. Document store uses `onJobCompleted()` for safe old-version deletion
  (only after `completeJob` succeeds).
- **Stateless correctness**: All stores are correct in the stateless processing model — re-process
  from last known good state on failure
- **Pluggable**: Three built-in options (in-process, Document, custom) with clear tradeoff guidance
- **Multi-replica safe**: No in-process state assumptions, any pod handles any job
- **Reconciliation-ready**: The `ConversationLoadResult` API allows external stores to signal when
  they've reconciled from a newer version, eliminating wasted LLM calls after `completeJob`
  failures. This is opt-in per store — built-in stores don't reconcile, custom stores can.
- **Prevents state corruption for mutable stores**: Stores with append-only/mutable semantics
  (e.g., AWS AgentCore) avoid the "partial write + re-processing = duplicated messages" problem
  because reconciliation detects the store is already ahead and skips re-processing.
- **Easy getting started**: In-process default requires zero infrastructure setup
- **Production path**: Document storage available as a one-click upgrade via element template

### What We Accept

- **Rare repeated LLM calls (for non-reconciling stores)**: On supersession or transient completion
  failure with the built-in stores (in-process, Document), the next iteration re-processes from the
  last completed state. This costs one extra LLM call in a narrow timing window. Custom stores that
  implement reconciliation avoid this cost entirely.
- **Variable growth with in-process storage**: In-process storage serializes the full conversation
  into `agentContext`. For long conversations this increases Zeebe storage costs and job payload
  sizes. Users are guided toward Document storage for production use cases.
- **No Operate visibility for Documents**: Documents are not yet visible in Operate's variable
  inspector. This is a platform concern and can be addressed independently. The document reference
  in `agentContext` is visible; the full conversation requires opening the document.

### What We Explicitly Don't Do

- **No in-process caching**: Cross-execution caches are architecturally broken in multi-replica
  deployments (cache misses are the norm, memory leaks, no cross-pod coordination).
- **No `setVariablesCommand` for conversation**: Job variables are snapshots at creation time.
  Writing via `setVariablesCommand` after job creation doesn't update already-created jobs. Timing
  between `setVariablesCommand` and job creation is non-deterministic.
- **No AgentExecutor refactoring**: That's a separate workstream. This plan focuses on storage only.
  The AgentExecutor work (pluggable orchestration, `AiFrameworkAdapter` signature change) builds on
  top of the cleaner storage foundation.

## Implementation Plan

### Phase 1: Storage SPI Refactoring

One atomic change that delivers the complete SPI rewrite, lifecycle hook wiring, and removal of
legacy patterns. All tasks in this phase are part of a single coherent commit.

#### Task 1.1: Change ConversationStore and ConversationSession APIs

**File**: `ConversationStore.java`

Replace:
```java
<T> T executeInSession(AgentExecutionContext ctx, AgentContext agentContext,
                       ConversationSessionHandler<T> handler);
default void compensateFailedJobCompletion(...) {}
```

With:
```java
ConversationSession createSession(AgentExecutionContext ctx, AgentContext agentContext);
```

Remove `executeInSession`, `compensateFailedJobCompletion`, and
`ConversationSessionHandler` (the callback interface). The store now has only `type()` and
`createSession()`.

**File**: `ConversationSession.java`

Replace:
```java
void loadIntoRuntimeMemory(AgentContext agentContext, RuntimeMemory memory);
AgentContext storeFromRuntimeMemory(AgentContext agentContext, RuntimeMemory memory);
```

With:
```java
public interface ConversationSession extends AutoCloseable {
    ConversationLoadResult loadMessages(AgentContext agentContext);
    AgentContext storeMessages(AgentContext agentContext, List<Message> messages);

    default void onJobCompleted(AgentContext agentContext) {}
    default void cleanup(AgentContext agentContext) {}

    @Override
    default void close() {}
}
```

Remove the `RuntimeMemory` import. Add `AutoCloseable`, lifecycle hooks, and `close()` — all with
no-op defaults. The `close()` override avoids checked `Exception` on the default — callers don't
need to handle exceptions for no-op sessions.

**New file**: `ConversationLoadResult.java`

```java
public record ConversationLoadResult(
    List<Message> messages,
    boolean reconciledFromStore
) {
    public static ConversationLoadResult of(List<Message> messages) {
        return new ConversationLoadResult(messages, false);
    }

    public static ConversationLoadResult reconciled(List<Message> messages) {
        return new ConversationLoadResult(messages, true);
    }
}
```

The `reconciledFromStore` flag indicates that the store loaded a newer version than what
`agentContext.conversation` referenced. When `true`, the handler skips processing and derives the
response from the conversation state. The factory methods make the intent clear at call sites.

#### Task 1.2: Add Version Field to ConversationContext

**File**: `ConversationContext.java`

Add `version` to the base interface:
```java
public interface ConversationContext {
    String conversationId();
    long version();
}
```

**File**: `InProcessConversationContext.java`

Add `version` field (default 0 for new conversations):
```java
public record InProcessConversationContext(
    String conversationId,
    long version,
    List<Message> messages
) implements ConversationContext { ... }
```

**File**: `CamundaDocumentConversationContext.java`

Simplify and add `version`:
```java
public record CamundaDocumentConversationContext(
    String conversationId,
    long version,
    Document document
) implements ConversationContext { ... }
```

**Key change**: The `previousDocuments: List<Document>` field is removed. The session tracks the
old document reference as transient state (set during `loadMessages()`, used in
`onJobCompleted()`). TTL is the safety net for orphaned documents if `onJobCompleted()` fails. This
simplifies the serialized context in `agentContext` and eliminates the retention-size bookkeeping.

**Backward compatibility**: Existing `agentContext` values in running processes may still have the
old shape (no `version`, or with `previousDocuments`). Jackson deserialization should handle missing
fields gracefully — `version` defaults to 0 (via `@JsonProperty(defaultValue = "0")` or a custom
deserializer), `previousDocuments` is ignored if present.

**Version incrementing**: Every `storeMessages()` call increments the version. Both built-in stores
and custom stores do this. The version in `agentContext.conversation` (persisted via `completeJob`)
serves as Zeebe's reference point — the store may have a newer version if a previous iteration
wrote but failed to complete.

#### Task 1.3: Update CamundaDocumentConversationSession and Store

**File**: `CamundaDocumentConversationStore.java`
- Replace `executeInSession` with `createSession` that returns a new
  `CamundaDocumentConversationSession` (pure factory, no callback)

**File**: `CamundaDocumentConversationSession.java`

Implement the new API. The session tracks the old document reference as transient state.

**`loadMessages(AgentContext)`**:
- Use `ConversationUtil.loadConversationContext(agentContext, ConversationContext.class)` (note:
  load as the base `ConversationContext` type, not a specific subtype)
- If `null` → return `ConversationLoadResult.of(List.of())` (first iteration)
- If `CamundaDocumentConversationContext` → load document, deserialize, return
  `ConversationLoadResult.of(messages)` (existing logic). Store the context's `document` reference
  as `previousDocument` (transient session field) for use in `onJobCompleted()`
- If `InProcessConversationContext` → return `ConversationLoadResult.of(context.messages())`
  directly (**migration path**). No `previousDocument` to track (no document to delete).

**Important**: The Document store returns `reconciledFromStore = false` today. Although documents
are created **before** `completeJob` (write-ahead), the Document API (`GET /documents/{documentId}`)
only supports retrieval by document ID — there is no search/query endpoint to find a document by
custom properties like `conversationId`. If `completeJob` failed, the written document exists but
we can't find it because the reference wasn't persisted in `agentContext`.

**Future reconciliation potential**: The session already stores `conversationId` as a custom
property on every document. If the platform adds a document search/query API (e.g.,
`POST /documents/search` with filter by `customProperties.conversationId`), the Document store
could reconcile by querying for the latest document for this conversation and comparing versions.
This would be a non-breaking enhancement to `CamundaDocumentConversationSession.loadMessages()`.

**`storeMessages(AgentContext, List<Message>)`**:
- Create new document with all messages (existing `createUpdatedDocument` logic, but taking
  `List<Message>` instead of extracting from `RuntimeMemory`)
- Set `expiresAt = now + configuredTTL` on the new document (rolling TTL — refreshed every
  iteration, default 30 days)
- Increment `version` from the loaded context (or start at 1 for new conversations)
- Return `agentContext.withConversation(newDocumentContext)` where `newDocumentContext` has the
  new document reference, incremented version, and no `previousDocuments`

**`onJobCompleted(AgentContext)`**:
- Delete `previousDocument` (the transient field set during `loadMessages()`), if any
- This keeps storage at 1 document per conversation. Only deletes after `completeJob` succeeds —
  if completion failed, the old document remains available for the next iteration.
- Log and swallow deletion failures — TTL handles cleanup as a fallback.

#### Task 1.4: Update InProcessConversationSession and Store

**File**: `InProcessConversationStore.java`
- Replace `executeInSession` with `createSession` that returns a new
  `InProcessConversationSession` (pure factory, no callback)

**File**: `InProcessConversationSession.java`

Update to implement the new API:

```java
ConversationLoadResult loadMessages(AgentContext agentContext) {
    previousConversationContext = loadConversationContext(agentContext, InProcessConversationContext.class);
    var messages = previousConversationContext != null
        ? previousConversationContext.messages() : List.of();
    return ConversationLoadResult.of(messages); // never reconciles — no external store
}

AgentContext storeMessages(AgentContext agentContext, List<Message> messages) {
    long nextVersion = previousConversationContext != null
        ? previousConversationContext.version() + 1 : 1;
    var builder = previousConversationContext != null
        ? previousConversationContext.with()
        : InProcessConversationContext.builder().conversationId(UUID.randomUUID().toString());
    return agentContext.withConversation(
        builder.version(nextVersion).messages(messages).build());
}
```

The in-process store always returns `reconciledFromStore = false` — there is no external store to
reconcile from. Conversation data is embedded in `agentContext` and persisted atomically via
`completeJob`. If `completeJob` fails, the next iteration re-processes from the previous snapshot.

#### Task 1.5: Update BaseAgentRequestHandler

**File**: `BaseAgentRequestHandler.java`

Change the `handleRequest` method to use `createSession()` instead of `executeInSession()`, and
change the `completeJob` signature to take `ConversationSession` instead of `ConversationStore`:

```java
private R handleRequest(C executionContext, AgentContext agentContext,
                        List<ToolCallResult> toolCallResults) {
    var store = conversationStoreRegistry.getConversationStore(executionContext, agentContext);
    var session = store.createSession(executionContext, agentContext);
    var agentResponse = handleRequest(executionContext, agentContext, toolCallResults, session);
    return completeJob(executionContext, agentResponse, session);
}
```

Change the inner `handleRequest` (the one with `ConversationSession`):

```java
var loadResult = session.loadMessages(agentContext);

if (loadResult.reconciledFromStore()) {
    // Store is ahead of Zeebe — a previous iteration wrote but failed completeJob.
    // The conversation already contains the LLM response. Derive the AgentResponse
    // from conversation state and skip all processing (no wasted LLM call).
    var messages = loadResult.messages();

    // Re-run the response pipeline on the last assistant message to create AgentResponse.
    var lastAssistantMessage = findLastAssistantMessage(messages);
    var toolCalls = gatewayToolHandlers.transformToolCalls(lastAssistantMessage.toolCalls(), ...);
    var processVariableToolCalls = toolCalls.stream()
        .map(ToolCallProcessVariable::from).toList();

    // Store to update agentContext version to match store's version
    agentContext = session.storeMessages(agentContext, messages);

    return responseHandler.createResponse(agentContext, lastAssistantMessage,
        processVariableToolCalls);
}

// Normal path — no reconciliation
runtimeMemory.addMessages(loadResult.messages());
// ... (all processing unchanged: add system/user/tool messages, LLM call, etc.)
agentContext = session.storeMessages(agentContext, runtimeMemory.allMessages());
return responseHandler.createResponse(...);
```

Change the `completeJob` abstract method signature:
```java
// Before:
protected abstract R completeJob(C executionContext,
    @Nullable AgentResponse agentResponse, @Nullable ConversationStore conversationStore);

// After:
protected abstract R completeJob(C executionContext,
    @Nullable AgentResponse agentResponse, @Nullable ConversationSession session);
```

**Key design point**: The reconciliation path re-runs `responseHandler.createResponse()` and
`gatewayToolHandlers.transformToolCalls()` on the already-present assistant message. This ensures
the `AgentResponse` has the correct format (text/JSON/full message) and tool calls are properly
transformed for gateway activation. These operations are cheap and deterministic — only the LLM
call is skipped.

**The `storeMessages()` call in the reconciliation path** may seem redundant (the store already has
these messages), but it serves a critical purpose: it returns an `agentContext` with an updated
`ConversationContext` whose version matches the store. When `completeJob` succeeds, Zeebe's
`agentContext` will be in sync with the store's version, preventing unnecessary reconciliation on
the next iteration. A `syncVersion()` optimization may be added later if the redundant write
becomes a measurable problem — for now simplicity wins.

For the built-in stores (in-process and Document), `reconciledFromStore` is always `false`, so the
reconciliation path is never taken — pure behavioral preservation. The reconciliation path activates
only when a custom store reports it.

#### Task 1.6: Update JobWorkerAgentRequestHandler

**File**: `JobWorkerAgentRequestHandler.java`

- Change `completeJob` to accept `ConversationSession` instead of `ConversationStore`
- In `completeWithResponse()`: replace the `onCompletionError` callback
  (`store.compensateFailedJobCompletion(...)`) with an `onCompletionSuccess` callback that calls
  `session.onJobCompleted(agentContext)` then `session.close()`. The error callback calls
  `session.close()` only (reconciliation handles recovery, not compensation).

**File**: `JobWorkerAgentCompletion.java`

- Add `@Nullable Consumer<AgentContext> onCompletionSuccess` field (or `@Nullable Runnable`)
- Keep `onCompletionError` for error logging + session close (simplify to logging + close, no
  compensation)

**File**: `AiAgentJobWorkerHandlerImpl.java`

- Wire `onCompletionSuccess` into the success path of `completeJob`. Currently the error handler
  is wired at lines 175-182 via the `exceptionHandlingStrategy` lambda. The success callback needs
  a similar wiring point — call `completion.onCompletionSuccess(...)` after `CommandWrapper`
  confirms success.

**Session close guarantee**: The session must be closed on both success and failure paths. The
success path: `onJobCompleted()` → `close()`. The error path: `close()` only (skip
`onJobCompleted` — recovery happens on next activation via reconciliation). The handler ensures
`close()` is called in a `finally`-like manner within the callbacks.

#### Task 1.7: Update OutboundConnectorAgentRequestHandler

**File**: `OutboundConnectorAgentRequestHandler.java`

- Change `completeJob` to accept `ConversationSession` instead of `ConversationStore`
- Call `session.close()` after returning the response. Since the outbound connector handler is
  synchronous (no async `CommandWrapper` flow), the session can be closed directly after
  `storeMessages()` succeeds. Alternatively, use try-with-resources if the handler creates the
  session.

#### Task 1.8: Rebuild Element Templates

Run:
```bash
mvn clean compile -pl connectors/agentic-ai
```

Verify:
- Storage type dropdown defaults to "In Process" (unchanged)
- All three options available: "In Process", "Camunda Document Storage", "Custom Implementation"
- Document-specific fields (TTL, custom properties) shown when Document is selected
- No behavioral changes from the user's perspective

### Phase 2: Tests

#### Task 2.1: Update Existing Tests

**Files to update**:
- `CamundaDocumentConversationStoreTest.java` — adapt for new session API (`loadMessages`/
  `storeMessages` instead of `loadIntoRuntimeMemory`/`storeFromRuntimeMemory`), verify version
  incrementing, verify simplified context (no `previousDocuments`), verify `onJobCompleted()`
  deletes old document
- `InProcessConversationStoreTest.java` — adapt for new session API, verify version incrementing
- `JobWorkerAgentRequestHandlerTest.java` — remove `compensateFailedJobCompletion` verification,
  verify `onCompletionSuccess` calls `session.onJobCompleted()`, adapt session mock expectations,
  verify `completeJob` receives `ConversationSession`
- `OutboundConnectorAgentRequestHandlerTest.java` — adapt session mock expectations, verify
  `completeJob` receives `ConversationSession`

#### Task 2.2: Add Migration Test

Add a test in `CamundaDocumentConversationStoreTest` (or a new dedicated test class) that:

1. Creates an `AgentContext` with an `InProcessConversationContext` containing messages
2. Calls `loadMessages()` on `CamundaDocumentConversationSession`
3. Verifies the messages are loaded correctly from the embedded context
4. Calls `storeMessages()` with those messages (plus new ones)
5. Verifies a Document was created and the returned `agentContext` has a
   `CamundaDocumentConversationContext` with version 1

This validates the transparent in-process → document migration path.

#### Task 2.3: Add Stateless Re-Processing Test

Add a test that simulates the supersession scenario:

1. First iteration: load empty → add messages → LLM response → store to document → get agentContext
   v1 with doc reference
2. Simulate supersession: use agentContext v0 (the OLD snapshot, before v1) to load messages
3. Verify: messages loaded from v0's document (or empty if first iteration), not from v1's
4. Re-process: add same tool results, verify LLM would be called again
5. Store: verify new document created with version 1 (or 2)

#### Task 2.4: Add Reconciliation Handling Tests

Add tests in `BaseAgentRequestHandlerTest` that verify the reconciliation path:

**Test 1 — Reconciliation with tool calls (re-loop)**:
1. Mock `ConversationSession.loadMessages()` to return
   `ConversationLoadResult.reconciled(messages)` where last message is an `AssistantMessage` with
   tool calls
2. Verify: LLM is NOT called (`AiFrameworkAdapter.executeChatRequest()` not invoked)
3. Verify: `responseHandler.createResponse()` IS called with the last assistant message
4. Verify: `gatewayToolHandlers.transformToolCalls()` IS called
5. Verify: `session.storeMessages()` IS called (to sync agentContext version)
6. Verify: the returned `AgentResponse` contains the tool calls from the last assistant message

**Test 2 — Reconciliation with final response (no tool calls)**:
1. Mock `ConversationSession.loadMessages()` to return
   `ConversationLoadResult.reconciled(messages)` where last message is an `AssistantMessage` with
   text content and no tool calls
2. Verify: LLM is NOT called
3. Verify: the `AgentResponse` is a final text response (no pending tool activations)
4. Verify: `storeMessages()` IS called

**Test 3 — No reconciliation (normal path)**:
1. Mock `ConversationSession.loadMessages()` to return `ConversationLoadResult.of(messages)`
2. Verify: normal processing path is taken (LLM IS called, messages added, etc.)

These tests validate that the reconciliation flag correctly routes through the two paths and that
the reconciliation path produces a correct `AgentResponse` from conversation state alone.

#### Task 2.5: Add Version Tracking Tests

- Verify version starts at 0 for new conversations, increments to 1 after first `storeMessages()`
- Verify version increments on every `storeMessages()` call
- Verify backward compatibility: loading an `agentContext` without a `version` field defaults to 0

### Phase 3: Documentation

#### Task 3.1: Update AGENTS.md

- Update the "Memory Storage Backends" table: add tradeoff guidance per store type
- Add recommendation note: in-process for getting started, Document for production
- Document the `ConversationLoadResult` and reconciliation capability for custom stores
- Document the `version` field on `ConversationContext`
- Document rolling TTL strategy and the idle-period constraint

#### Task 3.2: Update Architecture Deep-Dive

**File**: `docs/ai-agent-architecture-deep-dive.md`

Update sections:
- §6 (Conversation Memory): Document recommended for production, in-process limitations documented,
  rolling TTL strategy
- §10 (Concurrency Challenges): Stateless processing model, accepted re-processing cost,
  reconciliation for custom stores, version tracking
- Remove references to `compensateFailedJobCompletion` and `executeInSession`
- Update code snippets showing the new `ConversationSession` and `ConversationStore` APIs

## Task Dependency Graph

```
Phase 1 (Storage SPI Refactoring — single atomic change):
  1.1 ConversationStore + Session APIs ─┬─ 1.2 Add version to ConversationContext
                                        ├─ 1.3 Document Session + Store (new API, simplified context)
                                        ├─ 1.4 InProcess Session + Store (new API, version)
                                        ├─ 1.5 BaseAgentRequestHandler (createSession, completeJob sig)
                                        ├─ 1.6 JobWorkerAgentRequestHandler (success callback, remove
                                        │      compensation)
                                        ├─ 1.7 OutboundConnectorAgentRequestHandler (completeJob sig)
                                        └─ 1.8 Rebuild Element Templates

Phase 2 (Tests):
  1.* ──┬── 2.1 Update existing tests
        ├── 2.2 Add migration test
        ├── 2.3 Add stateless re-processing test
        ├── 2.4 Add reconciliation handling tests
        └── 2.5 Add version tracking tests

Phase 3 (Documentation):
  2.* ──┬── 3.1 Update AGENTS.md
        └── 3.2 Update architecture deep-dive
```

Phase 1 is a single commit/PR — all tasks are interdependent and form one coherent change. Phase 2
can follow immediately. Phase 3 is non-functional.

## Open Questions

1. ~~**Document TTL defaults**~~ **Resolved**: Use a rolling TTL (default: 30 days). Each
   `storeMessages()` sets `expiresAt = now + configuredTTL` on the new document.
   `onJobCompleted()` deletes the previous document, keeping storage at 1 doc per conversation.
   The TTL must exceed the longest expected idle period between iterations — document this
   constraint. See "Document TTL strategy" in the Decision section.

2. **Document size limits**: Are there practical limits on Camunda Document size? Very long
   conversations (thousands of messages) could produce large documents. Should we document
   guidelines or add warnings?

3. **Custom store migration**: When a user switches from InProcess to a custom store (not Document),
   should we provide a similar migration path, or is that their responsibility?

4. ~~**Version field in ConversationContext**~~ **Resolved**: Add `version` to the base
   `ConversationContext` interface. All stores track it; all `storeMessages()` calls increment it.
   See Task 1.2.

5. ~~**Reconciliation and `storeMessages()` idempotency**~~ **Resolved**: Accept the redundant
   write for simplicity. The reconciliation path calls `storeMessages()` to sync the `agentContext`
   version with the store. For non-reconciling stores (built-in), this path is never taken. For
   custom stores that reconcile, the redundant write is one cheap DB row update in an already-rare
   scenario. A `syncVersion()` optimization can be added later if needed — it would return an
   updated `agentContext` without writing, but adds API surface for minimal benefit today.

6. **Reconciliation in outbound connector mode**: The outbound connector handler
   (`OutboundConnectorAgentRequestHandler`) runs single-shot, not in the AHSP loop. Supersession
   and `completeJob` failures don't apply the same way. Should reconciliation be disabled for
   outbound connector mode, or is it harmless to leave it active? Likely harmless — the store
   would never be ahead in single-shot mode, so `reconciledFromStore` would always be `false`.

7. **InProcess variable size warning**: Should we add a runtime warning (log) when the in-process
   conversation exceeds a configurable size threshold (e.g., 100KB, 500KB)? This would alert
   operators to switch to Document storage before hitting practical limits.

8. **Document search API for reconciliation**: The Document store already writes `conversationId`
   as a custom property. If the platform adds a `POST /documents/search` endpoint with filter by
   `customProperties`, Document storage could reconcile out of the box. Should we file a platform
   feature request for this? This would make the default production store (Document) fully
   reconciliation-capable without custom code.

9. **cleanup() invocation**: When should `cleanup()` be called? For the Document store, TTL-based
   expiry handles end-of-life automatically (see "Document TTL strategy") — `cleanup()` is a no-op.
   For custom stores (RDBMS, Redis) without TTL, cleanup matters more. Options:
   - On AHSP completion (agent finished all iterations) — requires a hook from the AHSP lifecycle
   - On process instance completion — requires a process-level lifecycle hook (see Future
     Enhancement 1)
   - Via explicit user action (e.g., a service task that calls cleanup)
   For the initial implementation, `cleanup()` remains a no-op default with documentation on when
   custom stores should call it.

10. **Optimistic lock exception handling**: When a `storeMessages()` call fails due to optimistic
    concurrency (another worker wrote first), should the framework catch this and retry
    automatically, or let it propagate as a job failure? Recommendation: let it propagate. The
    stateless model with reconciliation handles this gracefully — the next job activation loads the
    winner's state and reconciles. Automatic retry within the same job iteration is unnecessary
    complexity, and the scenario is very rare in practice (see "Replace executeInSession" section).

11. ~~**ConversationSessionHandler removal**~~ **Resolved**: Remove in Phase 1 as part of
    Task 1.1 — clean break. This is a pre-1.0 API.

## Potential Future Platform Enhancements

These are **not part of this refactoring** — they are follow-up opportunities that would improve the
Document store's capabilities if the Camunda platform adds support.

1. **Process-scoped document lifecycle**: Documents with a `processInstanceKey` in their metadata
   could be automatically deleted when the process instance completes/terminates. This would
   eliminate the need for TTL-based garbage collection entirely — documents live exactly as long as
   their process. The platform already has `processInstanceKey` in `DocumentMetadata`; it would need
   to register a cleanup hook on process instance completion. This is the semantically correct model
   for conversation documents.

2. **Document TTL refresh endpoint**: A `PATCH /documents/{documentId}/metadata` or
   `POST /documents/{documentId}/refresh` endpoint that allows updating `expiresAt` without
   re-uploading content. This would let `onJobCompleted()` refresh the TTL on the current document
   instead of relying on the rolling create-new/delete-old pattern. Cheaper than re-uploading, but
   less impactful than process-scoped lifecycle.

3. **Document search/query API**: A `POST /documents/search` endpoint with filter by
   `customProperties` (e.g., `conversationId`). This would enable the Document store to support
   store-as-truth reconciliation — on load, query for the latest document for this conversation
   instead of only loading by the ID in `agentContext`. See Open Question 8.

## References

- Current `ConversationSession` API:
  `connectors/agentic-ai/src/main/java/.../memory/conversation/ConversationSession.java`
- Current `CamundaDocumentConversationSession`:
  `connectors/agentic-ai/src/main/java/.../memory/conversation/document/CamundaDocumentConversationSession.java`
- Current `InProcessConversationSession`:
  `connectors/agentic-ai/src/main/java/.../memory/conversation/inprocess/InProcessConversationSession.java`
- Current `BaseAgentRequestHandler`:
  `connectors/agentic-ai/src/main/java/.../aiagent/agent/BaseAgentRequestHandler.java`
- Current `JobWorkerAgentRequestHandler`:
  `connectors/agentic-ai/src/main/java/.../aiagent/agent/JobWorkerAgentRequestHandler.java`
- Architecture deep-dive:
  `connectors/agentic-ai/docs/ai-agent-architecture-deep-dive.md`
- Example custom ConversationStore (shows @Transactional migration case):
  `https://github.com/maff/camunda-agentic-ai-customizations`
- Zeebe SetVariables API (evaluated and rejected):
  `docs/apis-tools/zeebe-api/gateway-service.md` (lines 1128-1177)
- Camunda variable scoping concepts:
  `docs/components/concepts/variables.md`
