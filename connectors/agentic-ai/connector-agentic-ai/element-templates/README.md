# Agentic AI element templates

The [element templates](https://docs.camunda.io/docs/components/modeler/desktop-modeler/element-templates/about-templates/)
of the Agentic AI connectors live in the `element-templates` folder of each connector module. This page
is the index. Each connector module has its own README that lists the template to use for each
Camunda minor version.

| Connectors | Module | Templates |
| --- | --- | --- |
| [AI Agent](#ai-agent-connectors) | `connector-aiagent` | [`connector-aiagent/element-templates/`](../../connector-aiagent/element-templates/README.md) |
| [MCP Client](#mcp-client-connectors) | `connector-mcp` | [`connector-mcp/element-templates/`](../../connector-mcp/element-templates/README.md) |
| [A2A](#a2a-connectors) | `connector-a2a` | [`connector-a2a/element-templates/`](../../connector-a2a/element-templates/README.md) |
| [Ad-hoc tools schema](#ad-hoc-tools-schema-connector) | `connector-ad-hoc-tools-schema` | [`connector-ad-hoc-tools-schema/element-templates/`](../../connector-ad-hoc-tools-schema/element-templates/README.md) |

## Which template do I need?

Templates are versioned per Camunda minor version. For each connector, the module README lists the
newest template for each Camunda minor version that the connector supports. The newest template lives
directly in the `element-templates` folder of the module. Templates for older Camunda minor versions
live in its `versioned/` folder.

Pick the row with the highest minimum Camunda version that is not higher than the Camunda version you
run. A template also works on later Camunda minor versions: the `engines.camunda` field `^8.9` means
"Camunda 8.9 or later". For example, on Camunda 8.9 use the AI Agent Task (v1) template version `7`,
and on Camunda 8.10 use the AI Agent Task (v2) template version `1`.

## AI Agent connectors

AI Agent Task and AI Agent Sub-process, in the (v2) and the deprecated (v1) flavor. See the
[AI Agent templates](../../connector-aiagent/element-templates/README.md) and the
[AI Agent connector documentation](https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent/).

## MCP Client connectors

MCP Client and MCP Remote Client, the clients for the [Model Context Protocol](https://modelcontextprotocol.io/).
See the [MCP Client templates](../../connector-mcp/element-templates/README.md) and the
[MCP Client connector documentation](https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/agentic-ai-mcp-client/).

## A2A connectors

The A2A Client outbound connector and its polling and webhook inbound connectors, which implement the
[Agent2Agent protocol](https://a2a-protocol.org/). See the
[A2A templates](../../connector-a2a/element-templates/README.md) and the
[A2A Client connector documentation](https://docs.camunda.io/docs/next/components/early-access/alpha/a2a-client/).

## Ad-hoc tools schema connector

Resolves the tools available in an ad-hoc sub-process. See the
[ad-hoc tools schema template](../../connector-ad-hoc-tools-schema/element-templates/README.md) and the
[Ad-hoc tools schema resolver documentation](https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/agentic-ai-ad-hoc-tools-schema-resolver/).

## Maintaining this index

This folder holds only this index. Keep the table and the sections in sync with the connector modules
when a connector is added, moved or removed. Version changes go into the README of the module.
