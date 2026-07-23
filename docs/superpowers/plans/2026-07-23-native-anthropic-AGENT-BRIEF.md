# Native Anthropic Provider — Agent Launch Brief

You are implementing the **native Anthropic chat-model provider** for the Camunda `connectors`
repo, epic #7211 ("own the LLM layer").

## How to work

**REQUIRED SUB-SKILL:** Use `superpowers:subagent-driven-development` to execute the plan
task-by-task (fresh implementer subagent per task + a task review after each, broad review at the
end). **Dispatch every implementer and reviewer subagent on the Sonnet model (`model: "sonnet"`)
— never Opus.**

## Read first (do NOT copy these into this branch — reference by path)

- **Plan:** `/Users/mathias.geat/orca/workspaces/connectors/issue-7211-own-llm-layer/docs/superpowers/plans/2026-07-23-native-anthropic-provider-plan.md`
- **Design spec:** `/Users/mathias.geat/orca/workspaces/connectors/issue-7211-own-llm-layer/docs/superpowers/specs/2026-07-23-native-anthropic-provider-design.md`

Start with **Task 0** (branch reconnaissance), then proceed through the tasks in order. Only stop
for BLOCKED status or genuine ambiguity — otherwise run continuously.

## Port source (read-only reference)

The classes in the plan's port-map are the pilot's native Anthropic implementation. They live in
the **pilot worktree**:

`/Users/mathias.geat/orca/workspaces/connectors/issue-7211-own-llm-layer/<same relative path>`

Read the source there; write your target files into **THIS** worktree at the same relative path.
**Never modify the pilot worktree.** This is a rebuild-by-hand port (adapt per the plan) — do NOT
`git cherry-pick`.

## Your worktree / branch

This worktree, branch `agentic-ai/native-anthropic-provider`, **stacked on
`agentic-ai/v2-config-types`**. Work only here.

## Critical constraints (also in the plan's Global Constraints — obey all)

- **Do NOT push. Do NOT create a PR.** When the plan is complete, STOP, report, and wait for
  further instruction.
- **Do NOT commit the superpowers design/plan/brief docs into this branch.** They live in the
  pilot worktree and are referenced by path only.
- **NEVER use `mvn … -am`** — it reformats proprietary license headers to Apache across ~1500
  files. Build affected modules directly; cross-build the e2e module via install-to-m2 + `-pl`.
- **Regenerate element templates with `mvn clean process-classes`** (NOT `compile` — the generator
  binds `process-classes`).
- The e2e tests are a **separate module** (`connectors-e2e-test/connectors-e2e-test-agentic-ai`):
  install the connector to `~/.m2` first, then run affected classes with `-pl` (no `-am`). Read
  the surefire XML reports for results, not mvn stdout.
- **Do NOT run real-API e2e** (no `RUN_NATIVE_LLM_E2E`, no real keys). The real-API smoke IT is
  **compile-only** in this PR.
- **Scope:** backends `anthropic-api` (direct) + `compatible` only. NO Bedrock/Azure/Vertex, NO
  OAuth on compatible, NO server tools, NO Skills, NO capability matrix. Drop all `*Capabilities*`
  and `AnthropicSkillReference` files; the `ChatModelApi` supplies **no** `capabilities()`.
- **Keep `pause_turn` handling** (maps to `ChatModelResult.Continuation`).
- **Reasoning/effort ships WITHOUT matrix-driven validation** — preserve only matrix-independent
  structural checks; invalid combos fail loud at the Anthropic API.
- **Rename scheme:** bare `ProviderConfiguration` in the versioned package (no `V2` type prefix);
  `Ai` prefix → `Agent`; `V1`/`V2` suffix only on connector `Function` + `Request` classes. Verify
  the actual branch shape in Task 0. Connector type-ID strings are the external contract — never
  change them.
- **Subagents only `git add`/`commit` their own work** — never `checkout`/`reset`/`revert`/
  `rebase`/`stash`/`push`.
- **Commit messages describe the change only** — no plan/review/round/pilot/superpowers wording.

Update the Orca worktree comment at meaningful checkpoints:
`orca worktree set --worktree active --comment "…" --json`.
