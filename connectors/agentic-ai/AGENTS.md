# Agentic AI Module: Agent Instructions

The `agentic-ai` module implements Camunda's AI Agent connectors: an agentic orchestration system that
runs LLM tool-calling loops inside BPMN processes. It ships in two flavors, an AI Agent Task (on a
service task) and an AI Agent Sub-process (on an ad-hoc sub-process), plus supporting infrastructure
for tool calling, conversation memory, gateway tools (MCP, A2A), and event handling.

## How to use this document

This file is a router and operating-rules layer. It is not the architecture reference. It orients an
agent working in the module (new to it or returning) and routes it to the right resource:

1. Build the minimal [mental model](#mental-model) below.
2. Use [Start here, navigation](#start-here-navigation) to jump to the exact reference section for
   your task. Read that section by anchor or `Read` offset rather than the whole file.
3. Follow the [testing rules](docs/acceptance-testing.md) and
   [Definition of Done](#definition-of-done) before declaring a change complete.

Task guidance and architecture live in linked documents:

- [`docs/design-guidelines.md`](docs/design-guidelines.md): invariants, contract decisions and ADRs
- [`docs/acceptance-testing.md`](docs/acceptance-testing.md): test selection, feature acceptance and provider coverage
- [`docs/architecture/`](docs/architecture/): content that spans modules (the agentic loop, extension
  points, Spring auto-configuration)
- `<module>/README.md`: purpose, key types and docs of each module. Docs about the types of one module
  are in `<module>/docs/`.
- [`docs/adr/`](docs/adr/): architecture decision records
- [`docs/template-text-guidelines.md`](docs/template-text-guidelines.md): help text conventions for
  element templates
- [`docs/implementation-rules.md`](docs/implementation-rules.md): how to approach a change, domain rules
  from past review findings, and code design rules for new code
- [`docs/documentation-rules.md`](docs/documentation-rules.md): where each doc goes and how to keep the
  docs up to date
- [`docs/target-architecture.md`](docs/target-architecture.md): target state of the module
- [`docs/breaking-changes.md`](docs/breaking-changes.md): breaking changes for custom extensions, per release
- E2E tests [`AGENTS.md`](../../connectors-e2e-test/connectors-e2e-test-agentic-ai/AGENTS.md): e2e
  modules, running e2e tests, real-LLM CPT coverage
- Repo-root [`AGENTS.md`](../../AGENTS.md): repo-wide build, commit, PR, CI, spotless, license, and
  element-template conventions. These are not duplicated here.

## Modules

The module is split into Maven modules with a fixed dependency direction
([ADR 015](docs/adr/015-split-agentic-ai-into-modules.md)):

| Module                          | Package root                         | Contents                                                                                        | Depends on (agentic AI modules)                                       |
| ------------------------------- | ------------------------------------ | ----------------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `agentic-ai-common`             | `common`                             | Shared helpers grouped by topic (`json`, `retry`, `http`, `validation`, ...), common properties | none                                                                  |
| `aiagent-model`                 | `aiagent.model`                      | Agent model referenced by the extension points                                                  | common                                                                |
| `aiagent-api`                   | `aiagent.api`                        | Extension points (gateway tools, chat model, storage, prompts)                                  | model, common                                                         |
| `aiagent-tools-schema`          | `tools.schema`                       | Ad-hoc tools schema resolution                                                                  | api, model, common                                                    |
| `connector-ad-hoc-tools-schema` | `adhoctoolsschema`                   | Ad-hoc tools schema connector                                                                   | tools-schema                                                          |
| `connector-mcp`                 | `mcp.client`                         | MCP client connectors                                                                           | common                                                                |
| `aiagent-mcp-integration`       | `mcp.integration`                    | MCP client as AI Agent gateway tool                                                             | connector-mcp, api, model, common                                     |
| `connector-a2a`                 | `a2a.client`                         | A2A client connectors                                                                           | common                                                                |
| `aiagent-a2a-integration`       | `a2a.integration`                    | A2A client as AI Agent gateway tool                                                             | connector-a2a, api, model, common                                     |
| `connector-aiagent`             | `aiagent` (except `model` and `api`) | AI Agent connectors, providers, stores, agent instance client                                   | tools-schema, api, model, common                                      |
| `agentic-ai-bundle`             | none (`pom` packaging)               | All connectors and gateway tool integrations, for runtimes                                      | aiagent, ad-hoc tools schema, MCP and A2A connectors and integrations |
| `agentic-ai-archunit-tests`     | none (test-only)                     | Architecture tests for the modules above, not shipped                                           | all modules, in test scope                                            |
| `agentic-ai-docs-lint`          | none (test-only)                     | Checks that keep the docs in step with the modules and the code, not shipped                    | none                                                                  |

Client connectors never depend on AI Agent code, and `connector-aiagent` never depends on the clients
or integrations. Package roots are relative to `io.camunda.connector.agenticai`;
put new classes under the root of their module, and no Java package spans several modules. Each module has its own
auto-configuration, element templates and icons.
All artifacts use the group `io.camunda.connector.agenticai`. Runtimes that ship all agentic AI connectors (the
bundles) depend on `agentic-ai-bundle` with `<type>pom</type>`; add a new connector or integration there. The e2e
modules depend on the connector modules they test instead (see the
[e2e tests `AGENTS.md`](../../connectors-e2e-test/connectors-e2e-test-agentic-ai/AGENTS.md#modules)).
Build all of them with `./mvnw clean install -f connectors/agentic-ai/pom.xml`.

### Architecture tests

`agentic-ai-archunit-tests` holds the ArchUnit rules (`archunit-junit5`) for the production classes of
all modules, in package `io.camunda.connector.agenticai.archunit`:

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
  When you break a cycle, delete its entry; never add one.
- `ElementTemplateAnnotationsArchTest`: the library modules do not use the element template generator annotations,
  except the records listed in the test (the ADR 015 follow-up). The list must only shrink.
- `RegistryEncapsulationArchTest`: concrete `ConversationStore` implementations are only used by
  `aiagent.memory.conversation` and `aiagent.configuration`; concrete `ChatModelFactory` implementations are only
  used by `aiagent.chatmodel` and `aiagent.configuration`.
- `GeneralCodingRulesArchTest`: no access to the standard streams, no `java.util.logging`.
- `NamingConventionsArchTest`: a class named `FooImpl` implements the interface `Foo`.
- `ModuleInventoryTest` (plain JUnit): every Maven module of `connectors/agentic-ai` is registered in
  `AgenticAiModules` and the other way round, and `agentic-ai-bundle` depends on exactly the connector and
  integration modules.
- `ApiSignaturesLockTest` (plain JUnit): the public signatures of `aiagent-api` match
  `aiagent-api/docs/api-signatures.lock`.

`AgenticAiModules` maps modules to package roots and kinds (library, connector, integration). When you add
a module, a package root or a dependency between modules, update it and the layer rules; a new connector or
integration also goes into `agentic-ai-bundle`. Run only the architecture tests with:

```bash
./mvnw test -f connectors/agentic-ai/pom.xml -pl agentic-ai-archunit-tests -am \
  '-Dtest=*ArchTest,ModuleInventoryTest,ApiSignaturesLockTest' -Dsurefire.failIfNoSpecifiedTests=false
```

`-am` builds the modules the tests read from their current sources. Keep `-f connectors/agentic-ai/pom.xml`:
it limits `-am` to the modules of `connectors/agentic-ai`. Without `-am`, the `agentic-ai-archunit-tests`
module reads the jars of the other modules from the local Maven repository, so it checks the last install
and not your changes. `./mvnw clean install -f connectors/agentic-ai/pom.xml` runs the architecture tests too.

### Docs checks

`agentic-ai-docs-lint` holds the docs checks (plain JUnit, `ReferenceDocsTest`). Each module README has the
fixed sections and links the docs of its module. Relative links and anchors resolve. The docs hold no `java`
code blocks. The types and members that the docs name in backticks exist in the repository sources. The docs name
every public type that `aiagent-api/docs/api-signatures.lock` declares. Run them with:

```bash
./mvnw test -f connectors/agentic-ai/pom.xml -pl agentic-ai-docs-lint
```

A failure names the file and line, why it fails and what to verify. The code is the source of truth. A warning
does not fail the test. The build log shows each warning as one line `WARN <path>:<line>: <what>`, and the
JUnit report (the IDE test view) holds a `warning` entry with the why and the fix.

`ApiSignaturesLockTest` (in `agentic-ai-archunit-tests`) fails on every `aiagent-api` signature change, additions
included, and lists the docs that name the changed types. A lock change comes with a doc change: update those docs,
then regenerate the lock with
`./mvnw test -f connectors/agentic-ai/pom.xml -pl agentic-ai-archunit-tests -am -Dtest=ApiSignaturesLockTest -Dsurefire.failIfNoSpecifiedTests=false -Dupdate-doc-lock`.
A new public type also needs a mention in the docs of its module, which `agentic-ai-docs-lint` checks.
Revapi ([`docs/api-compatibility.md`](../../docs/api-compatibility.md)) only gates breaking changes against a release, and is skipped here until 8.11.

## Start here, navigation

> Use the table below to find the doc for your task, then read only that doc or section. Each module `README.md` lists the docs of that module.

| Working on…                                                              | Read                                                                                                                                                 |
| ------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| The distributed agent loop / execution model                             | [`agentic-loop.md`](docs/architecture/agentic-loop.md#the-agentic-loop--distributed-execution-model)                                                 |
| Agent state machine & initialization (INITIALIZING/TOOL_DISCOVERY/READY) | [`agent-state-machine.md`](connector-aiagent/docs/agent-state-machine.md)                                                                            |
| Data model (`AgentContext`, `AgentResponse`, `ToolCallProcessVariable`)  | [`data-model.md`](aiagent-model/docs/data-model.md), [`response-handling.md`](connector-aiagent/docs/response-handling.md)                           |
| Conversation memory, storage backends, write-ahead store contract        | [`conversation-store-spi.md`](aiagent-api/docs/conversation-store-spi.md), [`conversation-memory.md`](connector-aiagent/docs/conversation-memory.md) |
| Tool resolution, FEEL `fromAi()` extraction, JSON-schema generation      | [`tool-resolution.md`](aiagent-tools-schema/docs/tool-resolution.md)                                                                                 |
| Job completion / ad-hoc sub-process directives                           | [`agentic-loop.md`](docs/architecture/agentic-loop.md#job-completion--the-heart-of-the-distributed-loop)                                             |
| Tool completion, partial results, no-op completion                       | [`agentic-loop.md`](docs/architecture/agentic-loop.md#what-happens-when-tools-complete)                                                              |
| Concurrency & race conditions (supersession, store-ahead-of-Zeebe)       | [`agentic-loop.md`](docs/architecture/agentic-loop.md#concurrency-challenges--race-conditions)                                                       |
| Event handling (sub-process only)                                        | [`agentic-loop.md`](docs/architecture/agentic-loop.md#event-handling)                                                                                |
| Chat model provider SPI, provider selection, LangChain4j converter chain | [`chat-model-spi.md`](aiagent-api/docs/chat-model-spi.md), [`chat-model.md`](connector-aiagent/docs/chat-model.md), [`langchain4j-chat-model.md`](connector-aiagent/docs/langchain4j-chat-model.md) |
| System prompt composition / contributors                                 | [`system-prompt-contributor.md`](aiagent-api/docs/system-prompt-contributor.md), [`system-prompt.md`](connector-aiagent/docs/system-prompt.md)       |
| Response handling (text / JSON / full message)                           | [`response-handling.md`](connector-aiagent/docs/response-handling.md)                                                                                |
| Error codes                                                              | [`error-codes.md`](connector-aiagent/docs/error-codes.md)                                                                                            |
| Spring auto-configuration, feature toggles, config defaults              | [`spring-auto-configuration.md`](docs/architecture/spring-auto-configuration.md)                                                                     |
| Process instance migration (what's allowed / blocked)                    | [`process-instance-migration.md`](connector-aiagent/docs/process-instance-migration.md)                                                              |
| Gateway tool pattern (the MCP/A2A extensibility mechanism)               | [`extension-points.md`](docs/architecture/extension-points.md#gateway-tool-pattern)                                                                  |
| MCP integration                                                          | [`mcp-gateway-tool.md`](aiagent-mcp-integration/docs/mcp-gateway-tool.md), [`mcp-client.md`](connector-mcp/docs/mcp-client.md)                       |
| A2A integration                                                          | [`a2a-gateway-tool.md`](aiagent-a2a-integration/docs/a2a-gateway-tool.md), [`a2a-client.md`](connector-a2a/docs/a2a-client.md)                       |
| Example BPMN processes & configurations                                  | the examples section of the connector module READMEs, [`examples/`](examples/)                                                                       |
| Agent instance API (status, metrics, history reporting)                  | [`agent-instance.md`](connector-aiagent/docs/agent-instance.md)                                                                                      |
| **Architectural invariants (what you must not break)**                   | [`design-guidelines.md`](docs/design-guidelines.md#architectural-invariants)                                                                         |
| **Extension points** (add provider / contributor / store)                | [`extension-points.md`](docs/architecture/extension-points.md#extension-points)                                                                      |
| Native provider specifics (Anthropic/Bedrock/OpenAI wire-format details) | [`native-providers.md`](connector-aiagent/docs/native-providers.md)                                                                                  |

## Mental model

Two flavors share one orchestration core (`BaseAgentRequestHandler`):

- **AI Agent Task** (`AgentTaskV1Function`): a service-task connector whose tool-calling loop is modeled
  explicitly in BPMN.
- **AI Agent Sub-process** (`AgentSubProcessV1Function`): a job worker on an ad-hoc sub-process (AHSP) whose
  loop is implicit (engine-driven) and supports events. This is the recommended flavor.

The loop (sub-process flavor): each job initializes the agent, loads memory, adds input (user prompt or
tool results plus events), calls the LLM, stores memory, and completes; tool calls activate AHSP
elements and continue the loop, no tool calls finishes it. Zeebe appends each tool result to
`toolCallResults` and creates a new job; if results are still incomplete the worker no-ops and waits.

Full flavor comparison (tool source, config-per-iteration, completion) and the loop diagram:
[`flavors.md`](connector-aiagent/docs/flavors.md) and [`agentic-loop.md`](docs/architecture/agentic-loop.md#the-agentic-loop--distributed-execution-model)
(mechanics in [Job Completion](docs/architecture/agentic-loop.md#job-completion--the-heart-of-the-distributed-loop) and
[What Happens When Tools Complete](docs/architecture/agentic-loop.md#what-happens-when-tools-complete)).

## Glossary

These terms are overloaded; the three `*Context` types are the usual confusion. Full definitions:
[`data-model.md`](aiagent-model/docs/data-model.md) for the data-model types (`AgentContext`,
`AgentExecutionContext`) and [`conversation-memory.md`](connector-aiagent/docs/conversation-memory.md#agentconversation-conversationsnapshot--messagewindowfilter) for the
conversation and turn types (everything else below).

| Term                                  | One-line meaning                                                                                                         |
| ------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| **Turn** (`AgentConversationTurn`)    | one LLM call (input messages, assistant response, per-turn metrics); pending when `assistantMessage == null`             |
| **Iteration** / `iterationKey`        | 1-based counter across the agent's lifetime identifying a turn                                                           |
| **AgentContext**                      | durable persisted state (state, metrics, tool definitions, conversation pointer); the `agentContext` process variable    |
| **ConversationContext**               | pointer inside `AgentContext` to where messages live (in-process holds the messages, other stores hold only the pointer) |
| **AgentExecutionContext**             | transient per-invocation request context (job metadata, provider config, prompts, limits); not persisted                 |
| **AgentConversation**                 | immutable in-process turn aggregate, built per invocation                                                                |
| **Snapshot** (`ConversationSnapshot`) | transient windowed read-only view sent to the LLM                                                                        |
| **Window**                            | sliding message limit (`MessageWindowFilter`) applied to the snapshot; full history is always persisted                  |

## Gotchas

High-frequency traps (detail behind each link):

- **Job supersession / `NOT_FOUND`**: a completing tool creates a new job; the stale in-flight job may be rejected.
  The AI Agent connectors (v1 and v2, both flavors) opt into job leasing (`@OutboundConnector(withLease = true)`), so
  completion is fenced against a superseded activation; `CamundaAgentInstanceClient` forwards the lease token via
  `jobLeaseToken(...)` on each batched turn update
  (`applyTurnStart`/`applyTurnCompletion`/`applyToolCallResults`/`applyToolDiscoveryStart`). The engine rejects
  such an update (`404`) when its job is no longer active; that maps to a non-retryable `AGENT_INSTANCE_SUPERSEDED`
  failure, not the ordinary retry path (ADR 013). An update with a stale lease on a job that is still active is
  accepted, and the engine skips its status change. Gateway/MCP/A2A connectors are not leased. [`agentic-loop.md`](docs/architecture/agentic-loop.md#concurrency-challenges--race-conditions),
  [`agent-instance.md`](connector-aiagent/docs/agent-instance.md).
- **Partial tool results → no-op (but not silent)**: incomplete results make the composer return `Deferred` and the
  worker completes without an LLM call — expected, not a bug — but it now also reports whichever results have
  arrived so far to agent instance history first (ADR 011). E2e tests asserting `verifyNoMoreInteractions` on
  `AgentInstanceClient` for a multi-tool-call turn with staggered completion times must account for this call.
  [`agentic-loop.md`](docs/architecture/agentic-loop.md#what-happens-when-tools-complete).
- **Write-ahead, pointer-based store contract**: `storeMessages` writes a new location; `loadMessages` follows the
  `AgentContext` pointer. Never overwrite what the current pointer references.
  [`conversation-store-spi.md`](aiagent-api/docs/conversation-store-spi.md#storage-contract).
- **Sub-process config frozen at AHSP entry**: input mappings evaluate once, so config/migration changes don't reach a
  running instance (Task flavor re-evaluates per iteration).
  [`process-instance-migration.md`](connector-aiagent/docs/process-instance-migration.md).
- **Migration blocked cases**: removing/renaming tools or adding/removing gateway tools fails; adding tools or changing
  descriptions is allowed. [`process-instance-migration.md`](connector-aiagent/docs/process-instance-migration.md).
- **`toolCallResult = ""` scoping trick**: the empty default keeps the variable local to the tool element so it doesn't
  bubble up to the AHSP scope.
  [`agentic-loop.md`](docs/architecture/agentic-loop.md#job-completion--the-heart-of-the-distributed-loop).
- **Events have `id = null`**: tool-call results without an id are event results, partitioned from real tool results.
  [`agentic-loop.md`](docs/architecture/agentic-loop.md#event-handling).
- **Gateway `transformToolCallResults` must carry `completedAt` forward**: MCP/A2A handlers rebuild a
  new `ToolCallResult` when unwrapping the gateway envelope; unlike `elementId`, `completedAt` has no
  fallback resolution and is silently dropped if not copied explicitly.
  [`extension-points.md`](docs/architecture/extension-points.md#gateway-tool-pattern), [`agent-instance.md`](connector-aiagent/docs/agent-instance.md).
- **Agent instance features gate behavior per instance**: `AgentContext.features` is set only when a new agent
  instance creates its context (`AgentInitializerImpl`) and never added to a context that exists without it. An
  instance without the `runtimeTools` feature (created before it) is offered no runtime tools and no tool element is
  checked against runtime tool names; the feature gates only what is offered. Gate new behavior that changes the wire
  format or the persisted shape on `AgentContext.supports(...)`. The one deliberate exception is the tagging and the
  rejection of tool calls (next item), which applies to every agent instance: handing the call of an unknown tool to
  the process made the agent wait for a result that never came, without an incident. [ADR 016](docs/adr/016-runtime-tools.md).
- **Tool calls are admitted once, and the agent produces the tool call results of local tool calls itself**: before an
  assistant message enters the conversation, `ToolCallAdmission` sets `ToolCall.kind` to the kind of the tool of the call's name among the
  tools offered in the job, and `ToolCall.rejection` (the error text) when the call must not be carried out, such as a name that is not offered, and never computes either
  again, for every agent instance, with or without the `runtimeTools` feature. Both are persisted (`kind` written only
  for `RUNTIME`, `rejection` only when set), so every conversation store must keep them. A call with a rejection
  (a rejected call) gets its rejection as its tool call result and is never executed, also when a runtime tool of that
  name is enabled later; the calls of runtime tools go to `RuntimeTools`. Both are local tool calls
  (`ToolCall.isLocal()`), the calls whose tool call result the agent produces itself: the rejection when the
  `ToolCallBatch` is created, the calls of runtime tools in `LocalToolCallExecutor` only. An interrupting event cancels
  every call without a result, also the calls of runtime tools that were not executed yet. When the model issues local tool calls and process tool calls together, only
  the process tool calls are activated, and the agent adds the tool call results of the local tool calls when the next
  turn is composed, merged with the process results in call order. [`agentic-loop.md`](docs/architecture/agentic-loop.md#what-happens-when-tools-complete), [ADR 016](docs/adr/016-runtime-tools.md).
- **`Message.id()` is randomly generated per construction, not deterministic**: two freshly built
  messages with identical content have *different* ids. Stability only holds once a message has
  been persisted and reloaded (or the exact same instance is reused) — tests that independently
  construct an "expected" message and compare it for equality against one built by the code under
  test will fail on `id` alone. [`data-model.md`](aiagent-model/docs/data-model.md#message-identity), [ADR 012](docs/adr/012-stable-self-generated-message-ids.md).
- **Map iteration order differs between JVMs after a record builder copied the map**: the `@AgenticAiRecord`
  builders copy maps and sets with `Map.copyOf` and `Set.copyOf`, whose iteration order changes with every JVM start.
  Never let a hash, an id or any value compared across runs depend on that order. Sort the map keys where the value
  is computed (`SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS` in `AgentConfiguration.fingerprint()`, which sorts
  every map at any depth), and carry an order that has meaning, such as the order of the tool parameters, as a list.
  No mapper flag sorts a set, so a hashed value must not contain one. Do not add a record annotation or a special
  collection type to work around the copy. [`agent-instance.md`](connector-aiagent/docs/agent-instance.md#conversation-history-items).

## Architectural invariants

Before changing module structure or extension points, read the
[architectural invariants](docs/design-guidelines.md#architectural-invariants).

The [target architecture](docs/target-architecture.md) shows the target state beyond the invariants.

## Working in this module

Read the [implementation rules](docs/implementation-rules.md) and the
[documentation rules](docs/documentation-rules.md) before you plan, implement or review a change.

Before you design stored shapes, names, defaults, limits or observability, also read
[`docs/design-guidelines.md`](docs/design-guidelines.md). Before you plan a structural change (a new
module, collaborator, SPI or cross-module policy), also read the
[target architecture](docs/target-architecture.md). The sections below are the module specifics.

### Building & testing

```bash
mvn clean install -f connectors/agentic-ai/pom.xml               # build the module, including tests
mvn clean install -DskipTests -f connectors/agentic-ai/pom.xml   # build the module, skipping tests
mvn test -f connectors/agentic-ai/pom.xml                        # unit / integration tests
```

For every code change, follow [`docs/acceptance-testing.md`](docs/acceptance-testing.md) to choose
the test level and establish feature acceptance. Repo-wide build/commit/PR/CI/spotless/license rules
live in the repo-root [`AGENTS.md`](../../AGENTS.md).

### Null safety

All production code is `@NullMarked` via per-package `package-info.java`: every reference type is non-null by default.

- Add `@Nullable` (from `org.jspecify.annotations`) on fields, parameters, and return types that may be absent.
- Fix null-safety errors by handling the null. Never suppress them.
- `@NullUnmarked` is a named deferral only. Add a comment explaining why; file a follow-up issue.
- For third-party APIs without nullability annotations, inspect their source to determine the actual contract.
- After adding a new package, run `connector-aiagent/bin/generate-package-info.groovy` in the module
  that got the package to fill in any missing `package-info.java` (it's idempotent and scans every
  package under that module's `src/main/java`, so it also catches pre-existing gaps, not just the one
  you added):
  ```bash
  cd connectors/agentic-ai/<module>
  mvn org.codehaus.gmavenplus:gmavenplus-plugin:5.1.0:execute -Dscripts="file:///$(pwd)/../connector-aiagent/bin/generate-package-info.groovy"
  ```
  Run `mvn license:format` on the module afterwards — the script doesn't stamp the license header — then
  commit the generated file(s).

### ErrorProne

ErrorProne warnings fail the `main` compilation (`-Werror`); generated sources under
`target/generated-sources` are excluded. Fix a finding, or suppress it with `@SuppressWarnings("<CheckName>")`
on the narrowest scope plus a comment stating why. Test code is not checked.

### Definition of Done

Before claiming a change is complete:

- [ ] Module builds (`mvn clean install -f connectors/agentic-ai/pom.xml`).
   - [ ] If deviating from the `mvn install` command, ensure `mvn spotless:apply` and `mvn license:format` run clean (pre-commit hooks enforce these).
- [ ] Relevant unit, component or integration tests added/updated and passing.
- [ ] E2E test decision made per the [testing rules](docs/acceptance-testing.md#choosing-e2e-vs-unit-tests-mandatory-decision): test added, reason stated, or question asked.
- [ ] User-facing features meet the [acceptance rules](docs/acceptance-testing.md).
- [ ] New stored-shape, customer-facing naming, default or limit decisions and their rationale are
  stated in the PR description per the [design guidelines](docs/design-guidelines.md).
- [ ] Real-provider changes meet the [coverage requirements](docs/acceptance-testing.md#real-provider-coverage).
- [ ] Element templates regenerated (`mvn clean compile -f connectors/agentic-ai/pom.xml`) if template properties changed;
  check the JSON diff.
- [ ] The change follows the [implementation rules](docs/implementation-rules.md).
- [ ] Documentation updated per [Keeping documentation up to date](#keeping-documentation-up-to-date).
- [ ] Breaking changes for custom extensions are recorded in [`docs/breaking-changes.md`](docs/breaking-changes.md).
  The build does not check this yet: the API compatibility check skips `aiagent-api` and
  `aiagent-model` until 8.11.0 is its baseline. From then on, a break of these modules fails the build
  until it is accepted in the module's `api-changes.json` with a link to its `breaking-changes.md`
  anchor ([API compatibility check](../../docs/api-compatibility.md)).

## Extension points

Add an LLM provider, a `SystemPromptContributor`, a `ConversationStore`, a gateway tool, or a runtime
tool (a tool the connector runtime executes itself, without activating a BPMN element; a process tool must not have its name)
by implementing the SPI and registering a Spring bean. A runtime tool is the three-method interface `RuntimeTool`
(`definition`, `isEnabled`, `call`), an experimental SPI in 8.11. Where to start (SPI + reference implementation per
type): [Extension Points](docs/architecture/extension-points.md#extension-points) (gateway tools:
[Gateway Tool Pattern](docs/architecture/extension-points.md#gateway-tool-pattern); runtime tools:
[Add a runtime tool](docs/architecture/extension-points.md#add-a-runtime-tool) and [ADR 016](docs/adr/016-runtime-tools.md)).

## Element templates

The JSON element templates are **generated**, not hand-edited. They are produced from the
`@ElementTemplate`-annotated connector functions and their bound data models (`@TemplateProperty`
fields), so the source of truth is the Java, not the JSON. The template version comes from the
annotation's `version` attribute on the connector function; bumping it there bumps the generated
template. The AI Agent Sub-process template is in turn derived from the AI Agent Task
template via `connector-aiagent/bin/transform-ai-agent-sub-process-template.groovy` (gmavenplus-plugin, `process-classes`
phase); the same script also derives the v2 AI Agent Sub-process template from the v2 AI Agent Task template.

The transform scripts in `connector-aiagent/bin/` and `connector-a2a/bin/` find properties by hard-coded
ids. They share generic helpers through [`bin/TemplateTransforms.groovy`](bin/TemplateTransforms.groovy), which
fails the build when an expected property id or anchor is missing; AI Agent specific helpers live in
[`connector-aiagent/bin/AgentTemplateTransforms.groovy`](connector-aiagent/bin/AgentTemplateTransforms.groovy). When you rename a property a script
looks up, update the script in the same change. `AgentElementTemplateTransformsTest` (`connector-aiagent`)
and `A2aElementTemplateTransformsTest` (`connector-a2a`) assert what the scripts inject into the
generated JSON. They check what the scripts change, not property order, so reordering properties
doesn't require test changes; review any generated template diff instead.

To regenerate, run `mvn clean compile -f connectors/agentic-ai/pom.xml` and commit the JSON diff; never edit
the generated JSON by hand. For the generation mechanism and annotation reference, see the
[element template generator documentation](https://docs.camunda.io/docs/components/connectors/custom-built-connectors/connector-sdk/#element-template-generation).
When a version is bumped, a template moves into `versioned/`, or a connector is added, also update the
README in the `element-templates` folder of the connector module (for example
[`connector-aiagent/element-templates/README.md`](connector-aiagent/element-templates/README.md)) following
these rules. The index of all modules is
[`connector-agentic-ai/element-templates/README.md`](connector-agentic-ai/element-templates/README.md). It
stays at the path the templates had before the module split because external documentation links to it,
and the `element-templates` symlink in this directory points to it.

1. **Identify the new minimum Camunda version** by checking the `engines.camunda` field of the new
   template (e.g. `^8.10`).
2. **Same minimum as the current top row**: bump the top row's template version and keep its link
   pointing to the latest `./<file>.json` in this folder. The superseded version moves into `versioned/`
   but is not listed (the table shows only the latest template per minimum Camunda version).
3. **Higher minimum than the current top row**: insert a new top row with the new minimum Camunda
   version and template version, and move the previous top row's link under `versioned/`.
4. **AI Agent has two tables** (Task and Sub-process) sharing the same version numbers. Update both.
5. **New connector**: in an existing module, add a section to the README of the module with an intro
   paragraph linking to the `docs.camunda.io` overview page. In a new module, add a README like the
   existing ones, including the "Which template do I need?" section, and add the module to the table
   and the sections of the index in the same order as the existing ones (AI Agent, MCP Client, A2A,
   Ad-hoc tools schema).

Do not list `hybrid/` templates in the README. They are intentionally omitted.

When the `engines.camunda` minimum of the AI Agent templates is bumped (rule 3 above), also add regression
fixtures for the new template generation in the same change, as described in
[Element template regression fixtures](../../connectors-e2e-test/connectors-e2e-test-agentic-ai/AGENTS.md#element-template-regression-fixtures).

### Template text

When you write or change help text in `@TemplateProperty` annotations (`label`, `tooltip`,
`placeholder`, `description`), follow [`docs/template-text-guidelines.md`](docs/template-text-guidelines.md).

### Agent definition marker

All AI Agent Task and Sub-process templates (v1 and v2, all flavors, including hybrid) carry a
hidden `zeebe:agentDefinition` property so the engine detects a native agent at deploy time
(camunda/connectors#8176). `transform-ai-agent-task-template.groovy` adds it to each task template
(`agentType=aiAgentTask`); `transform-ai-agent-sub-process-template.groovy` adds its own
(`agentType=aiAgentSubProcess`) to the derived sub-process template. Both take `agentType` as a
Maven property. Both scripts drop any incoming `zeebe:agentDefinition` before adding their own, so
execution order does not matter — the sub-process transformer cannot carry the task marker into the
derived template regardless of when the task-marker script runs.

### Deprecation marker (v1 only)

Only the current main/hybrid v1 AI Agent Task and Sub-process templates (version 13, minimum Camunda
8.10) carry a top-level `deprecated` block pointing modelers to the v2 template, and have `(Deprecated)`
appended to their `name`. Their `versioned/` snapshots at versions 10–12 do not: Web Modeler never
offers an Update action on a deprecated template, so deprecating those older versions too would strand
anyone still on them with no click-path to version 13 (see #8431's follow-up revert). Earlier v1
versions (8.8/8.9-minimum, versions 0–7) carry neither, and neither do v2 templates. For the generated
main/hybrid templates, both transform scripts accept optional `deprecationMessage` /
`deprecationDocumentationRef` Maven properties — wired only into the v1 executions in `pom.xml` — and
each script strips any inherited `deprecated` block before deciding its own and appending `(Deprecated)`
to `name` if not already present, the same pattern used for the agent definition marker above.
`versioned/` snapshots are static and are patched by hand instead (three pre-8.10 snapshots already
carried an unrelated legacy `"deprecated": true` boolean flag and were left untouched).

## Key entry points

| File                                        | Purpose                                    |
| ------------------------------------------- | ------------------------------------------ |
| `AgentTaskV1Function.java`                  | Connector (Task) entry point               |
| `AgentSubProcessV1Function.java`            | Job worker (Sub-process) entry point       |
| `BaseAgentRequestHandler.java`              | Core orchestrator (shared by both flavors) |
| `AgenticAiConnectorsAutoConfiguration.java` | Spring Boot wiring                         |

## Architecture decision records

When planning a significant technical decision, use the
[ADR criteria](docs/design-guidelines.md#architecture-decision-records).
When writing or revising an ADR, read the [authoring rules](docs/adr/README.md).

## Keeping documentation up to date

When a change touches documented structure (classes, interfaces, data-model records, config
properties, error codes, behavioral contracts), update the matching doc in the same change:

- **`AGENTS.md`** (this file): high-level orientation (mental model, navigation, glossary, gotchas,
  build/test commands, entry points). Design rules live in `docs/design-guidelines.md`; testing rules
  live in `docs/acceptance-testing.md`; ADR authoring rules live in `docs/adr/README.md`.
- **`<module>/README.md`** and **`<module>/docs/`**: the purpose and key types of a module, and the docs
  about its types (data model, memory, tools, converters, providers, error codes, MCP, A2A). The
  [docs checks](#docs-checks) enforce the README layout and the links.
  `connector-aiagent/README.md` is generated: edit `connector-aiagent/README.peb`. It uses the shared
  connector README layout, so its title is `# AI Agent Task` instead of `# connector-aiagent`.
- **`docs/architecture/`**: content that spans modules (the agentic loop, extension points, Spring
  auto-configuration).
- **`docs/design-guidelines.md`**: the architectural invariants.
- **`docs/breaking-changes.md`**: breaking changes for custom extensions (SPIs, public classes and
  packages, persisted data, job types, configuration). Add an entry in the same change that
  introduces the break, under the section of the release in development, with the old and new API
  and a migration hint. The public docs link to it.
- **`<connector module>/element-templates/README.md`** and the index
  **`connector-agentic-ai/element-templates/README.md`**: template version bumps, moves to `versioned/`, or new
  connectors (maintenance rules are in the Element templates section above).

After a doc change, run the [docs checks](#docs-checks). The
[documentation rules](docs/documentation-rules.md) say where each doc goes and how to find the docs that a
change affects.

> `CLAUDE.md` in this directory imports this file via `@AGENTS.md`, giving a single source of truth.
> Edit `AGENTS.md`; never add content directly to `CLAUDE.md`.
