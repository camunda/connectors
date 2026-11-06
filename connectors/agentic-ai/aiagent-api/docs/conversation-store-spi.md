# Conversation Memory & Storage

## Architecture

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

## Storage Contract

Every `ConversationStore` implementation must follow the **write-ahead with pointer-based visibility** pattern to guarantee correctness across retries.

### The fundamental invariant

The `AgentContext` stored in Zeebe is the **sole source of truth** for which conversation data to read. The `ConversationContext` inside it acts as a pointer (storage cursor) to the data. The conversation store may write data ahead of Zeebe — but that data only becomes "committed" when Zeebe accepts the job completion containing the updated `AgentContext`.

If job completion fails (e.g., the job was superseded), Zeebe retries with the **old** `AgentContext`, which contains the **old** pointer. The newly written data is invisible to the retry and becomes an orphan.

### Rules for implementations

1. **`storeMessages` must always write to a new location.** Never mutate or overwrite the data that the current `ConversationContext` points to. Create a new version, snapshot, branch, or record — then return a new `ConversationContext` pointing to it. This ensures the old pointer always resolves to the old data.

2. **`loadMessages` must be guided by the pointer.** Load only the data that the `ConversationContext` (from `agentContext.conversation()`) references. Do not load "latest" or "most recent" — that would break retry safety.

3. **Orphaned writes are expected.** When job completion fails after `storeMessages`, the written data is never pointed to. Implementations should tolerate orphans and may clean them up via the `onJobCompleted` / `onJobCompletionFailed` completion callbacks (see below).

4. **`ConversationContext` must be serializable and self-contained.** It is persisted as part of the `agentContext` process variable in Zeebe. It must contain everything needed to locate the conversation data (e.g., a document reference, a version number, a branch pointer) — without relying on external state that could change between turns.

### How each built-in store satisfies this contract

| Store | Write target | Pointer | Orphan on failure |
|-------|-------------|---------|-------------------|
| **InProcess** | `agentContext` variable itself (messages in `ConversationContext`) | The variable *is* the data | No orphan — variable update and job completion fail together |
| **CamundaDocument** | New immutable document per turn | `document` reference in context | Orphaned document (tracked in `previousDocuments` for cleanup) |
| **AwsAgentCore** | New branch per turn (events forked from previous turn's last event) | `branchName` + `lastEventId` | Orphaned branch (invisible without pointer, no parent-chain traversal reaches it) |

## ConversationSession Lifecycle

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

## Completion Callbacks

After Zeebe accepts or rejects the job-completion command, the runtime notifies the connector
function via `JobCompletionListener` (from the connector SDK). Both `AgentTaskV1Function` and
`AgentSubProcessV1Function` implement this interface (via the shared `AgentConnectorFunction` mixin) and
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
