---
applyTo: "connectors/agentic-ai/**,connectors-e2e-test/connectors-e2e-test-agentic-ai/**"
---

# Agentic AI documentation rules

These rules apply to `connectors/agentic-ai` and `connectors-e2e-test/connectors-e2e-test-agentic-ai`.
Follow them when you change code or docs. In a review, flag each change that breaks a rule.

## Where a doc goes

The docs in scope are the Markdown files under these two directories. The scope does not include
`docs/adr/`, `examples/` and the prompt files in `src/main/resources/`.

- **`<module>/README.md`:** each Maven module has a README. The first heading is `# <module>`. Then
  `## Purpose`, `## Key types` and `## Docs` come first, in this order. `## Docs` links each file in
  `<module>/docs/` and the architecture docs that apply to the module.
- **`<module>/docs/<topic>.md`:** content about the types of one module.
- **`docs/architecture/`:** content about types in more than one module. Examples: the agentic loop,
  the extension points and the Spring auto-configuration.
- **Central docs in `docs/`:** the [design guidelines](design-guidelines.md) with the architectural
  invariants, the [implementation rules](implementation-rules.md), these rules, the
  [acceptance testing rules](acceptance-testing.md), the [target architecture](target-architecture.md),
  the [template text guidelines](template-text-guidelines.md) and the [breaking changes](breaking-changes.md).
  The [ADR authoring rules](adr/README.md) are in `docs/adr/`. [`AGENTS.md`](../AGENTS.md) gives the orientation:
  the mental model, the navigation, the glossary, the gotchas and the build commands.
- **`<connector module>/element-templates/README.md`:** template versions of the connector. The index
  is `connector-agentic-ai/element-templates/README.md`.

Put a section in the module that has the types that it describes. If the section describes types in
more than one module, put it in `docs/architecture/`.

Exception: a template generates `connector-aiagent/README.md`. Edit `connector-aiagent/README.peb`. It
uses the shared connector README layout, so its first heading is `# AI Agent Task`.
`## Purpose`, `## Key types` and `## Docs` come first after the description.

## Update the docs in the same change

A change to behavior, to a contract, to a public type or member, to a stored shape, to a configuration
property or to an error code must update the docs in the same change.

1. Find the names of each changed type and member.
2. Search for each name in the docs in scope and in the Javadoc of the module sources.
3. Update each match that the change makes incorrect.
4. For a breaking change for custom extensions, add an entry to
   [`breaking-changes.md`](breaking-changes.md) under the release in development. Give the old API,
   the new API and a migration hint.

## No code excerpts

Do not copy code into a doc. A copy does not change when the code changes. Name the type and the
member in backticks, for example `GatewayToolHandler#resolveElementId`. Then write 1 or 2 sentences
about the contract. You can use JSON, YAML and Mermaid fences.

## The signature lock of aiagent-api

`aiagent-api/docs/api-signatures.lock` has the public signatures of `aiagent-api`. When the lock
changes, the docs must change too:

1. Run the build. `ApiSignaturesLockTest` fails, and gives the doc files and headings that name
   each changed type.
2. Update the docs in these sections.
3. Write the lock again with this command:

```bash
./mvnw test -f connectors/agentic-ai/pom.xml -pl agentic-ai-archunit-tests -am \
  -Dtest=ApiSignaturesLockTest -Dsurefire.failIfNoSpecifiedTests=false -Dupdate-doc-lock
```

## Checks

`agentic-ai-docs-lint` checks the docs in scope. Run it after a doc change:

```bash
./mvnw test -f connectors/agentic-ai/pom.xml -pl agentic-ai-docs-lint
```

- `ReferenceDocsTest`: each module has a README with the fixed headings, `## Docs` links each module
  doc, each relative link resolves, no doc has a Java fence, each backticked type exists in the code,
  each backticked member reference (type, `#`, member) resolves, and a doc names each public
  `aiagent-api` type in backticks.

`ApiSignaturesLockTest` in `agentic-ai-archunit-tests` checks that the public signatures of
`aiagent-api` agree with the lock.

The checks do not find a doc that describes old behavior with the current names. Thus the rules
above and the review are necessary.

## In a review

- Add a comment when a pull request changes behavior in a module and does not update that module's
  README or docs, or the architecture doc that names the changed type.
- Add a comment when `api-signatures.lock` changes and no doc changes.
