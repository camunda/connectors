# Breaking Changes

This document tracks breaking changes relevant to [AI Agent customization](https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent-customization/).
The public documentation links here instead of repeating it. It targets developers who extend the AI Agent
connector in a Self-Managed or hybrid runtime (custom beans, SPI implementations, or code that depends
on the module's classes).

The agentic AI ecosystem evolves quickly. The APIs used for these customizations can change between
minor releases.

This list is curated: it covers the obvious extension points (SPIs and replaceable beans) and the
most common code changes, not every moved class or changed signature. Compile your extension against
the new version to find the rest.

## Camunda 8.10 to 8.11

Summary of what changes for a custom extension. Details follow in the linked sections.

| Area                            | Affects                                                                 |
| ------------------------------- | ----------------------------------------------------------------------- |
| [Module split](#module-split)   | Every extension: Maven dependencies, some imports, memory and custom provider configuration, conversation context subtypes, auto-configuration |
| [MCP gateway tool property](#mcp-gateway-tool-property) | Runtimes that disable the MCP Client connectors as AI Agent tools |
| [Server URL validation](#server-url-validation) | MCP and A2A server URLs that are not HTTP or HTTPS URLs |
| [Turn input composer and response handler](#turn-input-composer-and-response-handler) | Custom `AgentConversationTurnInputComposer` and `AgentResponseHandler` |
| [Gateway tool handlers](#gateway-tool-handlers)              | Custom `GatewayToolHandler` that overrides `extractDocuments` |
| [Agent instance client and mappers](#agent-instance-client-and-mappers)      | Custom `AgentInstanceClient`, `AgentInstanceHistoryMapper`, `AgentInstanceToolMapper` |
| [Replaceable bean constructors](#replaceable-bean-constructors)              | Code that constructs the request handlers |
| [Conversation window](#conversation-window)                                  | Code that calls `AgentConversation.window`                      |
| [Runtime tools and tool names](#runtime-tools-and-tool-names)                | Processes with a tool element named like a runtime tool        |
| [Model classes](#model-classes)                                              | Code that constructs or pattern-matches the model records (`AgentContext`, `ToolCall`), switches over `Content` |
| [Persisted data](#persisted-data-8-11)                                       | Custom `ConversationStore` implementations, code reading stored messages, the agent context, stored tool calls or the `CONFIGURATION` items of the agent instance history, rollbacks |

### Module split

The agentic AI connectors are split into several Maven modules, and each module owns its Java
packages ([ADR 015](adr/015-split-agentic-ai-into-modules.md)). The split keeps job types, element
templates, configuration properties and persisted data.

#### Artifacts

The `io.camunda.connector:connector-agentic-ai` artifact is replaced by the following artifacts in the
new group `io.camunda.connector.agenticai`:

| Artifact | Package root | Contents |
| ------------------------------- | --- | ------------------------------------------------------------------------- |
| `connector-aiagent` | `aiagent` (except `model`, `api`) | AI Agent connectors, chat model providers, conversation stores |
| `aiagent-api` | `aiagent.api` | Extension points: gateway tools, chat model, conversation storage, system prompt contributors |
| `aiagent-model` | `aiagent.model` | Agent model used by the extension points |
| `aiagent-tools-schema` | `tools.schema` | Ad-hoc tools schema resolution |
| `connector-ad-hoc-tools-schema` | `adhoctoolsschema` | Ad-hoc tools schema connector |
| `connector-mcp` | `mcp.client` | MCP client connectors |
| `aiagent-mcp-integration` | `mcp.integration` | MCP client as AI Agent gateway tool |
| `connector-a2a` | `a2a.client` | A2A client connectors |
| `aiagent-a2a-integration` | `a2a.integration` | A2A client as AI Agent gateway tool |
| `agentic-ai-common` | `common` | Shared helpers and HTTP support |
| `agentic-ai-bundle` | – | All of the above (`pom` packaging, depend on it with `<type>pom</type>`) |

An extension that replaces beans of the AI Agent connector depends on `connector-aiagent`, which
brings the API, model, tools schema and common libraries. It does not bring the Ad-hoc tools schema
connector. Add `connector-ad-hoc-tools-schema`, or use the bundle, when processes use that connector. An extension that only implements an
extension point can depend on `aiagent-api` alone. The MCP and A2A modules are separate
dependencies. A custom runtime that runs all agentic AI connectors, like the previous
`connector-agentic-ai` dependency did, depends on the bundle:

```xml
<dependency>
  <groupId>io.camunda.connector.agenticai</groupId>
  <artifactId>agentic-ai-bundle</artifactId>
  <version>${version.connectors}</version>
  <type>pom</type>
</dependency>
```

#### Moved and renamed classes

| 8.10 (`io.camunda.connector.agenticai`)                                      | 8.11 (`io.camunda.connector.agenticai`)        |
| ---------------------------------------------------------------------------- | ---------------------------------------------- |
| `adhoctoolsschema.schema.AdHocToolsSchemaResolver(Impl)`, `AdHocToolSchemaGenerator(Impl)`, `AdHocToolSchemaGenerationException` | `tools.schema.*` |
| `adhoctoolsschema.processdefinition.*`                                       | `tools.schema.processdefinition.*`             |
| `adhoctoolsschema.model.AdHocToolsSchemaRequest`, `AdHocToolsSchemaResponse` | `tools.schema.model.*`                         |
| `a2a.client.agentic.tool.*`                                                  | `a2a.integration.*`                            |
| `a2a.client.agentic.tool.configuration.A2aClientAgenticToolConfiguration`    | `a2a.integration.configuration.A2aClientAgenticToolAutoConfiguration` |
| `mcp.discovery.*`                                                            | `mcp.integration.*`                            |
| `mcp.discovery.configuration.McpDiscoveryConfiguration`                      | `mcp.integration.configuration.McpClientAgenticToolAutoConfiguration` |
| `aiagent.tool`: `GatewayToolHandler`, `GatewayToolCallTransformer`, `GatewayToolDefinitionUpdates`, `GatewayToolDiscoveryInitiationResult`, `GatewayToolDiscoveryResult`; `adhoctoolsschema.schema`: `GatewayToolDefinitionResolver`, `TypePropertyBasedGatewayToolDefinitionResolver` | `aiagent.api.tool.*` |
| `aiagent.chatmodel`: `ChatModel`, `ChatModelFactory`, `ChatRequest`, `ChatResult`, `ChatModelRejectedException`, `ContentFilteredException`, `ContextWindowExceededException`, `GuardrailInterventionException` | `aiagent.api.chatmodel.*` |
| `aiagent.memory.conversation`: `ConversationStore`, `ConversationSession`, `ConversationLoadResult`, `ConversationStoreRequest`; `aiagent.memory`: `ConversationSnapshot` | `aiagent.api.conversation.*` |
| `aiagent.systemprompt`: `SystemPromptContributor`                            | `aiagent.api.systemprompt.*`                   |
| `aiagent.agent`: `ContentTreeDocumentWalker`                                 | `aiagent.api.document.*`                       |
| `aiagent.chatmodel`: `ChatModelConfiguration`                                | `aiagent.model.chatmodel.*`                    |
| `aiagent.memory.conversation`: `ConversationContext`, `ConversationContextTypeResolverBuilder` | `aiagent.model.conversation.*` |
| `adhoctoolsschema.model`: `AdHocToolElement`, `AdHocToolElementParameter`    | `aiagent.model.tool.*`                         |
| `aiagent.model.request`: `AgentSubProcessRequestData`, `AgentSubProcessResponseConfiguration`, `AgentSubProcessV1Request`, `AgentSubProcessV2Request`, `AgentTaskRequestData`, `AgentTaskResponseConfiguration`, `AgentTaskV1Request`, `AgentTaskV2Request`, `MemoryStorageTemplateConfiguration`, `MemoryTemplateConfiguration`, `ToolsConfiguration`, `V1ToV2ProviderConfigurationMapper(Impl)` | `aiagent.request.*` |
| `aiagent.model.request.v1.*`                                                 | `aiagent.request.v1.*`                         |
| `aiagent.model.request.v1.shared.TimeoutConfiguration`                       | `aiagent.request.shared.TimeoutConfiguration`  |
| `aiagent.model.request.v1.shared.HttpUrl`                                    | `common.validation.HttpUrl`                    |
| `aiagent.model.request.v2.*`                                                 | `aiagent.request.v2.*`                         |
| `aiagent.model`: `AgentConversation`, `AgentConversationTurn`, `TurnReconstructor`, `PreviousConversation`, `AgentInput` | `aiagent.conversation.*` |
| `aiagent.model`: `AgentResponse`, `AgentSubProcessResponse`, `AgentTaskExecutionContext`, `AgentSubProcessExecutionContext` | `aiagent.agent.*` |
| `aiagent.model.document.DocumentHandle`, `aiagent.model.message.DocumentReferenceXmlTag` | `aiagent.document.*`               |
| `aiagent.model.tool.ToolCallProcessVariable`                                 | `aiagent.tool.ToolCallProcessVariable`         |
| `aiagent.model.versioning.*`                                                 | `aiagent.memory.versioning.*`                  |
| `aiagent.util.JacksonExceptionMessageExtractor`                              | `common.json.JacksonExceptionMessageExtractor` |
| `autoconfigure`: `AgenticAiConnectorsAutoConfiguration`, `AgenticAiConnectorsConfigurationProperties` | `aiagent.configuration.*` |
| `mcp.McpClientErrorCodes`                                                    | `mcp.client.McpClientErrorCodes`               |
| `AgenticAiConnectorsConfigurationProperties.RetriesProperties`               | `common.retry.RetriesProperties`               |
| `AgenticAiConnectorsConfigurationProperties.HttpProperties`                  | `common.configuration.AgenticAiHttpProperties` |
| `AgenticAiConnectorsConfigurationProperties.ToolsProperties`                 | `tools.schema.configuration.ToolsSchemaProperties` |
| `common.AgenticAiRecord`                                                     | `common.annotation.AgenticAiRecord`            |
| `common.AgenticAiHttpProxySupport`                                           | `common.http.AgenticAiHttpProxySupport`        |
| `common.JsonSchemaConstants`                                                 | `common.jsonschema.JsonSchemaConstants`        |
| `common.util`: `ObjectMapperConstants`, `FeelOffsetDateTimeParser`, `FeelOffsetDateTimeSerializer`, `FeelOffsetDateTimeDeserializer` | `common.json.*`                                |
| `common.util.retry.*`                                                        | `common.retry.*`                               |
| `common.util.BpmnUtils`                                                      | `tools.schema.processdefinition.BpmnUtils`     |
| `a2a.client.common.convert.A2aPartToContentConverter(Impl)`                  | `a2a.client.common.convert.A2aPartConverter(Impl)` |
| `common.AgenticAiHttpProxySupport` AWS SDK methods (`createAwsHttpClientBuilder`, `createAwsAsyncHttpClientBuilder`) | `aiagent.proxy.aws.AwsSdkHttpProxySupport` (`httpClientBuilder`, `asyncHttpClientBuilder`), a separate bean |
| `common.AgenticAiHttpProxySupport.azureProxyOptions`                         | `aiagent.proxy.azure.AzureHttpProxySupport.proxyOptions`, a separate bean |
| `common.AgenticAiHttpProxySupport.defaultGoogleGenAiBaseUrl`                 | Google GenAI base URL helper of the Gemini provider (`aiagent.chatmodel.provider.gemini`) |
| `aiagent.agentinstance.AgentInstanceKey`                                     | `aiagent.model.AgentInstanceKey`               |

`AgenticAiConnectorsConfigurationProperties` keeps only the `aiagent` properties.

`AgentMetadata.agentInstanceKey` is an `AgentInstanceKey` (still persisted as a plain number) instead of a
`Long`. `AgentInstanceKey.from(AgentMetadata)` is removed: read `metadata.agentInstanceKey()`.

A2A messages and artifacts (`A2aMessage.contents()`, `A2aArtifact.contents()`) hold `A2aPart`
records (`A2aTextPart`, `A2aDataPart`, `A2aDocumentPart` in `a2a.client.common.model.part`) instead of the AI Agent message content
types. The serialized JSON is unchanged.

#### Memory and custom provider configuration

- `aiagent.model.request.MemoryConfiguration` is an interface. The element template record is
  `MemoryTemplateConfiguration`.
- `aiagent.model.request.MemoryStorageConfiguration` is renamed to
  `MemoryStorageTemplateConfiguration`. Its nested records keep their names.
- New interfaces `ConversationStoreConfiguration` and `CustomConversationStoreConfiguration` in
  `aiagent.model.request`. `MemoryConfiguration.storage()` returns a `ConversationStoreConfiguration`.
  A custom store reads its parameters through `CustomConversationStoreConfiguration` instead of
  casting to the template record.
- New interface `aiagent.model.chatmodel.CustomChatModelConfiguration`, implemented by
  `CustomProviderConfiguration`. A custom `ChatModelFactory` can match and read its configuration
  through the interface.

#### Conversation context subtypes

`ConversationContext` subtypes are discovered with `java.util.ServiceLoader`. Annotate the custom
context with `@JsonTypeName`, list it in an implementation of
`aiagent.model.conversation.ConversationContextSubtypes`, and register that implementation in
`META-INF/services/io.camunda.connector.agenticai.aiagent.model.conversation.ConversationContextSubtypes`.
Registering the subtype on the runtime's `ObjectMapper` beans, as described for 8.10, still works.

#### Auto-configuration

`AgenticAiConnectorsAutoConfiguration` keeps only the AI Agent beans. The other beans moved to
auto-configurations of their own modules:

| Beans                                                        | Auto-configuration                         |
| ------------------------------------------------------------ | ------------------------------------------ |
| HTTP proxy support                                           | `AgenticAiCommonAutoConfiguration`         |
| Tools schema resolution, process definition lookup           | `AgenticAiToolsSchemaAutoConfiguration`    |
| Ad-hoc tools schema connector                                | `AdHocToolsSchemaConnectorAutoConfiguration` |
| MCP client connectors                                        | `McpClientAutoConfiguration`               |
| MCP gateway tool integration                                 | `McpClientAgenticToolAutoConfiguration`    |
| A2A client connectors                                        | `A2aClientAutoConfiguration`               |
| A2A gateway tool integration                                 | `A2aClientAgenticToolAutoConfiguration`    |

Bean names, conditions and property names are unchanged, except for the
[MCP gateway tool property](#mcp-gateway-tool-property). To exclude an auto-configuration by name,
for example with `spring.autoconfigure.exclude`, use the new class names.

The beans of the outbound connector functions of the MCP Client, MCP Remote Client, A2A Client and
ad-hoc tools schema connectors are prototype-scoped, like the AI Agent functions. A custom bean that
replaces one of them must be prototype-scoped too.

### MCP gateway tool property

The property that disables the MCP Client connectors as AI Agent tools follows the naming of the
A2A Client connector:

| 8.10                                                | 8.11                                                          |
| --------------------------------------------------- | ------------------------------------------------------------- |
| `camunda.connector.agenticai.mcp.discovery.enabled` | `camunda.connector.agenticai.mcp.client.agentic.tool.enabled` |

The old property is a deprecated alias and will be removed in a future release. Setting either
property to `false` disables the MCP Client connectors as AI Agent tools. The runtime logs a warning
at startup while the old property is set. Replace it with the new property.

### Server URL validation

The MCP and A2A server URLs must be HTTP or HTTPS URLs (`common.validation.HttpUrl`):

- The `url` of the MCP Remote Client connector (Streamable HTTP and SSE).
- The `http.url` and `sse.url` of MCP clients configured in the runtime properties
  (`camunda.connector.agenticai.mcp.client.clients`).
- The A2A server URL of the A2A Client connectors.

A URL without the `http://` or `https://` scheme fails the validation. A runtime with such an MCP
client in its properties does not start.

### Turn input composer and response handler

`AgentConversationTurnInputComposer`:

- `compose(configuration, agentContext, previousConversation, agentInput)` is replaced by two steps. `resolve` with the
  same parameters resolves only the results of the process tool calls of the previous turn and returns a
  `CompositionResult`. `assemble(CompositionResult.Ready)` builds the input of the next turn and returns a `TurnInput`.
  Between the two, the request handler produces the tool call results of the local tool calls (calls of runtime tools and
  rejected calls) and adds them to the `Ready`. The composer executes no tool.
- `CompositionResult` has the variants `Deferred`, `NoInput` and `Ready`. `NextTurn` is removed; its messages are in
  `TurnInput`, with the documents that enter the conversation with it, which the request handler registers. `Ready` holds the agent context, the ids of the documents whose content the model reads, the tool calls of the previous turn and their results as a `ToolCallBatch`, the user prompt
  of a turn without tool call results, and the event messages. `Deferred.arrivedResults` holds only the results that
  arrived from the process.
- The constructor of `AgentConversationTurnInputComposerImpl` takes a `ToolCallResultsInputBuilder` and an
  `ObjectHandleRenderer`, beans of the auto-configuration.

`AgentResponseHandler.createResponse(AgentConversation)` keeps its signature. The tool calls of the response must be the
process tool calls of the last turn (`AgentConversationTurn.processToolCalls()`): the agent produces the tool call
results of the other calls (`AgentConversationTurn.localToolCalls()`) itself and never hands them to the process. The
response carries the Camunda document references in place of the document handles in the arguments of these calls
(`DocumentParameterResolver.resolveArguments` with the tool definition of the agent context), and the stored assistant message keeps the arguments as the model sent
them. The constructor of `AgentResponseHandlerImpl` takes the `DocumentParameterResolver`, a bean of the
auto-configuration.

`ToolCallResultsInputBuilder` builds the tool call result message and the messages that follow it from the results of a
`ToolCallBatch`, for the composer. It reports the documents of the results for registration and applies the effects of
the results of runtime tools (the content of a known document, see "Runtime tools and tool names").

Migration: extend `AgentConversationTurnInputComposerImpl` and `AgentResponseHandlerImpl` and override only what you
change. If you implement the composer interface directly, replace `compose` with `resolve` and `assemble`. If you
implement the response handler interface directly, hand only the process tool calls to the process and resolve their document parameters.

### Gateway tool handlers

`GatewayToolHandler.extractDocuments(ToolCallResult)` is called only for agent instances without the `documents` feature.
An instance with the feature (every instance created from 8.11 on) finds the documents of a result the way it renders
them as handles for the model, at any depth and also inside typed records, whatever handler manages the result.

Migration: none for the handler of an instance without the feature. A handler whose typed content holds a
`Document` needs no extra code for an instance with the feature, as long as Jackson can serialize the content.

### Agent instance client and mappers

- `AgentInstanceClient.applyTurnStart` and `applyToolCallResults` take the `HistoryDocuments` of the job right after the
  agent instance key. Build it with `HistoryDocuments.of(executionContext, agentContext)`: it holds the document handling
  of the agent instance, which decides how the documents of the messages are shown, and the document factory of the job.
  The agent documents of a `Tracked` instance are what document handles are expanded with.
- `AgentInstanceClient.applyToolCallResults` takes `List<ToolCallResultContent>` instead of `List<ToolCallResult>`;
  convert with `ToolCallResultContent.from(result)`.
- `AgentInstanceToolMapper` and `AgentInstanceHistoryMapper` keep their API. A runtime tool, decided by
  `ToolDefinition.kind()`, and a local tool call and its result, decided by `ToolCall.isLocal()` (a runtime tool call or
  a rejected call), are reported without an element id.
- `AgentInstanceHistoryMapper`: the constructor takes an `ObjectHandleRenderer`, and `inputHistoryItems` takes the
  `HistoryDocuments` of the job.

Migration: if you implement or decorate `AgentInstanceClient.applyTurnStart` or `applyToolCallResults`, take the
`HistoryDocuments` and convert the tool call results as described above. Most
extensions should keep `CamundaAgentInstanceClient` and the mappers and decorate them instead.

### Replaceable bean constructors

The constructors of `AgentTaskRequestHandler` and `AgentSubProcessRequestHandler` (and `BaseAgentRequestHandler`) take
`RuntimeTools`, `ToolCallAdmission`, `LocalToolCallExecutor` and `DocumentExpander`. The constructor of
`AgentConversationTurnInputComposerImpl` takes a `ToolCallResultsInputBuilder` and an `ObjectHandleRenderer`, and the
`ToolCallResultsInputBuilder` takes the `DocumentContentDelivery`, which decides whether the content of a document is added and takes the
`DocumentContentGuard`, the check of the documents whose content the agent adds, and the `DocumentRestorer` that restores a
document from its reference. `RuntimeTools` also takes the SDK `DocumentFactory`, with which a runtime
tool restores the documents it reads. The size limits of the guard are set by the properties
`camunda.connector.agenticai.aiagent.document.content-limits.*` (see §16 of the [reference](reference/ai-agent.md)). The
auto-configuration provides these beans and the built-in runtime tool, each `@ConditionalOnMissingBean`. A custom bean
built with `new` needs the extra arguments; inject them as beans.

### Conversation window

`AgentConversation.window(int size)` keeps its signature. The request handler hands the snapshot to
`DocumentExpander.expand(snapshot, documentHandling, documentFactory)` before it calls the model, with the document
factory of the job.
`AgentConversation.nextRuntimeToolRound` takes a `UnaryOperator<AgentContext>` as a second parameter. The conversation
applies it to its persisted context, and the next round continues with the context that it returns.

### Runtime tools and tool names

- Agent instances created from 8.11 on have the `runtimeTools` and the `documents` feature and are offered the built-in
  runtime tool `c8__load_document`, which the agent runs itself. An agent job fails with the error code
  `RUNTIME_TOOL_NAME_CONFLICT` when a tool of the process has the name of an enabled runtime tool; a runtime tool that is
  not enabled reserves no name. Give the runtime tools of your own runtime a name with a prefix of your own. The prefix
  `c8__` is for the tools the connector ships; no prefix is checked or reserved, only the exact name. Agent instances
  created before keep their behavior: they are offered no runtime tool and no name is checked.
- Behavior change for every agent instance, with or without the `runtimeTools` feature, in both flavors: every tool call
  is matched against the tools offered in the job. A call of a tool name that is not offered, for example a name the model
  invented, no longer reaches the process. The agent rejects it once, when the model response arrives, and adds the tool
  call result `The tool '<name>' does not exist.` in the same job (when the message has no process call) and calls the
  model again; the process activates no element for it. Before, an agent instance without the feature handed such a
  call to the process, which never returned a result for it, and the instance waited for a result forever, without an
  incident.
  This is a deliberate exception to gating a change of the persisted shape on the feature.
- To add a tool of your own, implement `RuntimeTool` (`definition`, `isEnabled(RuntimeToolContext)`,
  `call(RuntimeToolContext, RuntimeToolCall)`) and expose it as a Spring bean (see
  [Add a runtime tool](architecture/extension-points.md#add-a-runtime-tool)). `RuntimeToolContext.documents()` gives the documents the agent knows when the
  batch of calls starts; a tool can return documents in its result, which the agent registers after all calls of the
  message ran. Use a vendor-specific name prefix. `RuntimeToolResult` is sealed (`Success` and `Error`); a `Success` can
  ask the agent to add the content of a known document to the request that follows the tool results
  (`withDocumentContent(AgentDocumentId)` or `withDocumentContent(Document)`; they add a variant of the sealed
  `ToolCallEffect`, listed by `Success.effects()`), which the agent applies after all calls of the message ran, in call
  order, and after its content guard accepted the document; a document the guard rejects stays a reference and a note
  follows the tool results. An error result has no effects. A reference block with the delivery `content` in a result, and
  a reference block or an effect that names a document neither known to the agent nor returned by the result, fail the job.
  `RuntimeToolDocuments` returns the `AgentDocument` of `aiagent-model`, restores the live document with
  `document(AgentDocumentId)` and tells with `hasContentInConversation(AgentDocumentId)` whether the model reads the content
  of a document. The runtime tool SPI is marked `@Experimental` in 8.11 and may change in a minor release. A
  rejected call gets an error result from the agent, and a call of a runtime tool that is not enabled when it is
  executed gets an error result from `RuntimeTools`; neither goes to the process.
- The tool list of the `CONFIGURATION` agent instance history item holds the process tools and the enabled runtime
  tools.

### Model classes

- `AgentExecutionContext` has the method `documentFactory()`: the document factory of the job, which resolves and
  creates documents in the document store of the physical tenant of the job. The constructors of
  `AgentSubProcessExecutionContext` and `AgentTaskExecutionContext` take it after the `JobContext`. Migration: a custom
  implementation returns the `OutboundConnectorContext` of the job, which is a `DocumentFactory`.
- New record components, so a canonical constructor call or a record deconstruction pattern no longer compiles. Use the
  builder, or for `ToolCall` the constructors with three or four arguments and for `ToolDefinition` the constructor with
  three arguments: `AgentContext` (`documents`, the `AgentDocuments`, and `features`, a list), `ToolCall` (`kind`, a `ToolKind`: `PROCESS` or `RUNTIME`, `PROCESS`
  by default, and `rejection`, an optional error text, none by default; the canonical constructor has the six components
  `id`, `name`, `arguments`, `metadata`, `kind` and `rejection`) and `ToolDefinition` (`kind`, `PROCESS` by default; a
  runtime tool declares `RUNTIME`, for example with `ToolDefinition.runtime(...)`). The agent sets the kind and the
  rejection of the tool calls of an assistant message once, when it receives the message. `ToolCall.resultSource()`
  (`PROCESS`, `RUNTIME` or `REJECTED`) and `ToolCall.isLocal()` derive who produces the tool call result; neither is
  written to JSON.
- The sealed `Content` interface permits `AgentDocumentContent` (content type `agent_document`). A `switch` over
  `Content` must handle it. A custom chat model provider never receives it: the agent expands it before the request is
  built.
- For an agent instance with the `documents` feature, documents reach a chat model provider differently. A user prompt
  document goes out as content, as before. A document in a tool result is text, its handle, instead of a `DocumentContent` or
  the reference JSON, and the content of a document follows in a user message only when the model loaded it with
  `c8__load_document`. A custom `ChatModel` that handles `DocumentContent` in tool results
  therefore sees text for these instances; instances without the feature keep the earlier form.
- New in the model: `AgentDocumentId`, the value type of a document id (persisted as a plain string), used by the agent
  documents and `AgentDocumentContent`. The id is `doc_` followed by 12 lowercase hex characters derived from the
  identity of the document (the store and the id in the store of a Camunda document), so it is no longer the id of the
  document in its store.
  `AgentDocumentContent` holds the `AgentDocumentId`, the `delivery` and the content `metadata`; the file name, content
  type and size the model reads are those of the agent document of the id, and the `AgentDocumentHandle` (JSON, see below) is built from it.
  `DocumentContent` stays the content of a new document;
  `AgentDocumentContent` is the persisted form of a document the agent knows.
- New in the model: `aiagent.model.document.AgentDocumentHandle`, the JSON object the model reads for a document of an agent
  instance with the `documents` feature, `{"type":"camunda.agenticai.document","id":"doc_…","fileName":…,"contentType":…,"size":…}`
  (the keys in this order, absent values omitted, no origin). It replaces the XML tag `<doc id="…" … />` for these
  instances; instances without the feature keep the XML tag. A custom `ChatModel` sees the handle as the text of a
  `TextContent`.
- Removed: `aiagent.document.DocumentHandle` (see the [module split](#module-split)), whose `idFor` derived the id.
  Derive the id of a document with `AgentDocumentId.of(Document)` or `AgentDocumentId.of(DocumentReference)`; the id has
  the format described above.
- New in the libraries: the persisted document state (`AgentDocuments`, `AgentDocument`, the sealed `AgentDocumentOrigin`,
  persisted with the discriminator `type`: `user_message`, `event` or `tool_call`, and the sealed `DocumentHandling` of an
  agent instance, `Untracked` or `Tracked`) is in `aiagent.model.document` of `aiagent-model`, which depends on the
  `jackson-datatype-document` library of the connector SDK for the document reference, and the runtime tool SPI (`RuntimeTool`, `RuntimeToolCall`,
  `RuntimeToolContext`, `RuntimeToolDocuments`, `RuntimeToolResult`, `RuntimeToolException`) are in `aiagent.api.tool` of `aiagent-api`. The
  four-argument `RuntimeTools.execute` takes the ids of the documents whose content the model reads as of the start of the
  batch before the calls, and a `RuntimeToolExecution` carries the `RuntimeToolResult` the tool returned (`result()`); the
  tool call result that becomes part of the conversation is `toolCallResult()`.

<a id="persisted-data-8-11"></a>

### Persisted data

- The configuration fingerprint, the id of the `CONFIGURATION` agent instance history item, is computed differently: it is a
  SHA-256 over a JSON serialization of the configuration. Every running agent instance, also one created before 8.11, writes
  one additional `CONFIGURATION` history item on its first job after the upgrade.
- `AgentContext` gains `documents` (the agent documents), and the conversation of an agent instance with the `documents`
  feature can contain `agent_document` content blocks. A missing field binds to its empty value, so state written by 8.10
  loads unchanged.
- A `ToolCall` of a stored assistant message carries `kind` and `rejection`, also in an agent instance created before 8.11
  and without the `runtimeTools` feature. `kind` is the kind of the tool the call names; a call of a tool name that is not
  offered, or of a tool of either kind whose document parameter is not the handle of a known document (only with the `documents`
  feature), gets a `rejection`, the error text that is its tool call result; a call of a name that is not offered keeps the kind `PROCESS`, any
  other rejected call keeps the kind of its tool. The JSON holds `kind`
  only for a call of a runtime tool and `rejection` only when set, and a missing value reads as `PROCESS` and as none, so
  state written by 8.10 loads unchanged. The agent never computes them again, so a custom `ConversationStore` must keep
  them: a store that drops them turns a runtime tool call or a rejected call into a process tool call that never gets a
  result. Serializing the `ToolCall` record with Jackson keeps them.
- A stored message can hold an `AgentDocumentContent`, which a custom `ConversationStore` must keep like any other
  content block (serializing `Content` with Jackson keeps it). What a block stands for is in the agent documents of the agent
  context, not in the store, so code that rewrites the agent context must keep `documents`.
- The assistant message in the `responseMessage` of an `AgentResponse` (`includeAssistantMessage`) also carries `kind`
  for a runtime tool call and `rejection` for a rejected call.
- Do not rely on the JSON shape of these fields in external systems.

## Camunda 8.9 to 8.10

Summary of what changes for a custom extension. Details follow in the linked sections.

| Area                                                                              | Affects                                            |
| --------------------------------------------------------------------------------- | -------------------------------------------------- |
| [Conversation storage SPI](#conversation-storage-spi-redesign)                    | Custom `ConversationStore` implementations         |
| [Chat model SPI](#chat-model-spi)                                                 | Custom model integrations (`AiFrameworkAdapter`, LangChain4j `ChatModelFactory`) |
| [Moved and removed classes](#moved-and-removed-classes)                           | Any code importing the module's model or utility classes |
| [Persisted data and message model](#persisted-data-and-message-model)             | Code reading stored messages, message metadata, or agent context |
| [Job types and element templates](#job-types-and-element-templates)               | Hybrid setups overriding job worker types          |

Application-level changes when upgrading the host project:

- The 8.10 connector runtime is built on Spring Boot 4.x. Set `version.connectors` to `8.10.0`.
- Register custom `ConversationContext` subtypes on the connector runtime's `ObjectMapper` beans
  (`connectorObjectMapper`, `outboundConnectorObjectMapper`). These mappers are built from
  `ConnectorsObjectMapperSupplier.getCopy()` with `defaultCandidate = false`, so Spring Boot's
  `Jackson2ObjectMapperBuilderCustomizer`/`JsonMapperBuilderCustomizer` beans do not reach them. A
  `BeanPostProcessor` calling `registerSubtypes(...)` on every `ObjectMapper` bean works. There is
  currently no dedicated extension point for this.
- To serialize messages (for example into a JSON column), use the runtime's `@ConnectorsObjectMapper`
  `ObjectMapper` instead of a custom one. It registers the Jackson modules for connector types such
  as document references.

### Conversation storage SPI redesign

**ADR**: [003-conversation-storage-spi-redesign](adr/003-conversation-storage-spi-redesign.md)

#### `ConversationStore`

- `executeInSession(AgentExecutionContext, AgentContext, ConversationSessionHandler<T>)` removed.
  Use `createSession(AgentExecutionContext, AgentContext)` instead — returns a `ConversationSession`
  that should be used via try-with-resources.
- `compensateFailedJobCompletion(AgentExecutionContext, AgentContext, Throwable)` removed (was
  deprecated and never invoked). Replaced by
  `onJobCompleted(AgentExecutionContext, AgentContext)` and
  `onJobCompletionFailed(AgentExecutionContext, AgentContext, JobCompletionFailure)` — both default
  no-ops. The execution context is provided so implementations can create temporary sessions for
  compensation if needed.

#### `ConversationSession`

- Now extends `AutoCloseable`. The default `close()` is a no-op; implementations managing
  external resources (e.g., AWS clients) should override it.
- `loadIntoRuntimeMemory(AgentContext, RuntimeMemory)` removed.
  Use `loadMessages(AgentContext)` instead — returns a `ConversationLoadResult`.
- `storeFromRuntimeMemory(AgentContext, RuntimeMemory)` removed.
  Use `storeMessages(AgentContext, ConversationStoreRequest)` instead — returns a
  `ConversationContext` (storage cursor). The caller assembles the full `AgentContext` via
  `agentContext.withConversation(returnedContext)`.

#### `ConversationSessionHandler`

- Deleted. No longer needed with the factory pattern.

#### New types

- `ConversationLoadResult` — wraps `List<Message>` returned by `loadMessages`.
- `ConversationStoreRequest` — wraps `List<Message>` passed to `storeMessages`.

#### Migration guide for custom implementations

1. Replace `executeInSession(ctx, agentCtx, handler)` with `createSession(ctx, agentCtx)` returning
   a `ConversationSession`.
2. In your session, replace `loadIntoRuntimeMemory` with `loadMessages` returning a
   `ConversationLoadResult`.
3. Replace `storeFromRuntimeMemory` with `storeMessages` accepting a `ConversationStoreRequest` and
   returning only the `ConversationContext`. Do not assemble the full `AgentContext` — the caller
   does that.
4. If your session manages external resources (connections, clients), override `close()`.
5. Remove any `ConversationSessionHandler` references.

### Chat model SPI

**ADRs**: [009-chat-model-provider-spi](adr/009-chat-model-provider-spi.md),
[014-route-v1-requests-through-native-providers](adr/014-route-v1-requests-through-native-providers.md)

The LangChain4j bound `AiFrameworkAdapter` is replaced by a module-owned SPI in
`io.camunda.connector.agenticai.aiagent.chatmodel` (`ChatModelFactory` and `ChatModel`). Native
providers (Anthropic, OpenAI including Microsoft Foundry, Google Gemini including Vertex AI, AWS
Bedrock Converse, Mistral) implement it and are the target for the new `v2` element templates.

The previous LangChain4j factories (`AiFrameworkAdapter`, `ChatModelFactory`, `ChatModelFactoryImpl`
and the rest of the `aiagent.framework.langchain4j` package) are removed. If you overrode or
extended them, adapt the logic to the new factories. Alternatively, register a `ChatModelFactory` bean for the **Custom Implementation** provider of the `v2` templates.
It can extend `LangChain4JChatModelFactory` to reuse an existing LangChain4j chat model. The
connector creates and closes one `ChatModel` per request, so register only the factory as a bean.
`ChatModelRegistry` fails when zero or more than one factory matches, so `supports` must only match
the configurations your factory owns.

By default, v1 provider configurations (legacy element templates) are rewritten to their v2
equivalent and run on the native providers
(`camunda.connector.agenticai.aiagent.rewrite-v1-provider-config-to-v2`, default `true`). The
switch is temporary and will be removed in a future release. The LangChain4j based factories
(`aiagent.chatmodel.provider.langchain4j`) are only used when the switch is `false` and are
deprecated.

### Moved and removed classes

All message, tool and context model classes moved from the shared `model` package into the `aiagent`
package. Update the imports (the artifact `connector-agentic-ai` is unchanged).

| 8.9 (`io.camunda.connector.agenticai`)  | 8.10 (`io.camunda.connector.agenticai`)    |
| --------------------------------------- | ------------------------------------------ |
| `model.message.*`                       | `aiagent.model.message.*`                  |
| `model.message.content.*`               | `aiagent.model.message.content.*`          |
| `model.tool.*`                          | `aiagent.model.tool.*`                     |
| `model.AgenticAiRecord`                 | `common.AgenticAiRecord`                   |
| `JsonSchemaConstants`                   | `common.JsonSchemaConstants`               |
| `util.BpmnUtils`, `util.CollectionUtils`, `util.ObjectMapperConstants` | `common.util.*`  |
| `util.ConnectorUtils`, `util.JacksonExceptionMessageExtractor`, `util.ResponseTextUtil` | `aiagent.util.*` |
| `aiagent.model.request.provider.*`      | `aiagent.model.request.v1.*`               |

Replaceable beans that no longer exist. Their logic moved into the request handlers
(`BaseAgentRequestHandler` and its task and sub-process subclasses) and there is no replacement
extension point. A custom bean of these types is no longer used:

- `AgentLimitsValidator` and `AgentLimitsValidatorImpl`
- `AgentMessagesHandler` and `AgentMessagesHandlerImpl`
- `JobWorkerAgentRequestHandler` and `OutboundConnectorAgentRequestHandler`, replaced by
  `AgentTaskRequestHandler` and `AgentSubProcessRequestHandler`

Removed with the job worker consolidation into the SDK
([ADR 002](adr/002-consolidate-job-worker-into-sdk.md)): the `aiagent.jobworker` package
(`AiAgentJobWorkerHandler(Impl)`, `AiAgentJobWorkerValueCustomizer`,
`JobWorkerAgentExecutionContextFactory(Impl)`, `JobWorkerAgentResult`) and the
`JobWorkerAgent*`/`OutboundConnectorAgent*` execution context and request classes in
`aiagent.model` and `aiagent.model.request`. The two AI Agent flavors are regular outbound connector
functions that return SDK `ConnectorResponse` types.

### Persisted data and message model

- `AgentContext` carries a `schemaVersion`. State written by 8.9 has no version and is upgraded on
  read. The write path always persists the current shape, so a conversation migrates on its next
  write. Do not rely on the JSON shape of `agentContext` or of stored messages in external systems.
- Rolling back to 8.9 after 8.10 wrote conversation state is not supported. An 8.9 runtime fails on
  state that carries a newer schema version, so existing agent jobs can fail. Plan the upgrade
  accordingly before the first 8.10 write.
- Every message has a stable, connector generated `id`
  ([ADR 012](adr/012-stable-self-generated-message-ids.md)). Assistant messages can carry a
  `StopReason`.
- Tool call results carry structured content (a list of content blocks) and an optional
  `completedAt` timestamp ([ADR 008](adr/008-tool-result-history-timestamps.md)). For the AI Agent
  Task, the tools sub-process must set `completedAt: now()` in the output element, otherwise the
  connector uses the time it processes the results.
- The content model gains `ReasoningContent` (opaque provider reasoning payload) and
  `ProviderContent` (a provider native block, preserved verbatim). Code that switches over `Content`
  must handle or ignore them.
- `AgentMetrics.TokenUsage` gains `cacheReadTokenCount`, `cacheCreationTokenCount` and
  `reasoningTokenCount` (omitted from JSON when zero). There is no combined total.
- Only the LangChain4j converter writes the `metadata.framework` entry (`tokenUsage`,
  `finishReason`, and others) that 8.9 stamped on every assistant message. Since v1 requests run on
  native providers by default in 8.10, assistant messages carry provider specific metadata instead (for example OpenAI's `responseId` and `stopReason`). Token
  usage is reported in the agent metrics, not per message.
- Documents inside messages (user prompts, tool results) are serialized as document references. Code
  that persists messages must use an `ObjectMapper` with the connector document modules, such as the
  runtime's `@ConnectorsObjectMapper` mapper.

### Job types and element templates

- The new `v2` element templates run on new job types: `io.camunda.agenticai:aiagent:task:2` and
  `io.camunda.agenticai:aiagent:subprocess:2`. The legacy types stay registered for deployed `v1`
  templates.
- The environment variables `CONNECTOR_AI_AGENT_TASK_TYPE` (AI Agent Task) and
  `CONNECTOR_AI_AGENT_SUBPROCESS_TYPE` (AI Agent Sub-process) override the job type that the
  runtime registers its job worker for. This lets a custom runtime handle agent elements next to
  the default runtime: give it a distinct type and set the same type on the element, using the
  hybrid element template (see [hybrid mode](https://docs.camunda.io/docs/components/connectors/use-connectors-in-hybrid-mode/)). The legacy variables (`CONNECTOR_AI_AGENT_TYPE`,
  `CONNECTOR_AI_AGENT_JOB_WORKER_TYPE`) only affect the legacy job workers.
- Use only the `v2` hybrid element templates for new custom runtimes:
  [AI Agent Task](../connector-aiagent/element-templates/hybrid/agenticai-ai-agent-task.v2-hybrid.json) and
  [AI Agent Sub-process](../connector-aiagent/element-templates/hybrid/agenticai-ai-agent-subprocess.v2-hybrid.json).
