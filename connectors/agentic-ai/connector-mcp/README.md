# connector-mcp

## Purpose

The MCP Client and MCP Remote Client connectors. They call MCP servers to list and call tools, list and
read resources, and list and get prompts. They run standalone or as a gateway tool of the AI Agent
(see `aiagent-mcp-integration`). The module depends on `agentic-ai-common` only.

## Key types

- `McpClientFunction`: the MCP Client connector, for MCP connections configured on the runtime.
- `McpRemoteClientFunction`: the MCP Remote Client connector, for connections configured on the element.
- `McpClientRegistry` and `McpRemoteClientRegistry`: the client lifecycle.
- `McpClientDelegate`: the MCP client interface that the operations call.
- `McpClientResult`: the sealed result type of the operations.
- `McpClientAutoConfiguration`: the Spring configuration.

## Docs

- [MCP client reference](docs/mcp-client.md)
- [MCP gateway tool](../aiagent-mcp-integration/docs/mcp-gateway-tool.md)
- [Spring auto-configuration](../docs/architecture/spring-auto-configuration.md)
- [Element templates](element-templates/README.md)

## MCP Examples

The paths are under [`connectors/agentic-ai/examples/`](../examples/).

- **[`mcp/standalone/`](../examples/mcp/standalone/)**: MCP Client used independently (not as AI Agent tool)
  - Shows direct tool listing, tool calling, resource and prompt operations
