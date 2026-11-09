# Response Handling

## AgentResponse (the output)

The result of an agent turn: the updated `AgentContext` plus either the tool calls to execute next or
a final response to emit (text, JSON, or the full assistant message). Authoritative definition:
[`AgentResponse.java`](../../connector-aiagent/src/main/java/io/camunda/connector/agenticai/aiagent/agent/AgentResponse.java).

## AgentSubProcessConnectorResponse (job worker specific)

The job-worker completion directive: wraps the `AgentResponse` and tells the runtime how to complete
the ad-hoc sub-process job (whether the completion condition is fulfilled, whether to cancel
still-running tool instances, and which variables to set). Implements `AdHocSubProcessConnectorResponse`.
Authoritative definition:
[`AgentSubProcessConnectorResponse.java`](../../connector-aiagent/src/main/java/io/camunda/connector/agenticai/aiagent/AgentSubProcessConnectorResponse.java).

## ToolCallProcessVariable (tool call format for process variables)

Definition: [`ToolCallProcessVariable.java`](../../connector-aiagent/src/main/java/io/camunda/connector/agenticai/aiagent/tool/ToolCallProcessVariable.java).
Arguments are **flattened to the top level** so BPMN expressions can access them directly as
`toolCall.myParameter` rather than `toolCall.arguments.myParameter`. The `_meta` object holds the tool
call ID and name.

## AgentResponseHandlerImpl

Creates the `AgentResponse` from the LLM's assistant message. This runs once per turn, after the
continuation loop ([`chat-model.md`](chat-model.md#turn-based-continuation-loop)) has finished — i.e. on the assistant message of
the final, `Completed` round, not on any intermediate `Continuation` round:

1. **Response format**:
   - `TextResponseFormatConfiguration`: Response text returned as `responseText`. Optionally parse JSON from it.
   - `JsonResponseFormatConfiguration`: Response text must be valid JSON, parsed into `responseJson`. Fails with `FAILED_TO_PARSE_RESPONSE_CONTENT` if invalid.

2. **Markdown stripping**: Before JSON parsing, markdown code fences (` ```json ... ``` `) are stripped to handle models that wrap JSON in code blocks.

3. **Full message**: If `includeAssistantMessage` is true, the raw `AssistantMessage` is included in the response.

4. **Tool calls**: The process tool calls of the last turn (`AgentConversationTurn.processToolCalls()`), transformed by the
   gateway handlers, become `AgentResponse.toolCalls`. The local tool calls of the message stay in the
   stored assistant message and are not handed to the process ([`agentic-loop.md`](../../docs/architecture/agentic-loop.md#what-happens-when-tools-complete)).

## Job Worker Response Variables

On final completion (no tool calls):
- `agent` variable = `AgentSubProcessResponse` containing:
  - `responseText`, `responseJson`, `responseMessage`
  - Optionally `context` (if `includeAgentContext` is true)
- `agentContext` variable = updated agent context

On tool call iteration (tool calls present):
- `agentContext` = updated agent context
- `toolCallResults` = `[]` (cleared for next iteration)
