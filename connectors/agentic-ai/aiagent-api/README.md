# aiagent-api

## Purpose

The extension points (SPIs) of the AI Agent: conversation stores, system prompt contributors, gateway tools and
runtime tools. To extend the agent, implement an SPI and register a Spring bean. The module does not depend on Spring.
The chat model provider SPI is in [`aiagent-chatmodel-api`](../aiagent-chatmodel-api/README.md).

## Key types

- `ConversationStore` and `ConversationSession`: the conversation store SPI. `ConversationSnapshot` is the window of
  the persisted messages that the chat request is built from.
- `SystemPromptContributor`: adds text to the system prompt.
- `GatewayToolHandler`, `GatewayToolCallTransformer`, `GatewayToolDefinitionResolver` and
  `TypePropertyBasedGatewayToolDefinitionResolver`: the gateway tool SPI.
- `ContentTreeDocumentWalker`: finds the documents in a tool call result.
- `RuntimeTool`, `RuntimeToolCall`, `RuntimeToolContext`, `RuntimeToolResult` and `RuntimeToolException`: the
  runtime tool SPI, for a tool that the connector runtime executes itself. The SPI is experimental.

## Docs

- [Conversation store SPI](docs/conversation-store-spi.md)
- [System prompt contributor SPI](docs/system-prompt-contributor.md)
- [Extension points](../docs/architecture/extension-points.md)
- [Agentic loop](../docs/architecture/agentic-loop.md)
