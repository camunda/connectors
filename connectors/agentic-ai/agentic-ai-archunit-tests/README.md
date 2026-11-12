# agentic-ai-archunit-tests

## Purpose

Architecture tests for the production classes of all agentic AI modules. Test-only, not shipped.

## Key types

The tests use ArchUnit (`archunit-junit5`) and are in package `io.camunda.connector.agenticai.archunit`.

- `AgenticAiModules`: maps the modules to their package roots and kinds (library, connector, integration).
- `AgenticAiModulesTest` (plain JUnit): unit test of the module location lookup in `AgenticAiModules.isLocatedIn`.
- `ModuleLayersArchTest`: the dependency direction between the package roots (layers) of the modules.
- `ModulePackageOwnershipArchTest`: every class of a package root is loaded from the module owning it,
  and every module contains only classes of its own package root.
- `NullMarkedPackagesArchTest`: every package has a `@NullMarked` `package-info`.
- `ProxySupportArchTest`: the HTTP proxy support depends only on itself and the common library.
- `ChatModelProviderIndependenceArchTest`: the vendor chat model providers (packages below `aiagent.chatmodel.provider`,
  except `azure` and `configuration`) do not depend on each other, except for the allowed
  dependencies listed in the test (today: Mistral on OpenAI). The rest of the AI agent (outside `aiagent.chatmodel.provider`
  and `aiagent.configuration`) does not depend on the providers.
- `VendorSdkArchTest`: one table row per vendor SDK: each SDK (Anthropic, OpenAI, Gemini, Bedrock runtime and AgentCore, AWS and Azure HTTP clients,
  LangChain4j, A2A, MCP) is only used by the packages that integrate it.
- `AutoConfigurationArchTest`: every `@AutoConfiguration` class lives in a `configuration` package, is switched by
  `@ConditionalOnBooleanProperty` on `camunda.connector.agenticai.enabled` with `matchIfMissing = true`, and is listed
  in the `AutoConfiguration.imports` of the module it is loaded from; every Agentic AI entry of an `imports` resource
  resolves to an `@AutoConfiguration` class.
- `ConfigurationPropertyNamesArchTest`: property keys in `@ConfigurationProperties` prefixes,
  `@ConditionalOnProperty`, `@ConditionalOnBooleanProperty` and `@Value` placeholders are kebab-case.
  Every `@ConfigurationProperties` prefix is `camunda.connector.agenticai` or below it.
- `NoFieldInjectionArchTest`: no field injection (`@Autowired`, `@Value`, `@Resource`, `@Inject` on fields).
- `SpringFreeModelAndApiArchTest`: `aiagent.model` and `aiagent.api` do not depend on `org.springframework`.
- `PolymorphicJsonArchTest`: no `@JsonTypeInfo(use = Id.CLASS | Id.MINIMAL_CLASS)`, and every concrete
  `ConversationContext` implementation is annotated with `@JsonTypeName`.
- `OutboundConnectorBeanScopeArchTest`: `@Bean` methods returning an outbound connector function (annotated with
  `@OutboundConnector` or implementing `OutboundConnectorFunction`) are annotated `@Scope("prototype")`.
- `PackageCyclesArchTest`: no package cycles between the chat model providers, between the conversation stores,
  and between the sub-packages of the A2A client and of the tools schema. The first-level packages of the AI Agent
  connector and of the MCP client have no cycles except the known ones in `KNOWN_CYCLES` (the ADR 015 follow-up).
- `ElementTemplateAnnotationsArchTest`: the library modules do not use the element template generator annotations,
  except the records listed in the test (the ADR 015 follow-up).
- `RegistryEncapsulationArchTest`: concrete `ConversationStore` implementations are only used by
  `aiagent.memory.conversation` and `aiagent.configuration`; concrete `ChatModelFactory` implementations are only
  used by `aiagent.chatmodel` and `aiagent.configuration`.
- `GeneralCodingRulesArchTest`: no access to the standard streams, no `java.util.logging`.
- `NamingConventionsArchTest`: a class named `FooImpl` implements the interface `Foo`.
- `ModuleInventoryTest` (plain JUnit): every Maven module of `connectors/agentic-ai` is registered in
  `AgenticAiModules` and the other way round, and `agentic-ai-bundle` depends on exactly the connector and
  integration modules.

## Docs

- [Architecture tests](../AGENTS.md#architecture-tests): when to update the tests and how to run them
- [Architectural invariants](../docs/design-guidelines.md#architectural-invariants)
