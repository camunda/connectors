# agentic-ai-docs-lint: Agent Instructions

This module holds the docs checks of the agentic AI modules. It is a development tool: no runtime loads
it, no bundle ships it, Maven does not deploy it, and no user calls its API. The rules of the agentic AI
modules are written for production code that runs agents in a process. This file states which of them
apply here and where this module deviates on purpose. Where this file says nothing, the
[module agent instructions](../AGENTS.md) and the [implementation rules](../docs/implementation-rules.md)
apply.

The [README](README.md) gives the purpose and the key types. The
[docs checks section](../AGENTS.md#docs-checks) gives what the checks enforce and how to run them.

## Module rules

- The main code knows no agentic AI paths, module names or type names. Everything specific to the
  agentic AI docs is in the config of the tests: `AgenticAiDocsTest`.
- The module depends on no agentic AI module. The other modules do not depend on it.
- The main code reads the sources with the JDK compiler tree API (`com.sun.source`) and the Markdown with
  CommonMark. It loads no repository classes. A check that needs a compiled class is out of scope.

## Rules that apply

- [Simplicity first](../docs/implementation-rules.md#simplicity-first),
  [Surgical changes](../docs/implementation-rules.md#surgical-changes) and
  [Code comments and Javadoc](../docs/implementation-rules.md#code-comments-and-javadoc).
- From the [code design rules](../docs/implementation-rules.md#code-design): the functional style,
  records and immutable values, no side effects on arguments, sealed and exhaustive hierarchies (for
  example `Finding`), `Optional` only as a return type, and null-marked code.
- [Null safety](../AGENTS.md#null-safety) and [ErrorProne](../AGENTS.md#errorprone) as in the other
  modules.
- Domain rule 6, tests pin the whole observable outcome: a test compares the complete findings, not a
  part of them.

## Deviations

These rules do not apply to this module. A review must not flag code here for them.

| Rule                                                                                                                                  | Here                                                                                                                                                                                                           |
| ------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [Before you plan](../docs/implementation-rules.md#before-you-plan)                                                                    | Does not apply. The module has no flavors, element templates, conversation stores, providers or running process instances.                                                                                     |
| [Domain rules](../docs/implementation-rules.md#domain-rules) 1 to 5                                                                   | Do not apply. They are about the agent runtime and the systems it calls.                                                                                                                                       |
| [Design guidelines](../docs/design-guidelines.md)                                                                                     | Do not apply, including the architectural invariants. The module has no stored data, customer-facing names, product defaults, extension points or Spring wiring.                                              |
| [Architecture tests](../AGENTS.md#architecture-tests)                                                                                 | Do not analyze this module. Keep the module rules above by review.                                                                                                                                             |
| Wire the main collaborators with dependency injection                                                                                 | No Spring. Create a collaborator with `new` or its factory method, for example `JavaSourceIndex.of` and `MarkdownParser.parse`.                                                                                |
| Generated builders, not long constructors                                                                                             | Use `@RecordBuilder` from record-builder directly, as `DocsLintConfig` does. `@AgenticAiRecord` is in an agentic AI module, and this module depends on none.                                                   |
| No new utility classes                                                                                                                | A check of one type is a method of that type, for example the README layout checks of `Doc`. A check that combines several types (docs, the doc tree, the source index and the config) is a static method of `DocChecks`.         |
| Rich domain models, tell, don't ask, one reason to change per class                                                                   | Apply only as far as the parsed docs and sources are the model. There is no service layer.                                                                                                                     |
| [Definition of Done](../AGENTS.md#definition-of-done): e2e test decision, acceptance rules, real-provider coverage, element templates | Do not apply. The tests of this module are the acceptance.                                                                                                                                                     |
| [Breaking changes](../docs/breaking-changes.md) and the API compatibility check                                                       | Do not apply. The API has no users outside this repository.                                                                                                                                                    |

## Changing a check

- Add a test to `DocsLintTest` for each case that the check accepts and each case that it reports.
- For a new rule, seed one defect in `AgenticAiDocsSmokeTest` that the config of `AgenticAiDocsTest`
  finds.
- A violation fails the build, so a new rule passes on the current docs. Fix a stale doc in the same
  change. Make a finding that cannot be verified a warning.
- Update the key types of the [README](README.md) and the [docs checks section](../AGENTS.md#docs-checks)
  when the checks or the types change.
- Run `./mvnw test -f connectors/agentic-ai/pom.xml -pl agentic-ai-docs-lint`.
