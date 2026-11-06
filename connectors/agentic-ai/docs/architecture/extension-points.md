# Gateway tools and extension points

## Gateway Tool Pattern

The Gateway Tool Pattern is the extensibility mechanism that allows the AI Agent to integrate with external tool providers that expose **multiple tools behind a single BPMN element**. MCP and A2A are both implementations of this pattern.

For MCP integration details, see [`mcp-gateway-tool.md`](../../aiagent-mcp-integration/docs/mcp-gateway-tool.md).
For A2A integration details, see [`a2a-gateway-tool.md`](../../aiagent-a2a-integration/docs/a2a-gateway-tool.md).

### Core Concept

A regular tool in the ad-hoc sub-process is a 1:1 mapping: one BPMN element = one tool the LLM can call. A **gateway tool** breaks this: one BPMN element = a **gateway** to many tools that are discovered at runtime.

For example, an MCP Client element "MyFilesystem" might expose `readFile`, `writeFile`, and `listDirectory`. The LLM sees three separate tools, but the BPMN only has one element.

### Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│ Tool Schema Resolution (AdHocToolsSchemaResolverImpl)                │
│                                                                      │
│  adHocSubProcessElements → for each element:                         │
│    1. Check GatewayToolDefinitionResolvers                           │
│       - McpClientGatewayToolDefinitionResolver (type = "mcpClient")  │
│       - A2aGatewayToolDefinitionResolver (type = "a2aClient")        │
│    2. If match → GatewayToolDefinition (not a regular tool)          │
│    3. If no match → ToolDefinition (regular tool with JSON schema)   │
│                                                                      │
│  Result: AdHocToolsSchemaResponse {                                  │
│    toolDefinitions: [...],          // regular tools                 │
│    gatewayToolDefinitions: [...]    // gateway tools needing discovery│
│  }                                                                   │
└──────────────────────────────────────────────────────────────────────┘
```

### Gateway Detection

Gateway elements are identified via BPMN **extension properties**. The element template sets:

```
extensionProperties = {
  @ExtensionProperty(
    name = "io.camunda.agenticai.gateway.type",   // GatewayToolConstants.GATEWAY_TYPE_EXTENSION
    value = "mcpClient",                            // or "a2aClient"
    condition = @PropertyCondition(property = "data.connectorMode.type", equals = "aiAgentTool"))
}
```

`TypePropertyBasedGatewayToolDefinitionResolver` checks each element's properties for a matching `io.camunda.agenticai.gateway.type` value. Both `McpClientGatewayToolDefinitionResolver` and `A2aGatewayToolDefinitionResolver` extend this base class.

### Gateway Tool Handler Interface

`GatewayToolHandler` is the core interface that each gateway type implements:

```java
public interface GatewayToolHandler extends GatewayToolCallTransformer {
    String type();                              // e.g., "mcpClient", "a2aClient"
    boolean isGatewayManaged(String toolName);  // tool name prefix check

    // Discovery lifecycle
    GatewayToolDiscoveryInitiationResult initiateToolDiscovery(agentContext, gatewayToolDefinitions);
    boolean allToolDiscoveryResultsPresent(agentContext, toolCallResults);
    boolean handlesToolDiscoveryResult(toolCallResult);
    List<ToolDefinition> handleToolDiscoveryResults(agentContext, toolCallResults);

    // Migration
    GatewayToolDefinitionUpdates resolveUpdatedGatewayToolDefinitions(agentContext, gatewayToolDefinitions);

    // Document extraction (default falls back to ContentTreeDocumentWalker for raw content trees)
    default List<Document> extractDocuments(ToolCallResult toolCallResult);
}

// From GatewayToolCallTransformer:
public interface GatewayToolCallTransformer {
    List<ToolCall> transformToolCalls(agentContext, toolCalls);             // LLM → process
    List<ToolCallResult> transformToolCallResults(agentContext, results);   // process → LLM
}
```

**Pitfall:** `transformToolCallResults` (MCP, A2A) rebuilds a new `ToolCallResult` via
`.builder()...build()`. Unlike `elementId`, `completedAt` has no fallback — copy it over explicitly
or the result fails the mapper's non-null `completedAt` invariant (ADR 008).

### Gateway Tool Handler Registry

`GatewayToolHandlerRegistryImpl` wraps multiple `GatewayToolHandler` instances and distributes operations:

- **Discovery initiation**: Iterates all handlers, collects discovery tool calls, merges agent context updates
- **Discovery result check**: All handlers must report all results present
- **Discovery result processing**: Groups results by handler type, merges discovered tool definitions into agent context
- **Tool call transformation**: Chains through all handlers (each transforms its own tool calls, passes others through)
- **Tool call result transformation**: Same chaining for results

### Discovery Flow (State Machine Integration)

The discovery flow integrates with the agent state machine from [`agent-state-machine.md`](../../connector-aiagent/docs/agent-state-machine.md):

```
INITIALIZING
  ├─ AdHocToolsSchemaResolver → identifies gateway elements
  ├─ GatewayToolHandlerRegistry.initiateToolDiscovery()
  │   ├─ MCP handler: creates ToolCall("MCP_toolsList_<elementId>", "<elementId>", {method: "tools/list"})
  │   └─ A2A handler: creates ToolCall("A2A_fetchAgentCard_<elementId>", "<elementId>", {operation: "fetchAgentCard"})
  │
  ├─ If discovery calls exist:
  │   └─ Return AgentResponseInitializationResult with tool calls
  │      State → TOOL_DISCOVERY
  │      Job completed with: activateElement for each discovery call
  │
  └─ If no discovery calls:
      └─ State → READY (proceed normally)

