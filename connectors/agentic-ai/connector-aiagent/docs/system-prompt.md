# System Prompt Composition

For the `SystemPromptContributor` SPI, see
[`system-prompt-contributor.md`](../../aiagent-api/docs/system-prompt-contributor.md#systempromptcontributor-interface).

## SystemPromptComposerImpl

`compose(AgentExecutionContext executionContext, AgentContext agentContext)`:
1. Starts with the base system prompt from `SystemPromptConfiguration.prompt()` (via `AgentConfiguration.systemPrompt()`)
2. Iterates contributors sorted by `getOrder()` ascending
3. Non-blank contributions are collected and all parts joined with `"\n\n"`

## Known Implementations

| Implementation               | Order | Activation Condition                                  |
|------------------------------|-------|-------------------------------------------------------|
| `A2aSystemPromptContributor` | 100   | `agentContext.properties["a2aClients"]` is non-empty  |

The architecture supports adding more contributors by creating a Spring bean implementing the interface — the composer picks them up automatically.

## Usage Site

The core request handler invokes the composer once per turn while assembling the conversation. A blank
composed prompt produces no system message (nothing is added or persisted); a non-blank one becomes a
`SystemMessage` placed at the head of the conversation when it is rehydrated for the LLM.
