# Agentic AI Module – Agent Instructions

## Module Overview

The `agentic-ai` module implements Camunda's AI Agent connectors — a distributed agentic orchestration system that
integrates LLMs with BPMN processes via ad-hoc sub-processes. The module provides two connector flavors and supporting
infrastructure for tool calling, conversation memory, and event handling.

**This document focuses on the AI Agent core.** MCP and A2A integration build on top of this core via the
[Gateway Tool Pattern](#gateway-tool-pattern). For detailed code-level analysis, see
[`docs/ai-agent-architecture-deep-dive.md`](docs/ai-agent-architecture-deep-dive.md).

## Key Concepts

### Connector vs Job Worker

The module provides two AI Agent implementations:

| | AI Agent Task (Connector) | AI Agent Sub-process (Job Worker) |
|---|---|---|
| BPMN element | Service task | Ad-hoc sub-process |
| Entry class | `AiAgentFunction` | `AiAgentJobWorker` |
| Type | `io.camunda.agenticai:aiagent:1` | `io.camunda.agenticai:aiagent-job-worker:1` |
| Feedback loop | Explicit (modeled in BPMN) | Implicit (engine-managed) |
| Tool source | Camunda API (fetches BPMN XML) | `adHocSubProcessElements` variable (Zeebe) |
| Agent context | Wired via process variables by modeler | Scoped within sub-process |
| Event support | No | Yes (non-interrupting only) |
| Config per iteration | Yes (input mappings re-evaluated) | No (frozen on AHSP entry) |
| Job completion | Auto (connector runtime) | Manual (`autoComplete = false`) |

### Ad-Hoc Sub-Process (AHSP)

An ad-hoc sub-process has inner elements not connected to start/end events. With a job worker implementation:

1. Zeebe creates a job when entering the AHSP and when any inner flow completes
2. The job worker decides which elements to activate and when the AHSP is complete
3. **Only one active job exists at a time** — a new inner flow completion supersedes the current job
4. Job completion can result in `NOT_FOUND` if the job was superseded
5. The `adHocSubProcessElements` variable provides metadata about activatable elements
6. `outputCollection`/`outputElement` collect inner flow results into a list

### The Distributed Agent Loop (Sub-process Flavor)

```
Zeebe creates job → Worker picks up → Initialize agent → Load memory →
Add messages (user prompt OR tool results + events) → Call LLM →
Store memory → Complete job with:
  - If tool calls: activate elements in AHSP, continue loop
  - If no tool calls: completionConditionFulfilled=true, AHSP completes
```

When tools complete, Zeebe adds results to `toolCallResults` (via `outputElement`) and creates a new job.
If not all expected results are present, the worker completes as a no-op and waits for the next job.

## Architecture

### Core Components

```
agent/
├── AgentInitializerImpl        # State machine: INITIALIZING → TOOL_DISCOVERY → READY
├── BaseAgentRequestHandler     # Main orchestration (shared by both flavors)
├── JobWorkerAgentRequestHandler    # Job worker completion logic
├── OutboundConnectorAgentRequestHandler  # Connector completion logic
├── AgentMessagesHandlerImpl    # Message assembly (prompts, tool results, events)
├── AgentResponseHandlerImpl    # Response formatting (text/JSON/full message)
├── AgentToolsResolverImpl      # Tool definition loading & migration updates
└── AgentLimitsValidatorImpl    # Safety limits (max model calls)

framework/
├── AiFrameworkAdapter          # Abstract LLM interface
└── langchain4j/                # LangChain4J implementation

memory/
├── conversation/
│   ├── ConversationStore       # Pluggable storage backend interface
│   ├── ConversationSession     # Per-invocation load/store lifecycle
│   ├── inprocess/              # In-process store (messages in agentContext variable)
│   └── document/               # Camunda Document Storage backend
└── runtime/
    ├── RuntimeMemory           # Transient working memory for single execution
    └── MessageWindowRuntimeMemory  # Sliding window filter (keeps last N messages)

jobworker/
├── AiAgentJobWorkerHandlerImpl # Job lifecycle: execute → complete/fail/throwBpmnError
├── JobWorkerAgentExecutionContextFactoryImpl  # Binds job variables to request
└── AiAgentJobWorkerValueCustomizer  # Environment variable overrides for type/timeout

tool/
├── GatewayToolHandler          # Interface for gateway tools (MCP, A2A)
└── GatewayToolHandlerRegistry  # Registry of gateway tool handlers
```

### Data Model

**`AgentContext`** — persistent state across iterations:
- `state`: `INITIALIZING` | `TOOL_DISCOVERY` | `READY`
- `metadata`: `{processDefinitionKey, processInstanceKey}` (migration detection)
- `metrics`: `{modelCalls, tokenUsage}`
- `toolDefinitions`: resolved tool definitions for LLM
- `conversation`: storage-specific reference (in-process messages OR document reference)
- `properties`: extensible map (used by gateway tools for discovery state)

**`AgentResponse`** — output of a single agent execution:
- `context`: updated `AgentContext`
- `toolCalls`: list of `ToolCallProcessVariable` (tools to activate)
- `responseText` / `responseJson` / `responseMessage`: LLM response

**`ToolCallProcessVariable`** — tool call format for process variables:
- `_meta`: `{id, name}` — tool call metadata
- Top-level properties: flattened arguments (for `fromAi(toolCall.paramName)` access)

**`JobWorkerAgentCompletion`** — job worker specific:
- `completionConditionFulfilled`: `true` = AHSP done, `false` = continue
- `cancelRemainingInstances`: `true` = cancel running tools (events)
- `variables`: `{agentContext, toolCallResults/agent}`
- `onCompletionError`: compensation callback for failed completions

### Variable Flow (Sub-process)

**Variables set on job completion:**
- `agentContext` → updated agent context (always)
- `toolCallResults` → `[]` (cleared when tool calls present)
- `agent` → response variable (only on final completion)

**Variables set per activated tool element:**
- `toolCall` → the tool call data (`{_meta: {id, name}, ...args}`)
- `toolCallResult` → `""` (empty default to scope locally, prevents bubble-up)

**Output collection:**
- `outputCollection` = `toolCallResults`
- `outputElement` = `={id: toolCall._meta.id, name: toolCall._meta.name, content: toolCallResult}`

## Critical Behaviors

### Tool Result Matching & Partial Results

When the LLM requests multiple tools (e.g., A and B), each tool completes independently. Each completion triggers a
new job. `AgentMessagesHandlerImpl.createToolCallResultMessage()` checks if all expected tool results are present:

- **All present**: Creates a `ToolCallResultMessage` with results ordered to match the original tool calls
- **Missing (no events)**: Returns `null` → no messages added → `modelCallPrerequisitesFulfilled` returns `false` → **no-op completion** (just wait for the next job with more results)
- **Missing (with events + INTERRUPT_TOOL_CALLS)**: Creates cancelled results for missing tools, sets `cancelRemainingInstances = true`

### Job Supersession

A job may be superseded by a new job created when a tool completes. The worker must handle:
- `NOT_FOUND` rejections on completion (retried via `CommandWrapper`)
- No-op completions are safe even if superseded
- The `onCompletionError` callback enables conversation store compensation

### Conversation Persistence Timing

The conversation is stored **before** job completion is sent. For the document store, this means:
- A new document is created each time (immutable)
- If job completion fails, the document is "ahead" of Zeebe state
- On retry, the agent uses the old `agentContext` (with old document reference) — safe, just re-does the work
- Previous documents (last 2) are retained as safety net

### Event Handling (Sub-process Only)

Events from non-interrupting event sub-processes produce `ToolCallResult` entries with `id = null` (no tool call ID).
These are partitioned from actual tool results. Two behaviors:

- **WAIT_FOR_TOOL_CALL_RESULTS**: Wait for all tools to finish, then add event messages as user messages
- **INTERRUPT_TOOL_CALLS**: Cancel missing tools (synthetic cancelled results), cancel remaining instances

### Process Migration

Migration detection: `AgentMetadata.processDefinitionKey` vs current job's key.
- Allowed: adding tools, changing descriptions/parameters, changing implementations
- Blocked: removing tools (`MIGRATION_MISSING_TOOLS`), changing gateway tools (`MIGRATION_GATEWAY_TOOL_DEFINITIONS_CHANGED`)
- Task flavor: config changes picked up per iteration. Sub-process flavor: config frozen at AHSP entry

## Memory Storage Backends

| Backend | Type | Storage | Notes |
|---|---|---|---|
| In-process | `in-process` | Messages inside `agentContext` process variable | Durable (engine-persisted). Variable size limits for large conversations |
| Camunda Document | `camunda-document` | JSON document in document storage | Conversation stored externally; only a reference in `agentContext` |
| Custom | `custom` | User-provided implementation | Implement `ConversationStore`, `ConversationSession`, `ConversationContext` (via `@JsonTypeName`) |

`MessageWindowRuntimeMemory` limits messages sent to LLM (default: 20). Full history is always persisted.
System messages are never evicted. Evicting an assistant message also evicts its follow-up tool results.

## Building & Testing

```bash
# Build agentic-ai module only
mvn clean install -pl connectors/agentic-ai

# Generate element templates
mvn clean compile -pl connectors/agentic-ai

# Run unit tests
mvn test -pl connectors/agentic-ai
```

E2E tests are in `connectors-e2e-test/connectors-e2e-test-agentic-ai/`. **Do not run them in CI — they are slow and
expensive.** They use Spring Boot test with an embedded Zeebe engine and mock LLM responses.

## Gateway Tool Pattern

The Gateway Tool Pattern is the extensibility mechanism for integrating external tool providers (MCP, A2A) that expose
**multiple tools behind a single BPMN element**.

### How it works

1. Gateway elements are identified via `io.camunda.agenticai.gateway.type` extension property (set by element template)
2. `GatewayToolDefinitionResolver` implementations detect and separate gateway elements from regular tools
3. `GatewayToolHandler` implementations manage the two-phase lifecycle:
   - **Discovery phase**: creates tool calls that activate gateway connectors to list their tools
   - **Registration phase**: converts discovery results into `ToolDefinition`s with namespaced names
4. `GatewayToolCallTransformer` interface handles bidirectional name mapping between LLM and BPMN

### Discovery integrates with the agent state machine

```
INITIALIZING → (gateway elements found?) → discovery tool calls → TOOL_DISCOVERY
TOOL_DISCOVERY → (all results received?) → merge tools → READY
```

### Key files

| File | Purpose |
|---|---|
| `GatewayToolHandler.java` | Core interface: discovery, result handling, tool management |
| `GatewayToolCallTransformer.java` | Name transformation (LLM ↔ BPMN) |
| `GatewayToolHandlerRegistryImpl.java` | Registry wrapping multiple handlers |
| `TypePropertyBasedGatewayToolDefinitionResolver.java` | Base class for extension property detection |
| `AdHocToolsSchemaResolverImpl.java` | Separates gateway from regular tools |

## MCP Integration

MCP (Model Context Protocol) integration enables the AI Agent to discover and call tools from MCP servers.

### Two connector types

- **MCP Client** (`McpClientFunction`, type `io.camunda.agenticai:mcpclient:1`): Pre-configured MCP connections on runtime
- **MCP Remote Client** (`McpRemoteClientFunction`, type `io.camunda.agenticai:mcpremoteclient:1`): On-demand remote connections

### Tool naming: `MCP_<elementName>___<mcpToolName>`

One MCP server = many tools. The triple-underscore separator (`___`) distinguishes the gateway element from the tool.

### Key operations

| Operation | Purpose |
|---|---|
| `LIST_TOOLS` | Discovery (returns tool definitions from MCP server) |
| `CALL_TOOL` | Tool execution (called by LLM via gateway transform) |
| `LIST_RESOURCES` / `READ_RESOURCE` | Resource access (standalone only) |
| `LIST_PROMPTS` / `GET_PROMPT` | Prompt management (standalone only) |

### Client lifecycle

- `McpClientRegistry`: Lazy-initialized, long-lived clients (pre-configured via Spring)
- `McpRemoteClientRegistry`: Caffeine-cached clients keyed by `(processDefinitionKey, elementId)`, with TTL

### Key files

| File | Purpose |
|---|---|
| `McpClientGatewayToolHandler.java` | Gateway handler: discovery, transform, results |
| `McpToolCallIdentifier.java` | Tool name parsing (`MCP_<element>___<tool>`) |
| `McpClientExecutor.java` | Routes operations to `McpClientDelegate` |
| `McpClientRegistry.java` / `McpRemoteClientRegistry.java` | Client lifecycle |
| `McpSdkClientFactory.java` | Creates MCP SDK client instances |

## A2A Integration

A2A (Agent-to-Agent) integration enables the AI Agent to interact with remote autonomous agents.

### Key difference from MCP

- MCP: one element = **many tools** (discrete operations)
- A2A: one element = **one tool** (an entire remote agent)

### Tool naming: `A2A_<elementName>`

Each A2A Client element becomes a single tool. The LLM sends messages to the remote agent.

### Fixed tool input schema

All A2A tools share one schema: `{text, taskId?, contextId?, referenceTaskIds?}`. This supports multi-turn
conversations with remote agents.

### System prompt contribution

`A2aSystemPromptContributor` injects A2A protocol instructions (from `a2a/a2a-system-prompt.md`) when A2A tools are
detected. This teaches the LLM how to manage task states, IDs, and multi-turn flows.

### Operations and response handling

| Operation | Purpose |
|---|---|
| `fetchAgentCard` | Discovery (returns remote agent capabilities) |
| `sendMessage` | Send message to remote agent |

Response types: `MessageEvent` (direct reply) or `TaskEvent` (ongoing workflow with state: submitted, working,
input-required, completed, failed, cancelled, rejected).

### Async patterns

- **Polling**: `A2aClientPollingExecutable` — polls for task completion
- **Push notification**: `A2aClientWebhookExecutable` — receives callbacks with HMAC verification

### Key files

| File | Purpose |
|---|---|
| `A2aGatewayToolHandler.java` | Gateway handler: discovery, transform, results |
| `A2aToolCallIdentifier.java` | Tool name parsing (`A2A_<element>`) |
| `A2aClientOutboundConnectorFunction.java` | Outbound connector entry point |
| `A2aSystemPromptContributor.java` | System prompt injection |
| `A2aClientPollingExecutable.java` | Polling inbound connector |
| `A2aClientWebhookExecutable.java` | Webhook inbound connector |
| `a2a/tool-input-schema.json` | Fixed tool schema |
| `a2a/a2a-system-prompt.md` | Protocol instructions for LLM |

## Element Templates

- `agenticai-aiagent-outbound-connector.json` — AI Agent Task (service task)
- `agenticai-aiagent-job-worker.json` — AI Agent Sub-process (ad-hoc sub-process)

The job worker template is auto-generated from the outbound template via
`bin/transform-ai-agent-job-worker-template.groovy` (gmavenplus-plugin, `process-classes` phase).

## Key Source Files

| File | Purpose |
|---|---|
| `AiAgentFunction.java` | Connector entry point |
| `AiAgentJobWorker.java` | Job worker entry point |
| `AiAgentJobWorkerHandlerImpl.java` | Job lifecycle management (complete/fail/error) |
| `BaseAgentRequestHandler.java` | Core orchestration shared by both flavors |
| `AgentInitializerImpl.java` | Agent state machine (INITIALIZING → READY) |
| `AgentMessagesHandlerImpl.java` | Message assembly, tool result matching, event handling |
| `AgentResponseHandlerImpl.java` | Response formatting |
| `AgentToolsResolverImpl.java` | Tool resolution & migration handling |
| `AgentContext.java` | Persistent agent state |
| `JobWorkerAgentCompletion.java` | Job completion data including AHSP directives |
| `MessageWindowRuntimeMemory.java` | Context window sliding for LLM calls |
| `InProcessConversationStore.java` | In-process memory backend |
| `CamundaDocumentConversationSession.java` | Document storage memory backend |
| `GatewayToolHandler.java` | Core gateway interface (discovery, transform, results) |
| `GatewayToolHandlerRegistryImpl.java` | Multi-handler registry and dispatcher |
| `McpClientGatewayToolHandler.java` | MCP gateway: discovery, tool naming, transforms |
| `A2aGatewayToolHandler.java` | A2A gateway: discovery, tool naming, transforms |
| `A2aSystemPromptContributor.java` | Injects A2A protocol instructions into system prompt |
| `AgenticAiConnectorsAutoConfiguration.java` | Spring Boot wiring |
