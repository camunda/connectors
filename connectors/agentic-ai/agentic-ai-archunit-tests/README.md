# agentic-ai-archunit-tests

## Purpose

Architecture tests for the production classes of all agentic AI modules. Test-only, not shipped.

## Key types

The tests use ArchUnit (`archunit-junit5`) and are in package `io.camunda.connector.agenticai.archunit`,
one test class per topic. The class Javadoc of each architecture test describes what it checks.
`AgenticAiModules` maps the modules to their package roots and kinds.

## Docs

- [Architecture tests](../AGENTS.md#architecture-tests): when to update the tests and how to run them
- [Architectural invariants](../docs/design-guidelines.md#architectural-invariants)
