# LangChain4j chat models

The LangChain4j integration is a generic bridge for custom providers, not a provider stack. It ships no
factory for a vendor. The native providers serve every v1 and v2 provider configuration, see
[`native-providers.md`](native-providers.md). A custom `ChatModelFactory` brings its own configuration type
and its own LangChain4j model, and implements the chat model SPI of [`chat-model.md`](chat-model.md) through
this bridge. [ADR 019](../../docs/adr/019-langchain4j-extension-bridge.md) decides to keep the bridge and to remove the
LangChain4j provider stack ([ADR 014](../../docs/adr/014-route-v1-requests-through-native-providers.md)). The page
[custom LangChain4j provider](custom-langchain4j-provider.md) shows how to build on it.

## LangChain4j implementation

- `AgenticAiLangChain4JAutoConfiguration` wires the converter beans and the proxy support. It is a self-contained
  auto-configuration that depends only on `agentic-ai-common` and is off by default. The property
  `camunda.connector.agenticai.aiagent.langchain4j.enabled` switches it on. The registry picks a custom
  `ChatModelFactory` bean through `ChatModelFactory#supports`.
- `LangChain4JChatModel` is the `ChatModel` implementation and the entry point of the bridge. A custom factory builds
  the underlying LangChain4j model and wraps it in a `LangChain4JChatModel`. The constructor without a codec and a
  mapper uses no provider data and the base token usage. The other constructor takes a `ToolCallMetadataCodec` and a
  token usage mapper. The model converts the domain conversation and the tool definitions to LangChain4j types, runs
  one `chat()` call while timing it, and converts the response
  back. It maps a content filter finish reason to `ContentFilteredException`. LangChain4j has no
  pause semantics, so it always returns `ChatResult.Completed`.
- A `ToolCallMetadataCodec` carries provider data that the model must get back verbatim, for example thought
  signatures, through `ToolCall#metadata()`. `ToolCallMetadataCodec#none` carries nothing.
  `ChatMessageConverterImpl` persists the written data under the provider ID of the codec and gives the reader only
  that entry.
- `ChatModelHttpProxySupport` creates a JDK `HttpClient` builder with the proxy settings of the connector, for
  LangChain4j models that take an `HttpClientBuilder`.
- It does not use the built-in tool execution of LangChain4j. Tool calls are returned as data and the
  process executes them.

## Converter chain architecture

The module has its own domain model (`Message`, `ToolCall` and `Content` types) that does not depend on
LangChain4j. The converter chain translates between the two:

```text
LangChain4JChatModel
  ├── ChatMessageConverter         # Message <-> LangChain4j ChatMessage
  │     ├── ContentConverter       # Content -> LangChain4j Content (for user messages)
  │     │     └── DocumentToContentConverter  # Camunda Document -> LangChain4j Content
  │     └── ToolCallConverter      # ToolCall <-> ToolExecutionRequest, ToolCallResultContent -> ToolExecutionResultMessage
  └── ToolSpecificationConverter   # ToolDefinition <-> LangChain4j ToolSpecification
        └── JsonSchemaConverter    # Map<String,Object> <-> LangChain4j JsonSchemaElement
              └── JsonSchemaElementModule  # Jackson module for JsonSchemaElement round-trip
```

`LangChain4JChatModel` also uses `JsonSchemaConverter` directly, to map the JSON schema of the response
format.

Key converters:

- `ChatMessageConverter`: the top-level converter. `ChatMessageConverter#map` dispatches on the sealed
  message type (system, user, assistant, tool call result).
  `ChatMessageConverter#toAssistantMessage` converts a LangChain4j chat response back and stores the response
  metadata, model id, message id and stop reason.
- `ContentConverter`: converts `TextContent` to text, `DocumentContent` through
  `DocumentToContentConverter`, and `ObjectContent` to a JSON string. It rejects `ReasoningContent` and
  `ProviderContent` with an UnsupportedOperationException.
- `DocumentToContentConverter`: dispatches on the MIME type. Text types (`text/*`, XML, JSON, YAML) become
  `TextContent`, `application/pdf` becomes LangChain4j PDF content, and JPEG, PNG, GIF and WebP images become
  LangChain4j image content. Other types throw `DocumentConversionException`.
- `ToolSpecificationConverter`: converts the input schema of a tool with `JsonSchemaConverter`. It throws
  `ToolSpecificationConverterImpl.ParseSchemaException` when the schema cannot be parsed or is not an
  object.
- `JsonSchemaElementModule`: a Jackson module that serializes and deserializes the concrete
  LangChain4j JSON schema element types, because LangChain4j does not provide Jackson annotations for them.

The converter beans are `@ConditionalOnMissingBean`. An application can override a converter by declaring
its own bean of the same type.
