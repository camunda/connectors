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
- `window(int size)`: applies `MessageWindowFilter.apply(allMessages(), size)` and returns a
  read-only `ConversationSnapshot`.
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
while `assistantMessage == null`.

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
to the LLM: `(List<Message> messages, List<ToolDefinition> toolDefinitions)`.

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
