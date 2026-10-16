# MCP Client element templates

This directory contains the [element templates](https://docs.camunda.io/docs/components/modeler/desktop-modeler/element-templates/about-templates/)
of the MCP Client connectors, the clients for the [Model Context Protocol](https://modelcontextprotocol.io/).
The templates of the other Agentic AI connectors live in their own modules, see the
[index of all Agentic AI element templates](../../connector-agentic-ai/element-templates/README.md).

## Which template do I need?

Templates are versioned per Camunda minor version. The table below lists the newest template for each
Camunda minor version that the connector supports. The newest template lives directly in this folder.
Templates for older Camunda minor versions live in [`versioned/`](./versioned/).

Pick the row with the highest minimum Camunda version that is not higher than the Camunda version you
run. A template also works on later Camunda minor versions: the `engines.camunda` field `^8.9` means
"Camunda 8.9 or later".

For example, on Camunda 8.8 use the MCP Client template version `0`. On Camunda 8.9 and 8.10 use the
MCP Client template version `3`.

## MCP Client connectors

See the [MCP Client connector documentation](https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/agentic-ai-mcp-client/).

| Connector         | Minimum Camunda version | Template version | File |
| --- | --- | --- | --- |
| MCP Client        | 8.9 | 3 | [`agenticai-mcp-client-outbound-connector.json`](./agenticai-mcp-client-outbound-connector.json) |
| MCP Client        | 8.8 | 0 | [`versioned/agenticai-mcp-client-outbound-connector-0.json`](./versioned/agenticai-mcp-client-outbound-connector-0.json) |
| MCP Remote Client | 8.9 | 3 | [`agenticai-mcp-remote-client-outbound-connector.json`](./agenticai-mcp-remote-client-outbound-connector.json) |
| MCP Remote Client | 8.8 | 0 | [`versioned/agenticai-mcp-remote-client-outbound-connector-0.json`](./versioned/agenticai-mcp-remote-client-outbound-connector-0.json) |

## Maintaining this index

This index must stay in sync with the JSON files in this folder every time a template version is
bumped. The newest template of each connector lives in this folder; superseded ones move into
[`versioned/`](./versioned/) when `versionHistoryEnabled` is set.
