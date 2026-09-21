# Native Provider Reference

Implementation notes specific to each fully-native (non-LangChain4j) v2 chat model provider —
what a particular provider's converters and configuration do, and why. The general rules every new
v2 provider must follow (backend-subtype wrapping, provider-namespaced metadata, metadata stamping)
and how to add a provider at all live in
[`ai-agent.md` §25.1](ai-agent.md#251-add-an-llm-provider); read that first. This file only holds the
per-provider "here's what's special" detail that would otherwise bloat that section.

## Anthropic

One wire format (the Messages API), so a single backend axis covers everything: `AnthropicBackend`
(`anthropic-api` | `aws-bedrock-mantle` | `foundry` | `custom`). `descriptiveProvider()` reports this
backend too, e.g. `anthropic/aws-bedrock-mantle`.

### Backends

`AnthropicCustomBackend` is the only variant exposing user-configurable
`headers`/`queryParameters`/`bodyProperties`, and the only one supporting genuine no-auth
(`AnthropicCustomEndpointAuthentication.NoAuthentication`) alongside `apiKey` and
`OAuthClientCredentialsAuthentication` (OAuth 2.0 client-credentials flow). Overrides merge
additively per-key onto the SDK request builder (`AnthropicMessageRequestConverter
.applyRequestCustomizations`), never a wholesale replace.

`AnthropicChatModelFactory` adds an `OAuthBearerTokenInterceptor` (`com.anthropic.core.http.Interceptor`,
a supported public hook) via `builder.addInterceptor(...)`: it wraps the transport `HttpClient` and rewrites
each outgoing `HttpRequest` with a fresh `Authorization: Bearer` header, resolved per request from
the same shared `OAuthClientCredentialsTokenResolver` the OpenAI provider uses
(`provider/authentication/oauth/`).

`AnthropicFoundryBackend` (Microsoft Foundry) delegates to the Anthropic Java SDK's own
`com.anthropic.foundry.backends.FoundryBackend`, which normalizes the base URL (appending `/anthropic`
to the configured `endpoint` if missing) and authorizes each request: API-key auth sends the native
`x-api-key` header (not the generic Azure `api-key` header OpenAI's Foundry backend uses), while either
Entra ID variant supplies `Authorization: Bearer <token>` from a `Supplier<String>` the SDK calls per
request. Authentication config and Entra ID token handling are shared with the OpenAI provider — see
[Microsoft Foundry authentication](#microsoft-foundry-authentication).

### Credentials

`anthropic-api` supports a saved `io.camunda:agentic-ai-anthropic-api-credential:1` credential
(`apiKey` only); the escape hatches (`endpoint`/`headers`/`queryParameters`/`bodyProperties`) stay
inline and always visible regardless. See [ADR 015](../adr/015-v2-provider-credential-templates.md)
for the general rationale.

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
`descriptiveProvider()` reports both axes too, e.g. `openai/completions/custom`.

### Backends

`OpenAiCustomBackend` is the only variant exposing user-configurable
`headers`/`queryParameters`/`bodyProperties`. Overrides merge additively per-key via
`OpenAiRequestCustomizations` (shared between both converters). Its
`OpenAiCustomEndpointAuthentication` sealed interface supports `none`, `apiKey` (static), and
`OAuthClientCredentialsAuthentication` (OAuth 2.0 client-credentials flow, for OpenAI-compatible API
gateways that require it). Unlike Anthropic, `none` is not genuinely credential-less: the openai-java
client builder requires some credential source to build at all, so `applyCustomBackend` sends a
placeholder `apiKey` for this variant, surfacing on the wire only as a fixed `Authorization: Bearer
not-required` header, which self-hosted no-auth servers (LM Studio, Ollama, etc.) ignore.

For OAuth, `OpenAiChatModelFactory.applyCustomBackend` wraps the shared
`OAuthClientCredentialsTokenResolver` (`connector-commons/http-client`, backed by the same
`OAuthService`/`OAuthTokenCache` the HTTP connector uses) as a `com.openai.credential.BearerTokenCredential`
supplier via `builder.credential(...)`, invoked fresh on every request — the same mechanism
`FoundryCredentialResolver` uses for Entra ID. The MCP client's `OAuthHeadersSupplier` is
migrated onto the same resolver in a stacked follow-up, so all OAuth2 client-credentials token
fetching in this module eventually shares one cache.

`OpenAiFoundryBackend` (Microsoft Foundry / Azure OpenAI) exposes the same request customizations as
`headers`/`queryParameters`/`bodyProperties`, but hidden, matching
`AnthropicAwsBedrockMantleBackend`'s pattern rather than the fully-visible `custom` backend. Its
`OpenAiChatModelFactory.applyFoundryBackend` maps each `FoundryAuthentication` variant onto the
openai-java `Credential` the SDK builder needs: an Azure API key becomes an
`com.openai.azure.credential.AzureApiKeyCredential` (sent as the dedicated `api-key` header rather than
`Authorization: Bearer`), and either Microsoft Entra ID variant becomes a `BearerTokenCredential` over
the token supplier `FoundryCredentialResolver` returns — so the factory never sees a raw
`TokenCredential` or any secret material. The authentication model itself is shared with the Anthropic
provider: see [Microsoft Foundry authentication](#microsoft-foundry-authentication).

`OpenAiChatModelFactory` normalizes the configured `endpoint` onto the unified `/openai/v1` API surface
(appending it if missing) for both classic Azure OpenAI (`*.openai.azure.com`) and Foundry
(`*.services.ai.azure.com`) hosts alike, per
[Microsoft's current Foundry endpoints guidance](https://learn.microsoft.com/en-us/azure/foundry/foundry-models/concepts/endpoints).
This isn't optional: the openai-java SDK's own Azure-surface detection (`AzureUrlPathMode.AUTO`) only
classifies a base URL as unified if its *path* already ends in `/openai/v1` — a bare resource endpoint,
which is exactly what this backend's own `endpoint` field asks for, would otherwise be routed as the
legacy, deployments-based API regardless of host. Every request therefore targets the unified surface,
which is also why the Entra ID token scope is fixed per Azure cloud rather than derived per endpoint
(see [Microsoft Foundry authentication](#microsoft-foundry-authentication)).

`apiVersion` exists only as a hidden, optional escape hatch for pinning a specific version, wired
through the SDK's dedicated `azureServiceVersion(...)` builder method; the unified surface otherwise
uses implicit versioning.

### Credentials

`openai-api` supports a saved `io.camunda:agentic-ai-openai-api-credential:1` credential (`apiKey`,
`organizationId`, `projectId` — all three together); the escape hatches (`endpoint`/`headers`/
`queryParameters`/`bodyProperties`) stay inline and always visible regardless. See
[ADR 015](../adr/015-v2-provider-credential-templates.md) for the general rationale.

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

## Gemini

One backend today, `GeminiBackend.GeminiApiBackend` (`google-gemini-api`, API-key auth) against the
Google GenAI Java SDK's Developer API. A second backend, `google-vertex-ai`, is added on a stacked
branch and shares every converter described below unchanged.

### Backends

`GeminiChatModelFactory.buildClient` builds the SDK's `Client` directly from the API key; there is no
custom-backend variant (no user-configurable headers/query params, unlike Anthropic/OpenAI's
`*CustomBackend`). `descriptiveProvider()` reports this backend too, e.g.
`google-gemini/google-vertex-ai`.

### Reasoning

`GeminiThinking.enabled` gates `thinkingBudget` (Gemini 2.5, token budget) and `thinkingLevel`
(Gemini 3.x: `minimal`/`low`/`medium`/`high`, plus `MODEL_DEFAULT`) — both stay unset and no
`ThinkingConfig` is sent unless `enabled` is `true`. An omitted/`MODEL_DEFAULT` level is not simply
left out when thinking is enabled: `GeminiContentRequestConverter.toThinkingLevel` sends it as the
SDK's own `THINKING_LEVEL_UNSPECIFIED` sentinel, so "let the model choose" is explicit on the wire
rather than inferred from absence. Setting both a budget and an explicit level is rejected client-side
(`GeminiThinking.isBothThinkingBudgetAndLevelSet`, `@AssertFalse`) — stricter than the real API, which
only errors on Gemini 3.x (2.5 just ignores `thinkingLevel`); a deliberate simplification, not a bug.
`thoughtSignature` is preserved verbatim on any part it appears on (not only `functionCall`/thinking
parts — a plain text part can carry one too) and restored byte-identical on replay; Gemini 3 rejects a
follow-up request whose history dropped it.

### Caching

Implicit and read-only, like OpenAI: `GeminiContentResponseConverter.toMetrics` reads
`cachedContentTokenCount` for `cacheReadTokenCount` only, nothing to configure, no cache-write metric.
Gemini's explicit caching (`CachedContent`, a session-scoped resource with its own
create/reference/expire lifecycle) is out of scope for the same reason it's out of scope everywhere
else in this file — a different feature from a per-request model call, not a per-provider gap.

### Tool-result documents

Like Anthropic and OpenAI, a document inside a tool result is flattened to a JSON reference rather
than embedded natively: `GeminiContentConverter.toFunctionResponseParts` serializes just
`doc.document()` to a text `Part`, the same reference-only shape `AnthropicContentConverter
#toToolResultBlocks`/`OpenAiContentConverter#toResponsesToolResultOutputItems` produce — the
document's actual bytes are already delivered to the model elsewhere for tool results, so embedding
them here too would send them twice. Only `#toParts` (ordinary message content) embeds natively.

### Truncation

`SAFETY`/`RECITATION`/`BLOCKLIST`/`PROHIBITED_CONTENT`/`SPII`/`IMAGE_SAFETY`/
`IMAGE_PROHIBITED_CONTENT`/`IMAGE_RECITATION` finish reasons, and a blocked prompt (no candidate at
all, only `promptFeedback`), never reach `StopReason`: `toResult` checks the raw response shape
directly and throws `ContentFilteredException` before any of that finish-reason mapping runs — Gemini
has no refusal-without-a-signal case the way OpenAI does, since a blocked prompt is its own distinct
wire shape. `MAX_TOKENS` maps to `LENGTH`; a missing tool-use finish reason is synthesized as
`TOOL_USE` when the candidate carries `functionCall` parts, but only after the filtering check, so a
filtered candidate that also happens to carry a tool call is never misclassified. Every other finish
reason falls back to `UnknownStopReason` with the raw value preserved.

Gemini has no context-window-specific error signal, unlike OpenAI's `BadRequestException` with
`code=context_length_exceeded`: an over-length prompt surfaces as a plain `ApiException` (HTTP 400,
`status="INVALID_ARGUMENT"`, shared with many unrelated validation failures). `GeminiChatModel
#isContextWindowExceeded` matches on the one stable, distinctive substring of the message text
("exceeds the maximum number of tokens allowed", confirmed identical across the Developer API and
Vertex AI backends) to throw `ContextWindowExceededException` instead of the generic
`ERROR_CODE_FAILED_MODEL_CALL` — message-matching is inherently brittle against upstream wording
changes, but the SDK exposes no more reliable signal for this condition.

### Timeout and retry

`GeminiChatModelFactory.buildClient` always installs a `ClientOptions.customHttpClient` with a fixed
10-second OkHttp `connectTimeout`, merged into the same `ClientOptions` builder as any proxy
configuration. Without it, the SDK's own default `OkHttpClient` (built when no custom client is
supplied) leaves `connectTimeout`/`readTimeout`/`writeTimeout` at zero (unbounded) and relies solely
on `HttpOptions#timeout` as an overall `callTimeout` — a hung TCP connect would otherwise consume the
whole, often much longer, configured request budget before failing. The overall timeout itself stays
exactly as configurable as before (`TimeoutConfiguration`, shared with Anthropic/OpenAI); only connect
is bounded separately, and only at this fixed default — there is no user-facing property for it.
Retry needs no equivalent fix: the SDK unconditionally wraps every call in a `RetryInterceptor`
(decompiled defaults: 5 attempts, exponential backoff with full jitter, retrying on
408/429/500/502/503/504) whether or not `HttpOptions.retryOptions()` is configured, matching
Anthropic/OpenAI's own SDK-default retry behavior (neither configures anything explicitly either).

## Microsoft Foundry authentication

Shared by the [Anthropic](#anthropic) and [OpenAI](#openai) `foundry` backends: both target the same
Microsoft Entra ID surface, so the authentication model and all azure-identity plumbing live in one
place rather than once per provider — the same split `AwsAuthentication` already uses across Bedrock
Converse and Anthropic's `aws-bedrock-mantle` backend. Each provider's factory still does its own
final wrapping into its vendor SDK's credential type, since those types are vendor-specific.

### Authentication model

`FoundryAuthentication` (`ApiKeyAuthentication` | `ClientCredentialsAuthentication` |
`ManagedIdentityAuthentication`) is one sealed interface in `model.request.v2`, bound per provider at
`provider.<provider>.backend.foundry.authentication.*`. `ManagedIdentityAuthentication` is blocked on
SaaS (`ConnectorUtils.isSaaS()`) since a SaaS runtime doesn't execute inside the customer's Azure
tenant.

### Entra ID token scope

The scope is fixed per Azure cloud rather than derived per endpoint: `https://ai.azure.com/.default`
for Azure Public Cloud, `https://ai.azure.us/.default` for Azure US Government — the only other
sovereign cloud Foundry supports today. `ClientCredentialsAuthentication`'s `authorityHost` field
selects between them (matched against `com.azure.identity.AzureAuthorityHosts.AZURE_GOVERNMENT`;
anything else, including an unset host, is Azure Public Cloud); `ManagedIdentityAuthentication` has no
such field — its scope is always Azure Public Cloud, since IMDS-based managed identity is inherently
tied to the cloud the identity already runs in and there's currently no field to signal otherwise.
Each Entra ID variant carries its own hidden `entraIdScope` escape hatch for a wrong guess: a custom
`authorityHost` `FoundryCredentialResolver` doesn't recognize, or a managed identity that does need a
non-default scope.

`FoundryCredentialResolver` resolves an Entra ID variant into a plain `Supplier<String>` (no vendor SDK
type), pairing the `TokenCredential` from `EntraIdTokenCredentialFactory` with the scope above. It
caches no token itself: the supplier is invoked per request and relies on the credential's own cache.
The supplier reads the token straight off the credential (`getTokenSync`). azure-identity's
`AuthenticationUtil.getBearerTokenSupplier` is deliberately not used: it obtains the token by sending
a throwaway HTTP request to `www.example.com` and reading the `Authorization` header back off it,
which would put an outbound call to an unrelated host on every LLM request and bypass the
credential's own proxy configuration.

### Credential caching and proxy behavior

Since a `ChatModel` (and its underlying client) is rebuilt on every agent turn, azure-identity
`TokenCredential` instances (`ClientSecretCredential`, `ManagedIdentityCredential`) are cached and
reused across turns by `EntraIdTokenCredentialFactory`, a bounded Caffeine cache
(`camunda.connector.agenticai.aiagent.chat-model.azure.credential-cache.*`) keyed by a SHA-256 hash of
the credential configuration — never the raw secret material itself, mirroring
`CaffeineOAuthTokenCache` in connector-commons/http-client. Only the credential *object* is cached;
azure-identity's credentials already cache and auto-refresh their own tokens internally, so rebuilding
the client each turn never forces a fresh Entra ID token request as long as the credential object is
reused. The scope is deliberately not part of the cache key: azure-identity caches tokens per
requested scope on the credential itself.

`EntraIdTokenCredentialFactory` also applies the configured HTTP proxy (`AgenticAiHttpProxySupport
.azureProxyOptions`) to the `ClientSecretCredentialBuilder`, so the client-credentials flow's token
exchange with `login.microsoftonline.com` goes through the same proxy as the model API calls rather
than bypassing it. Managed identity is deliberately excluded: its token request targets the
link-local IMDS endpoint (or an environment-provided local sidecar endpoint), neither reachable via
an internet-facing egress proxy.

The connection's configured `timeout` reaches the token exchange as well as the model call, so a
slow Entra ID endpoint or IMDS cannot stall a request indefinitely. It is applied as the credential
HTTP client's connect and response timeout, for both Entra ID variants, which makes it a per-attempt
rather than an overall deadline: each phase may consume it in full, and azure-identity's own retries
start a fresh attempt, so a retrying token exchange can outlast a single `timeout`. Because it is baked
into that client, it is part of the credential cache key: two otherwise identical configurations with
different timeouts get their own credential rather than silently sharing whichever was built first.
When no connection timeout is supplied, the factories use the configured
`chat-model.api.default-timeout`; therefore the credential still receives a configured HTTP client
even when no proxy is present.
