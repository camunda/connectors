# Agent Instance Integration

The agent reports its lifecycle to the engine's **agent instance** API via `AgentInstanceClient`
(`CamundaAgentInstanceClient`). Every call goes to the cluster serving the job's physical tenant,
resolved per invocation from the `AgentExecutionContext`'s `JobContext` through
`PhysicalTenantClientSelector`. Update calls silently skip when the `agentInstanceKey` is `null`
(agents that pre-date the feature); create has no such key to check, since it produces one. All calls
retry transient failures via `CamundaApiRetry`. A `404` from `create` is treated as **permanent** (not
retried): its write endpoint is `x-eventually-consistent: false` and validated against primary
processing state, with Zeebe's key-based partition routing guaranteeing the create is visible to the
same partition before the key is ever returned to the caller. A `404` from any `update()` call
(`applyTurnStart`/`applyTurnCompletion`/`applyToolCallResults`/`applyToolDiscoveryStart` — each sending
`jobKey`/`jobLeaseToken` on a `history(...)` batch, empty for `applyToolDiscoveryStart`) is instead
treated as job supersession — see below.

## The two-call-per-turn design (ADR 013)

Per turn, the agent issues exactly two batched `update()` calls, each carrying `jobKey` and
`jobLeaseToken` (both mandatory steps on the agent-instance command builder) alongside a `history(...)`
list — never a separate create-history command:

- **`applyTurnStart`** (`BaseAgentRequestHandler.proceed`, before the LLM call): moves the agent
  instance to `THINKING` and appends the current turn's input-message history items (see below),
  prepending a `CONFIGURATION` item when the model, provider, system prompt, tool list, or model-call
  limit changed since the previous turn.
