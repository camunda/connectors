# A2A Integration

A2A (Agent-to-Agent) enables the AI Agent to interact with remote autonomous agents. Unlike MCP (discrete tools), A2A exposes **entire agents** as single tools.

- **A2A Client Outbound** (`A2aClientOutboundConnectorFunction`, type `io.camunda.agenticai:a2aclient:0`)
- **A2A Client Polling Inbound** (`A2aClientPollingExecutable`, type `io.camunda.agenticai:a2aclient:polling:0`)
- **A2A Client Webhook Inbound** (`A2aClientWebhookExecutable`, type `io.camunda.agenticai:a2aclient:webhook:0`)

Tool naming: `A2A_<elementName>`. `A2aSystemPromptContributor` injects protocol instructions when A2A tools are detected.

For the complete A2A reference including data model, connector modes, SDK client layer, async patterns, Spring configuration, and error codes, see **[`a2a-client.md`](../../connector-a2a/docs/a2a-client.md)**.

## Overview

Unlike MCP (which exposes discrete tools), A2A exposes **entire agents** as tools. Each A2A Client element in the AHSP
becomes a single tool the LLM can send messages to. A2A supports multi-turn conversations with remote agents via
`taskId` and `contextId` management.

## Key Differences from MCP

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

## Tool Name Convention

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

## Discovery Flow

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

## Tool Call Execution Flow

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
   - Deserializes content into the typed A2aSendMessageResult and sets it as the transformed
     ToolCallResult content
7. LLM receives the response and decides next action based on task state
8. A2aGatewayToolHandler.extractDocuments() walks the typed A2aSendMessageResult and collects
   Camunda Documents from A2aDocumentPart entries and from any depth of A2aDataPart data:
   A2aMessage.contents at the root, plus A2aTask.artifacts and (recursively) A2aTask.history.
   Documents flow into the synthetic
   document UserMessage produced by ToolCallResultDocumentExtractor (see extension-points.md
   "Document Extraction from Tool Call Results").
```

## Fixed Tool Input Schema

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

## System Prompt Contribution

`A2aSystemPromptContributor` implements `SystemPromptContributor` (order = 100) and automatically injects A2A protocol
instructions when A2A tools are detected.

**Activation condition**: `agentContext.properties["a2aClients"]` is a non-empty list (i.e., A2A discovery completed).

**Content**: Loaded from `classpath:a2a/a2a-system-prompt.md`, covering:
- How to interpret `kind: "message"` vs `kind: "task"` responses
- Task state handling (input-required, completed, submitted/working, failed)
- ID management rules (taskId, contextId, referenceTaskIds)
- Multi-turn flow examples
- Common error patterns to avoid

For the `SystemPromptContributor` SPI, see [`system-prompt-contributor.md`](../../aiagent-api/docs/system-prompt-contributor.md#systempromptcontributor-interface).

## A2aClientAgenticToolAutoConfiguration

Auto-configuration ordered after `A2aClientAutoConfiguration`. Does not depend on the A2A client beans.

**Condition**: `camunda.connector.agenticai.a2a.client.agentic.tool.enabled` (default: **true**)

Registers: `A2aGatewayToolDefinitionResolver`, `A2aGatewayToolHandler`, `A2aSystemPromptContributor`

Config: `camunda.connector.agenticai.a2a.client.agentic.tool.systemPrompt` (default:
`classpath:a2a/a2a-system-prompt.md`)

## Key Source Files

| File                                         | Purpose                                                       |
|----------------------------------------------|---------------------------------------------------------------|
| `A2aGatewayToolHandler.java`                 | Gateway handler: discovery, name transform, result transform  |
| `A2aToolCallIdentifier.java`                 | Tool name parsing/construction (`A2A_<element>`)              |
| `A2aGatewayToolDefinitionResolver.java`      | Identifies A2A elements in AHSP                               |
| `A2aSystemPromptContributor.java`            | Injects A2A protocol instructions into system prompt          |
| `a2a/tool-input-schema.json`                 | Fixed tool input schema for all A2A tools                     |
| `a2a/a2a-system-prompt.md`                   | A2A protocol instructions for LLM                             |
| `A2aClientAgenticToolAutoConfiguration.java` | Spring config for A2A gateway beans                           |
