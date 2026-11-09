# Design guidelines

Follow existing patterns, agreed requirements and relevant checks when choosing stored shapes,
customer-facing names, defaults or limits. If these do not resolve the choice, ask for the missing
requirements before choosing. State new contract decisions and their rationale in the PR description,
including the pattern or requirement they follow. A passing API compatibility check alone does not
justify a product name or a default.

This file covers contract and system design. For code structure, follow the
[code design rules](implementation-rules.md#code-design). For the target state of the module, read the
[target architecture](target-architecture.md).

## Architectural invariants

Before changing architecture or extension points, read and preserve these invariants.
These are the boundaries that keep the module maintainable and framework-pluggable.
They are documented here as the single source of truth. The
[architecture tests](../AGENTS.md#architecture-tests) enforce framework isolation and interface naming
(I1 to I3), so violations fail the build; review checks the rest. I4 and I5 are behavioral contracts:
review and the tests of the stores and SPIs enforce them.

- **I1. Framework-agnostic core.** The agent core (`aiagent/agent`, `aiagent/model`, `aiagent/memory`, the root `model/`,
  `adhoctoolsschema/`, `tool/`) stays framework-neutral. Only the LangChain4j adapter package may depend on LangChain4j.
  - **Rule**: nothing outside `io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.**` may
    import `dev.langchain4j.*`.
  - **Why**: the LLM provider is an SPI (`ChatModel` / `ChatModelFactory`, see
    [`chat-model-spi.md`](../aiagent-api/docs/chat-model-spi.md#the-chatmodel-provider-spi-adr-009)). Keeping LangChain4j behind the SPI means it can be replaced or
    extended per provider without touching orchestration, memory, or the data model.
  - **Verify**: `grep -rl "import dev.langchain4j" --include="*.java"
    connector-aiagent/src/main/java/io/camunda/connector/agenticai | grep -v /chatmodel/provider/langchain4j/` returns nothing.
- **I2. Domain types never leak framework types.** The module owns a framework-agnostic domain model in
  `io.camunda.connector.agenticai.aiagent.model.*` (the `Message`, `Content`, `ToolCall`, and `ToolDefinition` sealed types).
  - **Rule**: these types must not expose LangChain4j types in their API. Conversion to/from
    `dev.langchain4j` types happens **only** in the converter chain (see
    [`langchain4j-chat-model.md`](../connector-aiagent/docs/langchain4j-chat-model.md#converter-chain-architecture) for the converters).
  - **Why**: a leak here re-couples the whole codebase to the framework and defeats I1.
- **I3. Interface in package root, `*Impl` alongside.** Public collaborators are interfaces
  (`AgentToolsResolver`, `ConversationStoreRegistry`, `SystemPromptComposer`, and others), usually with one
  `*Impl`. Wire impls as Spring beans, depend on the interface (constructor injection). The architecture tests check that a class
  named `FooImpl` implements the interface `Foo`; review checks the rest.
  - The single implementation is named `<Interface>Impl` in the same package (e.g.
    `ConversationStoreRegistry` / `ConversationStoreRegistryImpl`, `AgentToolsResolver` / `AgentToolsResolverImpl`,
    `SystemPromptComposer` / `SystemPromptComposerImpl`). The convention is pervasive, so follow the nearest
    existing pair.
  - **Rule**: register the `Impl` as a Spring bean (typically `@ConditionalOnMissingBean` so it can be
    overridden). Don't inject `*Impl` directly.
  - **Enforced by**: review checks that there is a single implementation and that it is registered as an
    overridable bean.
  - [#9539](https://github.com/camunda/connectors/issues/9539) tracks a change to this convention.
- **I4. SPIs are plug-in points.** Add an LLM provider, system-prompt contributor, conversation store, or
  gateway tool by implementing the SPI and registering a bean. See
  [Extension Points](architecture/extension-points.md#extension-points). Do not modify the core to
  special-case a new provider, store, or contributor. The SPIs:

  | SPI                       | Add a…                          | Where to start      |
  |---------------------------|---------------------------------|---------------------|
  | `ChatModelFactory`        | LLM provider                    | [Add an LLM provider](architecture/extension-points.md#add-an-llm-provider) |
  | `SystemPromptContributor` | system-prompt contribution      | [Add a `SystemPromptContributor`](architecture/extension-points.md#add-a-systempromptcontributor) |
  | `ConversationStore`       | conversation memory backend     | [Add a `ConversationStore`](architecture/extension-points.md#add-a-conversationstore) |
  | `GatewayToolHandler`      | multi-tool gateway (MCP/A2A)    | [Gateway Tool Pattern](architecture/extension-points.md#gateway-tool-pattern) |
  | `RuntimeTool`             | tool the agent executes itself  | [Add a runtime tool](architecture/extension-points.md#add-a-runtime-tool) |
- **I5. Conversation stores follow the write-ahead, pointer-based contract.** Any `ConversationStore` must
  write each turn to a **new** location and load **only** what the `AgentContext` pointer references; never
  overwrite data the current pointer resolves to. This is the correctness foundation for
  retry/supersession safety; full statement in
  [Storage Contract](../aiagent-api/docs/conversation-store-spi.md#storage-contract).

## Preserve stored data

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
error codes and tool names are customer-facing contracts; apply the decision rule above before
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

## Make author-configurable concepts discoverable

Each author-configurable concept needs a template field or documented configuration, with an example
where usage is not obvious. Follow the repo-root [field-help conventions](../../../AGENTS.md#element-templates).

## Architecture decision records

Write an ADR in [`adr/`](adr/) when choosing between meaningful alternatives: replacing a framework,
restructuring a core subsystem, or changing storage strategy. Routine bug fixes, refactors and
pattern-following feature additions do not need one.

Read the [ADR authoring rules](adr/README.md) when writing or revising an ADR.
