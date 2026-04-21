# Agentic AI element templates

This directory contains the [element templates](https://docs.camunda.io/docs/components/modeler/desktop-modeler/element-templates/about-templates/)
for the Agentic AI connectors shipped with Camunda.

The latest template of each connector lives directly in this folder. Older revisions
that are still compatible with previous Camunda minor versions are kept in
[`versioned/`](./versioned/).

## Which template do I need?

Pick the highest template version whose *minimum Camunda minor version* is less
than or equal to the Camunda version you are running. The `engines.camunda`
field in each JSON file captures the same information (e.g. `^8.9` means
"requires Camunda 8.9 or later").

For example, if you are on Camunda 8.9, use the AI Agent template version `7`;
if you are on Camunda 8.10, use version `10`.

## AI Agent connectors

The AI Agent ships in two flavors that share the same versioning scheme.

### AI Agent Task (outbound connector)

`io.camunda.connectors.agenticai.aiagent.v1`

| Minimum Camunda minor | Template version | Connector     | File |
| --- | --- | --- | --- |
| 8.10 | 10 | AI Agent Task | [`agenticai-aiagent-outbound-connector.json`](./agenticai-aiagent-outbound-connector.json) |
| 8.9  | 7  | AI Agent Task | [`versioned/agenticai-aiagent-outbound-connector-7.json`](./versioned/agenticai-aiagent-outbound-connector-7.json) |
| 8.8  | 5  | AI Agent Task | [`versioned/agenticai-aiagent-outbound-connector-5.json`](./versioned/agenticai-aiagent-outbound-connector-5.json) |

### AI Agent Sub-process (job worker)

`io.camunda.connectors.agenticai.aiagent.jobworker.v1`

| Minimum Camunda minor | Template version | Connector            | File |
| --- | --- | --- | --- |
| 8.10 | 10 | AI Agent Sub-process | [`agenticai-aiagent-job-worker.json`](./agenticai-aiagent-job-worker.json) |
| 8.9  | 7  | AI Agent Sub-process | [`versioned/agenticai-aiagent-job-worker-7.json`](./versioned/agenticai-aiagent-job-worker-7.json) |
| 8.8  | 5  | AI Agent Sub-process | [`versioned/agenticai-aiagent-job-worker-5.json`](./versioned/agenticai-aiagent-job-worker-5.json) |

## MCP Client connectors

Clients for the [Model Context Protocol](https://modelcontextprotocol.io/).

| Minimum Camunda minor | Template version | Connector         | File |
| --- | --- | --- | --- |
| 8.9 | 2 | MCP Client        | [`agenticai-mcp-client-outbound-connector.json`](./agenticai-mcp-client-outbound-connector.json) |
| 8.8 | 0 | MCP Client        | [`versioned/agenticai-mcp-client-outbound-connector-0.json`](./versioned/agenticai-mcp-client-outbound-connector-0.json) |
| 8.9 | 2 | MCP Remote Client | [`agenticai-mcp-remote-client-outbound-connector.json`](./agenticai-mcp-remote-client-outbound-connector.json) |
| 8.8 | 0 | MCP Remote Client | [`versioned/agenticai-mcp-remote-client-outbound-connector-0.json`](./versioned/agenticai-mcp-remote-client-outbound-connector-0.json) |

## A2A connectors

Connectors implementing the [Agent2Agent protocol](https://a2a-protocol.org/).

| Minimum Camunda minor | Template version | Connector                                     | File |
| --- | --- | --- | --- |
| 8.9 | 0 | A2A Client (outbound)                         | [`agenticai-a2a-client-outbound-connector.json`](./agenticai-a2a-client-outbound-connector.json) |
| 8.9 | 0 | A2A Client Polling — Intermediate Catch Event | [`agenticai-a2a-client-polling-inbound-connector-intermediate.json`](./agenticai-a2a-client-polling-inbound-connector-intermediate.json) |
| 8.9 | 0 | A2A Client Polling — Receive Task             | [`agenticai-a2a-client-polling-inbound-connector-receive.json`](./agenticai-a2a-client-polling-inbound-connector-receive.json) |
| 8.9 | 0 | A2A Client Webhook — Intermediate Catch Event | [`agenticai-a2a-client-webhook-inbound-connector-intermediate.json`](./agenticai-a2a-client-webhook-inbound-connector-intermediate.json) |
| 8.9 | 0 | A2A Client Webhook — Receive Task             | [`agenticai-a2a-client-webhook-inbound-connector-receive.json`](./agenticai-a2a-client-webhook-inbound-connector-receive.json) |

## Ad-hoc tools schema connectors

Resolves the tools available in an ad-hoc sub-process.

| Minimum Camunda minor | Template version | Connector           | File |
| --- | --- | --- | --- |
| 8.8 | 2 | Ad-hoc tools schema | [`agenticai-adhoctoolsschema-outbound-connector.json`](./agenticai-adhoctoolsschema-outbound-connector.json) |
