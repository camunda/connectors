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
- [`docs/adr/`](docs/adr/): architecture decision records
- Repo-root [`AGENTS.md`](../../AGENTS.md): repo-wide build, commit, PR, CI, spotless, license, and
  element-template conventions. These are not duplicated here.

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
| Chat model SPI & LangChain4J bridge implementation                       | [§12](docs/reference/ai-agent.md#12-framework-abstraction)                              |
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

## Mental model

Two flavors share one orchestration core (`BaseAgentRequestHandler`):

- **AI Agent Task** (`AiAgentFunction`): a service-task connector whose tool-calling loop is modeled
  explicitly in BPMN.
- **AI Agent Sub-process** (`AiAgentJobWorker`): a job worker on an ad-hoc sub-process (AHSP) whose
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
  `ai-agent.md` §10.
- **Partial tool results → no-op**: incomplete results make the composer return `Deferred` and the worker complete
  without an LLM call. Expected, not a bug. `ai-agent.md` §9.
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

## Architectural invariants

Do not break these (full statement and rationale in
[§24](docs/reference/ai-agent.md#24-architectural-invariants)). They are the rules a future ArchUnit
suite will enforce (epic #7537):

- **Framework-agnostic core.** Only `aiagent/framework/langchain4j/**` may import `dev.langchain4j.*`.
  The agent core (`aiagent/agent`, `aiagent/model`, `aiagent/memory`, the root `model/`) stays
  framework-neutral. `aiagent/framework/api/**` is the provider-neutral chat model SPI
  (`ChatModelApi`, `ChatModelApiFactory`, `ChatModelApiRegistry`, …) and must import no vendor SDK;
  future native chat model implementations live under their own `aiagent/framework/<provider>/**`
  package, each importing only its own vendor SDK.
- **Domain types never leak framework types.** The domain `Message` / `ToolCall` / `Content` model
  (`io.camunda.connector.agenticai.model.*`) is translated to/from LangChain4J only through the
  converter chain (`ChatMessageConverter`, `ToolSpecificationConverter`, and friends).
  [§12](docs/reference/ai-agent.md#12-framework-abstraction).
- **Interface in package root, `*Impl` alongside.** Public collaborators are interfaces
  (`AgentToolsResolver`, `ConversationStoreRegistry`, `SystemPromptComposer`, and others) with a single
  `*Impl`. Wire impls as Spring beans, depend on the interface.
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

**Prerequisite**: the e2e tests require `element-templates-cli` on your PATH. The e2e harness shells out
to it (`BpmnFile.apply`) to apply templates, so it must be installed even though no pom declares it.
Template generation itself uses the `element-template-generator-maven-plugin` and needs no CLI. When it
is missing, e2e tests fail with errors that can look unrelated to your change. Install it once with
`npm i -g element-templates-cli`.

Unit tests use JUnit 5, Mockito, and AssertJ. In this module use unit tests only, apart from the few
existing Spring Boot tests and the e2e suite. For repo-wide build/commit/PR/CI/spotless/license rules,
see the repo-root [`AGENTS.md`](../../AGENTS.md). Do not duplicate them here.

E2E tests live in `connectors-e2e-test/connectors-e2e-test-agentic-ai/` (Camunda Process Test scenarios plus WireMock
LLM stubs). Extend `BaseAiAgentJobWorkerTest` (sub-process flavor) or `BaseAiAgentConnectorTest` (task
flavor).

```bash
mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -Dtest=<TestClassName>
```

Running the full e2e suite is slow. Search the e2e directory for tests relevant to your change and run
those selectively. Ensure that all e2e tests pass after completing a major work item.

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

### Definition of Done

Before claiming a change is complete:

- [ ] Module builds (`mvn clean install -f connectors/agentic-ai/pom.xml`).
   - [ ] If deviating from the `mvn install` command, ensure `mvn spotless:apply` and `mvn license:format` run clean (pre-commit hooks enforce these).
- [ ] Unit tests added/updated and passing.
- [ ] E2E test decision made per the rule above (test added, or reason stated, or question asked).
- [ ] Element templates regenerated (`mvn clean compile -f connectors/agentic-ai/pom.xml`) if template properties changed;
  check the JSON diff.
- [ ] Documentation updated per [Keeping documentation up to date](#keeping-documentation-up-to-date).

## Extension points

Add an LLM provider, a `SystemPromptContributor`, a `ConversationStore`, or a gateway tool by
implementing the SPI and registering a Spring bean. Where to start (SPI + reference implementation per
type): `ai-agent.md` §25 (gateway tools: §19).

## Element templates

The JSON element templates are **generated**, not hand-edited. They are produced from the
`@ElementTemplate`-annotated connector functions and their bound data models (`@TemplateProperty`
fields), so the source of truth is the Java, not the JSON. The template version comes from the
annotation's `version` attribute on the connector function; bumping it there bumps the generated
template. The AI Agent Sub-process template is in turn derived from the AI Agent Task template, and
the v2 (own LLM layer) Sub-process template from the v2 Task template (`AiAgentTaskV2Function`), both
via the shared `connector-agentic-ai/bin/transform-ai-agent-template.groovy` script (gmavenplus-plugin,
`process-classes` phase, one execution per template/hybrid combination, parameterized by
`sourceFile`/`outputFile`/`templateId`/`connectorType`).

To regenerate, run `mvn clean compile -f connectors/agentic-ai/pom.xml` and commit the JSON diff; never edit
the generated JSON by hand. For the generation mechanism and annotation reference, see the
[element template generator documentation](https://docs.camunda.io/docs/components/connectors/custom-built-connectors/connector-sdk/#element-template-generation).
When a version is bumped, a template moves into `versioned/`, or a connector is added, also update
[`connector-agentic-ai/element-templates/README.md`](connector-agentic-ai/element-templates/README.md) following these rules:

1. **Identify the new minimum Camunda version** by checking the `engines.camunda` field of the new
   template (e.g. `^8.10`).
2. **Same minimum as the current top row**: bump the top row's template version and keep its link
   pointing to the latest `./<file>.json` in this folder. The superseded version moves into `versioned/`
   but is not listed (the table shows only the latest template per minimum Camunda version).
3. **Higher minimum than the current top row**: insert a new top row with the new minimum Camunda
   version and template version, and move the previous top row's link under `versioned/`.
4. **AI Agent has two tables** (Task and Sub-process) sharing the same version numbers. Update both.
5. **New connector**: add a section in the same order as the existing ones (AI Agent, MCP Client, A2A,
   Ad-hoc tools schema) with an intro paragraph linking to the `docs.camunda.io` overview page.

Do not list `hybrid/` templates in the README. They are intentionally omitted.

## Model capability matrix

`connector-agentic-ai/src/main/resources/capabilities/model-capabilities.yaml` (per-family/per-model
LLM capability declarations consumed by `ModelCapabilitiesResolver`) is hand-maintained data, not
generated. When refreshing it as new model releases ship:

- Source context-window / max-output-tokens / reasoning figures from
  [models.dev](https://models.dev) (`api.json` dataset); do not invent numbers.
- A curated entry's glob (or pattern list) must never over-promise: its pinned numbers must be valid
  for every model the glob currently matches. Where matched models genuinely differ, split into
  narrower patterns/exact ids (preferred) or pin the conservative minimum across the matched
  members — never the maximum.
- Every capability field, including each input-modalities location (user-message, tool-result) and
  output-modalities (assistant-message), is per-model overridable via an entry's `capabilities`
  overlay; the family `defaults` block is only the baseline a model falls back to when it doesn't
  pin its own value.
- Each entry's `patterns` field accepts either a single glob string or a list of globs (matches when
  any glob in the list matches; longest match wins at resolve-time).
- Entries support an optional `backend` string (e.g. `azure-foundry`, `bedrock`) distinguishing how
  the same model id is served when that changes its capabilities. A backend-specific entry layers on
  top of the backend-agnostic entry matching the same model id, which in turn layers on top of the
  family `defaults`; `backend` is orthogonal to the `id`/`patterns` discriminator. No bundled entries
  use `backend` yet (no authoritative Bedrock/Azure Foundry data) — it's currently exercised only via
  `ModelCapabilitiesResolverTest` fixtures.
- See the YAML file's own header comment for the full structure, override mechanics, and resolution
  chain.

## Key entry points

| File                                        | Purpose                                            |
|---------------------------------------------|----------------------------------------------------|
| `AiAgentFunction.java`                      | Connector (Task) entry point                       |
| `AiAgentJobWorker.java`                     | Job worker (Sub-process) entry point               |
| `AiAgentTaskV2Function.java`                | v2 (own LLM layer) Task entry point                |
| `AiAgentSubProcessV2Function.java`          | v2 (own LLM layer) Sub-process entry point         |
| `BaseAgentRequestHandler.java`              | Core orchestrator (shared by both flavors/versions)|
| `AgenticAiConnectorsAutoConfiguration.java` | Spring Boot wiring                                 |

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

## Keeping documentation up to date

When a change touches documented structure (classes, interfaces, data-model records, config
properties, error codes, behavioral contracts), update the matching doc in the same change:

- **`AGENTS.md`** (this file): high-level orientation (mental model, navigation, glossary, gotchas,
  invariants, build/test commands, entry points).
- **`docs/reference/ai-agent.md`**: core agent framework (orchestration, memory, tools, converters,
  config, error codes, invariants §24, extension points §25). MCP → `mcp.md`, A2A → `a2a.md`.
- **`connector-agentic-ai/element-templates/README.md`**: template version bumps, moves to `versioned/`, or new connectors
  (maintenance rules are in the Element templates section above).

> `CLAUDE.md` in this directory imports this file via `@AGENTS.md`, giving a single source of truth.
> Edit `AGENTS.md`; never add content directly to `CLAUDE.md`.
