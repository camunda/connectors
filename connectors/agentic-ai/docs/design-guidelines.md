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

Before changing architecture or extension points, read and preserve the
[invariants in §24](reference/ai-agent.md#24-architectural-invariants), the authoritative rules,
rationale, examples and enforcement details.

## Preserve stored data

Existing process instances must continue working after an upgrade. Demonstrate that new code reads
supported older state and preserves its behavior. A compatible field addition does not by itself
require a schema version bump or a migration.

Use a schema version and an explicit migration when readers must distinguish representations that
cannot be interpreted safely in the same way. Follow `AgentContextSchemaMigration` and add a fixture
of the old shape for each migration. Preserve rejection of unsupported schema versions without
overwriting the state.

When versioning is needed, apply it to independently persisted payloads defined in
[the persisted-state contract](reference/ai-agent.md#5-data-model), not individual fields or tool calls.
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
