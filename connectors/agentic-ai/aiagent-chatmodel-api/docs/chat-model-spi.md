# Chat model provider SPI

## The `ChatModel` provider SPI (ADR 009)

`ChatModel` (`aiagent/api/chatmodel/ChatModel.java`) is the provider SPI: an `AutoCloseable` exposing a
single `ChatResult execute(ChatRequest request)`. `ChatRequest` bundles the `AgentExecutionContext` and
the `ChatConversation` (`BaseAgentRequestHandler` builds it via
`documentExpander.expand(conversation.window(agentConfiguration.contextWindowSize()), documentHandling, documentFactory)`
with the document handling of the instance and the document factory of the job; see
[Request messages](#request-messages)). `ChatResult` is a sealed `Completed |
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

## Request messages

`ChatConversation` holds the messages and the tool definitions of one model call
([ADR 019](../../docs/adr/019-typed-chat-request-messages.md)). The messages are the sealed `ChatRequestMessage`, one
record per role:

| Message                     | Content                                                                                                    |
| --------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `ChatSystemMessage`         | `TextContent` blocks; `text()` joins them with a line break                                                |
| `ChatUserMessage`           | `PortableContent`: `TextContent`, `DocumentContent`, `ObjectContent`                                       |
| `ChatAssistantMessage`      | `ChatContent` (`PortableContent`, `ReasoningContent`, `ProviderContent`), tool calls, message id, metadata |
| `ChatToolCallResultMessage` | `ChatToolCallResult` per call: the call id, the tool name and `PortableContent`                            |

`ChatContent` and `PortableContent` are sealed sub-interfaces of `Content`, in the content package of `aiagent-model`.
`AgentDocumentContent` is not a `ChatContent`, so a provider never receives it, and reasoning and provider-native content
reach a provider only in an assistant message. A provider converter switches over these types with no arm for blocks it
cannot receive. `ChatConversation.leadingSystemMessage()` returns the first message when it is a system message.

`ChatConversation.of(messages, toolDefinitions)` builds the conversation from persisted messages. It fails with an
`IllegalArgumentException` for a block that a message of its role cannot hold: an `AgentDocumentContent` in any message,
a `ReasoningContent` or a `ProviderContent` outside an assistant message, or a block other than `TextContent` in a system
message. `DocumentExpander` calls it after it expands the document blocks, for every agent instance. A test of a custom
`ChatModel` can build its request with it.

The LangChain4j chat model maps the conversation back to the persisted message types, which its converters read.

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
