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

## Bedrock Converse

One wire format (the Bedrock Runtime Converse API), reaching every model family Bedrock hosts
(Amazon Nova, Anthropic Claude, Llama, Mistral, DeepSeek, Cohere, Gemma, gpt-oss). There is no
backend axis: `BedrockConverseChatModelConfiguration` carries a region, an `AwsAuthentication`
(static credentials, API key, or the default credentials chain) and an optional custom endpoint.

### HTTP overrides

`headers`, `queryParameters` and `bodyProperties` are user-configurable on every connection, in the
same `advanced-provider-options` template group Anthropic/OpenAI use for their `custom` backend.
There's no backend axis here, so unlike those two the escape hatch is never gated behind a `custom`
variant. `headers`/`queryParameters` merge onto the SDK request via `overrideConfiguration`
(`BedrockConverseRequestConverter.applyOverrideConfiguration`); `bodyProperties` merges into the
model-specific `additionalModelRequestFields` document instead of a generic top-level request body -
Converse's wire shape has no top-level body to merge onto the way Anthropic/OpenAI's custom backends
do (see Reasoning below).

### Streaming transport

Every call goes through the async `BedrockRuntimeAsyncClient.converseStream`, streaming or not: the
synchronous `BedrockRuntimeClient` exposes no `converseStream` operation at all, and a plain `converse`
call sits silent on the socket for the whole generation (issue #7193). `BedrockConverseStreamAssembler`
reassembles the AWS EventStream event sequence into a `ConverseResponse`, so the response converter
sees the same type either way. This is why the module pulls in
`software.amazon.awssdk:netty-nio-client` and a Netty client builder
(`ChatModelHttpProxySupport.createAwsAsyncHttpClientBuilder`) alongside the synchronous
Apache-based builder its other AWS SDK usage keeps.

### Residual capture

AWS SDK v2 generated types are not Jackson-serializable — they implement `SdkPojo`/`SdkField` with a
`MarshallingType` per field. `BedrockConverseSdkPojoCodec` is a generic bidirectional
`capture(SdkPojo) → Map<String,Object>` / `replay(Map, Supplier<SdkPojo>) → SdkPojo` codec that walks
`sdkFields()` reflectively, used both for unmapped `ContentBlock` members (preserved as
`ProviderContent`) and as the residual-metadata mechanism for the three typed blocks (`text`,
`toolUse`, `reasoningContent`).

### Document identity

`DocumentBlock.name` is required and must be stable across requests, otherwise no prompt-cache prefix
containing a document can ever hit. `DocumentHandle.idFor(Document)`
(`aiagent/model/document/DocumentHandle.java`) derives it deterministically: the Camunda document id
verbatim, or a SHA-256 prefix of the external URL / inline content.

### Reasoning

No typed reasoning configuration — enabling reasoning happens entirely through the generic
`bodyProperties` escape hatch (`additionalModelRequestFields` on the wire). This is deliberate:
Converse's reachable model families each enable reasoning through a different, incompatible shape with
irreconcilable budget-vs-effort semantics and no documented generalized dial, and sniffing the vendor
out of a model id breaks on custom-model and marketplace ARNs. A generalized dial belongs with the
capability matrix this module does not have yet.

Only the *request* side varies by model family. The `reasoningContent` block Converse *returns* is a
single normalized union (`reasoningText`/`redactedContent`) regardless of which model produced it, so
`BedrockConverseContentConverter`/`BedrockConverseResponseConverter` never branch on model family. The
real-API acceptance suite exercises this round-trip against three model families that each enable
reasoning through a different `bodyProperties` shape (Amazon's own model, a non-Amazon third-party
model, and Claude via the Converse path) to prove the normalization holds in practice, not just on paper.

### Caching

Opt-in per model (`BedrockConverseModelParameters.promptCaching.enabled`, default `false`),
expressed as Converse `cachePoint` blocks. Converse always reports a distinct cache-write count in
`TokenUsage`.

### Tool-result documents

A document inside a tool result always renders as the JSON reference the runtime's standard
`DocumentSerializer` writes — the same shape an embedded document gets
(`BedrockConverseContentConverter.toToolResultBlocks`, see `BedrockConverseDocuments`) and the same
fields the composer's `<doc/>` tag carries, so the model can correlate the two 1:1 — never embedded
as a native `DocumentBlock`, regardless of content type. The composer's `<doc/>` fallback message is
the document's only bytes-delivery channel; embedding it here too would send the bytes twice and
trip Converse's duplicate-document-name validation, since `DocumentHandle.idFor` produces the same
`DocumentBlock.name` in both places. The nested Camunda document is serialized, never round-tripped
through Jackson deserialization: an `Object.class` target reconstructs another `Document` and would
recurse forever.

### Truncation

`stopReason` maps to the domain `StopReason`: `end_turn`/`stop_sequence` → `STOP`, `tool_use` →
`TOOL_USE`, `max_tokens` → `LENGTH`. Unrecognized values fall back to `UnknownStopReason` with the
raw value preserved.

Four stop reasons never reach that mapping's result, because `toResult` throws before returning.
`content_filtered`, `guardrail_intervened` and `model_context_window_exceeded` throw
`ContentFilteredException`/`GuardrailInterventionException`/`ContextWindowExceededException`,
carrying the assistant message and metrics already built for the turn as the exception's
`PartialResult` (see [ai-agent.md §12](ai-agent.md#12-framework-abstraction)).
`malformed_model_output` and `malformed_tool_use` are a generation failure rather than a policy
decision, so they fail the call with `ERROR_CODE_FAILED_MODEL_CALL` instead.

## OpenAI

Two orthogonal sealed axes: `OpenAiApi` (`completions` | `responses`, default `responses`) and
`OpenAiBackend` (`openai-api` | `foundry` | `custom`, default `openai-api`) vary independently, so any
backend can serve either wire format. The wire format is a sealed discriminator rather than a flat enum
so each family gets its own namespace for family-specific knobs — e.g. the differing max-token field
name (`maxCompletionTokens` vs `maxOutputTokens`) — without `condition` gating or collisions.

### Backends

`OpenAiCustomBackend` is the only variant exposing user-configurable
`headers`/`queryParameters`/`bodyProperties`, and requires an API key — no no-auth option, because the
SDK client builder requires a credential source to build at all. Overrides merge additively per-key
via `OpenAiRequestCustomizations` (shared between both converters).

`OpenAiFoundryBackend` (Microsoft Foundry / Azure OpenAI) exposes the same request customizations as
`headers`/`queryParameters`/`bodyProperties`, but hidden, matching
`AnthropicAwsBedrockMantleBackend`'s pattern rather than the fully-visible `custom` backend. Its
`FoundryAuthentication` sealed interface supports an Azure API key
(`com.openai.azure.credential.AzureApiKeyCredential`, sent as the dedicated `api-key` header rather than
`Authorization: Bearer`) or Microsoft Entra ID via `ClientCredentialsAuthentication` /
`ManagedIdentityAuthentication`, both wrapped as `BearerTokenCredential` suppliers over an
azure-identity `TokenCredential`. `ManagedIdentityAuthentication` is blocked on SaaS
(`ConnectorUtils.isSaaS()`) since a SaaS runtime doesn't execute inside the customer's Azure tenant.
Resolving a `FoundryAuthentication` into the openai-java `Credential` the SDK builder needs — which
credential type each variant maps to and the Entra ID token scope — is encapsulated in
`FoundryCredentialResolver`; `OpenAiChatModelFactory` only calls `resolver.credential(authentication)`
and never sees a raw `TokenCredential` or any secret material. The credential caching itself (see
below) lives one layer further down, in the provider-agnostic `EntraIdTokenCredentialFactory`.

The openai-java SDK detects the Azure API surface (legacy dated `api-version` + deployment-in-path vs.
the newer unified `/openai/v1` GA API) automatically from the endpoint hostname, so neither an
api-version nor a URL-path-mode field is exposed as a normal config property. `apiVersion` exists only
as a hidden, optional escape hatch for pinning a specific legacy-style API version, wired through the
SDK's dedicated `azureServiceVersion(...)` builder method rather than the generic hidden
`queryParameters` map — a manually-set `api-version` query parameter is silently dropped by the SDK
when combined with Entra ID auth on a legacy-style endpoint.

Since a `ChatModel` (and the underlying `OpenAIClient`) is rebuilt on every agent turn, azure-identity
`TokenCredential` instances (`ClientSecretCredential`, `ManagedIdentityCredential`) are cached and
reused across turns by `EntraIdTokenCredentialFactory`, a bounded Caffeine cache
(`camunda.connector.agenticai.aiagent.chat-model.azure.credential-cache.*`) keyed by a SHA-256 hash of
the credential configuration — never the raw secret material itself, mirroring
`CaffeineOAuthTokenCache` in connector-commons/http-client. Only the credential *object* is cached;
azure-identity's credentials already cache and auto-refresh their own tokens internally, so rebuilding
the `OpenAIClient` each turn never forces a fresh Entra ID token request as long as the credential
object is reused. `EntraIdTokenCredentialFactory` is deliberately provider-agnostic (it returns a plain
`TokenCredential`, no vendor SDK type) so a future Anthropic-on-Foundry backend (issue #8060) can reuse
it directly instead of re-implementing the same azure-identity plumbing.

`EntraIdTokenCredentialFactory` also applies the configured HTTP proxy (`AgenticAiHttpProxySupport
.azureProxyOptions`) to the `ClientSecretCredentialBuilder`, so the client-credentials flow's token
exchange with `login.microsoftonline.com` goes through the same proxy as the OpenAI API calls rather
than bypassing it. Managed identity is deliberately excluded: its token request targets the
link-local IMDS endpoint (or an environment-provided local sidecar endpoint), neither reachable via
an internet-facing egress proxy.

### Reasoning effort

One nullable `OpenAiEffort` enum per family. Completions maps it to `reasoningEffort` (input-only; no
reasoning content returns — `reasoning_tokens` comes from `completion_tokens_details` instead).
Responses maps it to `Reasoning.builder().effort(...)`, conditional on `effort` being configured.
`store(false)` and `include: ["reasoning.encrypted_content"]` are both requested unconditionally
instead, independent of `effort`: the connector always owns conversation state (OpenAI-side state
would only compete for authority), and a reasoning-capable model can apply its own default reasoning
effort even without an explicit `effort`, so `encrypted_content` must always be available to replay
that reasoning item on a later turn. A non-empty `summary` is joined onto
`ReasoningContent.text`, stripped from the raw payload only when reconstructible byte-identical on
replay.

### Caching

Automatic and read-only — no config, no cache-write metric, so the acceptance row sets
`reportsCacheCreationTokens = false`.

### Tool-result documents

A document inside a tool result always renders as a JSON reference, on both Responses
(`OpenAiContentConverter.toResponsesToolResultOutputItems`) and Completions
(`OpenAiCompletionsRequestConverter.toTextOutput`, which has its own tool-result flattening rather
than sharing the former), regardless of content type — never embedded natively as `input_image`/
`input_file`, so the bytes the composer's `<doc/>` fallback message already delivers aren't sent
twice.

### Truncation

`finish_reason=length` / `incomplete_details.reason=max_output_tokens` both map to `StopReason.LENGTH`;
normal completion maps to `STOP`, tool calls to `TOOL_USE`. Unrecognized `finish_reason` values fall
back to `StopReason.UnknownStopReason` with the raw value preserved. `content_filter` never reaches
this mapping on either API family: `toResult` throws `ContentFilteredException` directly, carrying
the assistant message and metrics already built for the turn as the exception's `PartialResult` (see
[ai-agent.md §12](ai-agent.md#12-framework-abstraction)). A refusal (Responses' message-content
`refusal` item, Completions' `message.refusal` field) is detected separately from `hasRefusal` and
throws the same exception, since neither API surfaces it as a stop/finish reason - it's a normal
completed turn whose content happens to be a declination. `LENGTH` never fails the job.

An over-length request is rejected outright with an HTTP 400 (`BadRequestException`,
`code=context_length_exceeded`) on both API families, rather than completing with a stop reason;
`OpenAiChatModel.execute` catches it directly and throws `ContextWindowExceededException` with no
`PartialResult` (the request was rejected before any response body could be converted).