TOOL_DISCOVERY
  ├─ Tool results arrive (e.g., MCP listTools response, A2A agent card)
  ├─ GatewayToolHandlerRegistry.allToolDiscoveryResultsPresent()
  │   ├─ Not all present → AgentDiscoveryInProgressInitializationResult (no-op completion)
  │   └─ All present:
  │       ├─ GatewayToolHandlerRegistry.handleToolDiscoveryResults()
  │       │   ├─ MCP: each tool from listTools → ToolDefinition("MCP_<elementId>___<mcpToolName>")
  │       │   └─ A2A: agent card → single ToolDefinition("A2A_<elementId>")
  │       ├─ Merge into agentContext.toolDefinitions
  │       └─ State → READY
```

### Tool Call Name Transformation

The gateway pattern uses namespaced tool names to maintain uniqueness:

**LLM sees** (fully qualified): `MCP_MyFilesystem___readFile`
**Process sees** (element activation): `MyFilesystem` with variables `{toolCall: {method: "tools/call", params: {name: "readFile", arguments: {...}}}}`

This mapping happens in `transformToolCalls()` (LLM → process) and `transformToolCallResults()` (process → LLM).

### Agent Context Properties

Gateway handlers store per-handler state in `AgentContext.properties`:

- MCP: `properties.mcpClients = ["elementId1", "elementId2"]` — list of MCP client element IDs
- A2A: `properties.a2aClients = ["elementId1", "elementId2"]` — list of A2A client element IDs

These are used during discovery checking and tool call result transformation.

### Document Extraction from Tool Call Results

Tool call results may contain Camunda `Document` instances — at the root, nested in maps/lists, or
embedded inside typed gateway responses (e.g. `McpDocumentContent`, A2A artifacts). The agent
extracts those documents into a synthetic follow-up `UserMessage` with native `DocumentContent`
blocks so LLMs can interpret them; see [ADR-004](../adr/004-document-handling-in-tool-call-results.md).

`ToolCallResultDocumentExtractor` is the entrypoint, called from `AgentConversationTurnInputComposerImpl` after
the `ToolCallResultMessage` is built. For each result it asks the registry which handler manages
the tool name (`GatewayToolHandlerRegistry.handlerForToolDefinition`) and either:

- delegates to that handler's `extractDocuments(ToolCallResult)` — handlers walk their own typed
  content (sealed-type `switch` over `McpContent` / `A2aSendMessageResult`), so documents inside
  typed records remain discoverable;
- falls back to `ContentTreeDocumentWalker` — a stateless static utility that recursively walks
  `Map`, `Collection`, `Object[]` and `Document` nodes. Used for plain BPMN tools whose content is
  the raw FEEL tree from the engine.

The default `GatewayToolHandler.extractDocuments` implementation also delegates to
`ContentTreeDocumentWalker`, so third-party handlers that return raw maps work without overriding.
Handlers whose typed content embeds raw user-generated subtrees can call the walker directly on
those subtrees.

```
ToolCallResultDocumentExtractor.extractDocuments(results)
  ├─ for each result:
  │    GatewayToolHandlerRegistry.handlerForToolDefinition(result.name())
  │      ├─ Some(handler) → handler.extractDocuments(result)        ── typed walk
  │      └─ None          → ContentTreeDocumentWalker.extractDocumentsFromContent(content)
  └─ groups documents by ToolCallDocuments(toolCallId, toolCallName, documents)
