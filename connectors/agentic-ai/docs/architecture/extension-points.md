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

    // Document extraction, called only for agent instances without the documents feature
    // (default falls back to ContentTreeDocumentWalker for raw content trees)
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

### Documents and Gateway Tools

A gateway handler that defines its tools itself can declare document parameters with
`DocumentParameter.documentSchema(...)` in the definitions it returns from `handleToolDiscoveryResults`; they are stored as
they are. `transformToolCalls()` receives the Camunda document references in place of the handles. A call with an invalid
document value gets an error result and never reaches the handler. The MCP and A2A handlers declare no document
parameters: the remote side defines their schemas.

### Document Extraction from Tool Call Results

Tool call results may contain Camunda `Document` instances — at the root, nested in maps/lists, or
embedded inside typed gateway responses (e.g. `McpDocumentContent`, A2A artifacts). The agent extracts those documents, and
what it does with them depends on the agent instance. For an instance with the `documents` feature the documents are
references in the conversation: the agent registers them in the agent documents of the `agentContext`, and the tool
result shows each document as its handle, a small JSON object, not its content. The agent registers exactly the documents
the model reads as handles: `ToolCallResultDocumentExtractor.renderedDocumentsOf` collects every document that
`ObjectHandleRenderer` replaces with its handle, at any depth and also inside a typed record, whatever gateway tool
handler manages the result. The same holds for the result of a runtime tool and for the content of an event, which
`ObjectHandleRenderer.documentsIn` searches. An instance without the feature keeps the
behavior of [ADR-004](../adr/004-document-handling-in-tool-call-results.md): the documents go into a synthetic follow-up
`UserMessage` with native `DocumentContent` blocks so LLMs can interpret them. Design rationale:
[ADR 018](../adr/018-document-flow.md), which extends [ADR 004](../adr/004-document-handling-in-tool-call-results.md).
The documents of the result of a runtime tool ([Add a runtime tool](#add-a-runtime-tool)), in a batch of local tool calls only or in a mixed one, are collected from its
content with the renderer for an instance with the feature (`LocalToolCallExecutor`) and with `ContentTreeDocumentWalker` for one without it, and are handled the same way. They are registered after all calls
of the message ran, in the order of the calls and together with the documents of the process results, by
`ToolCallResultsInputBuilder`, which the composer and the request handler's rounds of local tool call results share; the result of a
call that failed has none. The same place applies the effects of the results (below).

**The handle.** `{"type":"camunda.agenticai.document","id":"doc_0123456789ab","fileName":"a.pdf","contentType":"application/pdf","size":123}`,
the keys in the order `type`, `id`, `fileName`, `contentType`, `size`, written as compact JSON by Jackson from the
record `AgentDocumentHandle` (`aiagent.model.document`, `AgentDocumentHandle.TYPE` is the `type`). `fileName`, `contentType` and
`size` are omitted when null or blank; `size` is a JSON number. `AgentDocumentHandle.of(agentDocuments, id)` builds it from the agent document of
the id, and `ObjectHandleRenderer.text(handle)` and `tree(handle)` render it as text or as an object node, with the object
mapper of the connectors: the file name, content type and size are read from its reference, never by reading
the document, and an id the agent does not know is rendered as `{"type":"camunda.agenticai.document","id":"doc_…"}`.
`fileName` is the Camunda metadata file name; it is omitted when it
equals the Camunda document id of the reference (what a document stored without a name gets). `contentType` comes from
the Camunda metadata. `size` is the number of bytes, present for a Camunda document whose reference
metadata carries one. A store id, a content hash and the origin of the document are never part of the handle; the
message it sits in shows where the document came from. The model never needs to write `fileName`, `contentType` or `size`;
they are information for the model. In a user message the handle is a text block before the document, in a tool
result that is a document it is a text block, and a document nested in the object of a result is the handle object at the
place of the document.

The id is `doc_` followed by the first 12 lowercase hex characters of the SHA-256 of the identity key of the document
(`AgentDocumentId.of`). The identity is `camunda`, the store id and the document id. It is hashed as the concatenation of
its parts, each as a 4-byte big-endian length and its UTF-8 bytes. The content hash and the metadata are not part of it,
so the same document always gets the same id. An absent part is hashed as such, never as the text `null`. The id never
exposes a store id or the id of the document in its store.

**Loading content: `c8__load_document`.** The model calls it with the handle of a document (`id`, a document parameter).
Every agent instance with the `documents` feature offers it. It cannot be switched off. A switch is a follow-up of
[ADR 018](../adr/018-document-flow.md).

The tool returns no content. For a known document it returns `RuntimeToolResult.Success.withDocumentContent`, which carries
the effect `ToolCallEffect.AddDocumentContent`. A missing or unknown id gives an error result. The agent applies the effect,
not the tool: after all calls of the message ran, in call order, `ToolCallResultsInputBuilder` asks
`DocumentContentDelivery` to decide for each requested document. The builder then appends one sentence per document to the
result. `{ref}` is the file name and the id (`document1.pdf (doc_3a1d3166fe56)`), or the id alone without a file name.

| Decision | Sentence |
|----------|----------|
| the content is added | `The content of {ref} follows in the next message.` |
| the model has the content already, or an earlier result of the message asked for it | `The content of {ref} is already in the conversation.` |

The agent adds the content of a known document whatever its type and size. It does not check the content type or the size
against the limits of a model provider.

When at least one document is loaded, the builder adds a user message after the tool results. It starts with the preamble
`Document content from tool calls (added by the system):`. For each loaded document it holds the handle and the document.
Its metadata `toolCallDocuments` makes it leave the context window together with its tool results. A document parameter
(`fromAi(..., "document")`) never adds content.

**The user prompt.** The documents of the user prompt are references with the delivery `content`: the content of every
document goes out with the prompt, with no size limit and no type check, and a document the provider
cannot read fails the model call. Documents of events stay references, and so do those of the MCP and A2A gateway tools.
In an instance without the `documents` feature every document, also those of the user prompt, events and tool results,
is added as content.

**Only Camunda documents.** An instance with the `documents` feature accepts only Camunda documents
(`AgentDocument.isSupported`). A document with an external reference (a URL) or an inline reference (the content is in the
reference) fails the job with the error code `UNSUPPORTED_DOCUMENT_REFERENCE` ([Error Codes](../../connector-aiagent/docs/error-codes.md)) wherever it enters the conversation: in
the user prompt, in the result of a tool (at the root or nested at any depth in an object, also the result of a runtime
tool) and in an event. The agent checks on entry, before it registers or stores a document, so the agent documents, the
persisted messages, the expansion and the history never hold such a document. The message names the kind of the document
and the place, never a file name or a URL:

- `Only Camunda documents can be passed to the AI Agent, but the user prompt contains an external document.`
- `Only Camunda documents can be passed to the AI Agent, but the result of the tool 'getReport' contains an inline document.`
  (`the result of a tool` when the result has no tool name)
- `Only Camunda documents can be passed to the AI Agent, but an event contains an external document.`

A tool result and an event cannot change, so the incident repeats on each retry until the process passes a Camunda
document. The reason is in [ADR 018](../adr/018-document-flow.md).

**Instances without the `documents` feature.** An agent instance created before the feature (no `features` in its
`agentContext`) keeps the document handling of Camunda 8.10:

- A user prompt document is a bare `DocumentContent`. The documents of tool results and events go into a synthetic
  user message with a preamble (also for an external or an inline document), then per document the tag in the older attribute format
  (`DocumentReferenceXmlTag`: `toolName`, `toolCallId`, `documentId`, `storeId`, `contentType`, `fileName`, or `url`
  and `name` for an external document) and the `DocumentContent`, as in ADR-004. Nothing is registered.
- `documents` stays empty; the expander expands nothing, and a `Document` in a tool result renders as its reference JSON.
- Without the `runtimeTools` feature there are no runtime tools (`c8__load_document` included), no mixed-batch handling and no tool name check ([Add a runtime tool](#add-a-runtime-tool)).
- The document parameters of tool calls are not resolved or checked: the process gets the value the model sent as it is.
- A document parameter keeps the type `document` as authored; the tool schema is sent to the provider as it is. Only an
  instance with the feature gets the handle schema ([Document parameters](../../connector-aiagent/docs/document-parameters.md)).
- The history reports the messages as stored.

`LegacyDocumentConversationTest` pins this behavior with a conversation persisted by Camunda 8.10.

**Limits.** A document that expires in its store is not noticed by the agent. Documents named only in the content of an
evicted assistant message are not tracked, and handles the model writes in the final text are not interpreted. A
content type is not sniffed from the bytes for documents of unknown type. The follow-ups that close these limits are
listed in [ADR 018](../adr/018-document-flow.md).

Only for an instance without the `documents` feature, `ToolCallResultDocumentExtractor` is the entrypoint, called from
`AgentConversationTurnInputComposerImpl` for each process tool call result, before `ToolCallResultsInputBuilder` builds the `ToolCallResultMessage`. For each result it asks the registry which handler manages
the tool name (`GatewayToolHandlerRegistry.handlerForToolDefinition`) and either:

- delegates to that handler's `extractDocuments(ToolCallResult)` (called only for an instance without the `documents`
  feature; an instance with it finds the documents of a result the way it renders them as handles) — handlers walk their own typed
  content (sealed-type `switch` over `McpContent` / `A2aSendMessageResult`), so documents inside
  typed records remain discoverable;
- falls back to `ContentTreeDocumentWalker` (also only for an instance without the feature) — a stateless static utility that recursively walks
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

**Native providers (v2).** A provider is a `ChatModelFactory` bean plus a `ChatModel`. Implement both
with your own `ChatModelConfiguration` type from `request/v2`. Reference implementation:
`AnthropicChatModelFactory` (`aiagent/chatmodel/provider/anthropic`) with
`AnthropicChatModelConfiguration`; the other native providers under `aiagent/chatmodel/provider/**`
follow the same shape and are registered by `AgenticAiNativeProvidersConfiguration`. See
[`chat-model.md`](../../connector-aiagent/docs/chat-model.md) and
[`native-providers.md`](../../connector-aiagent/docs/native-providers.md).

**Custom providers.** A v2 provider without a built-in factory falls through to
`CustomProviderConfiguration` (`request/v2`, discriminator `custom`, Self-Managed/Hybrid only): it
carries a user-chosen `providerType` (dispatch discriminator), a dedicated `model` field (so
agent-instance reporting works without digging it out of opaque config), and an opaque `parameters` map
understood only by your factory. No built-in factory ever matches `CustomProviderConfiguration`, so the
registry fails with the ordinary "no factory registered" error until you register one.

**LangChain4j (v1 only).** The `LangChain4JChatModelFactory` subclasses serve the v1
`ProviderConfiguration` and are used only when
`camunda.connector.agenticai.aiagent.rewrite-v1-provider-config-to-v2` is `false`. They are not an
extension point for new providers, see
[`langchain4j-chat-model.md`](../../connector-aiagent/docs/langchain4j-chat-model.md). Only
`aiagent/chatmodel/provider/langchain4j/**` may import `dev.langchain4j.*` (invariant I1).

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

### Add a runtime tool

> **Experimental.** The runtime tool SPI (`RuntimeTool`, `RuntimeToolCall`, `RuntimeToolContext`,
> `RuntimeToolResult`, and `RuntimeToolException`, package `aiagent.api.tool` of
> `aiagent-api`) is experimental since 8.11 and may change in an incompatible way in a minor release.

A runtime tool is a tool the connector runtime executes itself, without activating a BPMN element: the model sees its
definition next to the tools of the process and the agent executes the call, in process. A built-in tool is a runtime tool
the connector ships. `LoadDocumentTool` (`c8__load_document`) is in `aiagent/tool/runtime/builtin` of `connector-aiagent`
and uses only this SPI. `BuiltInRuntimeToolsArchTest` limits its dependencies. `AgenticAiConnectorsAutoConfiguration`
wires it as the bean `aiAgentLoadDocumentRuntimeTool`. A bean with this bean name replaces it. A bean with another bean
name and the same tool name fails the startup, because tool names are unique. The rationale is in
[ADR 016](../adr/016-runtime-tools.md) and [ADR 018](../adr/018-document-flow.md). The contract of every type is in its
javadoc; `RuntimeTool` is the entry point, and `RuntimeToolTestConfiguration` in the e2e module of the AI Agent is a small
example.

**To add a tool**

1. Pick a name with a vendor prefix such as `acme_`; `c8__` is the prefix of the built-in tools. A tool of the process must
   not have the name while the tool is enabled: the agent job fails with `ERROR_CODE_RUNTIME_TOOL_NAME_CONFLICT`. No prefix
   is checked or reserved, only the exact name.
2. Build the definition with `ToolDefinition.runtime(...)` and `LinkedHashMap`s for the schema: the order of a map such
   as `Map.of` differs between JVMs, and the order of the parameters is part of the configuration fingerprint.
3. Implement `call`. Return `RuntimeToolResult.error(...)` or throw a `RuntimeToolException` for a failure the model can
   act on; any other exception fails the job. Keep no state in the tool: an agent runs one job after the other, possibly
   on different workers.
4. Override `isEnabled` if the tool is offered only to some agents. It reads the invocation and the agent context from the
   `RuntimeToolContext`, and is called when the tools of a job are assembled and again when a call is executed.
   Keep its answer stable while an agent instance runs: the tools are part of every model request, so a change
   invalidates the prompt cache of the provider and shows the model a different set of tools.
5. Register the tool as a Spring bean of type `RuntimeTool`. `RuntimeTools` rejects blank and duplicate names and
   definitions that do not declare the kind `RUNTIME` at startup. The `runtimeTools` feature only decides whether the tools are
   offered: an agent instance without it is offered no runtime tool.

**Execution contract**

- *At least once.* A retried job executes the call again, so make side effects outside the agent idempotent. The effects of
  an attempt that did not complete the job are discarded.
- *Bounded execution time.* A call is executed in the thread of the job, without a timeout: stay well below the job timeout.
- *Order and concurrency.* The results of a batch are applied in call order. `call` and `isEnabled` may be called from different
  threads, and a `RuntimeToolContext` is safe to read concurrently.
  The calls of one message see the same agent context, the one when the batch starts, and so the same documents.
- *Mixed batches.* Runtime calls of a message that also calls process tools are executed in the job that composes the next turn
  ([What Happens When Tools Complete](agentic-loop.md#what-happens-when-tools-complete)); a message that calls runtime tools only has them executed in the same job and calls the model again.
- *Failures.* A call of a tool that is not offered in the job is rejected and gets the error result "does not exist" from the
  agent; it never reaches the tool, also when a tool of that name is enabled later. A call of a tool that is no longer
  enabled when it is executed ("is not available"), a `RuntimeToolException` and `RuntimeToolResult.error` give an error
  result the model reads.

**Document parameters.** A parameter that carries a document is declared with `DocumentParameter.documentSchema(...)`. The agent
resolves it before the call: `RuntimeToolCall` holds the `AgentDocumentId` of the document, which `argumentsAs` binds to a
property typed `AgentDocumentId`. A call with a value that is not the handle of a known document is rejected at admission,
and the tool is not called. A tool does not look up the document to check it: the id it receives is the one of a known
document. `RuntimeTools` resolves the call again against the definition the tool is registered with, which can differ from
the one of the admission when the call runs in a later job; an invalid value gets a `RuntimeToolResult.error` with the
resolution message, and the tool is not called.

**Documents.** `RuntimeToolContext#document(AgentDocumentId)` returns the `Document` of the connector SDK for a document
the agent knows, restored with the document factory of the job, or nothing. Its content is read only when the tool reads
it. A result holds a document as the `Document` of the connector SDK, never as the `AgentDocument` of the agent context:
an `AgentDocument` carries the store details of the document, which never go to the model, and one in a result fails the
job. A result holds a new document as a `DocumentContent` block. After the calls
ran, the agent registers it with the tool call as origin, and the model reads its handle only
([Document Extraction from Tool Call Results](#document-extraction-from-tool-call-results)). A result holds a known
document as an `AgentDocumentContent` block with the delivery `reference`. An error result registers nothing.

**Effects.** `RuntimeToolResult.success(...)` returns a `Success`. `Success.withDocumentContent(AgentDocumentId)` adds the
effect `ToolCallEffect.AddDocumentContent` (`AgentDocumentId.of(document)` names a document the result returns). The agent
adds the content to the request after the tool results, once for each document. The agent writes the outcome
sentence into the result, so a tool does not. `LoadDocumentTool` is the reference.

Content comes only through the effect. These cases fail the job with an `IllegalArgumentException`: a reference block with the
delivery `content`, and a reference block or an effect with an id that the agent did not know when the batch started and the
result does not return.

The framework offers the definitions next to the process tools (they are part of the configuration fingerprint and never
of `AgentContext.toolDefinitions`), turns the content into a tool result, registers the documents it returns after the
calls ran, and reports the tool and its calls to agent instance history without an element id.
