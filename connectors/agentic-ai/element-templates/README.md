# Agentic AI element templates

This directory contains the [element templates](https://docs.camunda.io/docs/components/modeler/desktop-modeler/element-templates/about-templates/)
for the Agentic AI connectors shipped with Camunda.

The latest template of each connector lives directly in this folder. Older revisions
that are still compatible with previous Camunda minor versions are kept in
[`versioned/`](./versioned/).

## Which template do I need?

Pick the highest template version whose *minimum Camunda version* is less
than or equal to the Camunda version you are running. The `engines.camunda`
field in each JSON file captures the same information (e.g. `^8.9` means
"requires Camunda 8.9 or later").

For example, if you are on Camunda 8.9, use the AI Agent template version `7`;
if you are on Camunda 8.10, use version `10`.

## AI Agent connectors

The AI Agent ships in two flavors that share the same versioning scheme.

### AI Agent Task

`io.camunda.connectors.agenticai.aiagent.v1`

| Connector     | Minimum Camunda version | Template version | File |
| --- | --- | --- | --- |
| AI Agent Task | 8.10 | 10 | [`agenticai-aiagent-outbound-connector.json`](./agenticai-aiagent-outbound-connector.json) |
| AI Agent Task | 8.9  | 7  | [`versioned/agenticai-aiagent-outbound-connector-7.json`](./versioned/agenticai-aiagent-outbound-connector-7.json) |
| AI Agent Task | 8.8  | 5  | [`versioned/agenticai-aiagent-outbound-connector-5.json`](./versioned/agenticai-aiagent-outbound-connector-5.json) |

### AI Agent Sub-process

`io.camunda.connectors.agenticai.aiagent.jobworker.v1`

| Connector            | Minimum Camunda version | Template version | File |
| --- | --- | --- | --- |
| AI Agent Sub-process | 8.10 | 10 | [`agenticai-aiagent-job-worker.json`](./agenticai-aiagent-job-worker.json) |
| AI Agent Sub-process | 8.9  | 7  | [`versioned/agenticai-aiagent-job-worker-7.json`](./versioned/agenticai-aiagent-job-worker-7.json) |
| AI Agent Sub-process | 8.8  | 5  | [`versioned/agenticai-aiagent-job-worker-5.json`](./versioned/agenticai-aiagent-job-worker-5.json) |

## MCP Client connectors

Clients for the [Model Context Protocol](https://modelcontextprotocol.io/).

| Connector         | Minimum Camunda version | Template version | File |
| --- | --- | --- | --- |
| MCP Client        | 8.9 | 2 | [`agenticai-mcp-client-outbound-connector.json`](./agenticai-mcp-client-outbound-connector.json) |
| MCP Client        | 8.8 | 0 | [`versioned/agenticai-mcp-client-outbound-connector-0.json`](./versioned/agenticai-mcp-client-outbound-connector-0.json) |
| MCP Remote Client | 8.9 | 2 | [`agenticai-mcp-remote-client-outbound-connector.json`](./agenticai-mcp-remote-client-outbound-connector.json) |
| MCP Remote Client | 8.8 | 0 | [`versioned/agenticai-mcp-remote-client-outbound-connector-0.json`](./versioned/agenticai-mcp-remote-client-outbound-connector-0.json) |

## A2A connectors

Connectors implementing the [Agent2Agent protocol](https://a2a-protocol.org/).

| Connector                                     | Minimum Camunda version | Template version | File |
| --- | --- | --- | --- |
| A2A Client (outbound)                         | 8.9 | 0 | [`agenticai-a2a-client-outbound-connector.json`](./agenticai-a2a-client-outbound-connector.json) |
| A2A Client Polling — Intermediate Catch Event | 8.9 | 0 | [`agenticai-a2a-client-polling-inbound-connector-intermediate.json`](./agenticai-a2a-client-polling-inbound-connector-intermediate.json) |
| A2A Client Polling — Receive Task             | 8.9 | 0 | [`agenticai-a2a-client-polling-inbound-connector-receive.json`](./agenticai-a2a-client-polling-inbound-connector-receive.json) |
| A2A Client Webhook — Intermediate Catch Event | 8.9 | 0 | [`agenticai-a2a-client-webhook-inbound-connector-intermediate.json`](./agenticai-a2a-client-webhook-inbound-connector-intermediate.json) |
| A2A Client Webhook — Receive Task             | 8.9 | 0 | [`agenticai-a2a-client-webhook-inbound-connector-receive.json`](./agenticai-a2a-client-webhook-inbound-connector-receive.json) |

## Ad-hoc tools schema connector

Resolves the tools available in an ad-hoc sub-process.

| Connector           | Minimum Camunda version | Template version | File |
| --- | --- | --- | --- |
| Ad-hoc tools schema | 8.8 | 2 | [`agenticai-adhoctoolsschema-outbound-connector.json`](./agenticai-adhoctoolsschema-outbound-connector.json) |
