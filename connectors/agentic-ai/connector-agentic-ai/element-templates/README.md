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

For example, on Camunda 8.9, use AI Agent (v1) template version `7`;
on Camunda 8.10, use AI Agent (v2) template version `2`.

## AI Agent connectors

See the [AI Agent connector documentation](https://docs.camunda.io/docs/8.10/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent/).
The AI Agent ships in two flavors that share the same versioning scheme.

The current v2 templates offer optional reusable credentials for model connections. Leave the
credential chooser empty to configure authentication and connection settings inline, including in
c8run without creating a credential. Selecting a credential hides the inline fallback fields and
makes the credential authoritative. Provider, backend, API, and model settings remain local to the task.
Gateway endpoints and Bedrock endpoints/regions can still be overridden per task; gateway endpoints
and Bedrock regions are required inline when no credential supplies them. Older templates remain
supported by the runtime; v1 templates are unchanged.
Custom provider beans and conversation-memory connections are outside this credential support.
AI Gateway credentials offer API-key or OAuth 2.0 client-credentials authentication in one
credential type, shared by the Anthropic and OpenAI custom backends. Both methods are also available
inline. Anthropic additionally offers an inline no-auth option.

### AI Agent Task

AI Agent Task (v2) is a new, independently versioned connector type (`io.camunda.agenticai:aiagent:task:2`)
running on the new chat-model provider SPI, not a new template version of AI Agent Task (v1). As of
Camunda 8.10, AI Agent Task (v1) is deprecated in favor of (v2).

| Connector                      | Minimum Camunda version | Template version | File |
| --- | --- | --- | --- |
| AI Agent Task (v2)             | 8.10 | 2  | [`agenticai-ai-agent-task.v2.json`](./agenticai-ai-agent-task.v2.json) |
| AI Agent Task (v1, deprecated) | 8.10 | 13 | [`agenticai-aiagent-outbound-connector.json`](./agenticai-aiagent-outbound-connector.json) |
| AI Agent Task (v1)             | 8.9  | 7  | [`versioned/agenticai-aiagent-outbound-connector-7.json`](./versioned/agenticai-aiagent-outbound-connector-7.json) |
| AI Agent Task (v1)             | 8.8  | 5  | [`versioned/agenticai-aiagent-outbound-connector-5.json`](./versioned/agenticai-aiagent-outbound-connector-5.json) |

### AI Agent Sub-process

AI Agent Sub-process (v2) is a new, independently versioned connector type
(`io.camunda.agenticai:aiagent:subprocess:2`) running on the new chat-model provider SPI, not a new
template version of AI Agent Sub-process (v1). As of Camunda 8.10, AI Agent Sub-process (v1) is
deprecated in favor of (v2).

| Connector                             | Minimum Camunda version | Template version | File |
| --- | --- | --- | --- |
| AI Agent Sub-process (v2)             | 8.10 | 2  | [`agenticai-ai-agent-subprocess.v2.json`](./agenticai-ai-agent-subprocess.v2.json) |
| AI Agent Sub-process (v1, deprecated) | 8.10 | 13 | [`agenticai-aiagent-job-worker.json`](./agenticai-aiagent-job-worker.json) |
| AI Agent Sub-process (v1)             | 8.9  | 7  | [`versioned/agenticai-aiagent-job-worker-7.json`](./versioned/agenticai-aiagent-job-worker-7.json) |
| AI Agent Sub-process (v1)             | 8.8  | 5  | [`versioned/agenticai-aiagent-job-worker-5.json`](./versioned/agenticai-aiagent-job-worker-5.json) |

## MCP Client connectors

Clients for the [Model Context Protocol](https://modelcontextprotocol.io/).
See the [MCP Client connector documentation](https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/agentic-ai-mcp-client/).

| Connector         | Minimum Camunda version | Template version | File |
| --- | --- | --- | --- |
| MCP Client        | 8.9 | 3 | [`agenticai-mcp-client-outbound-connector.json`](./agenticai-mcp-client-outbound-connector.json) |
| MCP Client        | 8.8 | 0 | [`versioned/agenticai-mcp-client-outbound-connector-0.json`](./versioned/agenticai-mcp-client-outbound-connector-0.json) |
| MCP Remote Client | 8.9 | 3 | [`agenticai-mcp-remote-client-outbound-connector.json`](./agenticai-mcp-remote-client-outbound-connector.json) |
| MCP Remote Client | 8.8 | 0 | [`versioned/agenticai-mcp-remote-client-outbound-connector-0.json`](./versioned/agenticai-mcp-remote-client-outbound-connector-0.json) |

## A2A connectors

Connectors implementing the [Agent2Agent protocol](https://a2a-protocol.org/).
See the [A2A Client connector documentation](https://docs.camunda.io/docs/next/components/early-access/alpha/a2a-client/).

| Connector                                     | Minimum Camunda version | Template version | File |
| --- | --- | --- | --- |
| A2A Client (outbound)                         | 8.9 | 0 | [`agenticai-a2a-client-outbound-connector.json`](./agenticai-a2a-client-outbound-connector.json) |
| A2A Client Polling — Intermediate Catch Event | 8.9 | 0 | [`agenticai-a2a-client-polling-inbound-connector-intermediate.json`](./agenticai-a2a-client-polling-inbound-connector-intermediate.json) |
| A2A Client Polling — Receive Task             | 8.9 | 0 | [`agenticai-a2a-client-polling-inbound-connector-receive.json`](./agenticai-a2a-client-polling-inbound-connector-receive.json) |
| A2A Client Webhook — Intermediate Catch Event | 8.9 | 0 | [`agenticai-a2a-client-webhook-inbound-connector-intermediate.json`](./agenticai-a2a-client-webhook-inbound-connector-intermediate.json) |
| A2A Client Webhook — Receive Task             | 8.9 | 0 | [`agenticai-a2a-client-webhook-inbound-connector-receive.json`](./agenticai-a2a-client-webhook-inbound-connector-receive.json) |

## Ad-hoc tools schema connector

Resolves the tools available in an ad-hoc sub-process.
See the [Ad-hoc tools schema resolver documentation](https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/agentic-ai-ad-hoc-tools-schema-resolver/).

| Connector           | Minimum Camunda version | Template version | File |
| --- | --- | --- | --- |
| Ad-hoc tools schema | 8.8 | 2 | [`agenticai-adhoctoolsschema-outbound-connector.json`](./agenticai-adhoctoolsschema-outbound-connector.json) |

## Maintaining this index

This index must stay in sync with the JSON files in this folder every time a template version is
bumped or a connector is added. The latest template of each connector lives in this folder; superseded
ones move into [`versioned/`](./versioned/) when `versionHistoryEnabled` is set.
