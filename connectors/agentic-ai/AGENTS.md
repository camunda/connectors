# Agentic AI Module – Agent Instructions

## Module Overview

The `agentic-ai` module implements Camunda's AI Agent connectors — a distributed agentic orchestration system that
integrates LLMs with BPMN processes via ad-hoc sub-processes. The module provides two connector flavors and supporting
infrastructure for tool calling, conversation memory, and event handling.

**This document is a concise orientation and reference guide.** For detailed code-level analysis, see the reference docs:

- [`docs/adr/`](docs/adr/) — architecture decision records
- [`docs/reference/ai-agent.md`](docs/reference/ai-agent.md) — core AI Agent architecture
- [`docs/reference/mcp.md`](docs/reference/mcp.md) — MCP integration
- [`docs/reference/a2a.md`](docs/reference/a2a.md) — A2A integration

## Key Concepts

### Connector vs Job Worker

|                      | AI Agent Task (Connector)            | AI Agent Sub-process (Job Worker)              |
|----------------------|--------------------------------------|------------------------------------------------|
| BPMN element         | Service task                         | Ad-hoc sub-process                             |
| Entry class          | `AiAgentFunction`                    | `AiAgentJobWorker`                             |
| Type                 | `io.camunda.agenticai:aiagent:1`     | `io.camunda.agenticai:aiagent-job-worker:1`    |
| Feedback loop        | Explicit (modeled in BPMN)           | Implicit (engine-managed)                      |
| Tool source          | Camunda API (fetches BPMN XML)       | `adHocSubProcessElements` variable (Zeebe)     |
| Agent context        | Wired via process variables by modeler | Scoped within sub-process                    |
| Event support        | No                                   | Yes (non-interrupting only)                    |
| Config per iteration | Yes (input mappings re-evaluated)    | No (frozen on AHSP entry)                      |
| Job completion       | Auto (connector runtime)             | Manual (`autoComplete = false`)                |

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
├── BaseAgentRequestHandler     # Core orchestrator: init → memory → messages → LLM → response → complete
├── JobWorkerAgentRequestHandler    # Job worker completion logic
├── OutboundConnectorAgentRequestHandler  # Connector completion logic
├── AgentMessagesHandlerImpl    # Message assembly (prompts, tool results, events)
├── AgentResponseHandlerImpl    # Response formatting (text/JSON/full message)
├── AgentToolsResolverImpl      # Tool definition loading & migration updates
└── AgentLimitsValidatorImpl    # Safety limits (max model calls)

framework/
├── AiFrameworkAdapter          # Abstract LLM interface (RuntimeMemory → response)
└── langchain4j/                # LangChain4J implementation

memory/
├── conversation/
│   ├── ConversationStore       # Pluggable storage: executeInSession() callback pattern
│   ├── ConversationSession     # Per-invocation: loadIntoRuntimeMemory/storeFromRuntimeMemory
│   ├── ConversationContext     # Persistent reference (conversationId)
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

**`AgentResponse`** — output: updated `AgentContext` + tool calls + LLM response (text/JSON/full message).

**`ToolCallProcessVariable`** — flattened tool call for process variables: `{_meta: {id, name}, ...args}`.

**`JobWorkerAgentCompletion`** — job completion directives: AHSP done/continue, cancel flags, variables, error callback.

