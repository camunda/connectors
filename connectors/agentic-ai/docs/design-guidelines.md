# Design guidelines

Follow existing patterns and use existing checks. If neither a pattern nor an agreed requirement
resolves a contract choice, ask before choosing. State new contract decisions and their rationale
in the PR description.

## Architectural invariants

Do not break these (full statement and rationale in
[§24](reference/ai-agent.md#24-architectural-invariants)). The
[architecture tests](../AGENTS.md#architecture-tests) enforce the first three:

- **Framework-agnostic core.** Only `aiagent/chatmodel/provider/langchain4j/**` may import `dev.langchain4j.*`.
  The agent core (`aiagent/agent`, `aiagent/model`, `aiagent/memory`, the root `model/`) stays
  framework-neutral.
- **Domain types never leak framework types.** The domain `Message` / `ToolCall` / `Content` model
  (`io.camunda.connector.agenticai.aiagent.model.*`) is translated to/from LangChain4j only through the
  converter chain (`ChatMessageConverter`, `ToolSpecificationConverter`, and friends).
  [§12](reference/ai-agent.md#12-framework-abstraction).
- **Interface in package root, `*Impl` alongside.** Public collaborators are interfaces
  (`AgentToolsResolver`, `ConversationStoreRegistry`, `SystemPromptComposer`, and others), usually with one
  `*Impl`. Wire impls as Spring beans, depend on the interface. The architecture tests check that a class
  named `FooImpl` implements the interface `Foo`; review checks the rest.
- **SPIs are plug-in points.** Add an LLM provider, system-prompt contributor, conversation store, or
  gateway tool by implementing the SPI and registering a bean. See
  [§25](reference/ai-agent.md#25-extension-playbooks).

## Preserve stored data

Version each stored root; migrate supported older shapes on read and reject unsupported newer shapes
without overwriting them. Follow `AgentContextSchemaMigration`. For each schema change, add an
old-shape migration fixture and record compatibility and recovery in
[`breaking-changes.md`](breaking-changes.md).

## Follow existing naming patterns

Use established product terminology throughout templates, documentation and code
([ADR 010](adr/010-agent-task-subprocess-naming.md)). Ask before changing customer-facing contract
names: configuration keys, template IDs, job types, error codes or tool names.

## Choose defaults and limits that can evolve

Start restrictions as tight as justified by throughput, latency, compatibility or resource usage,
then relax as needed. Tightening a default later can break existing workloads; assess whether
upgrading installations must retain their previous value. In the PR, state the value, reason, how to
observe a hit and how to configure it. Limit errors name every configuration option that must change
to recover.

## Design observable automated behavior

Compaction, truncation, fallback and skipped work need evidence in logs, history or incidents.
Compaction needs a marker and logs identifying what was compacted and why, using metadata rather
than message contents or secrets.

## Make author-configurable concepts discoverable

Each author-configurable concept needs a template field or documented configuration, with an example
where usage is not obvious. Follow the repo-root [field-help conventions](../../../AGENTS.md#element-templates).

## Architecture decision records

Write an ADR in [`adr/`](adr/) when choosing between meaningful alternatives: replacing a framework,
restructuring a core subsystem, or changing storage strategy. Routine bug fixes, refactors and
pattern-following feature additions do not need one.

**Write the ADR before implementation.** Capture the decision and its drivers during planning so
review happens on the decision rather than after the code exists.

**An ADR is immutable once merged.** It may still be revised within the scope of the PR that
introduces it. After it lands on `main`, leave it as-is: supersede or extend it with a new ADR, marking
the old one's Status as `Superseded` and linking the replacement.

Follow [ADR 001](adr/001-replace-mcp-client-framework.md): Title, Deciders/Date, Status, Context &
Problem, Decision Drivers, Considered Options, Decision Outcome.

**Keep it at decision altitude.** State the problem, drivers, options and choice. No code excerpts,
line-number citations or class-by-class walkthroughs: implementation detail belongs in code or
reference docs. Cut paragraphs explaining *how* rather than *why*.

**ADRs own the why; reference docs own the current state.** State current behavior in reference
docs; link to an ADR when history matters instead of narrating it. Include implementation detail
in an ADR only when needed to justify the decision.

**Known gaps get a named Follow-up, not repeated disclaimers.** Record a rough edge once in a
`## Follow-up` section, with the concrete condition and mechanism that closes it. State the fact
where observed and link to the ADR for the reasoning.

**Code comments and javadoc state their own contract, not another module's rationale.** Describe
what a method does, its parameter requirements and precise caller-visible triggers. Link to sibling
types in the same package when useful (e.g. `handleNoInput` to `CompositionResult.NoInput`).
Do not use another package or module's internals or decision history to justify a precondition.
If understanding a precondition requires reading a caller across that boundary, document the coupling
in an ADR or reference doc, not the method's javadoc.
