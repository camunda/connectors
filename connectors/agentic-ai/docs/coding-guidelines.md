# Coding guidelines

Code contribution guidelines for anybody making changes in the modules owned by the
`@camunda/connectors-agentic-ai` team, independent of personal or global tooling configuration. They
establish a shared baseline so changes look consistent regardless of the author.

**Scope.** These apply to the team's modules as defined by the `@camunda/connectors-agentic-ai` entries
in the repo [`CODEOWNERS`](../../../CODEOWNERS) (the authority; treat the list below as an informative
snapshot): `connectors/agentic-ai`, `connectors/embeddings-vector-database`,
`connectors/aws/aws-bedrock-codeinterpreter`, `connectors/aws/aws-bedrock-knowledgebase`,
`connectors/aws/aws-bedrock-agentcore-runtime`, `connectors/aws/aws-bedrock-agentcore-long-term-memory`,
and `connectors-e2e-test/connectors-e2e-test-agentic-ai`.

**Relation to other connectors guidelines (align by default, diverge deliberately).** Follow the
repo-wide connectors conventions wherever they do not conflict with these guidelines. Where there is a
genuine conflict, these team guidelines take precedence for team-owned modules, and the divergence
should be called out (here or in the PR) rather than left implicit.

This file covers how to approach a change. It does not restate two neighbouring concerns:

- Module operating rules (testing, the Definition of Done, ADRs, architectural invariants) live in the
  module's own `AGENTS.md` (for agentic-ai: [`AGENTS.md`](../AGENTS.md)).
- Repo-wide build, commit, PR, CI, spotless, and license conventions live in the repo-root `AGENTS.md`.

These guidelines bias toward caution over speed. For trivial tasks, use judgment. Keep responses and
changes focused; skip non-essential context.

## 1. Think before coding

State assumptions; surface confusion early.

- Make assumptions explicit. If uncertain, ask.
- If multiple interpretations exist, present them rather than silently picking one.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop, name what is confusing, and ask before implementing.

## 2. Simplicity first

Write the minimum code that solves the problem; nothing speculative.

- No features beyond what was asked.
- No abstractions for single-use code.
- No flexibility or configurability that was not requested.
- No error handling for impossible scenarios.
- If 200 lines could be 50, rewrite it. Ask whether a senior engineer would call it overcomplicated.

## 3. Surgical changes

Touch only what the task requires; clean up only your own mess.

- Do not improve adjacent code, comments, or formatting in passing.
- Do not refactor working code. Before any refactor, even a small one, ask first.
- Match the existing style, even where you would do it differently.
- Mention unrelated dead code you notice; leave it in place unless asked to remove it.
- Remove imports, variables, and functions that your change orphaned; leave pre-existing dead code alone.
- The test: every changed line traces directly to the request.

## 4. Goal-driven execution

Define success criteria, then loop until verified.

- Turn tasks into verifiable goals. "Add validation" becomes "write tests for invalid inputs, then make
  them pass"; "fix the bug" becomes "write a test that reproduces it, then make it pass".
- For multi-step work, state a brief plan with a verification step for each step.
- Strong criteria let you work independently; weak criteria such as "make it work" force repeated
  re-clarification.

For verification specifics (unit vs e2e, the Definition of Done, TDD), see your module's `AGENTS.md`
(for agentic-ai: [`AGENTS.md`](../AGENTS.md)).
