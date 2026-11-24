# Design guidelines

<!-- docs-lint: allow-names Foo, FooImpl, Impl: placeholders of a naming rule -->

Follow existing patterns, agreed requirements and relevant checks when you make a customer-facing
decision: persisted state, a name, a default or a limit. If these do not resolve the choice, ask the
user for the missing requirements before choosing. State new customer-facing decisions and their
rationale in the PR description, including the pattern or requirement they follow. A passing API
compatibility check alone does not justify a product name or a default.

This file covers contract and system design. For code structure, follow the
[code design rules](implementation-rules.md#code-design). For the target state of the module, read the
[target architecture](target-architecture.md).

## Audiences

- **Process developer**: models the process and configures the connector templates, often low-code.
- **Operator**: installs and configures the connector runtime, watches running processes and resolves
  incidents.
- **Extension developer**: builds a runtime with their own SPI implementations or beans.
- **Business user**: the person the process serves.

## Architectural invariants

The [architecture tests](../AGENTS.md#architecture-tests) enforce the framework isolation and interface naming of
I1 to I3. Review checks the rest, and with the tests of the stores and SPIs, the behavioral contracts I4 and I5.

- **I1. Framework-agnostic core.** The agent core (`aiagent/agent`, `aiagent/model`, `aiagent/document`, `aiagent/tool`, `aiagent/memory`, the root `model/`,
  `adhoctoolsschema/`, `tool/`) stays framework-neutral. Only the LangChain4j adapter package may depend on LangChain4j.
  - **Rule**: nothing outside `io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.**` may
    import `dev.langchain4j.*`.
  - **Why**: the LLM provider is an SPI (`ChatModel` / `ChatModelFactory`, see
    [`chat-model-spi.md`](../aiagent-api/docs/chat-model-spi.md#the-chatmodel-provider-spi-adr-009)). Keeping LangChain4j behind the SPI means it can be replaced or
    extended per provider without touching orchestration, memory, or the data model.
- **I2. Domain types never leak framework types.** The module owns a framework-agnostic domain model in
  `io.camunda.connector.agenticai.aiagent.model.*` (the `Message`, `Content`, `ToolCall`, and `ToolDefinition` sealed types).
  - **Rule**: these types must not expose LangChain4j types in their API. Conversion to/from
    provider SDK and `dev.langchain4j` types happens **only** in the converters of the chat model providers
    (`aiagent/chatmodel/provider/**`): the request and response converters of each native provider (see
    [`native-providers.md`](../connector-aiagent/docs/native-providers.md)) and the converter chain of the
    LangChain4j chat models (see
    [`langchain4j-chat-model.md`](../connector-aiagent/docs/langchain4j-chat-model.md#converter-chain-architecture)).
  - **Why**: a leak here re-couples the whole codebase to the framework and defeats I1.
- **I3 (deprecated). Interface in package root, `*Impl` alongside.** Existing pairs stay: public collaborators such as
  `AgentToolsResolver`, `ConversationStoreRegistry` and `SystemPromptComposer` are interfaces with a `FooImpl` in the
  same package, registered as an overridable Spring bean and injected by the interface, never as `*Impl`. The
  architecture tests check that a class named `FooImpl` implements the interface `Foo`. A class that implements one
  interface per agent flavor of the same concept takes the concept name without `Impl` (e.g.
  `AgentExecutionContextFactory` for `AgentTaskExecutionContextFactory` and `AgentSubProcessExecutionContextFactory`).
  A new collaborator is a concrete `@ConditionalOnMissingBean` bean, per trait 4 of the
  [target architecture](target-architecture.md#traits) ([#9539](https://github.com/camunda/connectors/issues/9539)).
- **I4. SPIs are plug-in points.** Add an LLM provider, system-prompt contributor, conversation store,
  gateway tool or runtime tool by implementing the SPI and registering a bean. See
  [Extension Points](architecture/extension-points.md#extension-points). Do not modify the core to
  special-case a new provider, store, or contributor.
- **I5. Conversation stores follow the write-ahead, pointer-based contract.** Any `ConversationStore` must
  write each turn to a **new** location and load **only** what the `AgentContext` pointer references; never
  overwrite data the current pointer resolves to. This is the correctness foundation for
  retry/supersession safety; full statement in
  [Storage Contract](../aiagent-api/docs/conversation-store-spi.md#storage-contract).

## Preserve persisted state

Existing process instances must continue working after an upgrade. Demonstrate that new code reads
supported older state and preserves its behavior. A compatible field addition does not by itself
require a schema version bump or a migration.

Use a schema version and an explicit migration when readers must distinguish representations that
cannot be interpreted safely in the same way. Follow `AgentContextSchemaMigration` and add a fixture
of the old shape for each migration. Preserve rejection of unsupported schema versions without
overwriting the state.

When versioning is needed, apply it to independently persisted payloads defined in
[the persisted-state contract](../aiagent-model/docs/data-model.md), not individual fields or tool calls.
Record breaking impacts and required migration steps in [`breaking-changes.md`](breaking-changes.md).
Rollback support is not required.

## Follow existing naming patterns

Use established product terminology throughout templates, documentation and code
([ADR 010](adr/010-agent-task-subprocess-naming.md)). Configuration keys, template IDs, job types,
error codes and tool names are customer-facing decisions; apply the decision rule above before
changing them.

## Choose defaults and limits that can evolve

Prefer sensible existing or library defaults. Override them only when throughput, latency,
compatibility or resource usage justifies it. When choosing a new limit, prefer the tighter value
that meets the use case, rather than an arbitrarily low value that breaks normal workloads.

Tightening a default later can break existing workloads. Record breaking changes in
[`breaking-changes.md`](breaking-changes.md); do not introduce a per-installation default-preservation
mechanism without an agreed requirement. In the PR, state new or changed values, their reasons, how
to observe a limit hit and how to configure it. Limit errors name every configuration option that
must change to recover. A new field or capability does not change behavior when nobody sets it
([domain rule 1](implementation-rules.md#domain-rules)).

## Design observable automated behavior

Make important agent actions and outcomes observable through agent instance history, logs or
incidents, as appropriate. Fallbacks and skipped work need evidence of what happened and why.
For example, where a provider exposes displayable reasoning/thinking text, preserve its visibility
in agent instance history; do not infer or reveal opaque reasoning payloads.
Use metadata for diagnostic logs, never message contents or secrets.

## Make concepts that a process developer configures discoverable

Each concept that a process developer configures needs a template field or documented configuration,
with an example where usage is not obvious. Follow the repo-root
[field-help conventions](../../../AGENTS.md#element-templates).

## Architecture decision records

Write an ADR in [`adr/`](adr/) when choosing between meaningful alternatives: replacing a framework,
restructuring a core subsystem, or changing storage strategy. Routine bug fixes, refactors and
new capabilities that follow an existing pattern do not need one.

Read the [ADR authoring rules](adr/README.md) when writing or revising an ADR.