- **`applyTurnCompletion`** (`BaseAgentRequestHandler.driveContinuationLoop`, once per round including
  intermediate `ChatResult.Continuation` rounds): appends one `ASSISTANT` item carrying that round's
  assistant text, `toolCalls`, and metrics (input/output tokens + `durationMs`, measured by the
  `ChatModel` implementation itself and carried on `AgentConversationTurn.metrics()`), and sets the
  status — `THINKING` again on a continuation round, the terminal status (`IDLE`/`TOOL_CALLING`,
  based on that round's own tool-call count) on the final round. Exactly one status per call; metrics
  are per-round, never summed across rounds at the handler level.

Once a batch is attached to an `update()` command, the engine only allows `status` at the request
level — `model`/`provider`/`systemPrompt`/`limits`/`tools` can only change through a `CONFIGURATION`
history item. Of those, `systemPrompt`, `tools`, `limits`, and `provider` are re-pushed on every
`CONFIGURATION` item; `model` stays fixed at `create` time. The reported `provider` is a more
specific identifier than the plain `provider()` constant — it also covers backend and, for OpenAI,
API family (e.g. `openai/completions/custom`, `anthropic/aws-bedrock-mantle`) — so a configuration
change limited to that no longer goes unnoticed.

There is no more deferred, completion-listener-driven agent instance update: `applyTurnStart` and
`applyTurnCompletion` both fire synchronously inline, so a job that never completes (crash, timeout)
simply leaves the agent instance at its last-written status — the next activation overwrites it, the
engine never rolls it back. The `TOOL_DISCOVERY` status patch (`dispatchToolDiscovery`) is sent the
same way, synchronously, via `applyToolDiscoveryStart` (an empty-history batch through the same
batched `update()` mechanism).

## Conversation history items

- A `UserMessage` → one `USER` item (covers the user prompt, event messages, and virtual
  document messages). A message of an agent
  instance without the `documents` feature is reported as stored; the documents of one that tracks them are reported as
  described below.
- A `ToolCallResultMessage` → **one `TOOL_RESULT` item per result**, each with that result's content
  block(s) and a single-entry `toolCalls` array `{toolCallId: result.id(), toolName: result.name(),
  elementId, arguments}` correlating it back to the originating tool call. The `arguments` are the
  originating request arguments, looked up by tool-call id from the previous turn's assistant tool
  calls (`AgentConversation.lastTurn()` — the previous turn while the current one is still pending —
  → `AgentConversationTurn.toolCallsById()`), so each `TOOL_RESULT` item is self-contained for the
  paginated history API and need not be joined back to the `ASSISTANT` item. Event results
  (`id == null`) have no originating call and carry `{}`; a result with a non-null id and no matching
  tool call is an invariant violation and fails the turn.
- **Documents in the history** (an instance that tracks its documents). The history shows what the model reads. Every
  document is its handle, as an `OBJECT` block of its own that holds exactly the handle (no marker key besides the handle's
  own `type` = `camunda.agenticai.document`) and is never joined with other content, the same object `DocumentExpander`
  renders for the model, so a consumer can render it as a card. Where a document enters the conversation (the user prompt, an
  event, a tool result, also a tool result that is only a document) the handle is followed by a `DOCUMENT` block (the
  chip). An object with documents in it is reported as the object with the handle objects nested, followed by the chips.
  When a document reported as a `DOCUMENT` block has no file name, the reported metadata carries the agent document id
  (`doc_…`) as the file name, so the chip shows the id the handle shows; the document and its stored reference are not changed. A
  text is reported as it is, also when it holds a handle.
  `AgentInstanceClient.applyTurnStart` and `applyToolCallResults` take the `HistoryDocuments` of the job (the document
  handling of the agent instance and the document factory of the job): the document handling decides what the history
  shows, and the agent documents of a `Tracked` instance are what the handles are expanded with. An
  `Untracked` agent instance reports texts as they are.
- An `ASSISTANT` item's content blocks preserve the order the model produced them in because
  `AgentInstanceHistoryMapper.assistantContent` maps the source list directly. An assistant message
  with neither text/object/etc.
  content nor tool calls fails the turn (`IllegalArgumentException`) rather than falling back to a
  placeholder block.

**Local tool calls.** Calls of runtime tools (see [ADR 016](../../docs/adr/016-runtime-tools.md)) and rejected calls appear
in history like any other tool call. The `ASSISTANT` item lists every call the model issued and its metrics count every
one of them in `toolCalls`, whatever its kind. Their `TOOL_RESULT` items are written with the `applyTurnStart` that
carries them: the one of the round of the local tool call results of the same job when every call of the message is a local tool
call, or, in a mixed batch, the one of the job that composes the next turn from the process results and the local tool
call results. Both rounds of a
job are reported. They use the tool-call id as `historyItemId`, no `elementId` (there is no BPMN element, also not for a
rejected call), the completion time of the execution as `completedAt` and the originating arguments, like every other
result; an error result is plain text content, the history has no result status. A mixed batch completes its job with
`TOOL_CALLING`, a batch of local tool calls only with `THINKING` followed by the next round. The engine deduplicates
items by `historyItemId`, so results that more than one job writes appear once.

**What the history covers.** The history records what the model sent and received, in a representation suited to the
history; process data stays in the tool element variables. Each message of a model request is covered by a history item:
the system prompt by the `CONFIGURATION` item, user messages by `USER` items, assistant messages by `ASSISTANT` items and
tool results by `TOOL_RESULT` items. The representations may differ: a document with content is a `DOCUMENT` block next to
its handle in the history and the handle plus the native block in the request, so the history is a superset of what the model
receives. The history API has no tool kind and no result status, so a runtime tool is recognizable only by its missing
element id and an error result is plain text; tool input schemas are not reported.

**`historyItemId` (`AgentInstanceHistoryItemIds`).** Every history item carries a `historyItemId` so a
retried batch write, and the same result written by two jobs, dedup against the previous write (the engine keeps the first): `USER`/`ASSISTANT` items key off the domain
`Message.id()` (ADR 012); `TOOL_RESULT` items key off the originating tool-call id (so a streamed
early report and the later batch write for the same result dedup for free), falling back to a
deterministic hash of `elementId` + `completedAt` for id-less event results; `CONFIGURATION` items key
off `AgentConfiguration.fingerprint()` itself, so an unchanged configuration resent verbatim dedups
too. The fingerprint is a SHA-256 over a JSON serialization of the model, provider, system prompt,
model-call limit and tool definitions. The tool definitions are those the agent offers: the process tools and the
enabled runtime tools. The entries of every map are sorted by key, because the iteration order of a
tool's input schema is unspecified after a record builder copied it. The order of the parameters, of the tools and of
list values is significant, so the parameter order enters the hash as a list. The serialization is written and read
once before hashing, so a number hashes the same for a fresh tool definition and for one loaded from the process
variable. The fingerprint has its own mapper and does not inherit the settings of the connectors mapper; it writes a date or a
duration as ISO text, never as a timestamp.

**`producedAt` per item (ADR 008).** Every history item carries a required, non-null `producedAt`,
resolved before it reaches `AgentInstanceHistoryMapper`/`CamundaAgentInstanceClient` — neither
computes a timestamp itself. A `TOOL_RESULT` item uses `ToolCallResult.completedAt()`: the AHSP
`outputElement`'s engine timestamp (v11+ templates) if present, else a stateless `now()`
(`ToolCallResultCompletedAtResolver`, resolved at ingestion in `AgentInitializerImpl`; see ADR 008
for what the `now()` fallback does and doesn't cover). `USER`, `ASSISTANT`, and `CONFIGURATION` items
use a timestamp `BaseAgentRequestHandler.proceed` captures and passes down.

Each `toolCalls` entry carries the BPMN **`elementId`** alongside the (LLM-visible, possibly
namespaced) `toolName`. For tool results it is resolved once on the model in
`AgentConversationTurnInputComposerImpl` (`ToolCallResult.elementId`) and read directly; for assistant
tool calls the mapper resolves it from the namespaced name via `GatewayToolHandlerRegistry.resolveElementId`.
For ad-hoc tools the element id equals the tool name; for gateway tools (MCP/A2A) it is the BPMN
gateway element id parsed from the namespaced name; for runtime tools (calls, results and the tool entry
of the `CONFIGURATION` item) and for rejected calls it is `null`, because the engine field is nullable and neither has a
BPMN element. For calls and results the mapper reads `ToolCall.isLocal()` of the originating tool call; for
the tool list it reads the `kind` of the `ToolDefinition`, which is `RUNTIME` for the runtime tools the agent offers
(agent instances with the `runtimeTools` feature only).
The tool list of a `CONFIGURATION` item holds the process tools plus the enabled runtime tools
(`AgentConfiguration.toolDefinitions()`); the configuration fingerprint covers them.

Content blocks map by type: `TextContent` → text, `ObjectContent` → object (or JSON text),
`AgentDocumentContent` → the text of its handle, and
`DocumentContent` → a Camunda document reference block. External document references currently fall
back to an object/text block (see follow-ups). `ReasoningContent` maps to a tagged object containing
`camunda.agenticai.content.type`, optional `text`, and `payload`; `provider` and `metadata` are not
persisted. Replay-critical provider values must therefore remain durably preserved in `payload`.
For Gemini, the raw payload retains `thoughtSignature`, while the response converter also places a
base64 copy in transient `Content.metadata()` for the request converter to use during in-memory
replay. `AgentInstanceHistoryMapper` does not persist that metadata.
`ProviderContent` maps to an object wrapping its content discriminator, provider, and raw payload
(`AgentInstanceHistoryMapper`; neither content type is produced by the LangChain4j path yet). A
`ProviderContent` with non-blank `text` instead maps to a text content carrying only that `text`;
its lossless payload stays in conversation memory.

**Supersession as a non-retryable failure (ADR 013).** A `404` from a batched `update()` means the job
activation that issued it has been superseded by a later one (the engine rejects it because the job
is no longer active). `CamundaAgentInstanceClient` maps that specifically to a
`ConnectorRetryException` with `.retries(0)` and error code `AGENT_INSTANCE_SUPERSEDED`, so no retry
is provoked at any level — this applies uniformly to `applyTurnStart`/`applyTurnCompletion`/
`applyToolCallResults`/`applyToolDiscoveryStart`, the only writers of a batched `update()`.
`applyTurnStart`'s batch is the first write of the turn: an update for a job that is no longer active
is rejected there, before spending any model tokens. An update with a stale lease on a job that is
still active is accepted, and the engine skips its status change. Only `create` is rejected on a stale
lease.
See [`agentic-loop.md`](../../docs/architecture/agentic-loop.md#concurrency-challenges--race-conditions) for the job-lease mechanism.

**Streamed early (ADR 011).** `applyToolCallResults` fires from `BaseAgentRequestHandler.converse`'s
`Deferred` branch — before the turn's tool-call batch is complete — reporting whichever results have
arrived so far as a batched, status-less `update()`, one `TOOL_RESULT` item per result, same mapping
as above. Its iteration key (`previousTurn.iterationKey() + 1`) is best-effort, not authoritative — the
batch-complete write's key is. `applyTurnStart` writes these items again once the batch
completes; the engine deduplicates them by `historyItemId` (camunda/camunda#58792), so no duplicate row remains. See
[`agentic-loop.md`](../../docs/architecture/agentic-loop.md#what-happens-when-tools-complete) and
[ADR 011](../../docs/adr/011-stream-tool-call-results-to-agent-instance-history.md).
