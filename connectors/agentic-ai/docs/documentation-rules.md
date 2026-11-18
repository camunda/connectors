---
applyTo: "connectors/agentic-ai/**,connectors-e2e-test/connectors-e2e-test-agentic-ai/**"
---

# Agentic AI documentation rules

These rules apply to `connectors/agentic-ai` and `connectors-e2e-test/connectors-e2e-test-agentic-ai`.
Follow them when you change code or docs. In a review, flag each change that breaks a rule.

The docs in scope are the Markdown files under these two directories, except `docs/adr/`,
`examples/` and the prompt files in `src/main/resources/`.

## Where a doc goes

| Content | Location |
| --- | --- |
| Purpose, key types and doc index of one module | `<module>/README.md` |
| The types of one module | `<module>/docs/<topic>.md` |
| Types in more than one module (agentic loop, extension points, Spring auto-configuration) | `docs/architecture/` |
| Template versions of a connector | `<connector module>/element-templates/README.md` |
| Cross-cutting rules | `docs/`, see below |

Cross-cutting docs in `docs/`: [design guidelines](design-guidelines.md),
[implementation rules](implementation-rules.md), [acceptance testing](acceptance-testing.md),
[target architecture](target-architecture.md), [template text guidelines](template-text-guidelines.md),
[breaking changes](breaking-changes.md) and these rules. The [ADR authoring rules](adr/README.md) are
in `docs/adr/`. [`AGENTS.md`](../AGENTS.md) is the entry point: mental model, navigation, glossary,
gotchas and build commands.

Every module README has these headings, in this order: `# <module>`, `## Purpose`, `## Key types`,
`## Docs`. `## Docs` links each file in `<module>/docs/` and each architecture doc that applies.
`connector-aiagent/README.md` is generated. Edit `connector-aiagent/README.peb` instead. Its title is
`# AI Agent Task`.

## Update the docs in the same change

Update the docs when a change touches behavior, a contract, a public type or member, a stored shape, a
configuration property or an error code.

1. Search the docs in scope and the Javadoc for the name of each changed type and member.
2. Fix each match that the change makes wrong.
3. For a breaking change for custom extensions, add an entry to
   [`breaking-changes.md`](breaking-changes.md) under the release in development. Give the old API,
   the new API and a migration hint.

## No code excerpts

Do not copy code into a doc. Name the type and member in backticks, for example
`GatewayToolHandler#resolveElementId`, and write 1 or 2 sentences about the contract. JSON, YAML and
Mermaid fences are fine.

## Checks

`AgenticAiDocsTest` in `agentic-ai-docs-lint` checks the README layout, the links and anchors, the
absence of `java` code blocks, and that each backticked type and type-member reference exists in the sources.
Run it after a doc change. See [Docs checks](../AGENTS.md#docs-checks) for the command and for the
`docs-lint` comments that allow an exception.

It does not find a doc that describes old behavior with current names. The update rule above and the
review cover that.

## In a review

Comment when a pull request changes behavior in a module and does not update the README, the docs of
that module, or the architecture doc that names the changed type.
