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
  document-reference messages).
- A `ToolCallResultMessage` → **one `TOOL_RESULT` item per result**, each with that result's content
  block(s) and a single-entry `toolCalls` array `{toolCallId: result.id(), toolName: result.name(),
  elementId, arguments}` correlating it back to the originating tool call. The `arguments` are the
  originating request arguments, looked up by tool-call id from the previous turn's assistant tool
  calls (`AgentConversation.lastTurn()` — the previous turn while the current one is still pending —
  → `AgentConversationTurn.toolCallsById()`), so each `TOOL_RESULT` item is self-contained for the
  paginated history API and need not be joined back to the `ASSISTANT` item. Event results
  (`id == null`) have no originating call and carry `{}`; a result with a non-null id and no matching
  tool call is an invariant violation and fails the turn.
- An `ASSISTANT` item's content blocks preserve the order the model produced them in because
  `AgentInstanceHistoryMapper.assistantContent` maps the source list directly. An assistant message
  with neither text/object/etc.
  content nor tool calls fails the turn (`IllegalArgumentException`) rather than falling back to a
  placeholder block.

**`historyItemId` (`AgentInstanceHistoryItemIds`).** Every history item carries a `historyItemId` so a
retried batch write dedups against the previous attempt: `USER`/`ASSISTANT` items key off the domain
`Message.id()` (ADR 012); `TOOL_RESULT` items key off the originating tool-call id (so a streamed
early report and the later batch write for the same result dedup for free), falling back to a
deterministic hash of `elementId` + `completedAt` for id-less event results; `CONFIGURATION` items key
off `AgentConfiguration.fingerprint()` itself, so an unchanged configuration resent verbatim dedups
too. The fingerprint is a SHA-256 over a JSON serialization of the model, provider, system prompt,
model-call limit and tool definitions. The entries of every map are sorted by key, because the iteration order of a
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
gateway element id parsed from the namespaced name.

Content blocks map by type: `TextContent` → text, `ObjectContent` → object (or JSON text), and
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
batch-complete write's key is. These items duplicate what `applyTurnStart` writes again once the batch
completes; the duplicate row persists until `historyItemId`-based dedup lands (camunda/camunda#58792). See
[`agentic-loop.md`](../../docs/architecture/agentic-loop.md#what-happens-when-tools-complete) and
[ADR 011](../../docs/adr/011-stream-tool-call-results-to-agent-instance-history.md).
