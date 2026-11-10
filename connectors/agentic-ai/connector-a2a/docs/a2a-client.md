# A2A Integration Reference

This document provides a comprehensive, code-level reference for the A2A (Agent-to-Agent) integration in the
`agentic-ai` module. A2A enables the AI Agent to interact with remote autonomous agents via the A2A protocol.

For the core AI Agent architecture, see [`agentic-loop.md`](../../docs/architecture/agentic-loop.md).
For the Gateway Tool Pattern that A2A implements, see [`extension-points.md`](../../docs/architecture/extension-points.md#gateway-tool-pattern).

## Connector Types

**A2A Client Outbound** (`A2aClientOutboundConnectorFunction`):
- Type: `io.camunda.agenticai:a2aclient:0`
- Sends messages to remote A2A agents and fetches agent cards
- Two modes: standalone and AI Agent tool

**A2A Client Polling Inbound** (`A2aClientPollingExecutable`):
- Type: `io.camunda.agenticai:a2aclient:polling:0`
- Intermediate catch event / receive task
- Polls remote A2A server for task completion (async tasks)

**A2A Client Webhook Inbound** (`A2aClientWebhookExecutable`):
- Type: `io.camunda.agenticai:a2aclient:webhook:0`
- Intermediate catch event / receive task
- Receives push notifications from A2A servers
- HMAC signature verification for security

## Package Structure

See `io.camunda.connector.agenticai.a2a.client` — organized into `common`, `outbound`, `inbound/polling`, and `inbound/webhook` sub-packages. The AI Agent tool integration lives in `io.camunda.connector.agenticai.a2a.integration`.

The two sub-packages ship as separate Maven modules:

- `connector-a2a` holds `a2a.client` (the A2A Client outbound, polling and webhook connectors, their element templates
  and Spring auto-configuration). It depends on `agentic-ai-common` only.
- `aiagent-a2a-integration` holds `a2a.integration` (the gateway tool handler, resolver, tool call identifier, system
  prompt contributor and `A2aClientAgenticToolAutoConfiguration`, plus the `a2a/tool-input-schema.json` and
  `a2a/a2a-system-prompt.md` resources). It depends on `connector-a2a`, `aiagent-api`, `aiagent-model` and
  `agentic-ai-common`, not on `connector-aiagent`.

## Data Model

### Result Type Hierarchy

```
A2aResult (sealed interface, discriminated by "kind")
├── A2aAgentCard (kind: "agentCard")
│   └── name, description, skills: List<AgentSkill>
└── A2aSendMessageResult (sealed sub-interface)
    ├── A2aMessage (kind: "message")
    │   └── role, messageId, contextId, taskId, contents: List<A2aPart>
    └── A2aTask (kind: "task")
        ├── id, contextId
        ├── status: A2aTaskStatus
        │   └── state: TaskState, message: A2aMessage, timestamp
        ├── artifacts: List<A2aArtifact>
        │   └── artifactId, name, description, contents: List<A2aPart>
        └── history: List<A2aMessage>
```

`TaskState` enum: `SUBMITTED`, `WORKING`, `INPUT_REQUIRED`, `AUTH_REQUIRED`, `COMPLETED`, `CANCELED`, `FAILED`,
`REJECTED`, `UNKNOWN`

### A2aClientResponse

Wraps the connector output:
- `result: A2aResult` — the actual payload
- `pollingData: PollingData` — task/message ID for polling connector (nullable)
- `pushNotificationData: PushNotificationData` — token for webhook connector (nullable)

## Connector Modes & Operations

### A2aConnectorModeConfiguration (sealed)

Discriminated by `type`:

**`ToolModeConfiguration`** (`type = "aiAgentTool"`): AI agent tool mode.
- `toolOperation: A2aToolOperationConfiguration` — `operation` string + `params` map
- `sendMessageSettings: A2aCommonSendMessageConfiguration`

**`StandaloneModeConfiguration`** (`type = "standalone"`): Direct BPMN usage.
- `operation: A2aStandaloneOperationConfiguration`

### A2aStandaloneOperationConfiguration (sealed)

- `FetchAgentCardOperationConfiguration` (`operation = "fetchAgentCard"`)
- `SendMessageOperationConfiguration` (`operation = "sendMessage"`) with `A2aSendMessageOperationParameters` and
  `A2aCommonSendMessageConfiguration`

### A2aCommonSendMessageConfiguration.A2aResponseRetrievalMode (sealed)

- `Blocking` — waits synchronously for completion
- `Polling` — returns immediately; polling inbound connector retrieves result later
- `Notification` — configures webhook URL for push notifications

## SDK Client Layer

### A2aSdkClientFactory / A2aSdkClientFactoryImpl

Creates an `A2aSdkClient` from an `AgentCard`, event handler, and `A2aSdkClientConfig`.

Transport selection based on what the remote `AgentCard` advertises:
- `JSONRPCTransport` — JSON-RPC over HTTP
- `RestTransport` — REST
- `GrpcTransport` — gRPC (using `ManagedChannelFactory` for channel lifecycle)

### A2aSdkClient (AutoCloseable)

Thin wrapper around the A2A SDK `Client`:
- `sendMessage(Message)` — wraps `A2AClientException` in `ConnectorException`
- `getTask(TaskQueryParams)` — wraps `A2AClientException` in `ConnectorException`
- `close()` — shuts down SDK client and gRPC channels

### A2aSdkClientConfig

- `historyLength: int` — number of history messages to request
- `blocking: boolean` — SDK polls internally and blocks
- `pushNotificationConfig: PushNotificationConfig` — webhook config (mutually exclusive with blocking)

### ManagedChannelFactory

Creates and tracks `io.grpc.ManagedChannel` instances with coordinated shutdown: 5-second graceful window, 2-second
forced shutdown.

## Converter Chain

### SDK → Domain

**`A2aSdkObjectConverter` / `A2aSdkObjectConverterImpl`**: Converts A2A SDK types to Camunda domain types:
- `convert(Message)` → `A2aMessage` (maps parts via `A2aPartConverter`)
- `convert(Task)` → `A2aTask` (maps status, artifacts, history)

**`A2aPartConverter` / `A2aPartConverterImpl`**: Converts SDK `Part` → `A2aPart`:
- `TextPart` → `A2aTextPart`
- `DataPart` → `A2aDataPart`
- Other parts: `RuntimeException` (not yet supported)

### Domain → SDK

**`A2aDocumentToPartConverter` / `A2aDocumentToPartConverterImpl`**: Converts Camunda `Document` → A2A `Part`:
- `application/json` → `DataPart`
- `text/*`, `application/xml`, `application/yaml` → `TextPart`
- PDF, JPEG, PNG, GIF, WEBP → `FilePart` (base64-encoded)
- Others → `DocumentConversionException`

### Response Handling

**`A2aSendMessageResponseHandler` / `A2aSendMessageResponseHandlerImpl`**: Handles A2A SDK `ClientEvent`:
- `MessageEvent` → converts via `A2aSdkObjectConverter.convert(Message)`
- `TaskEvent` → converts via `A2aSdkObjectConverter.convert(Task)`; throws on `AUTH_REQUIRED` state

## Async Patterns

### Blocking (synchronous)

- `responseRetrievalMode = Blocking`
- SDK polls internally; `A2aMessageSenderImpl` blocks on `CompletableFuture` with configurable timeout
- Full result returned synchronously from the outbound connector
- Best for short-lived agent interactions

### Polling

```
A2A Client Outbound (sendMessage) → get taskId (submitted/working)
  ↓
A2A Client Polling (intermediate catch event) → polls until completed
  ↓
Result correlation
```

- Outbound returns partial `A2aTask` with `pollingData.id = task.id`
- Two-level polling scheduler:
  - `A2aPollingProcessInstancesFetcherTask` (outer, default 5s): refreshes active process instance list
  - `A2aPollingTask` (inner, default 10s): polls task status per instance
- Direct correlation shortcuts: messages and terminal-state tasks are correlated immediately

### Push Notification (webhook)

```
A2A Client Outbound (sendMessage) → get notification token
  ↓
A2A Client Webhook (intermediate catch event) → waits for callback
  ↓
Result correlation
```

- Outbound configures push notifications on SDK client: `webhookUrl`, `token`, credentials
- `A2aClientWebhookExecutable` receives POST, deserializes `Task`, verifies optional HMAC, correlates

## Spring Configuration

### A2aClientAutoConfiguration

Auto-configuration for the A2A client connectors, ordered after `AgenticAiCommonAutoConfiguration`.
Imports `A2aClientOutboundConnectorConfiguration`, `A2aClientPollingConfiguration` and
`A2aClientWebhookConfiguration`; each of them imports `A2aClientCommonConfiguration`.

### A2aClientCommonConfiguration

Active when at least one of the outbound, polling or webhook configurations is enabled. Registers: `A2aPartConverter`, `A2aSdkObjectConverter`,
`A2aAgentCardFetcher`, `A2aSdkClientFactory`

Config: `camunda.connector.agenticai.a2a.client.transport.grpc.useTls` (default: `true`)

### A2aClientOutboundConnectorConfiguration

**Condition**: `camunda.connector.agenticai.a2a.client.outbound.enabled` (default: **true**)

Registers: `A2aDocumentToPartConverter`, `A2aSendMessageResponseHandler`, `A2aMessageSender`,
`A2aClientRequestHandler`, `A2aClientOutboundConnectorFunction`

### A2aClientPollingConfiguration

**Condition**: `camunda.connector.agenticai.a2a.client.polling.enabled` (default: **true**)

Registers: `A2aPollingExecutorService` (default 10 threads), `A2aClientPollingExecutable` (prototype scope)

Config: `camunda.connector.agenticai.a2a.client.polling.threadPoolSize` (default: `10`)

### A2aClientWebhookConfiguration

**Condition**: `camunda.connector.agenticai.a2a.client.webhook.enabled` (default: **true**)

Registers: `A2aClientWebhookExecutable` (prototype scope)

## Error Codes

From `A2aErrorCodes`:

| Constant                                                | Value                                                 | Context                                        |
|---------------------------------------------------------|-------------------------------------------------------|------------------------------------------------|
| `ERROR_CODE_A2A_CLIENT_SEND_MESSAGE_RESPONSE_TIMEOUT`   | `A2A_CLIENT_SEND_MESSAGE_RESPONSE_TIMEOUT`            | Blocking mode timeout waiting for response     |
| `ERROR_CODE_A2A_CLIENT_AGENT_CARD_RETRIEVAL_FAILED`     | `ERROR_CODE_A2A_CLIENT_AGENT_CARD_RETRIEVAL_FAILED`   | Failed to fetch agent card from remote server  |
| `ERROR_CODE_A2A_CLIENT_SEND_MESSAGE_FAILED`             | `A2A_CLIENT_SEND_MESSAGE_FAILED`                      | Error sending message to remote agent          |
| `ERROR_CODE_A2A_CLIENT_TASK_RETRIEVAL_FAILED`           | `A2A_CLIENT_TASK_RETRIEVAL_FAILED`                    | Error polling task status                      |

## Key Source Files

| File                                         | Purpose                                                       |
|----------------------------------------------|---------------------------------------------------------------|
| `A2aClientOutboundConnectorFunction.java`    | Outbound connector entry point                                |
| `A2aClientRequestHandlerImpl.java`           | Handles fetchAgentCard and sendMessage operations             |
| `A2aClientPollingExecutable.java`            | Polling inbound connector                                     |
| `A2aClientWebhookExecutable.java`            | Webhook inbound connector                                     |
| `A2aSendMessageResponseHandlerImpl.java`     | Handles message/task events from A2A SDK                      |
| `A2aMessageSenderImpl.java`                  | Sends messages to remote A2A agents                           |
| `A2aAgentCardFetcherImpl.java`               | Fetches agent cards from remote servers                       |
