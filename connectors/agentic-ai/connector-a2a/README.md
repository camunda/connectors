# connector-a2a

## Purpose

The A2A Client connectors. The outbound connector fetches agent cards and sends messages to remote A2A
agents. The polling and webhook inbound connectors wait for the result of an asynchronous task. The
connectors run standalone or as a gateway tool of the AI Agent (see `aiagent-a2a-integration`). The
module depends on `agentic-ai-common` only.

## Key types

- `A2aClientOutboundConnectorFunction`: the outbound connector.
- `A2aClientPollingExecutable` and `A2aClientWebhookExecutable`: the inbound connectors.
- `A2aSdkClientFactory`: creates the A2A SDK client.
- `A2aResult`: the sealed result type (`A2aAgentCard`, `A2aMessage`, `A2aTask`).
- `A2aClientAutoConfiguration`: the Spring configuration.

## Docs

- [A2A client reference](docs/a2a-client.md)
- [A2A gateway tool](../aiagent-a2a-integration/docs/a2a-gateway-tool.md)
- [Spring auto-configuration](../docs/architecture/spring-auto-configuration.md)
- [Element templates](element-templates/README.md)

## A2A Examples

- **`a2a/a2a-agent-integration/`**: A2A Client integrated as AI Agent gateway tool
  - Shows A2A Client element inside AHSP alongside regular tools
  - Demonstrates multi-turn interaction with remote agents
- **`a2a/a2a-polling/`**: A2A Client with polling for async tasks
  - Shows outbound → intermediate catch event pattern for async task completion
- **`a2a/a2a-push-notification/`**: A2A Client with webhook push notifications
  - Shows outbound → webhook intermediate catch event pattern
