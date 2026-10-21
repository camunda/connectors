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
| [Model classes](#model-classes) | Code that imports the moved `AgentInstanceKey` or reads `AgentMetadata.agentInstanceKey` |
| [V1 provider configuration](#v1-provider-configuration) | Runtimes that set the switch `rewrite-v1-provider-config-to-v2`, and custom factories for the v1 provider types |

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
- New record `aiagent.model.chatmodel.CustomChatModelConfiguration`, which the request translation builds from
  `CustomProviderConfiguration`. A custom `ChatModelFactory` matches and reads its configuration
  through the record, no longer through `CustomProviderConfiguration`.

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

### Model classes

- `AgentInstanceKey` moved from `aiagent.agentinstance` of `connector-aiagent` to `aiagent.model` of `aiagent-model`, and
  `AgentMetadata.agentInstanceKey` is an `AgentInstanceKey` (still persisted as a plain number) instead of a `Long`.
  `AgentInstanceKey.from(AgentMetadata)` is removed: read `metadata.agentInstanceKey()`.
- The error codes of a model call (`ERROR_CODE_FAILED_MODEL_CALL`, `ERROR_CODE_UNSUPPORTED_MODEL_CONFIGURATION`,
  `ERROR_CODE_MODEL_RESPONSE_CONTENT_FILTERED`, `ERROR_CODE_MODEL_CONTEXT_WINDOW_EXCEEDED` and
  `ERROR_CODE_MODEL_RESPONSE_GUARDRAIL_INTERVENED`) moved from `aiagent.agent.AgentErrorCodes` to
  `aiagent.api.chatmodel.ChatModelErrorCodes` of `aiagent-api`. The values do not change.
- `AgentConfiguration` is a non-null domain model. `memory`, `limits`, `events` and `response` are always present
  and every default is applied when the connector translates its request. `PromptConfiguration`,
  `LimitsConfiguration`, `EventHandlingConfiguration`, `ResponseConfiguration`, `ResponseFormatConfiguration` and
  `MemoryConfiguration` in `aiagent.model.request` are plain records without element template annotations, so
  `UserPromptConfiguration.documents()` is never `null`, `LimitsConfiguration.maxModelCalls()`,
  `MemoryConfiguration.contextWindowSize()` and `ResponseConfiguration.includeAssistantMessage()` are primitives,
  `ResponseFormatConfiguration.TextResponseFormatConfiguration.parseJson()` is a `boolean`, and `MemoryConfiguration`
  and `ResponseConfiguration` are records instead of interfaces. The `DEFAULT_*` constants of `AgentConfiguration` are
  removed. The records the element templates show are `*TemplateConfiguration` in `aiagent.request`; their JSON is
  unchanged. `AgentSubProcessExecutionContext.response()` is replaced by `includeAgentContext()`.
- `MemoryConfiguration.storage()` returns the domain configuration of the store, not the element template record: the
  in-process, Camunda document and AWS AgentCore stores read `InProcessConversationStoreConfiguration`,
  `CamundaDocumentConversationStoreConfiguration` and `AwsAgentCoreConversationStoreConfiguration` of their own
  packages. A custom store keeps reading `CustomConversationStoreConfiguration`.
- The chat model providers read their own domain configuration instead of the element template records of
  `aiagent.request.v1` and `aiagent.request.v2`: `AnthropicConfiguration`, `OpenAiConfiguration`,
  `GeminiConfiguration`, `MistralConfiguration` and `BedrockConfiguration` in the package of the provider. The element
  template records no longer implement `ChatModelConfiguration`; `ChatModelConfigurationTranslator` of
  `aiagent.translation` turns them into the domain records. `ChatModelFactory`, `ChatModel`, request converters and
  family strategies of the providers take the domain records, and the factories of the Anthropic and OpenAI providers
  no longer take `ChatModelProperties` (the translator applies the default timeout). Shared domain types:
  `RequestCustomizations` and `EndpointAuthentication` in `aiagent.chatmodel.connection`, `AwsAuthentication` in
  `aiagent.chatmodel.provider.aws` and `FoundryAuthentication` in `aiagent.chatmodel.provider.azure`.
  `FoundryCredentialResolver.bearerTokenSupplier` takes `FoundryAuthentication.ClientCredentials` or
  `FoundryAuthentication.ManagedIdentity`. `ChatModelTimeouts` is removed.
- `AgentSubProcessExecutionContext` and `AgentTaskExecutionContext` hold the domain model only. The constructors take
  the `AgentConfiguration`, the agent context, the tool call results and (task flavor) the id of the tools container
  element, or (sub-process flavor) the `includeAgentContext` flag, instead of the request records. The connector
  functions build them with `AgentExecutionContextTranslator` of `aiagent.translation`. `AgentTaskExecutionContext.tools()`
  is removed.
- `AgentConversation.agentInstanceKey()` is removed: read `agentContext.metadata().agentInstanceKey()`.
- The connection settings of the native providers are resolved by `ConnectionResolver` in
  `aiagent.chatmodel.connection`. The `ChatModelFactory` of the OpenAI, Anthropic, Mistral, Gemini and Bedrock
  providers take a `ConnectionResolver` instead of the proxy support, the OAuth token resolver and the
  `FoundryCredentialResolver`. The behavior of the connections is the same for all providers now:
  - The connect timeout is 15 seconds, or the timeout of the model call if that is shorter. The OpenAI, Anthropic and
    Mistral clients used the timeout of the model call (3 minutes by default) as their connect timeout. The Gemini
    client used 10 seconds.
  - The OpenAI, Anthropic and Mistral providers skip the proxy for a host which matches a non-proxy host pattern, like
    the Gemini and Bedrock providers did.
  - `OAuthBearerTokenInterceptor.create` takes a `Supplier<String>` for the token.
- The native providers share the model call concerns in `ModelCallEnvelope` (`aiagent.chatmodel.envelope`), which the
  factory of each provider wraps around its chat model together with an `SdkFailureMapper` for the failures of its
  SDK. The chat models of the providers no longer time the call or map failures, and the `toResult` methods of the
  response converters no longer take the execution time: the envelope records it in the metrics of the result. The
  metrics in the `partialResult()` of a rejection carry no execution time. A `ChatModel` which is not created by one
  of these factories measures the execution time itself. The message of a failed model call has one format now:
  - `Model call failed with HTTP <status> (<type>): <message>` for an error response of the provider, with the error
    code or type of the provider. The OpenAI and Mistral providers used `Model call failed: <message>` before.
  - `Model call failed: <detail>` for any other failure.
  - A request which exceeds the context window of the model fails with `ContextWindowExceededException` and the
    message of `ContextWindowExceededException.DEFAULT_MESSAGE` on every provider. The OpenAI and Mistral providers
    used `Model call failed: <message>` and the Gemini provider used the same text as the default message.
  - A coded `ConnectorException` thrown by a response converter (for example Bedrock `MALFORMED_MODEL_OUTPUT`) passes
    on unchanged on every provider. The Gemini and Bedrock providers wrapped it in `Model call failed: <message>`.
  - A failure to convert the request (Anthropic, Gemini, Bedrock) is reported as `ERROR_CODE_FAILED_MODEL_CALL`
    instead of escaping as the raw exception.
  - The execution time covers the conversion of the request and the response as well as the call.

### V1 provider configuration

The switch `camunda.connector.agenticai.aiagent.rewrite-v1-provider-config-to-v2` is removed. v1 provider
configurations (legacy element templates) are always rewritten to their v2 equivalent and run on the native providers.
A runtime that set the switch to `false` must remove the property and move to the native providers.

Conversations that were persisted by the LangChain4j path do not resume on every native provider. The LangChain4j path
stored the Google Vertex AI thought signature of a tool call in the `google-vertex-ai` metadata namespace, and the
native Gemini provider reads it from the `google-gemini` namespace. Gemini 3 rejects the next request of a conversation
that has such a tool call in its history. Complete the Google Vertex AI conversations that call tools before the runtime
moves to the native providers.

The LangChain4j based factories for the v1 provider types (Anthropic, Azure OpenAI, Bedrock, Google Vertex AI, OpenAI
and OpenAI-compatible endpoints) are removed, together with their vendor LangChain4j dependencies. What stays is the
generic LangChain4j bridge for custom providers:

- `LangChain4JChatModelFactory<T>` is generic over `ChatModelConfiguration` instead of the v1 `ProviderConfiguration`.
  Its constructor takes the configuration type, `providerType()` is removed, and `supports` matches a configuration of
  that type (override it to narrow the match).
- `LangChain4JChatModel` and `ChatMessageConverter` take a `ChatModelConfiguration` instead of the v1
  `ProviderConfiguration`. `ChatMessageConverterImpl` takes a `ToolCallMetadataDecorator` as an additional constructor
  argument.
- `ToolCallMetadataDecorator` is an interface instead of a utility class for Google Vertex AI. It carries provider data
  that must be echoed back verbatim (such as thought signatures) through the tool call metadata. The default carries
  nothing; register a bean of the type to carry the data of a provider. The Google Vertex AI thought signature handling
  of the LangChain4j path is removed. The native Gemini provider handles it.
- `ChatModelHttpProxySupport` keeps `createJdkHttpClientBuilder()`, the HTTP client builder with the connector proxy
  settings for LangChain4j models. Its Azure and Google GenAI proxy options are removed.

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
