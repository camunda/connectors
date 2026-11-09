# agentic-ai-docs-lint

## Purpose

Checks that keep the docs of the agentic AI modules in step with the modules and the code. The module has
test code only and is not shipped.

## Key types

- `ReferenceDocsTest`: runs the checks per doc and per module, and checks that the docs name every public
  type of `aiagent-api/docs/api-signatures.lock`.
- `DocChecks`: the checks. Each returns findings.
- `Finding` with `Violation` and `Warning`: a violation fails the test, a warning is reported.
- `MarkdownParser` and `Doc`: a parsed Markdown file with its headings, links, code spans, fences and anchors.
- `JavaSourceIndex`: the types, imports, members and supertypes of the repository sources.
- `DocTree`: the docs in scope and the modules.

## Docs

- [Docs checks](../AGENTS.md#docs-checks)
