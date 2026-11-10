# Conversation memory

## AgentConversation, ConversationSnapshot & MessageWindowFilter

The in-process working representation of a conversation is the immutable `AgentConversation`
aggregate (see [ADR 007](../../docs/adr/007-agent-conversation-turn-aggregate.md)). It is built once per
invocation and transformed through copy-on-write methods.

`AgentConversation` (package `...aiagent.conversation`):
- Holds an optional `SystemMessage` (`null` when the composed system prompt is blank), a list of
  previous `AgentConversationTurn`s, the pending current turn, the durable base `AgentContext`, and the
  static `AgentConfiguration`.
- `rehydrate(history, systemMessage, inputMessages, agentContext, configuration)`: builds from the
  reconstructed history plus the composed system message and the current-turn input as a *pending*
  turn (`assistantMessage == null`).
- `ingest(assistantMessage, tokenUsage)`: completes the pending turn, recording per-turn metrics
  (1 model call, the token usage, and the tool-call count).
- `withStoredConversation(ref)`: updates the base context's persistence cursor after storing.
- `window(int size)`: applies `MessageWindowFilter.apply(allMessages(), size)` and returns a read-only
  `ConversationSnapshot` whose tool definitions are those of `AgentConfiguration.toolDefinitions()`. The request handler
  hands the snapshot to `DocumentExpander.expand(snapshot, documentHandling, documentFactory)` before the model is
  called. The document factory is the one of the job (`AgentExecutionContext.documentFactory()`): it restores the
  documents from the document store of the physical tenant of the job.
- `persistedContext()`: the base `AgentContext` without the metrics and iteration bookkeeping that `toAgentContext()`
  adds; it is what a runtime tool reads. `nextRuntimeToolRound(inputMessages, registerDocuments)` opens the next round of
  the job with the tool call results of the local tool calls, and the messages that follow them, as its input. It applies
  the `UnaryOperator<AgentContext>` `registerDocuments` to the persisted context and continues with the context that it
  returns: the persisted context with the documents of the input registered (`TurnInput.applyTo`).
  `documentIdsWithContent(addedMessages)` holds the ids of the documents whose content the model reads in the next model
  call, from the blocks with the delivery `content` in the windowed messages, with the messages added before that call
  counted (the tool call result message of a local round). `PreviousConversation` computes it
  (`documentIdsWithContent(contextWindowSize, addedMessages)`) for the conversation before the invocation, which
  `AgentConversation` delegates to; the window keeps a place for a system message whether or not the conversation has one,
  because the system message of the next call is composed in the invocation.
- `toAgentContext()`: reduces back to the serialized `AgentContext`, incrementing the durable
  `AgentContext.metrics` by the current turn's delta and, once the current turn has been `ingest`ed,
  stamping `AgentMetadata.lastIterationKey` with its `iterationKey` (when metadata is present).
- `jobMetrics()`: the metrics accrued in this invocation — every turn produced by this job (the
  current turn plus any continuation rounds rolled into `previousTurns`), excluding the durable base
  counter. Reconstructed prior-job turns carry `AgentMetrics.empty()` and contribute zero. Used for the
  cumulative `totalMetrics()` counter below; each turn's own metrics (not this sum) are what gets
  reported to the agent instance per round (see [`agent-instance.md`](agent-instance.md)).
- `totalMetrics()`: returns the durable `AgentContext.metrics()` plus `jobMetrics()` — **not** a sum
  over the reconstructed turns, which always carry `AgentMetrics.empty()`. The model-call limit check
  (`BaseAgentRequestHandler.throwIfLimitsReached`) relies on this cumulative counter.

