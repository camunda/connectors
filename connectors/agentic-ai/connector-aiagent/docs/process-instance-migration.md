# Process Instance Migration

## How Detection Works

`AgentInitializerImpl.resumeReadyAgent()` compares the job's current `processDefinitionKey` against
the one stored in the agent context's metadata. A mismatch (or absent metadata, for a pre-existing
agent context predating this field) triggers a tool definitions refresh. On a mismatch, only
`processDefinitionKey` is updated on the existing metadata — `agentInstanceKey`, `lastIterationKey`,
and `configurationFingerprintHistory` are preserved so the agent stays attached to its agent instance
across the migration. `processInstanceKey` is never refreshed: migration acts on the running process
instance itself, so its key is invariant.

## What's Allowed

- **Adding new tools**: Merged into existing definitions
- **Changing tool descriptions/parameters**: Updated in-place
- **Changing tool implementation** (without changing definition): Transparent — agent doesn't see the change

## What's Blocked

- **Removing/renaming tools**: `ERROR_CODE_MIGRATION_MISSING_TOOLS`
- **Adding/removing gateway tools (MCP/A2A)**: `ERROR_CODE_MIGRATION_GATEWAY_TOOL_DEFINITIONS_CHANGED`

## Task vs Sub-process Migration Difference

- **Task**: Input mappings are re-evaluated each loop iteration, so config changes (system prompt, model, etc.) are picked up immediately
- **Sub-process**: Input mappings are evaluated once on AHSP entry. Config changes via migration are **not** picked up for running instances
