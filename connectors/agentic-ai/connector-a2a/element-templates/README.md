# A2A element templates

This directory contains the [element templates](https://docs.camunda.io/docs/components/modeler/desktop-modeler/element-templates/about-templates/)
of the connectors that implement the [Agent2Agent protocol](https://a2a-protocol.org/). The templates of
the other Agentic AI connectors live in their own modules, see the
[index of all Agentic AI element templates](../../connector-agentic-ai/element-templates/README.md).

## Which template do I need?

Templates are versioned per Camunda minor version. The table below lists the newest template for each
Camunda minor version that the connector supports. The newest template lives directly in this folder.
Templates for older Camunda minor versions live in [`versioned/`](./versioned/).

Pick the row with the highest minimum Camunda version that is not higher than the Camunda version you
run. A template also works on later Camunda minor versions: the `engines.camunda` field `^8.9` means
"Camunda 8.9 or later".

The A2A connectors require Camunda 8.9 or later. For example, on Camunda 8.9 and 8.10 use the A2A
Client Webhook templates version `1`.

## A2A connectors

See the [A2A Client connector documentation](https://docs.camunda.io/docs/next/components/early-access/alpha/a2a-client/).

| Connector                                     | Minimum Camunda version | Template version | File |
| --- | --- | --- | --- |
| A2A Client (outbound)                         | 8.9 | 0 | [`agenticai-a2a-client-outbound-connector.json`](./agenticai-a2a-client-outbound-connector.json) |
| A2A Client Polling — Intermediate Catch Event | 8.9 | 0 | [`agenticai-a2a-client-polling-inbound-connector-intermediate.json`](./agenticai-a2a-client-polling-inbound-connector-intermediate.json) |
| A2A Client Polling — Receive Task             | 8.9 | 0 | [`agenticai-a2a-client-polling-inbound-connector-receive.json`](./agenticai-a2a-client-polling-inbound-connector-receive.json) |
| A2A Client Webhook — Intermediate Catch Event | 8.9 | 1 | [`agenticai-a2a-client-webhook-inbound-connector-intermediate.json`](./agenticai-a2a-client-webhook-inbound-connector-intermediate.json) |
| A2A Client Webhook — Receive Task             | 8.9 | 1 | [`agenticai-a2a-client-webhook-inbound-connector-receive.json`](./agenticai-a2a-client-webhook-inbound-connector-receive.json) |

## Maintaining this index

This index must stay in sync with the JSON files in this folder every time a template version is
bumped. The newest template of each connector lives in this folder; superseded ones move into
[`versioned/`](./versioned/) when `versionHistoryEnabled` is set.
