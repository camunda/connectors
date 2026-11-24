# Design guidelines

<!-- docs-lint: allow-names Foo, FooImpl: placeholders of a naming rule -->

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
- **I3 (legacy convention). Interface in package root, `*Impl` alongside.** Existing pairs such as
  `AgentToolsResolver` and `ConversationStoreRegistry` stay. Inject the interface. The architecture tests check that a
  class named `FooImpl` implements `Foo`. New code follows [trait 4](target-architecture.md#traits): a concrete
  `@ConditionalOnMissingBean` bean, and an interface only for an SPI or where two or more implementations exist in
  production code.
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

Use the default of the library or of the existing setting. Override it only when you can name the
throughput, latency, compatibility or resource problem it solves, and state that problem in the PR.
When choosing a new limit, prefer the tighter value that meets the use case, rather than an
arbitrarily low value that breaks normal workloads.

Tightening a default later can break existing workloads. Record breaking changes in
[`breaking-changes.md`](breaking-changes.md); do not introduce a per-installation default-preservation
mechanism without an agreed requirement. In the PR, state new or changed values, their reasons, how
to observe a limit hit and how to configure it. Limit errors name every configuration option that
must change to recover. A new field or capability does not change behavior when nobody sets it
([domain rule 1](implementation-rules.md#domain-rules)).

## Design observable automated behavior

Record a decision in agent instance history when a process developer or operator needs it to
understand a run, for example a rejected tool call. Log it when only runtime debugging needs it.
Raise an incident when the process cannot continue. When unsure, ask the user. Fallbacks and skipped
work need evidence of what happened and why. For example, where a provider exposes displayable
reasoning/thinking text, preserve its visibility in agent instance history; do not infer or reveal
opaque reasoning payloads. Use metadata for diagnostic logs, never message contents or secrets.

## Make concepts that a process developer configures discoverable

Each concept that a process developer configures needs a template field or documented configuration,
with an example where usage is not obvious. Follow the repo-root
[field-help conventions](../../../AGENTS.md#element-templates).

## Architecture decision records

Write an ADR in [`adr/`](adr/) when you choose between options that a later change cannot easily
undo, for example a framework, a storage strategy or a module boundary. When unsure, ask the user
whether the change needs an ADR. Routine bug fixes, refactors and new capabilities that follow an
existing pattern do not need one.

Read the [ADR authoring rules](adr/README.md) when writing or revising an ADR.
