# Replace LangChain4j with Native Provider Layer

* Deciders: Agentic AI Team
* Date: May 5, 2026

## Status

**Proposed**

## Context and Problem Statement

The agentic-ai module abstracts LLM access behind a single global `AiFrameworkAdapter` whose only
production implementation wraps LangChain4j (`Langchain4JAiFrameworkAdapter`). Six per-vendor
`ChatModelProvider<T>` beans construct LangChain4j `ChatModel` instances; converters translate our
domain `Message`/`Content` types to and from LangChain4j's `ChatMessage` types.

This abstraction worked while LangChain4j tracked provider features closely. It increasingly does
not. Concrete blockers we have hit or will imminently hit:

* `AssistantMessage` / `SystemMessage` content lists are restricted to a single `TextContent`
  block at conversion time
  ([`ChatMessageConverterImpl.java:104-110`](../../src/main/java/io/camunda/connector/agenticai/aiagent/framework/langchain4j/ChatMessageConverterImpl.java)).
  Reasoning blocks and multi-block assistant content cannot be represented even structurally.
* `ToolCallResultMessage` carries content as a single `Object` flattened to a JSON string. Native
  multimodal tool results (images and PDFs inside `tool_result` blocks, supported by Anthropic and
  AWS Bedrock at the wire level) cannot be expressed; the workaround in PR #6999 synthesizes a
  user message with `DocumentContent` blocks because the framework cannot carry documents in tool
  results.
* `AgentMetrics.TokenUsage` records only input + output token counts. Cache attribution
  (`cache_creation` / `cache_read`) and reasoning-token spend are not surfaced.
* Reasoning models are not supported. Anthropic extended thinking, OpenAI Responses-API reasoning,
  and Gemini thinking budgets all require carrying signed reasoning blocks across turns; the
  framework discards them.
* Prompt caching is not configurable. Every provider exposes some form of cache control
  (Anthropic per-block ephemeral markers, Bedrock cache points, OpenAI prompt cache key, Gemini
  context caching); the framework exposes none.
* For Claude on AWS Bedrock and Google Vertex AI, the framework routes through Bedrock Converse /
  Vertex generic adapters, which are lowest-common-denominator and lose Claude-specific features
  even though Anthropic publishes platform-backend SDKs that preserve full feature parity.
* Per-block cache control, server-side tool blocks (web search, code execution), and beta-API
  flags all require escape hatches the framework does not expose.

Should we extend LangChain4j upstream and continue building on it, or replace the framework layer
with our own provider abstraction?

## Decision Drivers

* **Feature surface**: We need access to provider features (multimodal tool results, reasoning
  with signature roundtrip, granular cache control, cache and reasoning token attribution) that
  the framework's lowest-common-denominator types structurally cannot represent.
* **Release cadence**: Closing each gap upstream requires waiting for the framework's release
  cycle; some of the gaps above are structural (data-model restrictions) and unlikely to be
  closed without breaking changes.
* **Maintenance burden**: A provider abstraction that we own lets us pick official vendor SDKs,
  benefit from their auth / retry / signing handling, and surface their richer types directly.
* **Predictable rollout**: A complete replacement avoids a long-lived hybrid code path with two
  sets of converters and conditional behavior.

## Considered Options

1. Continue with LangChain4j; contribute upstream PRs for missing features.
2. Build a thin custom HTTP/JSON layer per provider, owning the wire format end-to-end.
3. Replace the framework abstraction with a custom provider layer built on the official vendor
   Java SDKs (Anthropic, AWS Bedrock, OpenAI, Google GenAI).

## Decision Outcome

Chosen option: **Option 3 — Native provider layer over official vendor SDKs**.

The vendor SDKs already handle authentication (including SigV4, OAuth refresh, Bearer + Azure
credentials, GCP ADC), retries, timeouts, signing, SSE / event-stream parsing, and tool-schema
derivation. Reimplementing these in-house (Option 2) is unnecessary duplication. Continuing on
LangChain4j (Option 1) does not address the structural data-model restrictions.

