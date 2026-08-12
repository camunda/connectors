# Native Provider Reference

Implementation notes specific to each fully-native (non-LangChain4j) v2 chat model provider —
what a particular provider's converters and configuration do, and why. The general rules every new
v2 provider must follow (backend-subtype wrapping, provider-namespaced metadata, metadata stamping)
and how to add a provider at all live in
[`ai-agent.md` §25.1](ai-agent.md#251-add-an-llm-provider); read that first. This file only holds the
per-provider "here's what's special" detail that would otherwise bloat that section.

## Anthropic

One wire format (the Messages API), so a single backend axis covers everything: `AnthropicBackend`
(`anthropic-api` | `aws-bedrock-mantle` | `custom`). Reasoning effort, extended thinking, and prompt
caching are model parameters, not backend concerns — see
`AnthropicChatModelConfiguration.AnthropicModel.AnthropicModelParameters`.
`AnthropicMessageRequestConverter`/`AnthropicMessageResponseConverter` handle the whole
request/response cycle uniformly across backends.

## OpenAI

Two orthogonal sealed axes instead of one: `OpenAiApi` (`completions` | `responses`, default
`responses`) and `OpenAiBackend` (`openai-api` | `custom`, default `openai-api`) vary independently,
so any backend can serve either wire format. The wire format is a sealed discriminator rather than a
flat enum so each family gets its own namespace for family-specific knobs — e.g. the differing
max-token field name (`maxCompletionTokens` vs `maxOutputTokens`) — without `condition` gating or
collisions.

### Backends

`OpenAiApiBackend` mirrors `AnthropicApiBackend` field-for-field (`apiKey` plus a hidden
`endpoint`/`headers`/`queryParameters`/`bodyProperties` override quartet). `OpenAiCustomBackend`
takes a required `endpoint` (the SDK appends `/chat/completions` or `/responses`) and
`OpenAiCustomEndpointAuthentication` — API key only, no no-auth option, because the openai-java SDK
client builder requires a credential source to build at all (unlike Anthropic's genuine no-auth
`AnthropicCustomEndpointAuthentication.NoAuthentication`); kept polymorphic for a future OAuth 2.0
variant. Headers/query/body are merged by `OpenAiRequestCustomizations` (shared between both
converters), not the client builder, matching `AnthropicMessageRequestConverter`'s
`RequestCustomizations`. Azure OpenAI is a deferred backend, same as Anthropic's Bedrock.

### Reasoning effort

One nullable `OpenAiEffort` enum per family. Completions maps it to `reasoningEffort` (input-only; no
reasoning content returns — `reasoning_tokens` comes from `completion_tokens_details` instead).
Responses maps it to `Reasoning.builder().effort(...)` and, when set, also sets `store(false)` and
requests `include: ["reasoning.encrypted_content"]` (the connector owns conversation state, so
OpenAI-side state would only compete for authority). `OpenAiResponsesResponseConverter` captures the
`reasoning` item as `ReasoningContent` with the full raw item as `payload` (built via the SDK's own
`ObjectMappers.jsonMapper()` — the app `ObjectMapper` leaks a spurious field). A non-empty `summary`
is joined onto `ReasoningContent.text`; it's stripped from `payload` only when reconstructible
byte-identical on replay, otherwise both copies stay.

### Caching

Automatic and read-only — no config, no cache-write metric, so the acceptance row sets
`reportsCacheCreationTokens = false`.

### Tool-result documents

Native `input_file`/`input_image` items on Responses (`OpenAiContentConverter.toToolResultOutputItems`);
Completions' tool-role messages are text-only, so documents reach the model only through the synthetic
`<doc/>` message `AgentConversationTurnInputComposerImpl` already appends. Neither converter carries a
private fix for the shared tool-result double-send/reference-blob-leak issue (affects
Anthropic/Bedrock Converse identically) — it deliberately mirrors Anthropic's behavior and inherits
the fix from that PR on rebase.

### Truncation

`finish_reason=length` / `incomplete_details.reason=max_output_tokens` both map to `StopReason.LENGTH`
(mirrors Anthropic's `MAX_TOKENS`); normal completion maps to `STOP`, tool calls to `TOOL_USE`,
`content_filter` to `CONTENT_FILTERED`. Only `CONTENT_FILTERED` trips the
[terminal-stop-reason guard](ai-agent.md#12-framework-abstraction), so `LENGTH` never fails the job.
Unrecognized `finish_reason` values fall back to `StopReason.UnknownStopReason` with the raw value
preserved.

### Deferred

Server tools (`web_search`, `code_interpreter` — capture/replay path exists, provisioning doesn't);
the Azure OpenAI backend (own PR, like Anthropic's Bedrock); `store: true` server-side conversation
state; generalizing `RESPONSE_TRUNCATED` into a cross-provider error (belongs in one core change, not
a per-provider workaround).
