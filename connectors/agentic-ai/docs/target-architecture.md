# Target architecture

This document shows the target state of the agentic AI module. The reference docs show the current state. [ADR 017](adr/017-target-architecture.md) records the decision.

Read this document before you plan a structural change. A structural change adds a module, a collaborator, an SPI or a policy that crosses modules.

## How the code gets to the target

- New code follows the target from the start.
- Existing code moves to the target only in dedicated issues. Do not move it as a side effect of a different change. The [surgical changes rule](implementation-rules.md#surgical-changes) stays in effect.
- When a trait is stable, the team adds an architecture test for it and removes the prose rule.

## Traits

1. **Functional core, imperative shell.** Decisions live in immutable domain types. AI Agent decisions live in `aiagent-model`. MCP and A2A client decisions live in the client module that owns them (ADR 015). Connector functions, job workers, stores and provider adapters do only I/O. A test can check a core decision without Spring or mocks.
2. **Translate at the edge, one time.** The boundary converts element template binding records into a valid domain configuration. The binding records stay nullable, because running process instances from earlier versions do not set the new fields. The domain configuration shows the real nullability: a value is optional only when it can be absent. The core does not read binding records and does not resolve default values. Do not replace a missing value with a default that the provider does not accept. Example: a response format set to text broke the Anthropic configuration, which needs the field left out. (#5435, #9535)
3. **One owner for each policy.** One module owns each decision that crosses modules. Examples: connection settings, instance status, the blank text rule. (#9532, #9533, #9534, #9536)
4. **Interfaces only at real seams.** Interfaces exist for the SPIs and for seams with two or more adapters. An adapter only translates between the domain and the wire format. A shared contract test proves each adapter. A concrete bean with `@ConditionalOnMissingBean` is also an extension point: a user can replace it without an interface. Until #9539 changes the interface invariant in [`AGENTS.md`](../AGENTS.md#architectural-invariants), the invariant applies. (#9539)
5. **Typed outcomes.** A step returns a sealed outcome type. The caller matches all subtypes of the outcome. A flavor-specific adapter converts the outcome into the response. (#9537)
6. **The tests follow the structure.** Use pure tests for the domain. Use contract tests for adapters. Use e2e tests for wiring and engine behavior.
7. **The build enforces stable traits.** Prose rules cover only the decisions that need judgment. When a review finds a problem that a build check can find, add the check, or open a follow-up issue under #9591.

## Module depth and extension points

The [code design rules](implementation-rules.md#code-design) "Narrow surface, deep module" and "One owner per invariant" apply within a module. This section applies them across modules.

- **The extension points of one concept live together.** The extension points of a concept are its public SPI interfaces, the beans that users can replace, the classes that users can extend, and the exceptions that their contracts require. For AI Agent concepts, the contract lives in one package of an API module: `aiagent-api`, or `aiagent-chatmodel-api` for the chat model. The default implementation lives in one package in its owning connector, integration or provider module, respecting the module boundaries in [ADR 015](adr/015-split-agentic-ai-into-modules.md). Within each owning module, a concept does not spread its extension points over many packages. Example to avoid: one feature puts its SPI interface in `api.tool`, the exception that the SPI requires in `chatmodel.provider`, and the bean that users replace in `aiagent.agent`.
- **A typical next change is cheap.** A typical next change touches few files. Examples: a new input source, a new provider, a new content type, a change of a wire format. The correctness rules for the change live in one place, so the change cannot miss them. When a design makes such a change touch many files that each repeat the same rules, the design has a defect, even when each file is correct. A new sealed subtype that the compiler routes to each `switch` is acceptable, because the compiler finds each location.

## How this document changes

An agent MAY propose a change to this document in a PR. A team member approves each change. When a plan must deviate from a trait, or this document is missing information or out of date, the agent stops and asks. It does not decide the target alone.

## Migration path

The issues under #9532 move the existing code to these traits. The dependency graph in #9532 sets the order.