The replacement is delivered as one shipping unit (after a small additive Phase 0). LangChain4j
support is preserved as an opt-in bridge implementation for users running custom bundles with
provider models we have not yet covered natively, but is no longer registered by default.

### Positive Consequences

* Direct access to provider-native content blocks (multimodal tool results, signed reasoning,
  cache control breakpoints, server-side tool blocks).
* Full token-usage attribution including cache and reasoning tokens.
* Cleaner cloud Claude story: a single Anthropic implementation serves direct, AWS Bedrock,
  Google Vertex, and Azure Foundry deployments without lossy adapter routing.
* Opt-in SDK dependencies via `@ConditionalOnClass` — deployments only pull in the providers
  they use.
* Discriminated streaming events enable accurate tool-argument accumulation with mid-stream
  repair, partial-content preservation on errors, and clean abort propagation.

### Negative Consequences

* Larger per-provider implementation than today's LangChain4j wrappers.
* One-time migration effort across the framework, converters, and provider configuration shapes.
* Element template version bump for provider configuration restructuring (Task and Sub-process
  flavors).
* Capability matrix YAML to maintain alongside the supported model set.

## Architecture

The framework abstraction is replaced with a layered design:

```
┌──────────────────────────────────────────────────────────────┐
│  ChatClient (facade — what BaseAgentRequestHandler calls)   │
│   resolves model + capabilities, applies tool-result        │
│   strategy, dispatches to ChatModelApi.                     │
└──────────────────────────────────────────────────────────────┘
            │
            ▼
┌──────────────────────────────────────────────────────────────┐
│  ChatModelApi (per-job instance, configuration baked in)    │
│   capabilities() : ModelCapabilities                         │
│   complete(request, options, listener) : CompletableFuture  │
│                                                              │
│  Constructed by ChatModelApiFactory<C> — one bean per       │
│  wire-protocol family.                                       │
└──────────────────────────────────────────────────────────────┘
            │
            ▼
┌──────────────────────────────────────────────────────────────┐
│  Vendor SDKs                                                 │
│   anthropic-sdk-java | aws-sdk-java-v2 bedrockruntime |     │
│   openai-java | google-genai-java                           │
└──────────────────────────────────────────────────────────────┘
```

Five native API families ship in scope:

| Family             | Wire protocol            | Covers                                                                        |
|--------------------|--------------------------|-------------------------------------------------------------------------------|
| `anthropic-messages` | Anthropic Messages API   | Claude direct, Claude on Bedrock, Claude on Vertex AI, Claude on Azure Foundry|
| `bedrock-converse` | AWS Bedrock Converse     | Nova, Mistral, Llama, Cohere on Bedrock                                       |
| `openai-responses` | OpenAI Responses API     | GPT-5, o-series, Azure deployments thereof                                    |
| `openai-completions` | OpenAI Chat Completions  | Legacy OpenAI Chat models, OpenAI-compatible gateways (Ollama, vLLM, etc.)   |
| `google-genai`     | Google GenAI             | Gemini Developer API + Vertex AI (single client, backend toggle)              |

For Claude on Bedrock / Vertex / Foundry, the Anthropic SDK exposes platform-backend modules
(`anthropic-java-bedrock`, `anthropic-java-vertex`, `anthropic-java-foundry`) that preserve the
Anthropic wire format end-to-end. Routing Claude through these backends instead of through
generic platform adapters is a key reason for the restructure.

### Per-job instance pattern

`ChatModelApiFactory<C extends ProviderConfiguration>` is a singleton bean (one per wire
protocol). Per call, the registry resolves the factory by `ProviderConfiguration` discriminator
and produces a per-job `ChatModelApi` instance carrying the resolved configuration and (lazily
constructed) SDK client. `ChatClient` performs this resolution once at the start of each
request and reuses the instance across capability lookups, strategy application, and the call
itself.

This mirrors the current `ChatModelProvider<T>` pattern: factory beans are stateless, runtime
clients are per-call.

### Streaming-first internally, blocking surface externally

