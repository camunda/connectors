# Native Provider Reference

Implementation notes specific to each fully-native (non-LangChain4j) v2 chat model provider —
what a particular provider's converters and configuration do, and why. The general rules every new
v2 provider must follow (backend-subtype wrapping, provider-namespaced metadata, metadata stamping)
and how to add a provider at all live in
[`ai-agent.md` §25.1](ai-agent.md#251-add-an-llm-provider); read that first. This file only holds the
per-provider "here's what's special" detail that would otherwise bloat that section.

## Anthropic

One wire format (the Messages API), so a single backend axis covers everything: `AnthropicBackend`
(`anthropic-api` | `aws-bedrock-mantle` | `custom`).

### Backends

`AnthropicCustomBackend` is the only variant exposing user-configurable
`headers`/`queryParameters`/`bodyProperties`, and the only one supporting genuine no-auth
(`AnthropicCustomEndpointAuthentication.NoAuthentication`) alongside API-key auth. Overrides merge
additively per-key onto the SDK request builder (`AnthropicMessageRequestConverter
.applyRequestCustomizations`), never a wholesale replace.

### Reasoning

`AnthropicModelParameters.thinking` (`ENABLED`/`ADAPTIVE`/`DISABLED`) maps onto the SDK's
`ThinkingConfigParam`. The human-readable `thinking` text is lifted out of the raw response block
into `ReasoningContent.text` so it isn't persisted twice, then merged back in before replay to keep
the block byte-identical — required for thinking-signature verification. Redacted-thinking blocks
carry no text, only the encrypted payload.

### Caching

Opt-in per model (`AnthropicModelParameters.promptCaching.enabled`, default `false`): a single
top-level `cache_control` breakpoint covers the whole prefix (system prompt, tools, prior messages);
no per-message breakpoints.

### Tool-result documents

A document inside a tool result always renders as a JSON reference
(`AnthropicContentConverter.toToolResultBlocks`), never embedded natively, so the bytes the
composer's `<doc/>` fallback message already delivers aren't sent twice.

### Truncation

`stop_reason` maps to the domain `StopReason`: `max_tokens` → `LENGTH`, `tool_use` → `TOOL_USE`,
everything else → `STOP`. Unrecognized values fall back to `UnknownStopReason` with the raw value
preserved. `refusal` and `model_context_window_exceeded` never reach this mapping: `toResult` throws
`ContentFilteredException`/`ContextWindowExceededException` for them directly, carrying the
assistant message and metrics already built for the turn as the exception's `PartialResult` (see
[ai-agent.md §12](ai-agent.md#12-framework-abstraction)).

## OpenAI

Two orthogonal sealed axes: `OpenAiApi` (`completions` | `responses`, default `responses`) and
`OpenAiBackend` (`openai-api` | `custom`, default `openai-api`) vary independently, so any backend can
serve either wire format. The wire format is a sealed discriminator rather than a flat enum so each
family gets its own namespace for family-specific knobs — e.g. the differing max-token field name
(`maxCompletionTokens` vs `maxOutputTokens`) — without `condition` gating or collisions.

### Backends

`OpenAiCustomBackend` is the only variant exposing user-configurable
`headers`/`queryParameters`/`bodyProperties`, and requires an API key — no no-auth option, because the
SDK client builder requires a credential source to build at all. Overrides merge additively per-key
via `OpenAiRequestCustomizations` (shared between both converters).

### Reasoning effort

One nullable `OpenAiEffort` enum per family. Completions maps it to `reasoningEffort` (input-only; no
reasoning content returns — `reasoning_tokens` comes from `completion_tokens_details` instead).
Responses maps it to `Reasoning.builder().effort(...)` and, when set, also sets `store(false)` and
requests `include: ["reasoning.encrypted_content"]` (the connector owns conversation state, so
OpenAI-side state would only compete for authority). A non-empty `summary` is joined onto
`ReasoningContent.text`, stripped from the raw payload only when reconstructible byte-identical on
replay.

### Caching

Automatic and read-only — no config, no cache-write metric, so the acceptance row sets
`reportsCacheCreationTokens = false`.

### Tool-result documents

A document inside a tool result always renders as a JSON reference
(`OpenAiContentConverter.toToolResultOutputItems`), on both Responses and Completions, regardless of
content type — never embedded natively as `input_image`/`input_file`, so the bytes the composer's
`<doc/>` fallback message already delivers aren't sent twice.

### Truncation

`finish_reason=length` / `incomplete_details.reason=max_output_tokens` both map to `StopReason.LENGTH`;
normal completion maps to `STOP`, tool calls to `TOOL_USE`. Unrecognized `finish_reason` values fall
back to `StopReason.UnknownStopReason` with the raw value preserved. `content_filter` never reaches
this mapping on either API family: `toResult` throws `ContentFilteredException` directly, carrying
the assistant message and metrics already built for the turn as the exception's `PartialResult` (see
[ai-agent.md §12](ai-agent.md#12-framework-abstraction)). A refusal (a message content item, not a
stop reason) throws the same exception, since it carries no completion signal of its own to key off.
`LENGTH` never fails the job.

An over-length request is rejected outright with an HTTP 400 (`BadRequestException`,
`code=context_length_exceeded`) on both API families, rather than completing with a stop reason;
`OpenAiChatModel.execute` catches it directly and throws `ContextWindowExceededException` with no
`PartialResult` (the request was rejected before any response body could be converted).
