# Agentic AI Module: Agent Instructions

The `agentic-ai` module implements Camunda's AI Agent connectors: an agentic orchestration system that
runs LLM tool-calling loops inside BPMN processes. It ships in two flavors, an AI Agent Task (on a
service task) and an AI Agent Sub-process (on an ad-hoc sub-process), plus supporting infrastructure
for tool calling, conversation memory, gateway tools (MCP, A2A), and event handling.

## Documentation

Read before you act:

- Before you plan, implement or review a change: [`docs/implementation-rules.md`](docs/implementation-rules.md),
  how to approach a change, domain rules from past review findings, and code design rules for new code, and
  [`docs/documentation-rules.md`](docs/documentation-rules.md), where each doc goes and how to keep the docs up
  to date.
- To review a change: [`docs/code-review.md`](docs/code-review.md), including when a human must approve.
- For every code change: [`docs/acceptance-testing.md`](docs/acceptance-testing.md), the test level, feature
  acceptance and provider coverage.
- Before you design stored shapes, names, defaults, limits or observability:
  [`docs/design-guidelines.md`](docs/design-guidelines.md), the invariants, contract decisions and
  [ADR criteria](docs/design-guidelines.md#architecture-decision-records).
- Before you change module structure or extension points: the
  [architectural invariants](docs/design-guidelines.md#architectural-invariants). Before you plan a structural
  change (a new module, collaborator, SPI or cross-module policy): the
  [target architecture](docs/target-architecture.md), the target state beyond the invariants.
- Before you write or revise an ADR in [`docs/adr/`](docs/adr/): its [authoring rules](docs/adr/README.md).
- Help text of element templates: [`docs/template-text-guidelines.md`](docs/template-text-guidelines.md).
- E2E tests: the e2e [`AGENTS.md`](../../connectors-e2e-test/connectors-e2e-test-agentic-ai/AGENTS.md), with
  the e2e modules, running e2e tests and real-LLM CPT coverage.
- Repo-wide build, commit, PR, CI, spotless, license and element-template conventions: the repo-root
  [`AGENTS.md`](../../AGENTS.md). They are not duplicated here.

Update the docs in the same change as the code. Follow the
[documentation rules](docs/documentation-rules.md): where each doc goes, how to find the docs that a
change affects, and the [docs checks](#docs-checks). Each fact has one home: a
module doc, a doc in `docs/`, or a Javadoc. This file holds rules and links (mental model, navigation,
glossary, gotchas, build and test commands). Do not restate the details of a fact here (values, formats,
field lists, order, steps or semantics). Link to its home. A rule may name a type, a field or an error code
as a pointer to that home.

- `<module>/README.md` and `<module>/docs/`: the purpose and key types of a module, and the docs about its
  types (data model, memory, tools, converters, providers, error codes, MCP, A2A). The
  [docs checks](#docs-checks) enforce the README layout and the links. `connector-aiagent/README.md` is
  generated: edit `connector-aiagent/README.peb`. It uses the shared connector README layout, so its title is
  `# AI Agent Task` instead of `# connector-aiagent`.
- [`docs/architecture/`](docs/architecture/): content that spans modules (the agentic loop, extension points,
  Spring auto-configuration).
- [`docs/breaking-changes.md`](docs/breaking-changes.md): breaking changes for custom extensions (SPIs, public
  classes and packages, persisted data, job types, configuration). Add an entry in the same change that
  introduces the break, under the section of the release in development, with the old and new API and a
  migration hint. The public docs link to it.
- `<connector module>/element-templates/README.md` and the index
  `connector-agentic-ai/element-templates/README.md`: update them for template version bumps, moves to
  `versioned/` and new connectors by the [README rules](connector-aiagent/docs/element-template-generation.md#element-template-readmes). These READMEs serve users who
  pick or configure a template: which template fits which Camunda version and what a user selects. Put the
  details of how the templates are generated in `<module>/docs/`.

After a doc change, run the [docs checks](#docs-checks).

## Modules

The module is split into Maven modules with a fixed dependency direction
([ADR 015](docs/adr/015-split-agentic-ai-into-modules.md)):

| Module | Package root | Contents | Depends on (agentic AI modules) |
| --- | --- | --- | --- |
| `agentic-ai-common` | `common` | Shared helpers grouped by topic (`json`, `retry`, `http`, `validation`, ...), common properties | none |
| `aiagent-model` | `aiagent.model` | Agent model referenced by the extension points | common |
| `aiagent-api` | `aiagent.api` | Extension points (gateway tools, chat model, storage, prompts) | model, common |
| `aiagent-tools-schema` | `tools.schema` | Ad-hoc tools schema resolution | api, model, common |
| `connector-ad-hoc-tools-schema` | `adhoctoolsschema` | Ad-hoc tools schema connector | tools-schema |
| `connector-mcp` | `mcp.client` | MCP client connectors | common |
| `aiagent-mcp-integration` | `mcp.integration` | MCP client as AI Agent gateway tool | connector-mcp, api, model, common |
| `connector-a2a` | `a2a.client` | A2A client connectors | common |
| `aiagent-a2a-integration` | `a2a.integration` | A2A client as AI Agent gateway tool | connector-a2a, api, model, common |
| `connector-aiagent` | `aiagent` (except `model` and `api`) | AI Agent connectors, providers, stores, agent instance client | tools-schema, api, model, common |
| `agentic-ai-bundle` | none (`pom` packaging) | All connectors and gateway tool integrations, for runtimes | aiagent, ad-hoc tools schema, MCP and A2A connectors and integrations |
| `agentic-ai-archunit-tests` | none (test-only) | Architecture tests for the modules above, not shipped | all modules, in test scope |
| `agentic-ai-docs-lint` | `docslint` | Checks that keep the docs in step with the modules and the code, not shipped | none |

Client connectors never depend on AI Agent code, and `connector-aiagent` never depends on the clients
or integrations. Package roots are relative to `io.camunda.connector.agenticai`;
put new classes under the root of their module, and no Java package spans several modules. Each module has its own
auto-configuration, element templates and icons.
All artifacts use the group `io.camunda.connector.agenticai`. Runtimes that ship all agentic AI connectors (the
bundles) depend on `agentic-ai-bundle` with `<type>pom</type>`; add a new connector or integration there. The e2e
modules depend on the connector modules they test instead (see the
[e2e tests `AGENTS.md`](../../connectors-e2e-test/connectors-e2e-test-agentic-ai/AGENTS.md#modules)).

### Architecture tests

`agentic-ai-archunit-tests` holds the ArchUnit rules for the production classes of all modules (see its
[README](agentic-ai-archunit-tests/README.md#key-types)). The class Javadoc of each architecture test describes what it checks.
They skip `agentic-ai-docs-lint` on purpose, because it supports development only and is not deployed.

- `PackageCyclesArchTest` allows the package cycles in `KNOWN_CYCLES`. When you break a cycle, delete its entry;
  never add one.
- `ElementTemplateAnnotationsArchTest` allows the records listed in the test. The list must only shrink.
- `AgenticAiModules` maps modules to package roots and kinds. When you add a module, a package root or a dependency
  between modules, update it and the layer rules; a new connector or integration also goes into `agentic-ai-bundle`.

Run only the architecture tests with:

```bash
./mvnw test -f connectors/agentic-ai/pom.xml -pl agentic-ai-archunit-tests -am \
  '-Dtest=*ArchTest,ModuleInventoryTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Keep `-f connectors/agentic-ai/pom.xml`: it limits `-am`, which builds the modules the tests read from their current
sources, to `connectors/agentic-ai`. Without `-am`, the tests read the jars of the last local install and miss your
changes; `./mvnw clean install -f connectors/agentic-ai/pom.xml` also runs them.

### Docs checks

`agentic-ai-docs-lint` checks the docs against the modules and the code; its [README](agentic-ai-docs-lint/README.md#rules)
lists the rules and the findings. Mark a doc with:

- `<!-- docs-lint: allow-java-block: reason -->` before a `java` code block;
- `<!-- docs-lint: allow-names Name: reason -->` for a backticked name that is not in the repository sources;
- `<!-- docs-lint: history: reason -->` in a doc that records removed code.

Run the checks with:

```bash
./mvnw test -f connectors/agentic-ai/pom.xml -pl agentic-ai-docs-lint
```

Before you change the checks, read [`agentic-ai-docs-lint/AGENTS.md`](agentic-ai-docs-lint/AGENTS.md): the rules this
development tool deviates from.

## Start here, navigation

> Use the table below to find the doc for your task, then read only that doc or section. Each module `README.md` lists the docs of that module.

| Working on… | Read |
| --- | --- |
| Agent loop: execution model, job completion and AHSP directives, tool completion and partial results (no-op), concurrency and races (supersession, store-ahead-of-Zeebe), events (sub-process only) | `agentic-loop.md`: [execution model](docs/architecture/agentic-loop.md#the-agentic-loop--distributed-execution-model), [job completion](docs/architecture/agentic-loop.md#job-completion--the-heart-of-the-distributed-loop), [tools complete](docs/architecture/agentic-loop.md#what-happens-when-tools-complete), [concurrency](docs/architecture/agentic-loop.md#concurrency-challenges--race-conditions), [events](docs/architecture/agentic-loop.md#event-handling) |
| Agent state machine & initialization (INITIALIZING/TOOL_DISCOVERY/READY) | [`agent-state-machine.md`](connector-aiagent/docs/agent-state-machine.md) |
| Data model (`AgentContext`, `AgentResponse`, `ToolCallProcessVariable`) | [`data-model.md`](aiagent-model/docs/data-model.md), [`response-handling.md`](connector-aiagent/docs/response-handling.md) |
| Conversation memory, storage backends, write-ahead store contract | [`conversation-store-spi.md`](aiagent-api/docs/conversation-store-spi.md), [`conversation-memory.md`](connector-aiagent/docs/conversation-memory.md) |
| Tool resolution, FEEL `fromAi()` extraction, JSON-schema generation | [`tool-resolution.md`](aiagent-tools-schema/docs/tool-resolution.md) |
| Chat model provider SPI, provider selection, LangChain4j converter chain | [`chat-model-spi.md`](aiagent-api/docs/chat-model-spi.md), [`chat-model.md`](connector-aiagent/docs/chat-model.md), [`langchain4j-chat-model.md`](connector-aiagent/docs/langchain4j-chat-model.md), [`custom-langchain4j-provider.md`](connector-aiagent/docs/custom-langchain4j-provider.md) |
| Native provider specifics (Anthropic/Bedrock/OpenAI wire-format details) | [`native-providers.md`](connector-aiagent/docs/native-providers.md) |
| System prompt composition / contributors | [`system-prompt-contributor.md`](aiagent-api/docs/system-prompt-contributor.md), [`system-prompt.md`](connector-aiagent/docs/system-prompt.md) |
| Response handling (text / JSON / full message) | [`response-handling.md`](connector-aiagent/docs/response-handling.md) |
| Error codes | [`error-codes.md`](connector-aiagent/docs/error-codes.md) |
| Spring auto-configuration, feature toggles, config defaults | [`spring-auto-configuration.md`](docs/architecture/spring-auto-configuration.md) |
| Process instance migration (what's allowed / blocked) | [`process-instance-migration.md`](connector-aiagent/docs/process-instance-migration.md) |
| Agent documents: registry, handles, expansion for the model, only Camunda documents, references per agent (`data.documents.references`, the `documents` feature), user prompt delivery (`reference` or `content`, `data.documents.userPromptDelivery`), returning (`c8__return_document`, `responseDocuments`) and loading (`c8__load_document`) documents | [`extension-points.md`](docs/architecture/extension-points.md#document-extraction-from-tool-call-results), [`data-model.md`](aiagent-model/docs/data-model.md), [`response-handling.md`](connector-aiagent/docs/response-handling.md#agentresponsehandlerimpl), [ADR 018](docs/adr/018-document-flow.md#document-references-per-agent) |
| Document parameters of tools (`fromAi` type `document`, resolution, rejection) | [`document-parameters.md`](connector-aiagent/docs/document-parameters.md), [`extension-points.md`](docs/architecture/extension-points.md#documents-and-gateway-tools), [ADR 018](docs/adr/018-document-flow.md) |
| MCP integration | [`mcp-gateway-tool.md`](aiagent-mcp-integration/docs/mcp-gateway-tool.md), [`mcp-client.md`](connector-mcp/docs/mcp-client.md) |
| A2A integration | [`a2a-gateway-tool.md`](aiagent-a2a-integration/docs/a2a-gateway-tool.md), [`a2a-client.md`](connector-a2a/docs/a2a-client.md) |
| Example BPMN processes & configurations | the examples section of the connector module READMEs, [`examples/`](examples/) |
| Agent instance API (status, metrics, history reporting) | [`agent-instance.md`](connector-aiagent/docs/agent-instance.md) |
| Key entry points (connector functions of both flavors, shared orchestrator, Spring wiring) | [key types of `connector-aiagent`](connector-aiagent/README.md#key-types) |
| **Architectural invariants (what you must not break)** | [`design-guidelines.md`](docs/design-guidelines.md#architectural-invariants) |
| **Extension points**: add an LLM provider, a `SystemPromptContributor`, a `ConversationStore`, a gateway tool (the MCP/A2A extensibility mechanism) or a runtime tool by implementing the SPI and registering a Spring bean. A process tool must not have the name of a runtime tool that is enabled. | SPI and reference implementation per type: [`extension-points.md`](docs/architecture/extension-points.md#extension-points); gateway tools: [Gateway Tool Pattern](docs/architecture/extension-points.md#gateway-tool-pattern); runtime tools: [Add a runtime tool](docs/architecture/extension-points.md#add-a-runtime-tool), [ADR 016](docs/adr/016-runtime-tools.md) |

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

Flavor comparison and loop diagram: [`flavors.md`](connector-aiagent/docs/flavors.md) and
[`agentic-loop.md`](docs/architecture/agentic-loop.md#the-agentic-loop--distributed-execution-model).

## Glossary

These terms are overloaded; the three `*Context` types are the usual confusion. Full definitions:
[`data-model.md`](aiagent-model/docs/data-model.md) for the data-model types (`AgentContext`,
`AgentExecutionContext`) and [`conversation-memory.md`](connector-aiagent/docs/conversation-memory.md#agentconversation-conversationsnapshot--messagewindowfilter) for the
conversation and turn types (everything else below).

| Term | One-line meaning |
| --- | --- |
| **Turn** (`AgentConversationTurn`) | one LLM call (input messages, assistant response, per-turn metrics); pending when `assistantMessage == null` |
| **Iteration** / `iterationKey` | 1-based counter across the agent's lifetime identifying a turn |
| **AgentContext** | durable persisted state (state, metrics, tool definitions, conversation pointer); the `agentContext` process variable |
| **ConversationContext** | pointer inside `AgentContext` to where messages live (in-process holds the messages, other stores hold only the pointer) |
| **AgentExecutionContext** | transient per-invocation request context (job metadata, provider config, prompts, limits); not persisted |
| **AgentConversation** | immutable in-process turn aggregate, built per invocation |
| **Snapshot** (`ConversationSnapshot`) | transient windowed read-only view sent to the LLM |
| **Window** | sliding message limit (`MessageWindowFilter`) applied to the snapshot; full history is always persisted |

## Gotchas

High-frequency traps (detail behind each link):

- **Job supersession / `NOT_FOUND`**: a completing tool creates a new job, so a stale in-flight job may be rejected.
  An agent instance update for an inactive job fails with the non-retryable `AGENT_INSTANCE_SUPERSEDED`.
  [`agentic-loop.md`](docs/architecture/agentic-loop.md#concurrency-challenges--race-conditions),
  [`agent-instance.md`](connector-aiagent/docs/agent-instance.md).
- **Partial tool results → no-op (but not silent)**: with incomplete results the worker completes without an LLM call,
  after it reports the arrived results to agent instance history. E2e tests asserting `verifyNoMoreInteractions` on
  `AgentInstanceClient` for a multi-tool-call turn with staggered completion times must account for this call.
  [`agentic-loop.md`](docs/architecture/agentic-loop.md#what-happens-when-tools-complete).
- **Write-ahead, pointer-based store contract**: never overwrite what the current `AgentContext` pointer references.
  [`conversation-store-spi.md`](aiagent-api/docs/conversation-store-spi.md#storage-contract).
- **Sub-process config frozen at AHSP entry**: config and migration changes don't reach a running instance (the Task
  flavor re-evaluates per iteration). [`process-instance-migration.md`](connector-aiagent/docs/process-instance-migration.md).
- **Migration blocked cases**: removing or renaming tools and adding or removing gateway tools fails.
  [`process-instance-migration.md`](connector-aiagent/docs/process-instance-migration.md).
- **`toolCallResult = ""` scoping trick**: the empty default keeps the variable local to the tool element.
  [`agentic-loop.md`](docs/architecture/agentic-loop.md#job-completion--the-heart-of-the-distributed-loop).
- **Events have `id = null`**: tool-call results without an id are event results.
  [`agentic-loop.md`](docs/architecture/agentic-loop.md#event-handling).
- **Gateway `transformToolCallResults` must carry `completedAt` forward**: copy it into the rebuilt `ToolCallResult`;
  unlike `elementId`, it has no fallback resolution.
  [`extension-points.md`](docs/architecture/extension-points.md#gateway-tool-handler-interface), [`agent-instance.md`](connector-aiagent/docs/agent-instance.md).
- **Agent instance features gate behavior per instance**: never add a feature to an existing context. Resolve
  `AgentContext.documentHandling()` (`Untracked` or `Tracked`) once per step with an exhaustive `switch`;
  never branch on `supports(AgentFeatures.DOCUMENTS)` or a `boolean` parameter. Gate a new wire format or persisted
  shape on a feature and keep the legacy path covered by `LegacyDocumentConversationTest`; tool call admission is the
  one exception. [`data-model.md`](aiagent-model/docs/data-model.md), [ADR 016](docs/adr/016-runtime-tools.md),
  [ADR 018](docs/adr/018-document-flow.md).
- **Tool calls are admitted once, for every agent instance**: `ToolCallAdmission` sets `ToolCall.kind` and
  `ToolCall.rejection`; nothing computes them again, every conversation store keeps both, and a rejected call is never
  executed. [`agentic-loop.md`](docs/architecture/agentic-loop.md#what-happens-when-tools-complete),
  [`document-parameters.md`](connector-aiagent/docs/document-parameters.md), [ADR 016](docs/adr/016-runtime-tools.md).
- **Documents are persisted as references and expanded for the model**: a `Tracked` instance stores document blocks
  that `DocumentExpander` expands after the window, and accepts only Camunda documents. A provider never receives an
  `AgentDocumentContent`: every provider content converter fails with `UnexpandedAgentDocumentException` on one. Name
  new document types per [document type naming](aiagent-model/docs/data-model.md#document-type-naming). Details:
  [`data-model.md`](aiagent-model/docs/data-model.md),
  [`extension-points.md`](docs/architecture/extension-points.md#document-extraction-from-tool-call-results),
  [`conversation-memory.md`](connector-aiagent/docs/conversation-memory.md#agentconversation-conversationsnapshot--messagewindowfilter),
  [`agent-instance.md`](connector-aiagent/docs/agent-instance.md#conversation-history-items),
  [ADR 018](docs/adr/018-document-flow.md).
- **`Message.id()` is random per construction**: two freshly built messages with equal content have different ids
  until one is persisted and reloaded, so a test that compares an independently built expected message fails on `id`.
  [`data-model.md`](aiagent-model/docs/data-model.md#message-identity), [ADR 012](docs/adr/012-stable-self-generated-message-ids.md).
- **Map iteration order differs between JVMs after a record builder copied the map**: the `@AgenticAiRecord`
  builders copy maps and sets with `Map.copyOf` and `Set.copyOf`, whose iteration order changes with every JVM start.
  Never let a hash, an id or any value compared across runs depend on that order. Sort the map keys where the value
  is computed (`SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS` in `AgentConfiguration.fingerprint()`, which sorts
  every map at any depth), and carry an order that has meaning, such as the order of the tool parameters, as a list.
  No mapper flag sorts a set, so a hashed value must not contain one. Do not add a record annotation or a special
  collection type to work around the copy. [`agent-instance.md`](connector-aiagent/docs/agent-instance.md#conversation-history-items).

## Working in this module

### Building & testing

```bash
./mvnw clean install -f connectors/agentic-ai/pom.xml               # build all modules, including tests
./mvnw clean install -DskipTests -f connectors/agentic-ai/pom.xml   # build all modules, skipping tests
./mvnw test -f connectors/agentic-ai/pom.xml                        # unit / integration tests
```

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
  ./mvnw -f connectors/agentic-ai/<module>/pom.xml org.codehaus.gmavenplus:gmavenplus-plugin:5.1.0:execute \
    -Dscripts="file:///$(pwd)/connectors/agentic-ai/connector-aiagent/bin/generate-package-info.groovy"
  ```
  Run `./mvnw license:format` on the module afterwards — the script doesn't stamp the license header — then
  commit the generated file(s).

### ErrorProne

<!-- docs-lint: allow-names Generated: annotation emitted by record-builder in generated sources -->
ErrorProne warnings fail the `main` compilation (`-Werror`); generated classes (annotated `@Generated`)
are skipped. Fix a finding, or suppress it with `@SuppressWarnings("<CheckName>")`
on the narrowest scope plus a comment stating why. Test code is not checked.

### Definition of Done

Before claiming a change is complete:

- [ ] Module builds (`./mvnw clean install -f connectors/agentic-ai/pom.xml`).
   - [ ] If deviating from the `./mvnw install` command, ensure `./mvnw spotless:apply` and `./mvnw license:format` run clean (pre-commit hooks enforce these).
- [ ] Relevant unit, component or integration tests added/updated and passing.
- [ ] E2E test decision made per the [testing rules](docs/acceptance-testing.md#choosing-e2e-vs-unit-tests-mandatory-decision): test added, reason stated, or question asked.
- [ ] User-facing features meet the [acceptance rules](docs/acceptance-testing.md).
- [ ] New stored-shape, customer-facing naming, default or limit decisions and their rationale are
  stated in the PR description per the [design guidelines](docs/design-guidelines.md).
- [ ] Real-provider changes meet the [coverage requirements](docs/acceptance-testing.md#real-provider-coverage).
- [ ] If template properties changed, the repo-root [element templates checks](../../AGENTS.md#element-templates)
  pass (regeneration, validator, `spotless:check`); check the JSON diff.
- [ ] The change follows the [implementation rules](docs/implementation-rules.md).
- [ ] Documentation updated per [Documentation](#documentation).
- [ ] Each Javadoc and each document that names a changed method agrees with the code at the last commit of the PR.
- [ ] Breaking changes for custom extensions are recorded in [`docs/breaking-changes.md`](docs/breaking-changes.md).
  The [API compatibility check](../../docs/api-compatibility.md) states from when it gates `aiagent-api`
  and `aiagent-model`. Accept a break that it reports in the module's `api-changes.json` with a link to
  its `breaking-changes.md` anchor.

## Element templates

The JSON element templates are generated from the `@ElementTemplate`-annotated connector functions and their
bound data models (`@TemplateProperty` fields). Never hand-edit the JSON. The template version is the `version`
attribute of the annotation. To regenerate, run the repo-root [element templates checks](../../AGENTS.md#element-templates),
check the JSON diff and commit it. Generation mechanism and annotation reference: the
[element template generator documentation](https://docs.camunda.io/docs/components/connectors/custom-built-connectors/connector-sdk/#element-template-generation).

The AI Agent Sub-process templates (v1 and v2) are derived from the AI Agent Task templates by
`connector-aiagent/bin/transform-ai-agent-sub-process-template.groovy` (gmavenplus-plugin, `process-classes` phase).
The transform scripts in `connector-aiagent/bin/` and `connector-a2a/bin/` find properties by hard-coded ids. Their
shared helpers in [`bin/TemplateTransforms.groovy`](bin/TemplateTransforms.groovy) fail the build when an expected
property id or anchor is missing; AI Agent specific helpers live in
[`connector-aiagent/bin/AgentTemplateTransforms.groovy`](connector-aiagent/bin/AgentTemplateTransforms.groovy). When you
rename a property a script looks up, update the script in the same change. `AgentElementTemplateTransformsTest`
(`connector-aiagent`) and `A2aElementTemplateTransformsTest` (`connector-a2a`) assert what the scripts inject, not
property order; review any generated template diff.

When a version is bumped, a template moves into `versioned/`, or a connector is added, update the element template
READMEs by the [README rules](connector-aiagent/docs/element-template-generation.md#element-template-readmes). The index
of all modules, [`connector-agentic-ai/element-templates/README.md`](connector-agentic-ai/element-templates/README.md),
stays at the path the templates had before the module split because external documentation links to it, and the
`element-templates` symlink in this directory points to it.

When the `engines.camunda` minimum of the AI Agent templates is bumped (rule 3 of the
[README rules](connector-aiagent/docs/element-template-generation.md#element-template-readmes)), also add regression
fixtures for the new template generation in the same change, as described in
[Element template regression fixtures](../../connectors-e2e-test/connectors-e2e-test-agentic-ai/AGENTS.md#element-template-regression-fixtures).

### Template text

When you write or change help text in `@TemplateProperty` annotations (`label`, `tooltip`,
`placeholder`, `description`), follow [`docs/template-text-guidelines.md`](docs/template-text-guidelines.md).

### Template markers

The transform scripts add the hidden `zeebe:agentDefinition` marker to every AI Agent template and the
`deprecated` block to the current v1 templates. Which templates carry them and how the scripts add them:
[Markers added by the transform scripts](connector-aiagent/docs/element-template-generation.md#markers-added-by-the-transform-scripts).

> Edit `AGENTS.md`; `CLAUDE.md` only imports it.
