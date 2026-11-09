# aiagent-api

## Purpose

The extension points (SPIs) of the AI Agent: chat model providers, conversation stores, system prompt
contributors, gateway tools and runtime tools. To extend the agent, implement an SPI and register a Spring bean. The
module does not depend on Spring.

## Key types

- `ChatModel`, `ChatModelFactory`, `ChatRequest` and `ChatResult`: the chat model provider SPI.
  `ConversationSnapshot` is the windowed conversation that a `ChatRequest` sends to the LLM.
- `ChatModelRejectedException`: the sealed type for a rejected model call, with
  `ContentFilteredException`, `ContextWindowExceededException` and `GuardrailInterventionException`.
- `ConversationStore` and `ConversationSession`: the conversation store SPI.
- `ConversationStoreRequest`: the messages that `ConversationSession#storeMessages` writes.
- `SystemPromptContributor`: adds text to the system prompt.
- `GatewayToolHandler`, `GatewayToolCallTransformer`, `GatewayToolDefinitionResolver` and
  `TypePropertyBasedGatewayToolDefinitionResolver`: the gateway tool SPI.
- `GatewayToolDefinitionUpdates`: the names of the added and removed gateway tool definitions, from
  `GatewayToolHandler#resolveUpdatedGatewayToolDefinitions`.
- `GatewayToolDiscoveryResult`: the agent context with the discovered tool definitions, and the tool
  call results that are not tool discovery results.
- `ContentTreeDocumentWalker`: finds the documents in a tool call result.
- `RuntimeTool`, `RuntimeToolCall`, `RuntimeToolContext`, `RuntimeToolResult` and `RuntimeToolException`: the
  runtime tool SPI, for a tool that the connector runtime executes itself. The SPI is experimental.
  `RuntimeToolResult` is sealed, with `RuntimeToolResult.Success` and `RuntimeToolResult.Error`.

## Docs

- [Chat model provider SPI](docs/chat-model-spi.md)
- [Conversation store SPI](docs/conversation-store-spi.md)
- [System prompt contributor SPI](docs/system-prompt-contributor.md)
- [Extension points](../docs/architecture/extension-points.md)
- [Agentic loop](../docs/architecture/agentic-loop.md)
