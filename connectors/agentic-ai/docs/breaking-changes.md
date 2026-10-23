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
| [Agent instance client and mappers](#agent-instance-client-and-mappers)      | Custom `AgentInstanceClient`, `AgentInstanceHistoryMapper`, `AgentInstanceToolMapper` |
| [Replaceable bean constructors](#replaceable-bean-constructors)              | Code that constructs the request handlers, `AgentToolsResolverImpl` or `RuntimeToolRegistryImpl` |
| [Conversation window](#conversation-window)                                  | Code that calls `AgentConversation.window`                      |
| [Runtime tools and tool names](#runtime-tools-and-tool-names)                | Processes with a tool element named like a runtime tool        |
| [Model classes](#model-classes)                                              | Code that constructs or pattern-matches the model records (`AgentContext`, `ToolCall`), switches over `Content`, imports `AgentInstanceKey` or reads `AgentMetadata.agentInstanceKey` |
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

`AgenticAiConnectorsConfigurationProperties` keeps only the `aiagent` properties.

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

- New abstract method `composeWithRuntimeToolResults(configuration, agentContext, previousConversation, agentInput,
  List<RuntimeToolExecution> executions)`. The composer runs no tool. `compose(configuration, agentContext,
  previousConversation, agentInput)` keeps its signature, but resolves only the results of the process tool calls of the
  previous turn. When the process results are complete (or the missing ones are cancelled by an interrupting event) and
  the turn also has runtime tool calls, it returns the new `CompositionResult.RuntimeToolResultsRequired`. The request
  handler then executes the runtime tool calls and composes again with `composeWithRuntimeToolResults`, which merges the
  runtime tool results with the process results in call order and returns the turn input.
- `CompositionResult` gains the variant `RuntimeToolResultsRequired(List<ToolCall> runtimeToolCalls)`, so a `switch` over
  it must handle it. `NextTurn` gains the component `documents` (the documents that entered the conversation with the
  turn, which the request handler registers); the constructor `NextTurn(messages)` remains, so a pattern
  `NextTurn(var messages)` must become `NextTurn(var messages, var documents)`. `Deferred.arrivedResults` holds only the
  results that arrived from the process.

`AgentResponseHandler.createResponse(AgentConversation)` keeps its signature. The tool calls of the response must be the
process tool calls of the last turn (`AgentConversationTurn.processToolCalls()`): the agent executes the runtime tool calls
itself and never hands them to the process.

Migration: extend `AgentConversationTurnInputComposerImpl` and `AgentResponseHandlerImpl` and override only what you
change. If you implement the interfaces directly, add the new method, handle the new variant and hand only the process
tool calls to the process.

### Agent instance client and mappers

- `AgentInstanceClient.applyTurnStart` takes the `AgentContext` as a last parameter. Its features decide whether a tool of
  the `CONFIGURATION` item is a runtime tool and how the documents of the messages are shown, and its document registry is
  what document tags are expanded with.
- `AgentInstanceClient.applyToolCallResults` takes `List<ToolCallResultContent>` instead of `List<ToolCallResult>` and the
  `AgentContext` as a last parameter, whose features decide how the documents of the results are shown; convert with
  `ToolCallResultContent.from(result)`.
- `AgentInstanceToolMapper`: the constructor takes a `RuntimeToolRegistry` and `mapTools` takes the `AgentContext`. A
  runtime tool is reported without an element id.
- `AgentInstanceHistoryMapper`: the constructor takes a `DocumentExpander`, and `inputHistoryItems` takes the
  `AgentContext`. It reports a runtime tool call and its result without an element id, decided by `ToolCall.kind()`.

Migration: add the parameter and pass it on. Most extensions should keep `CamundaAgentInstanceClient` and the mappers and
decorate them instead.

### Replaceable bean constructors

The constructors of `AgentTaskRequestHandler` and `AgentSubProcessRequestHandler` (and `BaseAgentRequestHandler`) take a
`RuntimeToolRegistry`, a `RuntimeToolExecutor` and a `DocumentExpander` after the agent instance client. The constructor of
`AgentToolsResolverImpl` takes a `RuntimeToolRegistry`, and the constructor of `RuntimeToolRegistryImpl` takes the
`ObjectMapper` that reads a document from its reference. The auto-configuration provides the beans `RuntimeToolRegistry`,
`RuntimeToolExecutor` and `DocumentExpander`, all `@ConditionalOnMissingBean`. A custom bean built with `new` needs the
extra arguments; inject them as beans.

### Conversation window

`AgentConversation.window(int size)` is replaced by `window(int size, UnaryOperator<List<Message>> decorator)`. The
decorator turns the windowed messages into the messages of the snapshot; `UnaryOperator.identity()` leaves them as they
are. The request handler passes `DocumentExpander.windowDecorator(conversation)`.

### Runtime tools and tool names

- Agent instances created from 8.11 on have the `runtimeTools` feature. An ad-hoc tool element of the process
  that has the name of a registered runtime tool makes tool discovery and process migration fail with the error code
  `RUNTIME_TOOL_NAME_CONFLICT`, whether the tool is enabled or not. The agent also fails with this error code when the
  tools of a job contain such a name. The connector registers no runtime tool itself, so only the runtime tools of your
  own runtime matter. Rename the element. Agent instances created before keep their behavior: they are offered no runtime
  tool and no name is checked.
- To add a tool of your own, implement `RuntimeTool` (`definition`, `isEnabled(RuntimeToolContext)`,
  `call(RuntimeToolCall, RuntimeToolContext)`) and expose it as a Spring bean (see §25.4 of the
  [reference](reference/ai-agent.md)). `RuntimeToolContext.documents()` gives the documents the agent knows when the batch
  of calls starts; a tool can return documents in its result, which the agent registers after all calls of the message
  ran. Use a vendor-specific name prefix. `RuntimeToolResult` is sealed (`Success` and
  `Error`). The runtime tool SPI is marked `@Experimental` in 8.11 and may change in a minor release. A call of a
  registered runtime tool that is not enabled when it runs gets an error result and does not go to the process.
- The tool list of the `CONFIGURATION` agent instance history item holds the process tools and the enabled runtime
  tools.

### Model classes

- New record components, so a canonical constructor call or a record deconstruction pattern no longer compiles. Use the
  builder, or for `ToolCall` the constructors with three or four arguments: `AgentContext` (`documents`, a list of
  registry entries, and `features`, a list) and `ToolCall` (`kind`, a `ToolKind`: `PROCESS` or `RUNTIME`, `PROCESS` by
  default). The agent sets the kind of the tool calls of an assistant message once, when it receives the message.
- The sealed `Content` interface permits `DocumentReferenceContent` (content type `documentReference`). A `switch` over
  `Content` must handle it. A custom chat model provider never receives it: the conversation window expands it before the
  request is built.
- For an agent instance with the `documents` feature, documents reach a chat model provider differently. A user prompt
  document goes out as content, as before. A document in a tool result is text, its tag, instead of a `DocumentContent` or
  the reference JSON, and its content is not added. A custom `ChatModel` that handles `DocumentContent` in tool results
  therefore sees text for these instances; instances without the feature keep the earlier form.
- `AgentInstanceKey` moved from `aiagent.agentinstance` of `connector-aiagent` to `aiagent.model` of `aiagent-model`, and
  `AgentMetadata.agentInstanceKey` is an `AgentInstanceKey` (still persisted as a plain number) instead of a `Long`.
  `AgentInstanceKey.from(AgentMetadata)` is removed: read `metadata.agentInstanceKey()`.
- New in the model: `DocumentId`, the value type of a document id (persisted as a plain string), used by the document
  registry and `DocumentReferenceContent`. The `source` of a `DocumentReferenceContent` is the `DocumentSource` of the
  registry entry.
- Renamed: `DocumentReferenceXmlTag` (see the [module split](#module-split)) is `aiagent.document.LegacyDocumentReferenceXmlTag`,
  which only agent instances without the `documents` feature use.
- New in the libraries: the persisted document state (`DocumentRegistry`, `DocumentRegistryEntry`, `DocumentSource`) is in
  `aiagent.model.document` of `aiagent-model`, and the runtime tool SPI (`RuntimeTool`, `RuntimeToolCall`,
  `RuntimeToolContext`, `RuntimeToolDocuments`, `AgentDocument`, `RuntimeToolResult`, `RuntimeToolException`) is in
  `aiagent.api.tool` of `aiagent-api`.

<a id="persisted-data-8-11"></a>

### Persisted data

- The configuration fingerprint, the id of the `CONFIGURATION` agent instance history item, is computed differently: it is a
  SHA-256 over a JSON serialization of the configuration. Every running agent instance, also one created before 8.11, writes
  one additional `CONFIGURATION` history item on its first job after the upgrade.
- `AgentContext` gains `features` and `documents` (the document registry), and the conversation of an agent instance with
  the `documents` feature can contain `documentReference` content blocks. There is no schema version bump, and a missing
  field binds to its empty value, so state written by 8.10 loads unchanged. An 8.10 connector runtime ignores the new
  fields and drops them on its next write.
- A `ToolCall` of a stored assistant message carries `kind`. The JSON holds it only for a call of a runtime tool, and a
  missing value reads as `PROCESS`, so state written by 8.10 loads unchanged. The agent never computes the kind again, so a
  custom `ConversationStore` must keep it: a store that drops it turns a runtime tool call into a process tool call that
  never gets a result. Serializing the `ToolCall` record with Jackson keeps it.
- A stored message can hold a `DocumentReferenceContent`, which a custom `ConversationStore` must keep like any other
  content block (serializing `Content` with Jackson keeps it). What a reference stands for is in the registry of the agent
  context, not in the store, so code that rewrites the agent context must keep `documents`.
- Rolling back to 8.10 after 8.11 wrote the conversation of an agent instance with the `documents` feature is not
  supported: an 8.10 runtime fails to read a conversation that contains a `documentReference` block (an unknown content
  type).
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
