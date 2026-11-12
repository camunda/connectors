# aiagent-a2a-integration

## Purpose

Connects the A2A client connectors to the AI Agent as a gateway tool. Each A2A Client element becomes
one tool. When A2A tools are present, the module adds the A2A protocol instructions to the system
prompt. The module depends on `connector-a2a`, `aiagent-api`, `aiagent-model` and `agentic-ai-common`,
not on `connector-aiagent`.

## Key types

- `A2aGatewayToolHandler`: the gateway tool handler for A2A Client elements.
- `A2aGatewayToolDefinitionResolver`: finds the A2A Client elements in an ad-hoc sub-process.
- `A2aToolCallIdentifier`: builds and parses the tool names (`A2A_<element>`).
- `A2aSystemPromptContributor`: adds the A2A protocol instructions to the system prompt.
- `A2aClientAgenticToolAutoConfiguration`: the Spring configuration.

## Docs

- [A2A gateway tool](docs/a2a-gateway-tool.md)
- [A2A client reference](../connector-a2a/docs/a2a-client.md)
- [Extension points](../docs/architecture/extension-points.md): the gateway tool pattern
