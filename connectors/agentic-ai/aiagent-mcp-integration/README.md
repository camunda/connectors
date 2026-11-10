# aiagent-mcp-integration

## Purpose

Connects the MCP client connectors to the AI Agent as a gateway tool. It discovers the tools of an MCP
server and maps tool calls and tool call results between the LLM and the MCP Client element. The module
depends on `connector-mcp`, `aiagent-api`, `aiagent-model` and `agentic-ai-common`, not on
`connector-aiagent`.

## Key types

- `McpClientGatewayToolHandler`: the gateway tool handler for MCP Client elements.
- `McpClientGatewayToolDefinitionResolver`: finds the MCP Client elements in an ad-hoc sub-process.
- `McpToolCallIdentifier`: builds and parses the tool names (`MCP_<element>___<tool>`).
- `McpClientAgenticToolAutoConfiguration`: the Spring configuration.

## Docs

- [MCP gateway tool](docs/mcp-gateway-tool.md)
- [MCP client reference](../connector-mcp/docs/mcp-client.md)
- [Extension points](../docs/architecture/extension-points.md): the gateway tool pattern