`AgentConversationTurn` (record, `...aiagent.conversation`) is one LLM call:
`(int iterationKey, List<Message> inputMessages, @Nullable AssistantMessage assistantMessage,
AgentMetrics metrics)`. `iterationKey` is 1-based across the agent lifetime; the turn is pending
while `assistantMessage == null`. `processToolCalls()` and `localToolCalls()` return the tool calls of the assistant
message that are process tool calls and that are local tool calls (`ToolCall.isLocal()`, see
[`agentic-loop.md`](../../docs/architecture/agentic-loop.md#what-happens-when-tools-complete)), in call order.

The next turn's `iterationKey` is determined by `AgentConversation.rehydrate()`:
`AgentMetadata.lastIterationKey` (persisted by `toAgentContext()`, see above) is authoritative when
present. The reconstructed turn count is used as a fallback when it's absent (conversations created
before this field was introduced) and, when a stored key is present, to cross-validate against it:
a mismatch is logged as a warning (reconstruction drift), not thrown.

`TurnReconstructor.reconstruct(messages)` (`...aiagent.conversation`) rebuilds the turn list and the
optional system message from the persisted flat message list: the leading `SystemMessage` (if any)
is split off, and the remaining body is grouped by `AssistantMessage` boundaries. All reconstructed
turns carry `AgentMetrics.empty()` — per-invocation metrics are computed live from the current
turn, not read from history. This provides backward compatibility with existing conversations
without a data migration, and is now also the cross-validation source for `iterationKey` drift
detection described above.

`ConversationSnapshot` (record, `...aiagent.api.conversation`) is the transient, windowed, read-only view sent
to the LLM: `(List<Message> messages, List<ToolDefinition> toolDefinitions)`. For an agent instance with the
`documents` feature `DocumentExpander.expand` expands the document blocks
(`AgentDocumentContent`, a typed content block with the delivery `reference` or `content`) with the agent documents
(see [Document Extraction from Tool Call Results](../../docs/architecture/extension-points.md#document-extraction-from-tool-call-results)).
The stored messages hold the document block only. In a user message a block becomes its handle text as a
`TextContent` and, for the delivery `content`, the `DocumentContent` restored from the agent document after it. In a tool
result a top-level block becomes the handle text as a `TextContent`, and a document nested in an object becomes its handle
object inside the object, so the model providers have no document specific code for tool results. A block whose document
the agent does not know stays its handle text (the id only), and the expansion never fails the request. The result depends only on the
messages and the agent documents, so the request bytes are stable across requests (prompt caching). For an agent instance
that does not track its documents (`Untracked`) the expander returns the snapshot as it is: the messages are left as they
are and a `Document` in a tool result is serialized as its Camunda reference JSON. No provider receives a document inside a
tool result, so the content of a document reaches the model only in a user message. The content of a document stays in every request until
the message that holds it leaves the window; there is no removal.

The window never announces a document whose message it hides. The agent documents still resolve the handle of such a document,
so a handle that is still in the model's context, in its own text for example, can be passed to a tool or loaded again within
the guard.

`MessageWindowFilter.apply(List<Message>, int maxMessages)` (`...aiagent.memory.runtime`) is a pure
static window filter:
  - Keeps at most `maxMessages` (default: 20, from `AgentConfiguration.contextWindowSize()`) messages
  - Tool-call-document user messages are excluded from the count
  - The system message is never evicted
  - When evicting an `AssistantMessage` with tool calls, also evicts the follow-up
    `ToolCallResultMessage` entries (some providers error on orphaned tool results)
  - Also evicts follow-up tool-call-document user messages attached to evicted results

## ConversationStore Implementations

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
- Documents are created, read and deleted on the cluster serving the job's physical tenant. While a single tenant is served, the injected `documentFactory`/`documentStore` beans are used as they are, so overriding them keeps working

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
2. Annotating the custom `ConversationContext` with `@JsonTypeName("my-type")` and listing it in a `ConversationContextSubtypes` implementation registered in `META-INF/services/io.camunda.connector.agenticai.aiagent.model.conversation.ConversationContextSubtypes`. Registering the subtype on the connector runtime's `ObjectMapper` beans via `registerSubtypes()` (e.g., from a Spring `BeanPostProcessor`) works as well. A broken provider or subtype (for example one without `@JsonTypeName`) is logged at ERROR level and skipped, and deserializing an unregistered type id fails with an `InvalidTypeIdException` listing the known type ids
3. Selecting "Custom Implementation" as memory storage type in the element template and specifying the implementation type string
4. Registering the store as a Spring component

See the [`CustomMemoryStorageConfiguration`](../../connector-aiagent/src/main/java/io/camunda/connector/agenticai/aiagent/request/MemoryStorageTemplateConfiguration.java) type for configuration, and [camunda-agentic-ai-customizations](https://github.com/maff/camunda-agentic-ai-customizations) for a working example with a JPA-backed store.

## How each built-in store satisfies this contract

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

**Critical insight**: The conversation is stored **before** job completion. This is safe because all stores follow the write-ahead with pointer-based visibility contract — see [Storage Contract](../../aiagent-api/docs/conversation-store-spi.md#storage-contract) above.

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

**Best-effort guarantee**: Callbacks may never fire (e.g., runtime crash before command dispatch). They are optimizations for cleanup (e.g., orphan removal), not correctness mechanisms. The [Storage Contract](../../aiagent-api/docs/conversation-store-spi.md#storage-contract) ensures correctness without callbacks.

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
