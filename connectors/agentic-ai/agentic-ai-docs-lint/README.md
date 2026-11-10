# agentic-ai-docs-lint

## Purpose

Checks that keep the docs of the agentic AI modules in step with the modules and the code. The main code is a
docs lint that knows no agentic AI paths or names. The tests apply it to the agentic AI docs. The module is not
shipped.

## Key types

- `DocsLint`: checks a doc, a module README or the config, and lists the docs and modules in scope.
- `DocsLintConfig`: the root, the docs in scope, the modules, the README layout and the allowed names.
- `Findings` of `Violation` and `Warning`: a violation fails the test, a warning is reported.
- `AgenticAiDocsTest`: the config of the agentic AI docs. Runs the checks per doc, per module and on the config.
- `MarkdownParser` and `Doc`: a parsed Markdown file with its headings, links, code spans, fences and anchors.
- `JavaSourceIndex`: the types, imports, members and supertypes of the repository sources.
- `DocChecks` and `DocTree`: the checks, and the docs, modules and sources below the root.

## Docs

- [Docs checks](../AGENTS.md#docs-checks)
