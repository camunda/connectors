# agentic-ai-docs-lint

## Purpose

Checks that keep the docs of the agentic AI modules in step with the modules and the code. The main code
is a docs lint that knows no agentic AI paths or names. The tests apply it to the agentic AI docs. The
module is not shipped.

## Key types

- `DocsLint`: finds the stale references of a doc, the README layout violations of a module and the stale
  allowed names, and lists the docs and modules in scope.
- `DocsLintConfig`: the root, the docs in scope, the modules, the README layout and the allowed names.
- `Findings` of `Violation` and `Warning`: a violation fails the test, a warning is reported.
- `AgenticAiDocsTest`: the config of the agentic AI docs. Runs the checks per doc, per module and on the
  config.
- `AgenticAiDocsSmokeTest`: seeds one defect per rule in a small tree and proves that the config of
  `AgenticAiDocsTest` finds each.
- `MarkdownParser` and `Doc`: a parsed Markdown file with its headings, links, code spans, fences and
  anchors.
- `JavaSourceIndex`: the types, imports, members and supertypes of the repository sources.
- `DocChecks` and `DocTree`: the checks, and the docs, modules and sources below the root.

## Docs

- [Docs checks](../AGENTS.md#docs-checks)
