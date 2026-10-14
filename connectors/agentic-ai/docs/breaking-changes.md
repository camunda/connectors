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

| Area                                                                          | Affects                                                        |
| ----------------------------------------------------------------------------- | -------------------------------------------------------------- |
| [Turn input composer and response handler](#turn-input-composer-and-response-handler) | Custom `AgentConversationTurnInputComposer` and `AgentResponseHandler` |
| [Agent instance client and mappers](#agent-instance-client-and-mappers)      | Custom `AgentInstanceClient`, `AgentInstanceHistoryMapper`, `AgentInstanceToolMapper` |
| [Replaceable bean constructors](#replaceable-bean-constructors)              | Code that constructs the request handlers or `AgentToolsResolverImpl` |
| [Conversation window](#conversation-window)                                  | Code that calls `AgentConversation.window`                      |
| [Runtime tools and tool names](#runtime-tools-and-tool-names)                | Processes with a tool element named like a runtime tool        |
| [Model classes](#model-classes)                                              | Code that constructs or pattern-matches the model records, switches over `Content`, or imports the moved classes |
| [Persisted data](#persisted-data-8-11)                                       | Code reading stored messages or the agent context, rollbacks   |

### Turn input composer and response handler

`AgentConversationTurnInputComposer`:

- `compose(configuration, agentContext, previousConversation, agentInput)` gains a fifth parameter,
  `RuntimeToolCallSplit deferredCalls` (`RuntimeToolCallHandler.RuntimeToolCallSplit`): the calls of the last assistant
  message that the agent answers itself. The composer answers them, in call order, when the tool call results of the turn
  are composed. An implementation that does not answer calls itself passes
  `RuntimeToolCallSplit.allRemaining(List.of())` where it delegates.
- New abstract method `composeDocumentContentMessage(DocumentRegistry, List<RuntimeToolCallAnswer>)`: the message that
  carries the content of the documents the answers of the agent itself name. Return `null` if you add no content.
- `CompositionResult.NextTurn` gains the components `documents` (the documents that entered the conversation, registered by
  the handler) and `contextUpdates` (changes of the agent context made by the answers of the agent itself). The
  constructors `NextTurn(messages)` and `NextTurn(messages, documents)` remain, so a pattern `NextTurn(var messages)` must
  become `NextTurn(var messages, var documents, var contextUpdates)`. `Deferred.arrivedResults` holds only the results
  that arrived from the process.

`AgentResponseHandler` gains the abstract method `createResponse(AgentConversation, List<ToolCall> processToolCalls)`. It is
the method the request handlers call: `processToolCalls` are the calls of the assistant message that go to the process,
the other calls are answered by the agent itself. `createResponse(AgentConversation)` remains for all calls going to the
process.

Migration: extend `AgentConversationTurnInputComposerImpl` and `AgentResponseHandlerImpl` and override only what you
change. If you implement the interfaces directly, add the new parameter and method and make your single-argument
`createResponse` delegate to the two-argument one with all tool calls of the message.

### Agent instance client and mappers

- `AgentInstanceClient.applyTurnStart`, `applyTurnCompletion` and `applyToolCallResults` take the `AgentContext` as a last
  parameter. Its features decide what the history shows and its document registry is what document tags are expanded with.
- `applyToolCallResults` takes `List<ToolCallResultContent>` instead of `List<ToolCallResult>`; convert with
  `ToolCallResultContent.from(result)`.
- `AgentInstanceHistoryMapper`: the constructor takes a `RuntimeToolRegistry` and a `DocumentExpander`, and
  `inputHistoryItems`, `assistantContent` and `assistantToolCalls` take the `AgentContext`.
- `AgentInstanceToolMapper`: the constructor takes a `RuntimeToolRegistry` and `mapTools` takes the `AgentContext`. A
  runtime tool is reported without an element id.

Migration: add the parameter and pass it on. Most extensions should keep `CamundaAgentInstanceClient` and the mappers and
decorate them instead.

### Replaceable bean constructors

The constructors of `AgentTaskRequestHandler` and `AgentSubProcessRequestHandler` (and `BaseAgentRequestHandler`) take a
`RuntimeToolCallHandler` and a `DocumentExpander` after the agent instance client. The constructor of
`AgentToolsResolverImpl` takes a `RuntimeToolRegistry`. The auto-configuration provides the beans `RuntimeToolRegistry`,
`RuntimeToolCallHandler`, `DocumentExpander` and the two built-in runtime tools, all `@ConditionalOnMissingBean`. A
custom bean built with `new` needs the extra arguments; inject them as beans.

### Conversation window

`AgentConversation.window(int size)` is replaced by `window(int size, UnaryOperator<List<Message>> decorator)`. The
decorator turns the windowed messages into the messages of the snapshot; `UnaryOperator.identity()` leaves them as they
are. The request handler passes `DocumentExpander.windowDecorator(conversation)`. The tool definitions of the snapshot
have the format of their document parameters removed for the model provider.

### Runtime tools and tool names

- Agent instances created from 8.11 on are offered the built-in runtime tools `c8__get_document` and `c8__return_document`,
  which the agent runs itself. A tool element of the process (ad-hoc or gateway) that has the name of a registered runtime
  tool makes tool discovery and process migration fail with the error code `RUNTIME_TOOL_NAME_CONFLICT`, whether the tool is
  available or not. Rename the element. Agent instances created before keep their behavior: they are offered no runtime
  tool and no name is checked.
- To add a tool of your own, implement `RuntimeTool` (`definition`, `isAvailable`, `call`) and expose it as a Spring bean
  (see §25.4 of the [reference](reference/ai-agent.md)). Use a vendor-specific name prefix.
- The tool list of the `CONFIGURATION` agent instance history item holds the process tools and the available runtime
  tools, and the configuration fingerprint (the id of that item) is computed differently: it is a SHA-256 over a JSON
  serialization with sorted keys. An agent instance that is in flight at the upgrade reports one additional
  `CONFIGURATION` item.

### Model classes

- New record components, so a canonical constructor call or a record deconstruction pattern no longer compiles. Use the
  builders: `AgentContext` (`documents`, `responseDocuments`, `features`), `AgentResponse` and `AgentSubProcessResponse`
  (`responseDocuments`), `ToolDefinition` (`documentDelivery`; the three-argument constructor remains),
  `AgentInput.UserPrompt` (`documentDelivery`) and `PromptConfiguration.UserPromptConfiguration` (`documentDelivery`; the
  two-argument constructor remains).
- The sealed `Content` interface permits `DocumentReferenceContent` (content type `documentReference`). A `switch` over
  `Content` must handle it. A custom chat model provider never receives it: the conversation window expands it before the
  request is built.
- For agent instances created from 8.11 on, documents reach a chat model provider differently. A user prompt document is a
  tag as text, and its content is added only on request or with the delivery `content`. A document in a tool result is
  text, its tag, instead of a `DocumentContent` or the reference JSON, and the content of a document follows in a user
  message only when it was loaded or the delivery `content` applies. A custom `ChatModel` that handles `DocumentContent`
  in tool results therefore sees text for these instances; instances created before keep the earlier form.
- The `ToolDefinition` constructor appends a hint to the description of every document parameter (a string schema with the
  format `camunda-document-id`).
- Moved or renamed: `aiagent.model.document.DocumentHandle` is `aiagent.document.DocumentHandle`, and
  `aiagent.model.message.DocumentReferenceXmlTag` is `aiagent.model.message.LegacyDocumentReferenceXmlTag`, which only
  instances created before 8.11 use.

<a id="persisted-data-8-11"></a>

### Persisted data

- `AgentContext` gains `features`, `documents` (the document registry) and `responseDocuments`, and a conversation can
  contain `documentReference` content blocks. There is no schema version bump, and a missing field binds to its empty
  value, so state written by 8.10 loads unchanged.
- Rolling back to 8.10 after 8.11 wrote conversation state is not supported: an 8.10 runtime fails to read a conversation
  that contains a `documentReference` block (an unknown content type), and it drops the new context fields on its next
  write.
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
  [AI Agent Task](../connector-agentic-ai/element-templates/hybrid/agenticai-ai-agent-task.v2-hybrid.json) and
  [AI Agent Sub-process](../connector-agentic-ai/element-templates/hybrid/agenticai-ai-agent-subprocess.v2-hybrid.json).