Each `ChatModelApi` implementation drives the SDK's streaming API internally. Streaming gives
us accurate tool-argument JSON accumulation across partial chunks, partial-content preservation
when a call errors mid-stream, mid-call abort on job cancel, and incremental token-usage
updates. None of these benefits require exposing a streaming primitive to callers.

The public surface is `CompletableFuture<ChatResponse>` plus an optional
`ChatStreamListener` for in-process observability (logging, metrics, future event emission).
Listener defaults to NOOP. No reactive types in the public API.

### Stream event hierarchy

```java
public sealed interface ChatModelEvent permits
    StartEvent,
    TextStartEvent, TextDeltaEvent, TextEndEvent,
    ReasoningStartEvent, ReasoningDeltaEvent, ReasoningEndEvent,
    ToolCallStartEvent, ToolCallArgumentsDeltaEvent, ToolCallEndEvent,
    UsageEvent,
    DoneEvent, ErrorEvent { }
```

Each delta event carries a content-block index so the listener can group fragments by block.
`DoneEvent` carries the final assembled `ChatResponse`. `ErrorEvent` carries the error message,
partial content accumulated so far, and the partial usage record.

### Error semantics

Three classes of failure with distinct surface behavior:

| Class                      | Examples                                                                       | Surface                                                                                                 |
|----------------------------|--------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| Model-side terminal        | `stop_reason: refusal`, content filter, max-tokens hit, malformed tool-use     | `CompletableFuture` completes normally with `ChatResponse{stopReason=ERROR, errorMessage, content, usage}` |
| Transport / SDK / I/O      | Connection refused, read timeout, TLS failure, malformed wire response         | `CompletableFuture` completes exceptionally with `ConnectorException(ERROR_CODE_FAILED_MODEL_CALL, ...)` |
| Auth / config              | Bad API key, region not enabled, model not found                                | `CompletableFuture` completes exceptionally with a distinct error code                                 |

Token usage and partial content accumulated before the failure are preserved on the response
record where applicable, so call accounting remains accurate even on terminal errors.

## Domain Model Extensions

All changes are additive and nullable. Existing serialized `agentContext` records and stored
conversations deserialize unchanged with new fields defaulting to `null` / empty.

### `Content` sealed hierarchy

One new variant:

```java
public sealed interface Content permits
    TextContent, DocumentContent, ObjectContent,
    ReasoningContent { ... }                           // NEW

public record ReasoningContent(
    String text,
    @Nullable String signature,                        // opaque round-trip blob
    boolean redacted,
    Map<String, Object> metadata
) implements Content { }
```

`signature` carries the provider-specific encrypted reasoning blob (Anthropic encrypted
thinking, Gemini `thoughtSignature`, OpenAI Responses encrypted reasoning item). It is
required for multi-turn reasoning continuation; the model rejects requests where prior
reasoning is not replayed verbatim.

`DocumentContent` (Camunda `Document` reference) remains the single representation for any
multimedia in the system. Inline binary content does not appear in the domain model — bytes
exist only transiently inside a `ChatModelApi` implementation while serializing requests or
materializing model output to a Camunda Document.

### `AssistantMessage` provenance and metrics

Four new optional fields:

```java
public record AssistantMessage(
    List<Content> content,
    List<ToolCall> toolCalls,
    @Nullable String modelId,                          // NEW
    @Nullable String apiId,                            // NEW
    @Nullable StopReason stopReason,                   // NEW
    @Nullable TokenUsage usage,                        // NEW (per-message)
    Map<String, Object> metadata                       // existing — escape hatch
) implements Message, ContentMessage { }
```

`StopReason` is a normalized enum: `STOP | LENGTH | TOOL_USE | ERROR | ABORTED |
CONTENT_FILTERED | GUARDRAIL`. Each implementation maps provider-specific finish reasons into
this set.

### `ToolCallResult` multimodal support

```java
public record ToolCallResult(
    @Nullable String id,
    @Nullable String name,
    @Nullable Object content,                          // existing — string/JSON path
    @Nullable List<Content> contentBlocks,             // NEW — when present, used preferentially
    Map<String, Object> properties
) { }
```

