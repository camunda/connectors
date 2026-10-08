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

## Camunda 8.9 to 8.10

Summary of what changes for a custom extension. Details follow in the linked sections.

| Area                                                                              | Affects                                            |
| --------------------------------------------------------------------------------- | -------------------------------------------------- |
| [Conversation storage SPI](#conversation-storage-spi-redesign)                    | Custom `ConversationStore` implementations         |
| [Chat model SPI](#chat-model-spi)                                                 | Custom model integrations (`AiFrameworkAdapter`, LangChain4j `ChatModelFactory`) |
| [Moved and removed classes](#moved-and-removed-classes)                           | Any code importing the module's model or utility classes |
| [Agent initializer](#agent-initializer)                                           | Custom `AgentInitializer` beans or wrappers around `AgentInitializerImpl` |
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
`io.camunda.connector.agenticai.aiagent.chatmodel`. Native providers (Anthropic, OpenAI including
Microsoft Foundry, Google Gemini including Vertex AI, AWS Bedrock Converse, Mistral) implement it and are the
target for the new `v2` element templates.

#### Removed

- `AiFrameworkAdapter`, `AiFrameworkChatResponse`, and the whole `aiagent.framework.langchain4j`
  package, including the LangChain4j `ChatModelFactory` (`createChatModel(ProviderConfiguration)`)
  and `ChatModelFactoryImpl`. Custom `AiFrameworkAdapter` beans are no longer called. To keep a
  LangChain4j based custom provider, extend `LangChain4JChatModelFactory` instead (see
  [Provider configuration](#provider-configuration)); otherwise implement the new SPI directly.
- `RuntimeMemory`, `DefaultRuntimeMemory`, `MessageWindowRuntimeMemory`
  (see the storage SPI section).

#### New

- `ChatModelFactory` — `boolean supports(ChatModelConfiguration)` and
  `ChatModel create(ChatModelConfiguration)`. Register one bean per provider.
- `ChatModel` (`AutoCloseable`) — `ChatResult execute(ChatRequest)`. One instance serves a single
  agent request, and the connector closes it when the request is done. Each `execute` call performs
  one round trip against the provider.
- `ChatRequest(AgentExecutionContext, ConversationSnapshot)` and the sealed
  `ChatResult` (`Completed` | `Continuation`), each carrying an `AssistantMessage` and
  `AgentMetrics`. Return `Continuation` when the provider pauses mid-turn and must be called again.
- `ChatModelRegistry` — resolves the factory whose `supports(...)` returns `true`. It **fails when
  zero or more than one** factory matches, so `supports` must only match the configurations your
  factory owns.
- `ChatModelConfiguration` — neutral descriptor with `provider()` and `model()`.

#### Provider configuration

- `ProviderConfiguration` is split into `aiagent.model.request.v1` (legacy templates, previously
  `aiagent.model.request.provider`) and `aiagent.model.request.v2` (new templates, sealed).
- `CustomProviderConfiguration(providerType, model, parameters)` (`request.v2`) is the
  configuration for the **Custom Implementation** provider of the new templates. A factory typically
  matches on `providerType()` and reads `parameters()` (a FEEL context from the template).
- By default, v1 provider configurations (legacy element templates) are rewritten to their v2
  equivalent and run on the native providers
  (`camunda.connector.agenticai.aiagent.rewrite-v1-provider-config-to-v2`, default `true`). The
  switch is temporary and will be removed in a future release. v1 job types and provider
  configurations stay supported through the translation to v2. The LangChain4j based factories
  (`aiagent.chatmodel.provider.langchain4j`) are only used when the switch is `false`. They are
  deprecated, and [ADR 014](adr/014-route-v1-requests-through-native-providers.md) describes their
  removal. `LangChain4JChatModelFactory` adapts a LangChain4j chat model to `ChatModel` and can be
  extended to port an existing LangChain4j based custom provider.

#### Migration guide for custom model integrations

1. Remove custom `AiFrameworkAdapter` beans and LangChain4j `ChatModelFactory` overrides.
2. Implement `ChatModelFactory` as a Spring bean and `ChatModel` as a plain class that
   `create(...)` instantiates. The connector creates a `ChatModel` per request and closes it
   afterwards, so do not register it as a shared bean. To reuse a
   built-in provider, inject its factory bean (for example `OpenAiChatModelFactory`), build its
   configuration, and call `create(...)` directly. Do not go through `ChatModelRegistry` from inside
   your factory: it would resolve your own factory again, and fails if several factories match.
3. Select **Custom Implementation** in a `v2` element template and set the provider type, model and
   parameters.
4. To decorate a built-in provider, return a `ChatModel` that wraps the delegate and forwards
   `close()`.

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

### Agent initializer

**ADR**: [006-data-driven-agent-initialization](adr/006-data-driven-agent-initialization.md)

- `AgentInitializationResult` variants are renamed:

  | 8.9                                                               | 8.10                                                                                  |
  | ----------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
  | `AgentContextInitializationResult(agentContext, toolCallResults)` | `ReadyToConverse(agentContext, toolCallResults)`                                      |
  | `AgentResponseInitializationResult(agentResponse)`                | `DiscoverTools(agentContext, toolDiscoveryToolCalls)` — the initializer no longer builds an `AgentResponse`; the caller dispatches the discovery tool calls |
  | `AgentDiscoveryInProgressInitializationResult()`                  | `DeferConversation()`                                                                 |

- The `AgentInitializerImpl` constructor takes `AgentToolsResolver`, `GatewayToolHandlerRegistry`,
  `AgentInstanceClient`, and `ToolCallResultCompletedAtResolver`. Wrappers that construct it must
  inject the additional `AgentInstanceClient` bean. ADR 006 proposes folding the logic into the
  request handler.

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
- In hybrid setups, override the new types with `CONNECTOR_AI_AGENT_TASK_TYPE` and
  `CONNECTOR_AI_AGENT_SUBPROCESS_TYPE`. The legacy variables (`CONNECTOR_AI_AGENT_TYPE`,
  `CONNECTOR_AI_AGENT_JOB_WORKER_TYPE`) only affect the legacy job workers.
- Use only the `v2` hybrid element templates for new custom runtimes.
