# aiagent-model

## Purpose

The agent model that the extension points use: the agent state and context, messages and their
content, tools, tool calls and tool call results, and the request configuration records. The module
does not depend on Spring.

## Key types

- `AgentContext`: the persisted agent state. The AI Agent stores it in the `agentContext` process
  variable.
- `AgentExecutionContext`: the request context of one job activation. It is not persisted.
- `AgentConfiguration`: the configuration of one handler invocation (chat model, prompts, memory,
  limits, events, response and tools).
- `Message` with `SystemMessage`, `UserMessage`, `AssistantMessage` and `ToolCallResultMessage`, and
  `Content` with `TextContent`, `DocumentContent`, `ObjectContent`, `ReasoningContent` and
  `ProviderContent`.
- `ToolDefinition`, `GatewayToolDefinition`, `ToolCall`, `ToolCallResult` and `ToolCallResultContent`.
  `ToolKind` tells whether a tool is a BPMN element of the process or a runtime tool.
- `ConversationContext`: the pointer to the stored conversation. `ConversationContextSubtypes` registers
  custom subtypes.
- `ChatModelConfiguration`: the provider and model that a chat model factory selects on.

## Docs

- [Data model and agent context](docs/data-model.md)
- [Agent state machine](../connector-aiagent/docs/agent-state-machine.md): the transitions of the agent state
- [Agentic loop](../docs/architecture/agentic-loop.md)
- [Extension points](../docs/architecture/extension-points.md)
