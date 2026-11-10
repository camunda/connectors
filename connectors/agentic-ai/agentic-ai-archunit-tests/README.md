# agentic-ai-archunit-tests

## Purpose

Architecture tests for the production classes of all agentic AI modules. Test-only, not shipped.

## Key types

- `AgenticAiModules`: maps the modules to their package roots and kinds.
- The `*ArchTest` classes and `ModuleInventoryTest`.
- `ApiSignaturesLockTest`: checks the public signatures of `aiagent-api` against
  `aiagent-api/docs/api-signatures.lock`.

## Docs

- [Architecture tests](../AGENTS.md#architecture-tests)
- [Architectural invariants](../docs/design-guidelines.md#architectural-invariants)