When `contentBlocks` is present, implementations whose target API supports multimodal tool
results pass it through as native content (`tool_result` content list on Anthropic,
`ToolResultContentBlock` on Bedrock). Implementations without native support delegate to
`ToolCallResultStrategy` — see below.

### `TokenUsage` extension

```java
public record TokenUsage(
    int inputTokens,
    int outputTokens,
    int cacheReadInputTokens,                          // NEW
    int cacheCreationInputTokens,                      // NEW
    int reasoningTokens,                               // NEW
    int totalTokens                                    // computed when not reported
) { }
```

`AgentMetrics.tokenUsage` becomes the cumulative roll-up across calls;
`AssistantMessage.usage` is per-call.

### Removed surface

`AiFrameworkChatResponse#rawChatResponse()` is removed. The current interface exposes it
but no caller consumes it; the `AssistantMessage.metadata` map serves as the escape hatch
for provider-specific data when needed.

## Capability Matrix

A YAML resource on the classpath records capabilities per supported model. The schema is
oriented around what the runtime needs to decide:

```yaml
anthropic-messages:
  models:
    - id: claude-opus-4-7
      aliases: [claude-opus-latest]
      capabilities:
        input_modalities:
          user_message: [text, image, pdf]
          tool_result:  [text, image]
        output_modalities:
          assistant_message: [text]
        supports_reasoning: true
        supports_reasoning_signature_roundtrip: true
        supports_prompt_caching: true
        supports_parallel_tool_calls: true
        context_window: 200000
        max_output_tokens: 64000
    - pattern: claude-opus-*
      capabilities: { ... }                            # best-effort family default
    - pattern: claude-haiku-*
      capabilities:
        supports_reasoning: false                      # haiku family does not yet have extended thinking
        ...
```

Modality vocabulary: `text | image | pdf | audio | video`. Modality lists per location
(`user_message`, `tool_result`, `assistant_message`) are symmetric — every modality at every
location has an explicit answer for each model.

### Resolution order

Most-specific-first, scoped to the api family of the connector configuration:

1. **Connector config override** — user-declared `modelCapabilities` block on the provider
   configuration always wins.
2. **Exact id or alias match** — the `id` field of an entry, or any string in its `aliases`
   list, equals the requested model id.
3. **Pattern match** — `pattern` (glob with `*` only) matches the requested model id;
   longest-matching pattern wins.
4. **Conservative defaults** — text-only across the board, all `supports_*` flags `false`,
   numeric limits null.

Aliases resolve at step 2 directly (no pre-rewriting); patterns at step 3 match against
the original requested id. Resolution at steps 3 or 4 logs an INFO message on first use so
operators notice they are running on best-effort or default capabilities. Resolution at
step 2 is silent — alias mappings are verified declarations.

### Conservative defaults for unknown models

```yaml
input_modalities:
  user_message: [text]
  tool_result:  [text]
output_modalities:
  assistant_message: [text]
supports_reasoning: false
supports_reasoning_signature_roundtrip: false
supports_prompt_caching: false
supports_parallel_tool_calls: false
context_window: null
max_output_tokens: null
```

Unknown api families fail at validation, not at runtime — they have no factory bean to
resolve, so requests can never start.

## Tool Call Result Routing

