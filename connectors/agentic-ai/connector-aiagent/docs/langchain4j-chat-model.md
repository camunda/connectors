# LangChain4j chat models

The LangChain4j chat models implement the chat model SPI of [`chat-model.md`](chat-model.md) for the v1
provider configurations. They serve a request only when
`camunda.connector.agenticai.aiagent.rewrite-v1-provider-config-to-v2` is `false`. By default, v1 requests
are rewritten to v2 and run on the native providers, see [`native-providers.md`](native-providers.md).
[ADR 014](../../docs/adr/014-route-v1-requests-through-native-providers.md) decides to remove the
LangChain4j integration.

## LangChain4j implementation

- `AgenticAiLangChain4JFrameworkConfiguration` wires the converter beans and imports
  `AgenticAiLangChain4JChatModelConfiguration`, which wires one `ChatModelFactory` bean per provider.
  `AgenticAiConnectorsAutoConfiguration` imports the framework configuration unconditionally, so the
  factories are always registered. The registry picks a factory through `ChatModelFactory#supports`.
- `LangChain4JChatModelFactory<T extends ProviderConfiguration>` (the v1 `ProviderConfiguration`) is the
  abstract base. `supports` matches a `ProviderConfiguration` whose `provider()` equals the factory's
  `providerType()`. `create` builds the underlying LangChain4j model once with the abstract
  `createChatModel` and wraps it in a `LangChain4JChatModel`.
- The concrete subclasses are in the package `aiagent.chatmodel.provider.langchain4j.factory`:
  `AnthropicChatModelFactory`, `BedrockChatModelFactory`, `AzureOpenAiChatModelFactory`,
  `GoogleVertexAiChatModelFactory`, `OpenAiChatModelFactory` and `OpenAiCompatibleChatModelFactory`. The
  last two share `LangChain4JOpenAiBaseChatModelFactory`. The native providers have factories with the same
  simple names in their own packages.
- `LangChain4JChatModel` is the `ChatModel` implementation. It converts the domain conversation and the
  tool definitions to LangChain4j types, runs one `chat()` call while timing it, and converts the response
  back. It maps a content filter finish reason to `ContentFilteredException`. LangChain4j has no
  pause semantics, so it always returns `ChatResult.Completed`.
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
  ├── ToolSpecificationConverter   # ToolDefinition <-> LangChain4j ToolSpecification
  │     └── JsonSchemaConverter    # Map<String,Object> <-> LangChain4j JsonSchemaElement
  │           └── JsonSchemaElementModule  # Jackson module for JsonSchemaElement round-trip
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