For full record definitions, see [ai-agent.md §5](docs/reference/ai-agent.md#5-data-model).

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

Partial tool results trigger no-op completions until all expected results arrive. Jobs may be superseded when tools
complete — handled via `CommandWrapper` retries and `onCompletionError` callbacks. For detailed mechanics, see
[ai-agent.md §9 (tool completion)](docs/reference/ai-agent.md#9-tool-completion) and
[§10 (concurrency)](docs/reference/ai-agent.md#10-concurrency).

### Conversation Session Lifecycle

`ConversationStore.executeInSession()` wraps agent processing in a callback:

```
executeInSession(ctx, agentContext, session -> {
    session.loadIntoRuntimeMemory(agentContext, runtimeMemory)
    [add messages, call LLM, etc.]
    session.storeFromRuntimeMemory(agentContext, runtimeMemory)
}) → completeJob
```

For backend-specific behavior and failure handling, see [ai-agent.md §6](docs/reference/ai-agent.md#6-conversation-memory).

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

| Backend          | Type               | Storage                                          | Notes                                                                                                         |
|------------------|--------------------|--------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| In-process       | `in-process`       | Messages inside `agentContext` process variable  | Durable (engine-persisted). Variable size limits for large conversations                                      |
| Camunda Document | `camunda-document` | JSON document in document storage                | Conversation stored externally; only a reference in `agentContext`                                             |
| Custom           | `custom`           | User-provided implementation                     | Implement `ConversationStore`, `ConversationSession`, `ConversationContext`; register custom `ConversationContext` subtypes with the runtime `ObjectMapper` |

`MessageWindowRuntimeMemory` limits messages sent to LLM (default: 20). Full history is always persisted.
For eviction rules and architecture details, see [ai-agent.md §6](docs/reference/ai-agent.md#6-conversation-memory).

## Building & Testing

```bash
# Build agentic-ai module only
mvn clean install -pl connectors/agentic-ai

# Generate element templates
mvn clean compile -pl connectors/agentic-ai

# Run unit tests
mvn test -pl connectors/agentic-ai
```

In the agentic-ai module, only use unit testing apart from the few existing Spring Boot tests.

### E2E Tests

E2E tests are in `connectors-e2e-test/connectors-e2e-test-agentic-ai/`. They use Spring Boot test with an embedded
Zeebe engine and mock LLM responses.

```bash
# Run a specific e2e test class
mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -Dtest=<TestClassName>

# Run all e2e tests (slow — will take a long time)
mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai
```

When verifying changes, search the e2e test directory for test cases relevant to the change and run those selectively
rather than running the full suite. Running all e2e tests takes a long time.

## Gateway Tool Pattern

The Gateway Tool Pattern is the extensibility mechanism for integrating external tool providers (MCP, A2A) that expose
**multiple tools behind a single BPMN element**.

- MCP: one element = **many tools** (discrete operations from an MCP server)
- A2A: one element = **one tool** (an entire remote agent)

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

For implementation details, see [ai-agent.md §19](docs/reference/ai-agent.md#19-gateway-tool-pattern).

### Key files

| File                                                    | Purpose                                              |
|---------------------------------------------------------|------------------------------------------------------|
| `GatewayToolHandler.java`                               | Core interface: discovery, result handling, tool management |
| `GatewayToolCallTransformer.java`                       | Name transformation (LLM ↔ BPMN)                   |
| `GatewayToolHandlerRegistryImpl.java`                   | Registry wrapping multiple handlers                  |
| `TypePropertyBasedGatewayToolDefinitionResolver.java`   | Base class for extension property detection          |
| `AdHocToolsSchemaResolverImpl.java`                     | Separates gateway from regular tools                 |

## MCP Integration

MCP (Model Context Protocol) integration enables the AI Agent to discover and call tools from MCP servers.
Two connector types:

- **MCP Client** (`McpClientFunction`, type `io.camunda.agenticai:mcpclient:1`): Pre-configured MCP connections on runtime
- **MCP Remote Client** (`McpRemoteClientFunction`, type `io.camunda.agenticai:mcpremoteclient:1`): On-demand remote connections

For the complete MCP reference, see [`docs/reference/mcp.md`](docs/reference/mcp.md).

### Gateway tool naming

Tool naming: `MCP_<elementName>___<mcpToolName>` — one MCP server = many tools, triple-underscore separates gateway
element from tool name.

## A2A Integration

A2A (Agent-to-Agent) integration enables the AI Agent to interact with remote autonomous agents.
`A2aSystemPromptContributor` injects protocol instructions (from `a2a/a2a-system-prompt.md`) when A2A tools are
detected.

For the complete A2A reference, see [`docs/reference/a2a.md`](docs/reference/a2a.md).

### Gateway tool naming

Tool naming: `A2A_<elementName>` — one A2A element = one tool (an entire remote agent).

## Element Templates

- `agenticai-aiagent-outbound-connector.json` — AI Agent Task (service task)
- `agenticai-aiagent-job-worker.json` — AI Agent Sub-process (ad-hoc sub-process)

The job worker template is auto-generated from the outbound template via
`bin/transform-ai-agent-job-worker-template.groovy` (gmavenplus-plugin, `process-classes` phase).

## Key Entry Points

| File                                          | Purpose                                      |
|-----------------------------------------------|----------------------------------------------|
| `AiAgentFunction.java`                        | Connector (Task) entry point                 |
| `AiAgentJobWorker.java`                       | Job worker (Sub-process) entry point         |
| `BaseAgentRequestHandler.java`                | Core orchestrator (shared by both flavors)   |
| `AgenticAiConnectorsAutoConfiguration.java`   | Spring Boot wiring                           |

For the full code path reference, see [ai-agent.md §18](docs/reference/ai-agent.md#18-code-paths).

## Keeping Documentation Up to Date

When making code changes to this module, update the relevant documentation to reflect those changes:

- **This file (`AGENTS.md`)**: Update if the change affects high-level architecture, key concepts, build commands, or
  entry points.
- **`docs/reference/ai-agent.md`**: Update for changes to the core agent framework — orchestration, memory, tool
  resolution, converters, configuration, error handling.
- **`docs/reference/mcp.md`**: Update for changes to MCP client integration — data model, client lifecycle, transport,
  discovery, Spring configuration.
- **`docs/reference/a2a.md`**: Update for changes to A2A integration — data model, SDK client layer, async patterns,
  connectors, Spring configuration.

Documentation should stay accurate with the code. If a change adds, removes, or modifies classes, interfaces, data
model records, configuration properties, error codes, or behavioral contracts documented in the reference files, update
the corresponding sections.