`ToolCallResultStrategy` decides per tool result whether to pass `contentBlocks` through to
the provider as native multimodal content or to fall back to the existing user-message
extraction approach (PR #6999). The decision is per content block, driven by the capability
matrix:

```
for each Content block in tool result:
    modality = modalityOf(block)                              // text | image | pdf | audio | video
    if modality in capabilities.toolResultModalities():
        keep block inline as part of ToolCallResult.contentBlocks
    else:
        delegate to user-message fallback (synthetic UserMessage with DocumentContent)
```

Models with `supports_*_in_tool_result` modalities for a given media type get the native
path. Models without — including all models served via the LangChain4j bridge — get the
fallback. This makes PR #6999 the safe default and the multimodal-native path the
opt-in-by-capability optimization.

## Reasoning Support

Two-tier API:

```java
public sealed interface ReasoningConfig
    permits ReasoningEffort, ReasoningBudget, ReasoningDisabled { }

public record ReasoningEffort(Effort level) implements ReasoningConfig { }
   // MINIMAL | LOW | MEDIUM | HIGH | X_HIGH
public record ReasoningBudget(int tokens) implements ReasoningConfig { }
public record ReasoningDisabled() implements ReasoningConfig { }
```

`ChatOptions.reasoning` carries a high-level config; per-implementation translation maps it
to provider-native fields (Anthropic adaptive `effort` for newer Opus / Sonnet, budget-based
`thinking_budget_tokens` for older Claude 4; OpenAI Responses `reasoning.effort`; Gemini
`ThinkingConfig.thinkingBudget`; Bedrock per-model). Provider-specific overrides remain
available via `ChatOptions.providerOptions`.

`ReasoningContent.signature` round-trip is mandatory for multi-turn reasoning. Each
implementation:

* Accumulates signature deltas during streaming and stores them on the `ReasoningContent`
  block in the assistant message.
* Replays signatures on subsequent requests when the prior assistant turn is included in the
  conversation history.

`TokenUsage.reasoningTokens` populates from provider-native fields where reported (OpenAI
`outputTokensDetails.reasoningTokens`, Gemini `thoughtsTokenCount`); zero where not separately
reported.

## Prompt Caching

```java
public enum CacheRetention { NONE, SHORT, LONG }
```

`SHORT` corresponds to the provider's default ephemeral retention (Anthropic 5 minutes,
OpenAI default prompt cache, Bedrock default). `LONG` corresponds to extended retention
(Anthropic 1 hour, OpenAI 24 hours, Bedrock 1 hour). `NONE` strips all cache markers.

Cache breakpoint placement is implementation-specific. Each implementation places markers
at the boundaries the provider supports:

* `anthropic-messages`: `cache_control` ephemeral markers on system prompt, last tool
  definition, and last user / tool-result content block (up to 4 breakpoints).
* `bedrock-converse`: `cachePoint` blocks at the same boundaries, on supported models.
* `openai-responses` / `openai-completions`: caching is automatic; `prompt_cache_key` is
  set to the conversation identifier.
* `google-genai`: implicit caching is always on; explicit context cache lifecycle is a
  future extension.

`TokenUsage.cacheReadInputTokens` and `cacheCreationInputTokens` populate from provider-native
fields.

## Provider Configuration Restructure

