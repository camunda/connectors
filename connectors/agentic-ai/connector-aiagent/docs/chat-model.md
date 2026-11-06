# Chat model implementation

## Turn-based continuation loop

`BaseAgentRequestHandler.proceed` drives the SPI in a `do { … } while (continued)` loop: each
iteration calls `chatModel.execute(request)`, catches `ChatModelRejectedException` (below), ingests the
assistant message into the turn, and — only if the result was a `Continuation` — checks the
model-call limit and starts the next continuation round on a fresh turn before looping. A `Completed`
result ends the loop. LangChain4j always returns `Completed`, so the loop runs exactly once for it —
behavior-identical to the pre-SPI single call.

Agent-instance metrics are reported **once per continuation round**, not once per job: each round's
`applyTurnCompletion` call carries that round's own `AgentConversationTurn.metrics()` on its
`ASSISTANT` history item, and the engine derives its cumulative counters from the item itself
(`modelCalls += 1` per `ASSISTANT` item, `toolCalls` from that item's own tool-call count) rather than
from a request-level delta. See [`agent-instance.md`](agent-instance.md) and
[ADR 013](../../docs/adr/013-unified-agent-instance-turn-updates.md).

## LangChain4j Implementation

LangChain4j is the first (and currently only) implementation behind the SPI, reshaped into per-provider
factories:
- `AgenticAiLangChain4JFrameworkConfiguration` wires the converter beans;
  `AgenticAiLangChain4JChatModelConfiguration` wires one `ChatModelFactory` bean per provider. Provider
  selection is by `ChatModelFactory.supports(...)` via the SPI registry, so the configuration loads
  unconditionally rather than behind a global framework toggle.
- `LangChain4JChatModelFactory<T extends ProviderConfiguration>` (the v1 `ProviderConfiguration`) is the
  abstract base: `supports` matches a `ProviderConfiguration` whose `provider()` equals the factory's
  `providerType()`, and
  `create` builds the underlying LangChain4j model once via the abstract `createChatModel` and wraps it
  in a `LangChain4JChatModel`. Concrete subclasses: `AnthropicChatModelFactory`,
  `BedrockChatModelFactory`, `OpenAiChatModelFactory`, `OpenAiCompatibleChatModelFactory`,
  `AzureOpenAiChatModelFactory`, `GoogleVertexAiChatModelFactory` (the OpenAI-family factories share a
  common `LangChain4JOpenAiBaseChatModelFactory`).
- `LangChain4JChatModel` is the `ChatModel` implementation: converts the domain conversation and tool
  definitions to LangChain4j types, drives one `chat()` call while timing it (`System.nanoTime()`, fed
  into `AgentMetrics.executionTime`), and converts the response back. LangChain4j has no
  pause/continuation semantics, so it always returns `ChatResult.Completed`.
- **Does NOT use LangChain4j's built-in tool execution** — tool calls are returned as data, execution happens via BPMN.

## Converter Chain Architecture

The module maintains its own domain model (framework-agnostic `Message`, `ToolCall`, `Content` types) separate from LangChain4j types. The converter chain translates between them:

```
LangChain4JChatModel
  ├── ChatMessageConverter         # Message ↔ LangChain4j ChatMessage
  │     ├── ContentConverter       # Content → LangChain4j Content (for user messages)
  │     │     └── DocumentToContentConverter  # Camunda Document → LangChain4j Content
  │     └── ToolCallConverter      # ToolCall ↔ ToolExecutionRequest, ToolCallResult → ToolExecutionResultMessage
  ├── ToolSpecificationConverter   # ToolDefinition ↔ LangChain4j ToolSpecification
  │     └── JsonSchemaConverter    # Map<String,Object> ↔ LangChain4j JsonSchemaElement
  │           └── JsonSchemaElementModule  # Jackson module for JsonSchemaElement round-trip
  └── LangChain4j*ChatModelFactory # creates the LangChain4j ChatModel per provider config
```

**Key converters:**

- **`ChatMessageConverter`**: Top-level converter. `map(Message)` dispatches on sealed type (System/User/Assistant/ToolCallResult). `toAssistantMessage(ChatResponse)` converts back, attaching metadata (timestamp, finishReason, tokenUsage).
- **`ContentConverter`**: Converts `TextContent` → text, `DocumentContent` → delegates to `DocumentToContentConverter`, `ObjectContent` → JSON string. Uses the injected `@ConnectorsObjectMapper`, which `ConnectorsAutoConfiguration` registers with `JacksonModuleDocumentSerializer` for nested document serialization.
- **`DocumentToContentConverter`**: Dispatches on MIME type: `text/*` → `TextContent`; `application/pdf` → `PdfFileContent`; images → `ImageContent`; throws `DocumentConversionException` for unsupported types.
- **`ToolSpecificationConverter`**: Uses `JsonSchemaConverter` to convert between `Map<String,Object>` (domain) and `JsonObjectSchema` (LangChain4j). Throws `ParseSchemaException` if schema is not an object.
- **`JsonSchemaElementModule`**: Custom Jackson module needed because LangChain4j doesn't expose standard polymorphic annotations on `JsonSchemaElement`. Serializer/deserializer handle all concrete types (`JsonObjectSchema`, `JsonEnumSchema`, `JsonStringSchema`, `JsonArraySchema`, `JsonAnyOfSchema`, `JsonReferenceSchema`, etc.).

All converter beans are `@ConditionalOnMissingBean`, so an application can override any of them by declaring its own bean of the same type.
