# Chat model provider SPI

## The `ChatModel` provider SPI (ADR 009)

`ChatModel` (`aiagent/api/chatmodel/ChatModel.java`) is the provider SPI: an `AutoCloseable` exposing a
single `ChatResult execute(ChatRequest request)`. `ChatRequest` bundles the `AgentExecutionContext` and
the windowed `ConversationSnapshot` (`BaseAgentRequestHandler` builds the snapshot via
`conversation.window(configuration.contextWindowSize())`). `ChatResult` is a sealed `Completed |
Continuation`, each carrying the produced `AssistantMessage` and `AgentMetrics` for that one round-trip:
- **`Completed`** — the round-trip finished the agent's turn; no further model call is needed for it.
- **`Continuation`** — the provider paused mid-turn (e.g. Anthropic's `pause_turn` stop reason) and
  must be called again, without new user/tool input, to continue producing the same logical turn.

A `ChatModel` instance serves a single agent request across all of its continuation rounds and is
resolved and closed per request (try-with-resources) via `ChatModelRegistry`, so a provider may hold
per-invocation resources without a shared singleton lifecycle. A `ChatModelFactory`
(`aiagent/api/chatmodel/ChatModelFactory.java`) exposes `supports(ChatModelConfiguration)` and
`create(ChatModelConfiguration)`.

`ChatModelConfiguration` is a neutral descriptor a factory inspects to decide whether it can serve a
request. It exposes just `provider()` (the provider discriminator) and `model()` (the model
identifier) — enough for routing and agent-instance reporting — and is the type carried through
`AgentConfiguration` (`chatModel()`) and dispatched on by the registry, so a provider can supply its
own configuration through the SPI rather than being confined to the module's built-in provider union.
The sealed `ProviderConfiguration` (`AnthropicProviderConfiguration`, `BedrockProviderConfiguration`,
etc.) is the concrete implementation contributed by this module. Today the v1 request supplies
configurations through this sealed union only; request-side binding for a custom/native
`ChatModelConfiguration` is delivered incrementally by the v2 request types (`ProviderConfiguration`
in `request/v2`), which is the extension point for both built-in native providers and
user-supplied factories via `CustomProviderConfiguration` (see [`extension-points.md`](../../docs/architecture/extension-points.md#add-an-llm-provider)).
`ChatModelRegistryImpl` asks every registered `ChatModelFactory` whether it `supports` the
configuration and routes to the single match; a configuration matched by zero factories throws
`IllegalArgumentException`, and one matched by more than one throws `IllegalStateException` — fail
loud rather than resolving implicitly.

## Normalized stop reasons & ChatModelRejectedException

`StopReason` is a sealed, provider-neutral finish reason on `AssistantMessage`. A provider that
recognizes an unrecoverable rejection condition throws `ChatModelRejectedException` (a sealed type,
one concrete subtype per condition) directly, at the point of detection, rather than returning data
for a caller to inspect. Each subtype carries an optional `PartialResult` — the assistant message
and metrics already built for the turn, when any exist; a provider that rejects the request before
producing any content has none. `BaseAgentRequestHandler` catches the sealed type around each
`chatModel.execute(...)` call and maps it to the matching coded `ConnectorException` (see
[Error Codes](../../connector-aiagent/docs/error-codes.md)) before ingesting anything, so a rejected turn is never persisted to
conversation memory.
