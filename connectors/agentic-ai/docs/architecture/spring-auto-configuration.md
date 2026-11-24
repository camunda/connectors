# Spring Auto-Configuration

## Auto-configurations

The agentic AI connectors are wired by several Spring Boot auto-configurations, all registered in `AutoConfiguration.imports` and each activated by `@ConditionalOnBooleanProperty("camunda.connector.agenticai.enabled", matchIfMissing=true)` — on by default. `@AutoConfiguration(after = ...)` orders them as listed:

| Auto-configuration                             | After                                                          | Provides                                                                                  |
|------------------------------------------------|----------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| `AgenticAiCommonAutoConfiguration`             | —                                                              | `AgenticAiHttpProxySupport`, `AgenticAiHttpProperties`                                    |
| `AgenticAiToolsSchemaAutoConfiguration`        | common                                                         | Ad-hoc tool schema generation and process definition tool element resolution              |
| `AdHocToolsSchemaConnectorAutoConfiguration`   | tools schema                                                   | `AdHocToolsSchemaFunction` (`ad-hoc-tools-schema-resolver.enabled`)                       |
| `A2aClientAutoConfiguration`                   | common                                                         | A2A client connector configurations (see [`a2a-client.md`](../../connector-a2a/docs/a2a-client.md#spring-configuration))    |
| `A2aClientAgenticToolAutoConfiguration`        | `A2aClientAutoConfiguration`                                   | A2A gateway tool beans for the AI Agent                                                   |
| `McpClientAutoConfiguration`                   | common                                                         | MCP client connector configurations (see [`mcp-client.md`](../../connector-mcp/docs/mcp-client.md#spring-configuration))    |
| `McpClientAgenticToolAutoConfiguration`        | `McpClientAutoConfiguration`                                   | MCP gateway tool beans for the AI Agent                                                   |
| `AgenticAiConnectorsAutoConfiguration`         | common, tools schema                                           | AI Agent beans (task and sub-process functions, handlers, conversation stores, registries, AWS SDK and Azure HTTP proxy support) |
| `AgenticAiLangChain4JAutoConfiguration`        | common                                                         | LangChain4j bridge: converter chain and proxy support for custom `ChatModelFactory` beans built on LangChain4j. Off by default, `camunda.connector.agenticai.aiagent.langchain4j.enabled=true` loads it |

The AI Agent does not reference the MCP and A2A integrations. It collects every `GatewayToolHandler`
and `SystemPromptContributor` bean through list injection, so the order of the integration
auto-configurations does not matter.

`AgenticAiConnectorsAutoConfiguration` imports:
- `AgenticAiNativeProvidersConfiguration`: `ChatModelFactory` beans of the native providers (v2 provider configurations)

Also registers `ChatModelRegistry` (`ChatModelRegistryImpl`, taking every `ChatModelFactory` bean) directly, outside of the imported configurations.

The `aiAgentExecutionContextFactory` bean (`AgentExecutionContextFactory`) implements `AgentTaskExecutionContextFactory`
and `AgentSubProcessExecutionContextFactory`. It backs off when a bean of either interface exists. Then each enabled
flavor needs a bean of its interface: the AI Agent Task functions
(`camunda.connector.agenticai.aiagent.outbound-connector.enabled`) need an `AgentTaskExecutionContextFactory` bean, and
the AI Agent Sub-process functions (`camunda.connector.agenticai.aiagent.job-worker.enabled`) need an
`AgentSubProcessExecutionContextFactory` bean.

## Key Differences from Standard Connectors

1. **Dual activation modes**: Both an outbound connector (`AgentTaskV1Function`) and a job worker (`AgentSubProcessV1Function`) are registered. The job worker bypasses the standard connector runtime, handling variable resolution, secret injection, and exception handling directly.
2. **Pluggable LLM providers**: the `ChatModel` provider SPI ([`chat-model-spi.md`](../../aiagent-api/docs/chat-model-spi.md#the-chatmodel-provider-spi-adr-009)) is implemented by the native providers, and any `ChatModelFactory` bean extends it, for example a custom factory built on the LangChain4j bridge. Provider selection is by `ChatModelFactory.supports(...)` via the SPI registry, which needs exactly one match. The native configuration loads unconditionally, the LangChain4j configuration only with `camunda.connector.agenticai.aiagent.langchain4j.enabled=true`; v1 requests run on the native providers.
3. **Pluggable system prompt contributors**: All `SystemPromptContributor` beans are auto-collected into `SystemPromptComposerImpl`.
4. **Pluggable gateway tool handlers**: All `GatewayToolHandler` beans are auto-collected into `GatewayToolHandlerRegistryImpl`.
5. **Caffeine caching of BPMN resolution**: Process definition fetch (API + XML parse + FEEL extraction) is cached with configurable TTL and max size.
6. **Dual conversation stores**: `InProcessConversationStore` and `CamundaDocumentConversationStore` are both registered; `ConversationStoreRegistry` selects based on request config.

## Feature Toggles

| Property                                                          | Default      | Controls                           |
|-------------------------------------------------------------------|--------------|------------------------------------|
| `camunda.connector.agenticai.enabled`                             | `true`       | Master switch                      |
| `camunda.connector.agenticai.aiagent.outbound-connector.enabled`  | `true`       | AI Agent Task connector (v1 and v2)     |
| `camunda.connector.agenticai.aiagent.job-worker.enabled`          | `true`       | AI Agent Sub-process job worker (v1 and v2) |
| `camunda.connector.agenticai.ad-hoc-tools-schema-resolver.enabled` | `true`     | Ad-Hoc Tools Schema connector     |

## Key Configuration Defaults

Configurable under the `camunda.connector.agenticai.*` prefix, in three main areas: process-definition
fetch retries and the BPMN-resolution Caffeine cache (`tools.processDefinition.*`), and the chat-model
API timeout (`aiagent.chatModel.api.*`). For the current defaults, see
[`ToolsSchemaProperties`](../../aiagent-tools-schema/src/main/java/io/camunda/connector/agenticai/tools/schema/configuration/ToolsSchemaProperties.java)
(`tools.*`) and
[`AgenticAiConnectorsConfigurationProperties`](../../connector-aiagent/src/main/java/io/camunda/connector/agenticai/aiagent/configuration/AgenticAiConnectorsConfigurationProperties.java)
(`aiagent.*`); each property carries its own `@DefaultValue`.
