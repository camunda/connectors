# Target architecture

This document shows the target state of the agentic AI module. The reference docs show the current state. [ADR 017](adr/017-target-architecture.md) records the decision.

Read this document before you plan a structural change. A structural change adds a module, a collaborator, an SPI or a policy that crosses modules.

## How the code gets to the target

- New code follows the target from the start.
- Existing code moves to the target only in dedicated issues. Do not move it as a side effect of a different change. The [coding guidelines](coding-guidelines.md) on surgical changes stay in effect.
- When a trait is stable, the team adds an architecture test for it and removes the prose rule.

## Traits

1. **Functional core, imperative shell.** Decisions live in immutable domain types. AI Agent decisions live in `aiagent-model`. MCP and A2A client decisions live in the client module that owns them (ADR 015). Connector functions, job workers, stores and provider adapters do only I/O. A test can check a core decision without Spring or mocks.
2. **Translate at the edge, one time.** The boundary converts element template binding records into a valid domain configuration. The core does not read nullable binding records. The core does not resolve default values. (#9535)
3. **One owner for each policy.** One module owns each decision that crosses modules. Examples: connection settings, instance status, the blank text rule. (#9532, #9533, #9534, #9536)
4. **Interfaces only at real seams.** Interfaces exist for the SPIs and for seams with two or more adapters. An adapter only translates between the domain and the wire format. A shared contract test proves each adapter. Until #9539 changes the interface invariant in [`AGENTS.md`](../AGENTS.md#architectural-invariants), the invariant applies. (#9539)
5. **Typed outcomes.** A step returns a sealed outcome type. The caller matches all subtypes of the outcome. A flavor-specific adapter converts the outcome into the response. (#9537)
6. **The tests follow the structure.** Use pure tests for the domain. Use contract tests for adapters. Use e2e tests for wiring and engine behavior.
7. **The build enforces stable traits.** Prose rules cover only the decisions that need judgment.

## Migration path

The issues under #9532 move the existing code to these traits. The dependency graph in #9532 sets the order.
