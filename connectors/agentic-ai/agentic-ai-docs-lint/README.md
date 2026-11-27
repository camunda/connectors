# agentic-ai-docs-lint

## Purpose

Checks that keep the docs of the agentic AI modules in step with the modules and the code. The main code
is a docs lint that knows no agentic AI paths or names. The tests apply it to the agentic AI docs. The
module supports development only and is not deployed, so the architecture tests do not analyze it.

## Key types

- `DocsLint`: finds the stale references of a doc, including its stale allowed names, and the README layout
  violations of a module, and lists the docs and modules in scope.
- `DocsLintConfig`: the root, the docs in scope, the modules and the README layout.
- `Findings` of `Violation` and `Warning`: a violation fails the test, a warning is reported.
- `AgenticAiDocsTest`: the config of the agentic AI docs. Runs the checks per doc, per module and on the
  config.
- `AgenticAiDocsSmokeTest`: seeds one defect per rule in a small tree and proves that the config of
  `AgenticAiDocsTest` finds each.
- `MarkdownParser` and `Doc`: a parsed Markdown file with its headings, links, code spans, fences and
  anchors, and the `docs-lint` comments that allow type names or a Java block, or mark a history doc. `Doc`
  checks its first heading, its sections and its Java fences, and gives the links under `## Docs`.
- `CodeSpan`: a code span of a doc, with the type names and the member reference it names.
- `JavaSourceIndex`: the types, imports, members and supertypes of the repository sources, and the
  declared names similar to a name.
- `DocChecks` and `DocTree`: the checks that combine the docs, the sources and the config, and the docs,
  modules and sources below the root.

## Docs

- [Docs checks](../AGENTS.md#docs-checks)
- [Agent instructions](AGENTS.md): the rules of the agentic AI modules that this module deviates from

## Rules

`AgenticAiDocsTest` (plain JUnit) applies these checks to the agentic AI docs:

- Each module README has the fixed sections and links the docs of its module.
- Relative links and anchors resolve.
- The docs hold no `java` code blocks, unless a `<!-- docs-lint: allow-java-block: reason -->` comment precedes the
  block.
- The types and members that the docs name in backticks exist in the repository sources, unless the doc allows the
  name with `<!-- docs-lint: allow-names Name: reason -->`.
- A doc that records removed code carries `<!-- docs-lint: history: reason -->`, which skips its types and members.

## Findings

A failure names the file and line, why it fails and what to verify. The code is the source of truth. A warning does
not fail the test. The build log shows each warning as one line `WARN <path>:<line>: <what>`, and the JUnit report
(the IDE test view) holds a `warning` entry with the why and the fix.
