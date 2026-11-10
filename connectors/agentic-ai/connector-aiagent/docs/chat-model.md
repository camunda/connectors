# Chat model implementation

## Turn-based continuation loop

`BaseAgentRequestHandler#driveContinuationLoop` drives the SPI in a loop. Each round calls
`ChatModel#execute`, turns a `ChatModelRejectedException` into a coded connector error, tags the tool
calls of the assistant message with their kind or a rejection, ingests the assistant message into the
turn and reports the round to the agent instance.

A `ChatResult.Continuation` starts the next round on a fresh turn, after the model-call limit check. A
`ChatResult.Completed` ends the loop, unless the message has local tool calls and no process tool calls:
then the round of the local tool call results described in
[`agentic-loop.md`](../../docs/architecture/agentic-loop.md#what-happens-when-tools-complete) is the next
round.

Only `AnthropicMessageResponseConverter` returns a `ChatResult.Continuation`, for the `pause_turn` stop
reason. All other providers, including `LangChain4JChatModel`, always return `ChatResult.Completed`, so the
loop runs once for them unless local tool calls add a round.

Agent-instance metrics are reported **once per round**, not once per job: each round's
`AgentInstanceClient#applyTurnCompletion` call carries that round's own `AgentConversationTurn#metrics`
on its assistant history item. The handler never sums metrics across rounds. See [`agent-instance.md`](agent-instance.md) and
[ADR 013](../../docs/adr/013-unified-agent-instance-turn-updates.md).

## Implementations

The `ChatModelRegistry` collects every `ChatModelFactory` bean. `ChatModelRegistryImpl#resolve` asks each
factory whether it supports the `ChatModelConfiguration` and uses the one that does. It fails with an
IllegalArgumentException when no factory supports the configuration and with an IllegalStateException
when more than one does.

Two kinds of implementation exist:

- **Native providers** call the vendor SDK directly: Anthropic, Bedrock Converse, OpenAI (including the
  Azure and Microsoft Foundry backends), Mistral and Gemini (including the Vertex AI backend).
  `AgenticAiNativeProvidersConfiguration` registers their factories. Each factory supports the matching
  `request.v2` configuration type. See [`native-providers.md`](native-providers.md).
- **LangChain4j** has one factory per v1 provider. Each factory supports the v1 `ProviderConfiguration` of its
  provider. See [`langchain4j-chat-model.md`](langchain4j-chat-model.md).

A configuration is either a v1 or a v2 type, so only one kind of factory can match it. By default,
`AgentTaskV1Function` and `AgentSubProcessV1Function` rewrite the v1 provider configuration to its v2
equivalent with `V1ToV2ProviderConfigurationMapper`, so v1 requests run on the native providers. The
LangChain4j factories are used only when
`camunda.connector.agenticai.aiagent.rewrite-v1-provider-config-to-v2` is `false`. A
`CustomProviderConfiguration` is served by a user-supplied factory, see
[`extension-points.md`](../../docs/architecture/extension-points.md#add-an-llm-provider).
