# A2A Integration Reference

This document provides a comprehensive, code-level reference for the A2A (Agent-to-Agent) integration in the
`agentic-ai` module. A2A enables the AI Agent to interact with remote autonomous agents via the A2A protocol.

For the core AI Agent architecture, see [`ai-agent.md`](ai-agent.md).
For the Gateway Tool Pattern that A2A implements, see [ai-agent.md §19](ai-agent.md#19-gateway-tool-pattern).

---

## Table of Contents

1. [Overview](#1-overview)
2. [Key Differences from MCP](#2-key-differences-from-mcp)
3. [Connector Types](#3-connector-types)
4. [Package Structure](#4-package-structure)
5. [Tool Name Convention](#5-tool-name-convention)
6. [Discovery Flow](#6-discovery-flow)
7. [Tool Call Execution Flow](#7-tool-call-execution-flow)
8. [Data Model](#8-data-model)
9. [Connector Modes & Operations](#9-connector-modes--operations)
10. [SDK Client Layer](#10-sdk-client-layer)
11. [Converter Chain](#11-converter-chain)
12. [System Prompt Contribution](#12-system-prompt-contribution)
13. [Async Patterns](#13-async-patterns)
14. [Spring Configuration](#14-spring-configuration)
15. [Error Codes](#15-error-codes)
16. [Key Source Files](#16-key-source-files)

---

<a id="1-overview"></a>

## 1. Overview

Unlike MCP (which exposes discrete tools), A2A exposes **entire agents** as tools. Each A2A Client element in the AHSP
becomes a single tool the LLM can send messages to. A2A supports multi-turn conversations with remote agents via
`taskId` and `contextId` management.

---

<a id="2-key-differences-from-mcp"></a>

## 2. Key Differences from MCP

| Aspect             | MCP                                  | A2A                                                                |
|--------------------|--------------------------------------|--------------------------------------------------------------------|
| What it exposes    | Multiple discrete tools per server   | One agent per remote server                                        |
| Discovery          | `listTools` → N tool definitions    | `fetchAgentCard` → 1 tool definition                              |
| Tool name format   | `MCP_<element>___<mcpTool>`          | `A2A_<element>` (no sub-tool)                                      |
| Input schema       | Per-tool (from MCP server)           | Fixed (text + taskId + contextId + referenceTaskIds)               |
| Tool description   | Per-tool (from MCP server)           | Serialized agent card JSON                                         |
| Multi-turn         | Not applicable (stateless tools)     | Supported (taskId/contextId for continuation)                      |
| System prompt      | None                                 | A2A protocol instructions injected automatically                   |
| Response handling  | Simple content return                | Task lifecycle (submitted/working/input-required/completed/failed) |

---

<a id="3-connector-types"></a>

## 3. Connector Types

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

---

<a id="4-package-structure"></a>

## 4. Package Structure

```
io.camunda.connector.agenticai.a2a/
└── client/
    ├── agentic/tool/
    │   ├── A2aGatewayToolHandler              # GatewayToolHandler impl for A2A
    │   ├── A2aGatewayToolDefinitionResolver   # detects A2A elements in BPMN
    │   ├── A2aToolCallIdentifier              # naming: A2A_<element>
    │   ├── systemprompt/
    │   │   └── A2aSystemPromptContributor     # injects A2A protocol instructions
    │   └── configuration/
    │       ├── A2aClientAgenticToolConfiguration
    │       └── A2aClientAgenticToolConfigurationProperties
    ├── common/
    │   ├── A2aConstants                       # gateway type, property keys
    │   ├── A2aErrorCodes                      # error code constants
    │   ├── A2aAgentCardFetcher                # interface + impl
    │   ├── convert/
    │   │   ├── A2aSdkObjectConverter          # SDK Task/Message → domain
    │   │   └── A2aPartToContentConverter      # SDK Part → Camunda Content
    │   ├── model/result/
    │   │   ├── A2aResult                      # sealed: AgentCard|Message|Task
    │   │   ├── A2aSendMessageResult           # sealed sub: Message|Task
    │   │   ├── A2aAgentCard                   # agent card with skills
    │   │   ├── A2aMessage                     # role, contents, IDs
    │   │   ├── A2aTask                        # status, artifacts, history
    │   │   ├── A2aTaskStatus                  # state + message + timestamp
    │   │   ├── A2aArtifact                    # final output artifact
    │   │   └── A2aClientResponse              # wraps result + polling/push data
    │   └── sdk/
    │       ├── A2aSdkClientFactory            # interface + impl
    │       ├── A2aSdkClient                   # wraps SDK Client (AutoCloseable)
    │       ├── A2aSdkClientConfig             # historyLength, blocking, push config
    │       └── grpc/
    │           └── ManagedChannelFactory       # gRPC channel lifecycle
    ├── outbound/
    │   ├── A2aClientOutboundConnectorFunction # connector entry point
    │   ├── A2aClientRequestHandlerImpl        # fetchAgentCard + sendMessage dispatch
    │   ├── A2aMessageSenderImpl               # builds + sends A2A messages
    │   ├── A2aSendMessageResponseHandlerImpl  # handles MessageEvent/TaskEvent
    │   ├── convert/
    │   │   └── A2aDocumentToPartConverterImpl # Camunda Document → A2A Part
    │   ├── model/
    │   │   ├── A2aConnectorModeConfiguration  # sealed: ToolMode | Standalone
    │   │   ├── A2aStandaloneOperationConfiguration # sealed: FetchAgentCard | SendMessage
    │   │   ├── A2aToolOperationConfiguration  # tool mode operation + params
    │   │   ├── A2aSendMessageOperationParameters # text, taskId, contextId, etc.
    │   │   └── A2aCommonSendMessageConfiguration # response retrieval mode
    │   └── configuration/
    │       └── A2aClientOutboundConnectorConfiguration
    ├── inbound/polling/
    │   ├── A2aClientPollingExecutable          # inbound connector
    │   ├── task/
    │   │   ├── A2aPollingProcessInstancesFetcherTask # outer loop (per process)
    │   │   └── A2aPollingTask                  # inner loop (per task)
    │   ├── model/
    │   │   ├── A2aPollingActivationProperties  # polling intervals
    │   │   └── A2aPollingRuntimeProperties     # per-instance connection + response
    │   ├── service/
    │   │   └── A2aPollingExecutorService       # ScheduledThreadPoolExecutor wrapper
    │   └── configuration/
    │       └── A2aClientPollingConfiguration
    └── inbound/webhook/
        ├── A2aClientWebhookExecutable          # webhook connector
        ├── model/
        │   └── A2aWebhookProperties            # HMAC + auth config
        └── configuration/
            └── A2aClientWebhookConfiguration
```

---

<a id="5-tool-name-convention"></a>

## 5. Tool Name Convention

`A2aToolCallIdentifier` manages the naming scheme:

```
A2A_<elementName>
```

- Prefix: `A2A_`
- Element name: BPMN element ID of the A2A Client in the AHSP

Example: `A2A_WeatherAgent`

Simpler than MCP because there's only one "tool" per A2A element (the agent itself).

- `isA2aToolCallIdentifier(String)` → `startsWith("A2A_") && length > 4`
- `fullyQualifiedName()` → `"A2A_" + elementName`
- `fromToolCallName(String)` → strips `"A2A_"` prefix

---

<a id="6-discovery-flow"></a>

## 6. Discovery Flow

```
1. Agent enters INITIALIZING state
2. AdHocToolsSchemaResolver identifies A2A Client element via gateway type extension
3. A2aGatewayToolHandler.initiateToolDiscovery():
   - Stores A2A client element IDs in agentContext.properties.a2aClients
   - Creates tool call: id="A2A_fetchAgentCard_<elementId>", name="<elementId>",
     args={operation: "fetchAgentCard"}
4. Agent completes job with activateElement("<elementId>")
5. A2A Client connector fetches agent card from remote server
6. Result (agent card JSON) flows back via outputElement
7. Agent receives result in TOOL_DISCOVERY state
8. A2aGatewayToolHandler.handleToolDiscoveryResults():
   - Creates single ToolDefinition per A2A element:
     name: "A2A_<elementId>"
     description: serialized agent card JSON (the LLM reads agent capabilities from this)
     inputSchema: fixed schema from a2a/tool-input-schema.json
9. Tools merged into agentContext.toolDefinitions
10. State → READY
```

---

<a id="7-tool-call-execution-flow"></a>

## 7. Tool Call Execution Flow

```
1. LLM requests: "A2A_WeatherAgent" with args {text: "What's the weather?"}
2. A2aGatewayToolHandler.transformToolCalls():
   - Parses A2aToolCallIdentifier: elementName="WeatherAgent"
   - Transforms to: ToolCall(name="WeatherAgent",
     args={operation: "sendMessage", params: {text: "What's the weather?"}})
3. Agent completes job with activateElement("WeatherAgent")
4. A2A Client connector sends message to remote agent
5. Result (A2aSendMessageResult — either A2aTask or A2aMessage) flows back
6. A2aGatewayToolHandler.transformToolCallResults():
   - Rebuilds fully qualified name: "A2A_WeatherAgent"
   - Returns ToolCallResult with the send message result content
7. LLM receives the response and decides next action based on task state
```

---

<a id="8-data-model"></a>

## 8. Data Model

### Result Type Hierarchy

```
A2aResult (sealed interface, discriminated by "kind")
├── A2aAgentCard (kind: "agentCard")
│   └── name, description, skills: List<AgentSkill>
└── A2aSendMessageResult (sealed sub-interface)
    ├── A2aMessage (kind: "message")
    │   └── role, messageId, contextId, taskId, contents: List<Content>
    └── A2aTask (kind: "task")
        ├── id, contextId
        ├── status: A2aTaskStatus
        │   └── state: TaskState, message: A2aMessage, timestamp
        ├── artifacts: List<A2aArtifact>
        │   └── artifactId, name, description, contents
        └── history: List<A2aMessage>
```

`TaskState` enum: `SUBMITTED`, `WORKING`, `INPUT_REQUIRED`, `AUTH_REQUIRED`, `COMPLETED`, `CANCELED`, `FAILED`,
`REJECTED`, `UNKNOWN`

### A2aClientResponse

Wraps the connector output:
- `result: A2aResult` — the actual payload
- `pollingData: PollingData` — task/message ID for polling connector (nullable)
- `pushNotificationData: PushNotificationData` — token for webhook connector (nullable)

### Fixed Tool Input Schema

All A2A tools share `a2a/tool-input-schema.json`:

```json
{
  "type": "object",
  "properties": {
    "text":             { "type": "string", "description": "The request or follow-up message" },
    "taskId":           { "type": "string", "description": "Existing task ID (for continuation)" },
    "contextId":        { "type": "string", "description": "Context ID (from first response)" },
    "referenceTaskIds": { "type": "array", "description": "Prior task IDs for context" }
  },
  "required": ["text"]
}
```

---

<a id="9-connector-modes--operations"></a>

## 9. Connector Modes & Operations

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

---

<a id="10-sdk-client-layer"></a>

## 10. SDK Client Layer

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

---

<a id="11-converter-chain"></a>

## 11. Converter Chain

### SDK → Domain

**`A2aSdkObjectConverter` / `A2aSdkObjectConverterImpl`**: Converts A2A SDK types to Camunda domain types:
- `convert(Message)` → `A2aMessage` (maps parts via `A2aPartToContentConverter`)
- `convert(Task)` → `A2aTask` (maps status, artifacts, history)

**`A2aPartToContentConverter` / `A2aPartToContentConverterImpl`**: Converts SDK `Part` → Camunda `Content`:
- `TextPart` → `TextContent`
- `DataPart` → `ObjectContent`
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

---

<a id="12-system-prompt-contribution"></a>

## 12. System Prompt Contribution

`A2aSystemPromptContributor` implements `SystemPromptContributor` (order = 100) and automatically injects A2A protocol
instructions when A2A tools are detected.

**Activation condition**: `agentContext.properties["a2aClients"]` is a non-empty list (i.e., A2A discovery completed).

**Content**: Loaded from `classpath:a2a/a2a-system-prompt.md`, covering:
- How to interpret `kind: "message"` vs `kind: "task"` responses
- Task state handling (input-required, completed, submitted/working, failed)
- ID management rules (taskId, contextId, referenceTaskIds)
- Multi-turn flow examples
- Common error patterns to avoid

For the `SystemPromptContributor` SPI, see [ai-agent.md §13](ai-agent.md#13-system-prompt-composition).

---

<a id="13-async-patterns"></a>

## 13. Async Patterns

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

---

<a id="14-spring-configuration"></a>

## 14. Spring Configuration

### A2aClientCommonConfiguration

Always active when A2A is enabled. Registers: `A2aPartToContentConverter`, `A2aSdkObjectConverter`,
`A2aAgentCardFetcher`, `A2aSdkClientFactory`

Config: `camunda.connector.agenticai.a2a.client.transport.grpc.useTls` (default: `true`)

### A2aClientOutboundConnectorConfiguration

**Condition**: `camunda.connector.agenticai.a2a.client.outbound.enabled` (default: **true**)

Registers: `A2aDocumentToPartConverter`, `A2aSendMessageResponseHandler`, `A2aMessageSender`,
`A2aClientRequestHandler`, `A2aClientOutboundConnectorFunction`

### A2aClientAgenticToolConfiguration

**Condition**: `camunda.connector.agenticai.a2a.client.agentic.tool.enabled` (default: **true**)

Registers: `A2aGatewayToolDefinitionResolver`, `A2aGatewayToolHandler`, `A2aSystemPromptContributor`

Config: `camunda.connector.agenticai.a2a.client.agentic.tool.systemPrompt` (default:
`classpath:a2a/a2a-system-prompt.md`)

### A2aClientPollingConfiguration

**Condition**: `camunda.connector.agenticai.a2a.client.polling.enabled` (default: **true**)

Registers: `A2aPollingExecutorService` (default 10 threads), `A2aClientPollingExecutable` (prototype scope)

Config: `camunda.connector.agenticai.a2a.client.polling.threadPoolSize` (default: `10`)

### A2aClientWebhookConfiguration

**Condition**: `camunda.connector.agenticai.a2a.client.webhook.enabled` (default: **true**)

Registers: `A2aClientWebhookExecutable` (prototype scope)

---

<a id="15-error-codes"></a>

## 15. Error Codes

From `A2aErrorCodes`:

| Constant                                                | Value                                                 | Context                                        |
|---------------------------------------------------------|-------------------------------------------------------|------------------------------------------------|
| `ERROR_CODE_A2A_CLIENT_SEND_MESSAGE_RESPONSE_TIMEOUT`   | `A2A_CLIENT_SEND_MESSAGE_RESPONSE_TIMEOUT`            | Blocking mode timeout waiting for response     |
| `ERROR_CODE_A2A_CLIENT_AGENT_CARD_RETRIEVAL_FAILED`     | `ERROR_CODE_A2A_CLIENT_AGENT_CARD_RETRIEVAL_FAILED`   | Failed to fetch agent card from remote server  |
| `ERROR_CODE_A2A_CLIENT_SEND_MESSAGE_FAILED`             | `A2A_CLIENT_SEND_MESSAGE_FAILED`                      | Error sending message to remote agent          |
| `ERROR_CODE_A2A_CLIENT_TASK_RETRIEVAL_FAILED`           | `A2A_CLIENT_TASK_RETRIEVAL_FAILED`                    | Error polling task status                      |

---

<a id="16-key-source-files"></a>

## 16. Key Source Files

| File                                         | Purpose                                                       |
|----------------------------------------------|---------------------------------------------------------------|
| `A2aClientOutboundConnectorFunction.java`    | Outbound connector entry point                                |
| `A2aClientRequestHandlerImpl.java`           | Handles fetchAgentCard and sendMessage operations             |
| `A2aGatewayToolHandler.java`                 | Gateway handler: discovery, name transform, result transform  |
| `A2aToolCallIdentifier.java`                 | Tool name parsing/construction (`A2A_<element>`)              |
| `A2aGatewayToolDefinitionResolver.java`      | Identifies A2A elements in AHSP                               |
| `A2aSystemPromptContributor.java`            | Injects A2A protocol instructions into system prompt          |
| `A2aClientPollingExecutable.java`            | Polling inbound connector                                     |
| `A2aClientWebhookExecutable.java`            | Webhook inbound connector                                     |
| `A2aSendMessageResponseHandlerImpl.java`     | Handles message/task events from A2A SDK                      |
| `A2aMessageSenderImpl.java`                  | Sends messages to remote A2A agents                           |
| `A2aAgentCardFetcherImpl.java`               | Fetches agent cards from remote servers                       |
| `a2a/tool-input-schema.json`                 | Fixed tool input schema for all A2A tools                     |
| `a2a/a2a-system-prompt.md`                   | A2A protocol instructions for LLM                             |
| `A2aClientAgenticToolConfiguration.java`     | Spring config for A2A gateway beans                           |
