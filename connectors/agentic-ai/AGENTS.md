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
3. Follow the [operating rules](#working-in-this-module), especially the e2e-vs-unit decision rule
   and the Definition of Done, before declaring a change complete.

Deep architecture lives in the reference docs, linked instead of copied:

- [`docs/reference/ai-agent.md`](docs/reference/ai-agent.md): core AI Agent architecture (sections cited as
  `ai-agent.md §N`)
- [`docs/reference/mcp.md`](docs/reference/mcp.md): MCP integration
- [`docs/reference/a2a.md`](docs/reference/a2a.md): A2A integration
- [`docs/reference/native-providers.md`](docs/reference/native-providers.md): per-provider specifics
  for the native/v2 (non-LangChain4j) chat model providers
- [`docs/adr/`](docs/adr/): architecture decision records
- [`docs/breaking-changes.md`](docs/breaking-changes.md): breaking changes for custom extensions, per release
- E2E tests [`AGENTS.md`](../../connectors-e2e-test/connectors-e2e-test-agentic-ai/AGENTS.md): e2e
  modules, running e2e tests, real-LLM CPT coverage
- Repo-root [`AGENTS.md`](../../AGENTS.md): repo-wide build, commit, PR, CI, spotless, license, and
  element-template conventions. These are not duplicated here.

## Modules

The module is split into Maven modules with a fixed dependency direction
([ADR 015](docs/adr/015-split-agentic-ai-into-modules.md)):

| Module                          | Package root                         | Contents                                                       | Depends on (agentic AI modules)   |
|---------------------------------|--------------------------------------|----------------------------------------------------------------|-----------------------------------|
| `agentic-ai-common`             | `common`                             | Shared helpers grouped by topic (`json`, `retry`, `http`, `validation`, ...), common properties | none                              |
| `aiagent-model`                 | `aiagent.model`                      | Agent model referenced by the extension points                 | common                            |
| `aiagent-api`                   | `aiagent.api`                        | Extension points (gateway tools, chat model, storage, prompts) | model, common                     |
| `aiagent-tools-schema`          | `tools.schema`                       | Ad-hoc tools schema resolution                                 | api, model, common                |
| `connector-ad-hoc-tools-schema` | `adhoctoolsschema`                   | Ad-hoc tools schema connector                                  | tools-schema                      |
| `connector-mcp`                 | `mcp.client`                         | MCP client connectors                                          | common                            |
| `aiagent-mcp-integration`       | `mcp.integration`                    | MCP client as AI Agent gateway tool                            | connector-mcp, api, model, common |
| `connector-a2a`                 | `a2a.client`                         | A2A client connectors                                          | common                            |
| `aiagent-a2a-integration`       | `a2a.integration`                    | A2A client as AI Agent gateway tool                            | connector-a2a, api, model, common |
| `connector-aiagent`             | `aiagent` (except `model` and `api`) | AI Agent connectors, providers, stores, agent instance client  | tools-schema, api, model, common  |
| `agentic-ai-bundle`             | none (`pom` packaging)               | All connectors and gateway tool integrations, for runtimes     | aiagent, ad-hoc tools schema, MCP and A2A connectors and integrations |
| `agentic-ai-archunit-tests`     | none (test-only)                     | Architecture tests for the modules above, not shipped          | all modules, in test scope        |

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

`AgenticAiModules` maps modules to package roots and kinds (library, connector, integration). When you add
a module, a package root or a dependency between modules, update it and the layer rules; a new connector or
integration also goes into `agentic-ai-bundle`. Run only the architecture tests with:

```bash
./mvnw test -f connectors/agentic-ai/pom.xml -pl agentic-ai-archunit-tests -am \
  '-Dtest=*ArchTest,ModuleInventoryTest' -Dsurefire.failIfNoSpecifiedTests=false
```

`-am` builds the modules the tests read from their current sources. Keep `-f connectors/agentic-ai/pom.xml`:
it limits `-am` to the modules of `connectors/agentic-ai`. Without `-am`, the `agentic-ai-archunit-tests`
module reads the jars of the other modules from the local Maven repository, so it checks the last install
and not your changes. `./mvnw clean install -f connectors/agentic-ai/pom.xml` runs the architecture tests too.

## Start here, navigation

> **`ai-agent.md` is a long reference.** Use the table below to locate the exact section, then read it by anchor or `Read` with `offset`/`limit`. Do not ingest the whole file.

| Working on…                                                              | Read                                                                                    |
|--------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| The distributed agent loop / execution model                             | [§3](docs/reference/ai-agent.md#3-the-agentic-loop)                                     |
| Agent state machine & initialization (INITIALIZING/TOOL_DISCOVERY/READY) | [§4](docs/reference/ai-agent.md#4-agent-state-machine)                                  |
| Data model (`AgentContext`, `AgentResponse`, `ToolCallProcessVariable`)  | [§5](docs/reference/ai-agent.md#5-data-model)                                           |
| Conversation memory, storage backends, write-ahead store contract        | [§6](docs/reference/ai-agent.md#6-conversation-memory)                                  |
| Tool resolution, FEEL `fromAi()` extraction, JSON-schema generation      | [§7](docs/reference/ai-agent.md#7-tool-resolution)                                      |
| Job completion / ad-hoc sub-process directives                           | [§8](docs/reference/ai-agent.md#8-job-completion)                                       |
| Tool completion, partial results, no-op completion                       | [§9](docs/reference/ai-agent.md#9-tool-completion)                                      |
| Concurrency & race conditions (supersession, store-ahead-of-Zeebe)       | [§10](docs/reference/ai-agent.md#10-concurrency)                                        |
| Event handling (sub-process only)                                        | [§11](docs/reference/ai-agent.md#11-event-handling)                                     |
| Chat model provider SPI & LangChain4j converter chain                    | [§12](docs/reference/ai-agent.md#12-framework-abstraction)                              |
| System prompt composition / contributors                                 | [§13](docs/reference/ai-agent.md#13-system-prompt-composition)                          |
| Response handling (text / JSON / full message)                           | [§14](docs/reference/ai-agent.md#14-response-handling)                                  |
| Error codes                                                              | [§15](docs/reference/ai-agent.md#15-error-codes)                                        |
| Spring auto-configuration, feature toggles, config defaults              | [§16](docs/reference/ai-agent.md#16-spring-auto-configuration)                          |
| Process instance migration (what's allowed / blocked)                    | [§17](docs/reference/ai-agent.md#17-migration)                                          |
| Code paths & class diagram                                               | [§18](docs/reference/ai-agent.md#18-code-paths)                                         |
| Gateway tool pattern (the MCP/A2A extensibility mechanism)               | [§19](docs/reference/ai-agent.md#19-gateway-tool-pattern)                               |
| MCP integration                                                          | [§20](docs/reference/ai-agent.md#20-mcp-integration), [`mcp.md`](docs/reference/mcp.md) |
| A2A integration                                                          | [§21](docs/reference/ai-agent.md#21-a2a-integration), [`a2a.md`](docs/reference/a2a.md) |
| Example BPMN processes & configurations                                  | [§22](docs/reference/ai-agent.md#22-examples), [`examples/`](examples/)                 |
| Agent instance API (status, metrics, history reporting)                  | [§23](docs/reference/ai-agent.md#23-agent-instance-integration)                         |
| **Architectural invariants (what you must not break)**                   | [§24](docs/reference/ai-agent.md#24-architectural-invariants)                           |
| **Extension points** (add provider / contributor / store)                | [§25](docs/reference/ai-agent.md#25-extension-playbooks)                                |
| Native provider specifics (Anthropic/Bedrock/OpenAI wire-format details) | [`native-providers.md`](docs/reference/native-providers.md)                             |

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
`ai-agent.md` §2 and §3 (mechanics in §8, §9).

## Glossary

These terms are overloaded; the three `*Context` types are the usual confusion. Full definitions:
[§5](docs/reference/ai-agent.md#5-data-model) for the data-model types (`AgentContext`,
`AgentExecutionContext`) and [§6](docs/reference/ai-agent.md#6-conversation-memory) for the
conversation and turn types (everything else below).

| Term                                  | One-line meaning                                                                                                         |
|---------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
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
  (`applyTurnStart`/`applyTurnCompletion`/`applyToolCallResults`/`applyToolDiscoveryStart`), so a
  stale activation's writes are rejected (`404`) too — mapped to a non-retryable `AGENT_INSTANCE_SUPERSEDED` failure,
  not the ordinary retry path (ADR 013). Gateway/MCP/A2A connectors are not leased. `ai-agent.md` §10, §23.
- **Partial tool results → no-op (but not silent)**: incomplete results make the composer return `Deferred` and the
  worker completes without an LLM call — expected, not a bug — but it now also reports whichever results have
  arrived so far to agent instance history first (ADR 011). E2e tests asserting `verifyNoMoreInteractions` on
  `AgentInstanceClient` for a multi-tool-call turn with staggered completion times must account for this call.
  `ai-agent.md` §9.
- **Write-ahead, pointer-based store contract**: `storeMessages` writes a new location; `loadMessages` follows the
  `AgentContext` pointer. Never overwrite what the current pointer references. `ai-agent.md` §6.
- **Sub-process config frozen at AHSP entry**: input mappings evaluate once, so config/migration changes don't reach a
  running instance (Task flavor re-evaluates per iteration). `ai-agent.md` §17.
- **Migration blocked cases**: removing/renaming tools or adding/removing gateway tools fails; adding tools or changing
  descriptions is allowed. `ai-agent.md` §17.
- **`toolCallResult = ""` scoping trick**: the empty default keeps the variable local to the tool element so it doesn't
  bubble up to the AHSP scope. `ai-agent.md` §8.
- **Events have `id = null`**: tool-call results without an id are event results, partitioned from real tool results.
  `ai-agent.md` §11.
- **Gateway `transformToolCallResults` must carry `completedAt` forward**: MCP/A2A handlers rebuild a
  new `ToolCallResult` when unwrapping the gateway envelope; unlike `elementId`, `completedAt` has no
  fallback resolution and is silently dropped if not copied explicitly. `ai-agent.md` §19, §23.
- **`Message.id()` is randomly generated per construction, not deterministic**: two freshly built
  messages with identical content have *different* ids. Stability only holds once a message has
  been persisted and reloaded (or the exact same instance is reused) — tests that independently
  construct an "expected" message and compare it for equality against one built by the code under
  test will fail on `id` alone. `ai-agent.md` §6, [ADR 012](docs/adr/012-stable-self-generated-message-ids.md).
- **Map iteration order differs between JVMs after a record builder copied the map**: the `@AgenticAiRecord`
  builders copy maps and sets with `Map.copyOf` and `Set.copyOf`, whose iteration order changes with every JVM start.
  Never let a hash, an id or any value compared across runs depend on that order. Canonicalize where the value is
  computed: sort the keys where the order has no meaning (the top-level keys of a tool input schema in
  `AgentConfiguration.fingerprint()`), and keep the authored order where it has meaning (nested schema maps are
  insertion-ordered). Do not add a record annotation or a special collection type to work around the copy.
  `ai-agent.md` §23.

## Architectural invariants

Do not break these (full statement and rationale in
[§24](docs/reference/ai-agent.md#24-architectural-invariants)). The
[architecture tests](#architecture-tests) enforce the first three:

- **Framework-agnostic core.** Only `aiagent/chatmodel/provider/langchain4j/**` may import `dev.langchain4j.*`.
  The agent core (`aiagent/agent`, `aiagent/model`, `aiagent/memory`, the root `model/`) stays
  framework-neutral.
- **Domain types never leak framework types.** The domain `Message` / `ToolCall` / `Content` model
  (`io.camunda.connector.agenticai.aiagent.model.*`) is translated to/from LangChain4j only through the
  converter chain (`ChatMessageConverter`, `ToolSpecificationConverter`, and friends).
  [§12](docs/reference/ai-agent.md#12-framework-abstraction).
- **Interface in package root, `*Impl` alongside.** Public collaborators are interfaces
  (`AgentToolsResolver`, `ConversationStoreRegistry`, `SystemPromptComposer`, and others), usually with one
  `*Impl`. Wire impls as Spring beans, depend on the interface. The architecture tests check that a class
  named `FooImpl` implements the interface `Foo`; review checks the rest.
- **SPIs are plug-in points.** Add an LLM provider, system-prompt contributor, conversation store, or
  gateway tool by implementing the SPI and registering a bean. See
  [§25](docs/reference/ai-agent.md#25-extension-playbooks).

## Working in this module

General coding behavior (think before coding, simplicity, surgical changes, goal-driven execution)
lives in [`docs/coding-guidelines.md`](docs/coding-guidelines.md). The rules below are the module
specifics.

### Building & testing

```bash
mvn clean install -f connectors/agentic-ai/pom.xml               # build the module, including tests
mvn clean install -DskipTests -f connectors/agentic-ai/pom.xml   # build the module, skipping tests
mvn test -f connectors/agentic-ai/pom.xml                        # unit / integration tests
```

Unit tests use JUnit 5, Mockito, and AssertJ. In this module use unit tests only, apart from the few
existing Spring Boot tests and the e2e suite. For repo-wide build/commit/PR/CI/spotless/license rules,
see the repo-root [`AGENTS.md`](../../AGENTS.md). Do not duplicate them here.

E2E tests live in `connectors-e2e-test/connectors-e2e-test-agentic-ai/` (Camunda Process Test scenarios
plus WireMock LLM stubs, and acceptance tests against real LLM providers). Its
[`AGENTS.md`](../../connectors-e2e-test/connectors-e2e-test-agentic-ai/AGENTS.md) describes the e2e
modules, their prerequisites, how to run them, where new tests go, and the real-LLM CPT coverage.
The [E2E runbook](../../connectors-e2e-test/connectors-e2e-test-agentic-ai/README.md) covers test
ownership, local commands, paid-provider authorization, and how to verify that tests ran.
Whether a change needs an e2e test is decided by the rule [below](#choosing-e2e-vs-unit-tests-mandatory-decision).

**Test approach**: follow TDD (red, green, refactor) wherever possible. E2e tests can be written
implementation-independently first: express the behavior, watch it fail, implement, then confirm it
passes. Tests should be usecase- and behavior-driven, asserting real behavior. Avoid tests that exist
only to close coverage gaps in a per-class equivalence suite.

### Choosing e2e vs unit tests (mandatory decision)

For every code change, explicitly decide whether an e2e test is warranted, using this rule. If you add
none for a change that touches the element template or behavioral characteristics, say why. If you are unsure, ask before
starting a change. The examples below are illustrative, not exhaustive; when a case is not
listed, reason from the principle (does correctness depend on the engine and BPMN wiring?).

- **E2E test required** when the change touches the distributed / BPMN contract:
    - job completion semantics or ad-hoc sub-process directives
    - element activation from tool calls
    - variable flow to/from Zeebe (`toolCallResults`, `agentContext`, `toolCall`, `toolCallResult`)
    - conversation memory persistence across job boundaries
    - event handling (interrupt / wait behaviors)
    - the full request → LLM → response cycle
    - element-template behavior
- **Unit-test-only is sufficient** when the change is internal to a single class/converter and provable
  without the engine:
    - prompt composition, schema generation, content/tool conversion
    - response formatting, error messages, pure transformations

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

### Definition of Done

Before claiming a change is complete:

- [ ] Module builds (`mvn clean install -f connectors/agentic-ai/pom.xml`).
   - [ ] If deviating from the `mvn install` command, ensure `mvn spotless:apply` and `mvn license:format` run clean (pre-commit hooks enforce these).
- [ ] Unit tests added/updated and passing.
- [ ] E2E test decision made per the rule above (test added, or reason stated, or question asked).
- [ ] Real-provider changes update the `RealProviderApiSmokeSupport` provider/capability matrix and
  `RealProviderSelectionTest` where applicable, plus the corresponding `ci: true` registry row or an
  explicit manual-only decision in `requiredCiCapabilities` (see
  [Real-LLM CPT coverage](../../connectors-e2e-test/connectors-e2e-test-agentic-ai/AGENTS.md#real-llm-cpt-coverage)).
- [ ] Element templates regenerated (`mvn clean compile -f connectors/agentic-ai/pom.xml`) if template properties changed;
  check the JSON diff.
- [ ] Documentation updated per [Keeping documentation up to date](#keeping-documentation-up-to-date).
- [ ] Breaking changes for custom extensions are recorded in [`docs/breaking-changes.md`](docs/breaking-changes.md).

## Extension points

Add an LLM provider, a `SystemPromptContributor`, a `ConversationStore`, or a gateway tool by
implementing the SPI and registering a Spring bean. Where to start (SPI + reference implementation per
type): `ai-agent.md` §25 (gateway tools: §19).

## Element templates

The JSON element templates are **generated**, not hand-edited. They are produced from the
`@ElementTemplate`-annotated connector functions and their bound data models (`@TemplateProperty`
fields), so the source of truth is the Java, not the JSON. The template version comes from the
annotation's `version` attribute on the connector function; bumping it there bumps the generated
template. The AI Agent Sub-process template is in turn derived from the AI Agent Task
template via `connector-aiagent/bin/transform-ai-agent-sub-process-template.groovy` (gmavenplus-plugin, `process-classes`
phase); the same script also derives the v2 AI Agent Sub-process template from the v2 AI Agent Task template.

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
|---------------------------------------------|--------------------------------------------|
| `AgentTaskV1Function.java`                  | Connector (Task) entry point               |
| `AgentSubProcessV1Function.java`            | Job worker (Sub-process) entry point       |
| `BaseAgentRequestHandler.java`              | Core orchestrator (shared by both flavors) |
| `AgenticAiConnectorsAutoConfiguration.java` | Spring Boot wiring                         |

Full code-path reference and class diagram: `ai-agent.md` §18.

## Architecture decision records

ADRs in [`docs/adr/`](docs/adr/) capture significant technical decisions (context, alternatives,
rationale). Write an ADR when a change chooses between meaningful alternatives, such as replacing a
framework, restructuring a core subsystem, or changing a storage strategy. Routine bug fixes,
refactors, and pattern-following feature additions do not need one.

**Write the ADR before implementation.** It is the primary artifact of the planning phase and the
major context for it: capture the decision and its drivers up front, so review happens on the decision
rather than after the code exists.

**An ADR is immutable once merged.** It may still be revised within the scope of the PR that
introduces it. After it lands on `main`, leave it as-is: supersede or extend it with a new ADR, marking
the old one's Status as `Superseded` and linking the replacement.

Follow the structure of existing ADRs (see [ADR 001](docs/adr/001-replace-mcp-client-framework.md)):
Title, Deciders/Date, Status, Context & Problem, Decision Drivers, Considered Options, Decision
Outcome.

**Keep it at decision altitude.** An ADR states the problem, the drivers, the options, and the
choice. It is not a mechanism walkthrough: no code excerpts, no line-number citations, no
class-by-class narration of how the implementation works. That level of detail belongs in the code
itself or in the reference docs, and it rots as the code changes while the decision doesn't. If a
paragraph explains *how* something works rather than *why* it was chosen this way, cut it.

**ADRs own the why; reference docs own the current state.** `ai-agent.md` and friends describe the
system as it is today, not how it got there. Don't write "this used to be a no-op, but now..." in a
reference doc — state the current behavior plainly and, if the history matters, link to the ADR
that made the change. Conversely, don't restate current implementation detail in the ADR beyond
what's needed to justify the decision.

**Known gaps get a named Follow-up, not a repeated disclaimer.** If a decision leaves a rough edge
(a duplicate write, a cosmetic divergence, a missing feature blocked on something external), say so
once, in a `## Follow-up` section, with the concrete condition and mechanism that closes it. Don't
scatter "accepted as a tradeoff until X" across every file the change touches — state the fact where
it's observed, and point once at the ADR for the reasoning.

**Code comments and javadoc state their own contract, not another module's rationale.** A method's
doc should describe what it does and what it requires of its own parameters, including the precise
state or trigger a caller must observe — even when a sibling type in the same package expresses that
most precisely (e.g. `handleNoInput`'s link to `CompositionResult.NoInput`). Draw the line at the
package/module boundary: don't pull in a different module's internal types or decision history just
to justify a precondition (e.g. an `agentinstance`-package method explaining its own contract via
the `agent`-package composer's internals). If a precondition can only be understood by reading a
caller in a different module, that coupling belongs in the ADR or the reference doc, not the method
signature's javadoc.

## Keeping documentation up to date

When a change touches documented structure (classes, interfaces, data-model records, config
properties, error codes, behavioral contracts), update the matching doc in the same change:

- **`AGENTS.md`** (this file): high-level orientation (mental model, navigation, glossary, gotchas,
  invariants, build/test commands, entry points).
- **`docs/reference/ai-agent.md`**: core agent framework (orchestration, memory, tools, converters,
  config, error codes, invariants §24, extension points §25). MCP → `mcp.md`, A2A → `a2a.md`, native/v2
  provider specifics → `native-providers.md`.
- **`docs/breaking-changes.md`**: breaking changes for custom extensions (SPIs, public classes and
  packages, persisted data, job types, configuration). Add an entry in the same change that
  introduces the break, under the section of the release in development, with the old and new API
  and a migration hint. The public docs link to it.
- **`<connector module>/element-templates/README.md`** and the index
  **`connector-agentic-ai/element-templates/README.md`**: template version bumps, moves to `versioned/`, or new
  connectors (maintenance rules are in the Element templates section above).

> `CLAUDE.md` in this directory imports this file via `@AGENTS.md`, giving a single source of truth.
> Edit `AGENTS.md`; never add content directly to `CLAUDE.md`.
