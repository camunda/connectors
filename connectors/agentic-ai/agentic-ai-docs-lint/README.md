# agentic-ai-docs-lint

## Purpose

Checks that keep the docs of the agentic AI modules in step with the modules and the code. The main code
is a docs lint that knows no agentic AI paths or names. The tests apply it to the agentic AI docs. The
module supports development only and is not deployed, so the architecture tests do not analyze it.

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
  anchors. `Doc` checks its first heading, its sections and its Java fences, and gives the links under
  `## Docs`.
- `CodeSpan`: a code span of a doc, with the type names and the member reference it names.
- `JavaSourceIndex`: the types, imports, members and supertypes of the repository sources, and the
  declared names similar to a name.
- `DocChecks` and `DocTree`: the checks that combine the docs, the sources and the config, and the docs,
  modules and sources below the root.

## Docs

- [Docs checks](../AGENTS.md#docs-checks)
