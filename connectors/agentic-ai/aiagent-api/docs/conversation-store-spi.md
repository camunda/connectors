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

3. **Orphaned writes are expected.** When job completion fails after `storeMessages`, the written data is never pointed to. Implementations should tolerate orphans and may clean them up via the `onJobCompleted` / `onJobCompletionFailed` completion callbacks (see [Completion Callbacks](../../connector-aiagent/docs/conversation-memory.md#completion-callbacks)).

4. **`ConversationContext` must be serializable and self-contained.** It is persisted as part of the `agentContext` process variable in Zeebe. It must contain everything needed to locate the conversation data (e.g., a document reference, a version number, a branch pointer) — without relying on external state that could change between turns.

For how each built-in store satisfies this contract, the `ConversationSession` lifecycle and the completion
callbacks, see [`conversation-memory.md`](../../connector-aiagent/docs/conversation-memory.md#how-each-built-in-store-satisfies-this-contract).