Configurations are restructured by wire format. The `ProviderConfiguration` sealed type
retains six members (matching today's count) but each gains a discriminator for backend or
api-family choice. Element template UI groups conditional fields under each discriminator
value.

```
ProviderConfiguration
├── AnthropicProviderConfiguration         api: anthropic-messages
│     backend: { direct | bedrock | vertex | foundry }     ← NEW
│     auth fields conditional on backend
│     model: AnthropicModel
├── BedrockProviderConfiguration           api: bedrock-converse
│     (non-Anthropic Bedrock only: Nova, Mistral, Llama, Cohere)
│     auth (existing AWS chain)
│     model: BedrockModel
├── OpenAiProviderConfiguration            api: openai-{responses|completions}
│     apiFamily: { responses | completions }               ← NEW (default: responses)
│     auth (API key)
│     model: OpenAiModel
├── AzureOpenAiProviderConfiguration       api: openai-{responses|completions}
│     apiFamily: { responses | completions }               ← NEW
│     auth (existing — endpoint, key)
│     model: AzureModel
├── OpenAiCompatibleProviderConfiguration  api: openai-{responses|completions}
│     apiFamily: { responses | completions }               ← NEW (default: completions)
│     auth (existing — endpoint, optional key)
│     model: OpenAiCompatibleModel
└── GoogleGenAiProviderConfiguration       api: google-genai
      backend: { developer-api | vertex }                  ← NEW
      auth fields conditional on backend
      model: GeminiModel
```

`GoogleVertexAiProviderConfiguration` is renamed to `GoogleGenAiProviderConfiguration` since
the same configuration now covers both backends.

`BedrockProviderConfiguration` becomes non-Anthropic-only at validation time. Saved
configurations with `anthropic.*` model identifiers are rewritten by the Jackson migration
deserializer to the Anthropic configuration with backend = bedrock — see below.

### Backward compatibility — Jackson migration

A custom `StdDeserializer<ProviderConfiguration>` rewrites legacy configuration shapes to
the new structure at deserialization time. Existing process instances continue to work
without manual intervention.

| Saved shape                                                                | Rewritten to                                                                              | Detection             |
|----------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|-----------------------|
| `{type: bedrock, bedrock.model.model: "anthropic.claude-..."}`             | `{type: anthropic, anthropic.backend: bedrock, anthropic.model.model: "anthropic..."}`    | model-id prefix       |
| `{type: bedrock, bedrock.model.model: "amazon.nova-..." \| "meta..." }`    | passthrough                                                                               | model-id prefix       |
| `{type: googleVertexAi, ...}`                                              | `{type: googleGenAi, googleGenAi.backend: vertex, ...}`                                   | discriminator rename  |
| `{type: anthropic, ...}` (no backend)                                      | `{type: anthropic, anthropic.backend: direct, ...}`                                       | missing field default |
| `{type: openai, ...}` (no apiFamily)                                       | `{type: openai, openai.apiFamily: completions, ...}`                                      | missing field default |
| `{type: azureOpenAi, ...}` (no apiFamily)                                  | same with `apiFamily: completions`                                                        | missing field default |
| `{type: openAiCompatible, ...}` (no apiFamily)                             | same with `apiFamily: completions`                                                        | missing field default |

Defaults during migration preserve current behavior (Chat Completions for OpenAI variants,
direct for Anthropic). Newly created configurations pick up the new defaults (Responses API
for OpenAI direct).

The migration deserializer is permanent infrastructure — kept indefinitely so that stale
process variables remain readable.

### Element template version bump

Both element templates (`agenticai-aiagent-outbound-connector.json` and
`agenticai-aiagent-job-worker.json`) version-bump together per the AGENTS.md rule. Old
templates move to `element-templates/versioned/` per the existing pattern. The
`element-templates/README.md` index is updated accordingly.

## Migration Plan

Two phases:

### Phase 0 — Domain model extensions (additive, behavior-preserving)

* Add `ReasoningContent` to the `Content` sealed hierarchy.
* Add optional `modelId`, `apiId`, `stopReason`, `usage` fields to `AssistantMessage`.
* Add optional `contentBlocks` field to `ToolCallResult`.
* Add `cacheReadInputTokens`, `cacheCreationInputTokens`, `reasoningTokens` to `TokenUsage`.
* Drop `AiFrameworkChatResponse#rawChatResponse()`.
* LangChain4j converter populates the new fields where the framework provides them; uses
  null defaults elsewhere.

No call-site changes. Existing tests pass with no behavior change.

### Phase 1 — Complete replacement (one shipping unit)

* New SPI: `ChatModelApiFactory<C>`, `ChatModelApiRegistry`, `ChatModelApi`, `ChatClient`
  facade, `ChatModelEvent` sealed hierarchy, `ChatStreamListener`.
* Capability matrix YAML + resolution chain (config override → exact id / alias → pattern
  → conservative defaults).
* `ToolCallResultStrategy` (multimodal-native and user-message-fallback policies).
* Five native `ChatModelApiFactory` implementations: `anthropic-messages`,
  `bedrock-converse`, `openai-responses`, `openai-completions`, `google-genai`.
* `ProviderConfiguration` restructure with Jackson migration deserializer.
* Element template version bump (Task and Sub-process flavors).
* `BaseAgentRequestHandler` cuts over to `ChatClient`. The `AiFrameworkAdapter` interface
  is removed.
* The LangChain4j integration (`framework/langchain4j/`) is preserved as an
  opt-in bridge for users assembling custom bundles with provider models we have not yet
  covered natively. It implements `ChatModelApiFactory` but is not registered by default.
  Same plan applies to a future Spring AI bridge — the architecture supports it as a
  drop-in `ChatModelApiFactory` implementation, but adoption is out of scope for this
  iteration.

The rollout is all-or-nothing. No feature flag selects between the legacy framework and
the native layer; users on supported models migrate transparently when they consume the
release.

## Future Extensions

Items deliberately scoped out of this iteration:

* **Cross-model session normalization**. A `MessageTransformer` layer that handles
  conversations spanning multiple models in a single session (degrading reasoning blocks
  when model changes, normalizing tool-call IDs across provider ID-format constraints,
  skipping errored assistant turns on replay) is unnecessary while a job is bound to a
  single model. The architecture leaves an interception point at the boundary between
  `ChatClient` and `ChatModelApi` where this slots in cleanly when needed.
* **Spring AI bridge**. The `ChatModelApiFactory` SPI is shaped so a future bridge
  implementing the same interface lights up Spring AI's broader provider catalog without
  blocking the current scope.
* **Cost tracking**. Token accounting is in scope; cost computation from token counts is
  deferred. The capability matrix schema does not carry cost fields at this stage.
* **Reactive streaming surface**. The boundary is `CompletableFuture<ChatResponse>`;
  exposing the underlying event stream as a reactive primitive is deferred. The internal
  `ChatStreamListener` extension point covers in-process observability needs without
  committing to a public reactive API.
* **Connector SDK retry classification**. The error semantics distinguish model-side errors
  (response with `stopReason: ERROR`) from transport / auth errors (exceptional future
  completion). Whether the Connector SDK can suppress automatic retry per error code so
  authentication / configuration errors do not retry pointlessly is a separate work item;
  the current design does not depend on it.
* **Output media materialization**. `output_modalities` in the capability matrix
  accommodates models that emit images or other media; the `ChatModelApi` implementation
  responsible for the first such model adds the byte → Camunda Document materialization
  path at that time. The schema is forward-compatible.
* **Tool argument JSON repair**. A small utility that runs a best-effort repair pass on
  malformed tool-argument JSON streamed from providers will be added under
  `framework/util/` and called from each native implementation. Behavior is identical to
  the SDK accumulators for well-formed JSON; recovery only kicks in on malformed cases
  observed in production.

## References

* Current framework abstraction:
  [`AiFrameworkAdapter.java`](../../src/main/java/io/camunda/connector/agenticai/aiagent/framework/AiFrameworkAdapter.java),
  [`Langchain4JAiFrameworkAdapter.java`](../../src/main/java/io/camunda/connector/agenticai/aiagent/framework/langchain4j/Langchain4JAiFrameworkAdapter.java)
* Single migration call site:
  [`BaseAgentRequestHandler.java`](../../src/main/java/io/camunda/connector/agenticai/aiagent/agent/BaseAgentRequestHandler.java)
* Domain model:
  [`Message.java`](../../src/main/java/io/camunda/connector/agenticai/model/message/Message.java),
  [`Content.java`](../../src/main/java/io/camunda/connector/agenticai/model/message/content/Content.java),
  [`ToolCallResult.java`](../../src/main/java/io/camunda/connector/agenticai/model/tool/ToolCallResult.java),
  [`AgentMetrics.java`](../../src/main/java/io/camunda/connector/agenticai/aiagent/model/AgentMetrics.java)
* Provider configurations:
  [`ProviderConfiguration.java`](../../src/main/java/io/camunda/connector/agenticai/aiagent/model/request/provider/ProviderConfiguration.java)
* Tool-result document handling (PR #6999):
  [`AgentMessagesHandlerImpl.java`](../../src/main/java/io/camunda/connector/agenticai/aiagent/agent/AgentMessagesHandlerImpl.java)
* Element templates:
  [`agenticai-aiagent-outbound-connector.json`](../../element-templates/agenticai-aiagent-outbound-connector.json),
  [`agenticai-aiagent-job-worker.json`](../../element-templates/agenticai-aiagent-job-worker.json),
  [`element-templates/README.md`](../../element-templates/README.md)