```

## Extension Points

Where to start for each SPI from [I4](../design-guidelines.md#architectural-invariants). The pattern is always the
same: implement the interface, copy the nearest existing implementation, and register a Spring bean.
This section points at code rather than transcribing the steps, so it cannot drift; read the named
reference implementation and its wiring for the current exact procedure, and respect the invariants in
[Architectural invariants](../design-guidelines.md#architectural-invariants).

### Add an LLM provider

**The SPI.** Implement `ChatModelFactory` (`supports(ChatModelConfiguration)` /
`create(ChatModelConfiguration)`) and register it as a Spring bean; `ChatModelRegistryImpl`
auto-collects every `ChatModelFactory` bean and routes each request to the one whose `supports`
returns true ([`chat-model-spi.md`](../../aiagent-api/docs/chat-model-spi.md#the-chatmodel-provider-spi-adr-009)).

**v1: LangChain4j-backed.** Extend the abstract `LangChain4JChatModelFactory<T extends
ProviderConfiguration>` (v1's `ProviderConfiguration`) — supply `providerType()` and
`createChatModel(T)` plus a matching `ProviderConfiguration` subtype; `supports`/`create` are already
implemented by the base class. Reference: `AnthropicChatModelFactory`
(`aiagent/chatmodel/provider/langchain4j/factory`) with `AnthropicProviderConfiguration`. Only
`aiagent/chatmodel/provider/langchain4j/**` may import `dev.langchain4j.*` (invariant I1).

**v2: fully native.** Implement `ChatModel`/`ChatModelFactory` directly with your own
`ChatModelConfiguration`, staying out of the `langchain4j` package. Reference implementation:
`AnthropicChatModelFactory` (`aiagent/chatmodel/provider/anthropic`) with
`AnthropicChatModelConfiguration` (`request/v2`); the other native providers under
`aiagent/chatmodel/provider/**` follow the same shape. A v2 provider without a built-in factory falls
through to `CustomProviderConfiguration` (`request/v2`, discriminator `custom`, Self-Managed/Hybrid
only): it carries a user-chosen `providerType` (dispatch discriminator), a dedicated `model` field (so
agent-instance reporting works without digging it out of opaque config), and an opaque `parameters` map
understood only by your factory. `LangChain4JChatModelFactory` cannot be extended for it — it's bound
to v1's `ProviderConfiguration`, a different sealed hierarchy — but its building block,
`LangChain4JChatModel`, is a public, framework-agnostic `ChatModel` wrapper: build your own
`dev.langchain4j.model.chat.ChatModel`, wrap it in a `CloseableChatModel`, and construct a
`LangChain4JChatModel` directly if you want to reuse LangChain4j from a v2-native factory. No built-in
factory ever matches `CustomProviderConfiguration`, so the registry fails with the ordinary "no factory
registered" error until you register one.

Every new v2 provider backend must follow three conventions, regardless of provider:

- **Backend-subtype wrapping.** A backend is a sealed interface with one `@TemplateSubType` per
  variant, discriminated by a `type` property (see `AnthropicChatModelConfiguration.AnthropicBackend`).
  The template generator only validates property-id uniqueness across the whole template, not per
  discriminator subtype, so two subtypes with a same-named field (e.g. both an `apiKey`) collide even
  though only one is ever active. Fix: wrap each subtype's fields inside a single container field
  uniquely named for that subtype (e.g. `anthropic`, `custom`) — the generator derives the id-path
  prefix from the Java field name, making the subtree collision-proof with no per-leaf `id=` overrides.
  Extract each subtype's discriminator string into a `public static final String` constant on its own
  record, and reference it from the `@TemplateSubType` id, `@JsonSubTypes.Type` name, `type()`, and the
  discriminator's `defaultValue`.
- **Provider-namespaced metadata.** Provider-specific data a converter attaches outside a domain
  object's mapped fields — on a content block's `metadata` ([`data-model.md`](../../aiagent-model/docs/data-model.md#toolcallresultcontent-persisted-tool-call-result-content)) or on
  `AssistantMessage#metadata()` — must nest under a single provider key, never as top-level entries.
- **Stamping `AssistantMessage#metadata()`.** Build it through
  `AssistantMessageMetadata.withDefaults(providerMetadata)` (`aiagent/util`), passing only your own
  provider-namespaced entries (or `Map.of()`); it adds the common `timestamp` entry. Never call
  `AssistantMessage.builder().metadata(...)` with a raw map directly.

Provider-specific converter/configuration decisions live in [`native-providers.md`](../../connector-aiagent/docs/native-providers.md),
one section per provider, rather than here.

### Add a `SystemPromptContributor`

Implement `SystemPromptContributor` (see [`system-prompt-contributor.md`](../../aiagent-api/docs/system-prompt-contributor.md#systempromptcontributor-interface)).
`SystemPromptComposerImpl` auto-collects every contributor bean and orders them by `getOrder()`.
Reference implementation: `A2aSystemPromptContributor`.

### Add a `ConversationStore`

Implement `ConversationStore`, `ConversationSession`, and `ConversationContext`, following the
write-ahead, pointer-based storage contract in [`conversation-store-spi.md`](../../aiagent-api/docs/conversation-store-spi.md#storage-contract) (invariant I5).
`ConversationStoreRegistryImpl` selects the store by `type()`. Reference implementation: the in-process
store (`InProcessConversationStore` and its session/context). Context subtypes are discovered via
`ServiceLoader`: annotate the context with `@JsonTypeName` and list it in a `ConversationContextSubtypes`
implementation (see `BuiltInConversationContextSubtypes`). A broken provider entry is logged at ERROR
level and skipped; it never disables the other subtypes. For a custom store outside this module,
see [camunda-agentic-ai-customizations](https://github.com/maff/camunda-agentic-ai-customizations).
