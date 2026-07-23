# Native OpenAI Provider — Design Spec

**Epic:** [#7211 — AI Agent: Own the LLM layer](https://github.com/camunda/connectors/issues/7211) (vertical pilot)
**Branch:** `agentic-ai/issue-7211-vertical-pilot`
**Date:** 2026-07-15
**Status:** Approved for planning

## Goal

Build a native OpenAI provider for the own-LLM-layer path, at feature parity with the
native Anthropic implementation, so we can lock in the API design of our neutral data
structures against a second real provider. Runs parallel to the LangChain4j bridge and is
graded by `NativeProviderAcceptanceIT` (real-API safety net).

## Context — what already exists vs. what's missing

The **config layer is already in place**; only the runtime is missing.

Already present (no build needed, reused as-is):
- `LlmProviderConfiguration` sealed discriminator already permits `OpenAiChatModel`.
- `OpenAiChatModel` config record: `OpenAiConnection` with `OpenAiApiFamily apiFamily`, sealed
  `OpenAiBackend` = `OpenAiDirectBackend` / `OpenAiCompatibleBackend`, `CompatibleAuthentication`,
  `OpenAiModel`, `OpenAiModelParameters`, `TimeoutConfiguration`, `capabilityOverride`.
- `OpenAiApiFamily` enum: `COMPLETIONS → "openai-completions"`, `RESPONSES → "openai-responses"`
  (`familyKey()` maps to the capability-matrix family keys).
- Capability-matrix blocks `openai-completions` and `openai-responses` in
  `model-capabilities.yaml` (incl. o-series/GPT-5 model entries; Responses tool-result modality
  `[text, image, document]`, Completions `[text]`).
- v2 element template `configuration.openai.*` UI surface.
- The neutral SPI and model: `ChatModelApi` / `ChatModelApiFactory` / `ChatModelApiRegistry`,
  `ChatModelResult` (sealed `Completed | Continuation`), `Content` (sealed:
  `TextContent`, `DocumentContent`, `ObjectContent`, `ReasoningContent`, `ProviderContent`),
  `ToolCall`, `AgentMetrics`, `ModelCapabilitiesResolver`, `CoreModelCapabilities`,
  `HttpTransportSupport`, `DocumentModality`, `CapabilityAwareToolCallResultStrategy`.

Missing (this pilot builds it):
- `framework/openai/**` runtime package (mirrors `framework/anthropic/**`).
- Maven dependency `com.openai:openai-java`.
- Reasoning `effort` field on `OpenAiModelParameters` + server-tool toggles on `OpenAiConnection`.
- OpenAI capability-projection classes + fail-fast validation.
- Two OpenAI rows in `NativeProviderAcceptanceIT`.

## Scope decisions (ratified during brainstorming)

1. **Both API families in this build.** Responses API to full parity (all acceptance scenarios,
   incl. reasoning round-trip); Chat Completions to its subset (tool-call loop, structured
   output, prompt caching, multimodal tool-result; **no reasoning**).
2. **Direct + Compatible backends on both families.** Compatible+Responses ships **unit-tested
   only** (few real servers implement Responses; not covered by the real-API IT).
3. **Streaming from the start**, both families (SDK `ResponseAccumulator` /
   `ChatCompletionAccumulator`), mirroring the Anthropic streaming path.
4. **Reasoning is a single `effort` axis** (`{MINIMAL, LOW, MEDIUM, HIGH, XHIGH, MAX}`, nullable).
   No token budget, no separate "adaptive" mode (effort *is* the adaptive dial). Unset → omit →
   model default. The enum is a superset mirroring the `AnthropicEffort` sibling (plus OpenAI's
   `MINIMAL`); which levels a model actually accepts is the capability matrix's per-model truth, and
   the validator fail-fast rejects unsupported levels. `NONE` (gpt-5.1 explicit reasoning-off)
   deferred — a distinct disable semantic, not an effort tier.
5. **Reasoning allowed only on Responses.** Matrix declares `effort-levels` only for
   `openai-responses` models; `openai-completions` declares none → validator rejects `effort` on
   Completions. Data model is not the limiter (see Deferred #1).
6. **Server tools (faithful): `web_search` + `code_interpreter`, Responses-only.** Config
   toggles + request-side provisioning + `ProviderContent` capture/replay. Real-API acceptance
   scenarios + fixture unit tests. Binary/file outputs held **opaquely** in `ProviderContent` —
   no document materialization (Deferred #3).
7. **`code_interpreter`, not `shell`.** Both run server-side in a hosted container, but
   code_interpreter has broader model support (stable gate) and stresses `ProviderContent`
   harder (container file/image output items — the closest parallel to Anthropic's
   `container_upload` / `code_execution_tool_result`).

## Architecture

New package `io.camunda.connector.agenticai.aiagent.provider.openai/`, mirroring
`framework/anthropic/`, with a **per-family strategy** inside one `OpenAiChatModelApi` (the two
wire shapes diverge enough to isolate cleanly):

```
framework/openai/
  OpenAiChatModelApi.java            # implements ChatModelApi; dispatches by apiFamily → strategy
  OpenAiChatModelApiFactory.java     # ChatModelApiFactory; ORDER=100; supports
                                     #   LlmProviderChatModelApiConfiguration + OpenAiChatModel
  OpenAiClientFactory.java           # interface: OpenAIClient create()
  OpenAiOkHttpClientFactory.java     # impl: direct (apiKey+org/project) & compatible
                                     #   (baseUrl+headers/queryParams+optional apiKey), proxy via
                                     #   HttpTransportSupport
  OpenAiContentConverter.java        # neutral Content ↔ OpenAI content parts (text/image/document),
                                     #   shared across families
  OpenAiModelCapabilities.java       # implements ModelCapabilities (core + optional reasoning)
  OpenAiModelCapabilitiesData.java   # sparse snake-case matrix-row DTO → toModelCapabilities()
  OpenAiProviderCapabilities.java    # typed provider bag (reasoning)
  OpenAiReasoningCapabilities.java   # effort-levels only
  OpenAiReasoningValidator.java      # fail-fast: effort gating + server-tools-require-Responses
  OpenAiReasoningEffort.java         # enum {MINIMAL, LOW, MEDIUM, HIGH, XHIGH, MAX}, lowercase @JsonProperty
  family/
    OpenAiApiFamilyStrategy.java     # interface: ChatModelResult call(client, ctx, snapshot,
                                     #   caps, modelMatched)
    responses/
      OpenAiResponsesRequestConverter.java
      OpenAiResponsesResponseConverter.java
      OpenAiResponsesStreamAssembler.java   # wraps ResponseAccumulator
    completions/
      OpenAiCompletionsRequestConverter.java
      OpenAiCompletionsResponseConverter.java
      OpenAiCompletionsStreamAssembler.java # wraps ChatCompletionAccumulator
  package-info.java
  configuration/
    AgenticAiOpenAiFrameworkConfiguration.java  # @Bean factory, gated
                                     #   camunda.connector.agenticai.aiagent.framework.openai.enabled
                                     #   (matchIfMissing=true); @Import in autoconfig
    package-info.java
```

**Wiring.** The factory bean auto-joins `ChatModelApiRegistryImpl` (Spring injects all
`ChatModelApiFactory` beans). `ORDER=100`, same as Anthropic — safe because they `supports()`
disjoint configs (Anthropic vs OpenAI `LlmProviderConfiguration` subtypes). LangChain4j bridge
stays at `ORDER=1000` (v1 `ProviderConfiguration` only).

**Dispatch.** `OpenAiChatModelApi.call()` looks up the strategy by `config.apiFamily()`
(COMPLETIONS / RESPONSES); the strategy owns its request/response converters + stream assembler.
The `OpenAIClient` is built per call from the backend (direct/compatible) and closed in
`finally` (confirm lifecycle in Task 0). Both families consume the API streamably.

**One-SDK-per-provider invariant.** `framework/openai/**` imports only `com.openai:openai-java`;
no cross-provider SDK leakage.

## Config-surface changes

- **`OpenAiModelParameters`**: add `@Nullable OpenAiReasoningEffort effort`. Existing fields
  (`maxCompletionTokens`, `temperature`, `topP`) unchanged. `maxCompletionTokens` maps to
  Responses `max_output_tokens` and Completions `max_completion_tokens`.
- **`OpenAiConnection`**: add server-tool toggles `@Nullable Boolean enableWebSearch`,
  `@Nullable Boolean enableCodeInterpreter` (Responses-only; validated).
- **No prompt-caching flag.** OpenAI caches automatically for prompts ≥ ~1024 tokens; cache hits
  surface in usage (`prompt_tokens_details.cached_tokens`). Asymmetry vs. Anthropic (opt-in +
  wire-explicit) is intentional and noted.
- **Structured output** stays provider-neutral — read from `ctx.configuration().response()`
  (`ResponseFormatConfiguration.JsonResponseFormatConfiguration`) and mapped by each family's
  request converter onto `text.format` (Responses) / `responseFormat` (Completions), strict mode.

## Data mapping

### Responses family (full)

**Request** (neutral → `ResponseCreateParams`):
- `SystemMessage` → `instructions`.
- `UserMessage` / `AssistantMessage` → input items.
- `ToolCall` → `function_call` items; `ToolCallResultMessage` → `function_call_output` items.
- Tools → function tool defs (JSON schema).
- Structured output → `text.format = json_schema` (strict).
- Reasoning → `reasoning(effort)` + `include(reasoning.encrypted_content)` + `store(false)`.
- **Reasoning replay** → the stashed item from `ReasoningContent.providerPayload` re-injected as
  an input item (skipped when payload is null).
- Server tools → when `enableWebSearch` / `enableCodeInterpreter`, add `web_search` /
  `code_interpreter` (`environment/container: auto`) tool defs.
- Prompt caching → automatic; nothing sent.

**Response** (`Response.output()` items → neutral):
- `output_text` → `TextContent`.
- `function_call` → `ToolCall`.
- reasoning item → `ReasoningContent` (`providerPayload` = raw item incl. `encrypted_content`;
  `text` = summary if present).
- `web_search_call`, `code_interpreter_call` + their outputs (incl. container file/image items)
  → `ProviderContent` (opaque, provider-neutral).
- any other item type → `ProviderContent` (safety net).
- Usage → `AgentMetrics` (input / output / cached / reasoning tokens).
- Stop → `Completed`.

### Completions family (subset)

**Request** (neutral → `ChatCompletionCreateParams`):
- Messages array; `SystemMessage` → system/developer message.
- `ToolCall` → assistant `tool_calls`; tool results → `tool` messages.
- Tools → function defs; structured output → `responseFormat = json_schema` (strict).
- **No reasoning replay** (deferred).

**Response** (`ChatCompletion.choices()[0].message()` → neutral):
- content → `TextContent`; `tool_calls` → `ToolCall`.
- reasoning text **dropped** (not round-trippable → not surfaced).
- Usage → `AgentMetrics` incl. cached tokens; reasoning tokens = 0.
- Stop → `Completed`.

### Multimodal (documents in tool results)

Reuse `CapabilityAwareToolCallResultStrategy` + `DocumentModality`. Matrix drives it: Responses
tool-result `[text, image, document]` → native inline (`input_image` / `input_file`); Completions
`[text]` → `<doc/>` XML text fallback. No new strategy code.

## Capability matrix & validation

**New typed classes** (`framework/openai/`), mirroring Anthropic:
- `OpenAiModelCapabilities(CoreModelCapabilities core, @Nullable OpenAiReasoningCapabilities reasoning)`
  — `supportsReasoning() = reasoning != null`.
- `OpenAiModelCapabilitiesData` — sparse snake-case DTO → `toModelCapabilities()`.
- `OpenAiProviderCapabilities(@Nullable OpenAiReasoningCapabilities reasoning)`.
- `OpenAiReasoningCapabilities(@JsonProperty("effort-levels") List<OpenAiReasoningEffort> effortLevels)`.

**Matrix content:**
- Reasoning `effort-levels` declared **only on `openai-responses` reasoning models** (gpt-5.x →
  `[minimal, low, medium, high]` per model). `openai-completions` models declare **no reasoning**.
- Modalities already differ correctly (Responses tool-result multimodal; Completions text-only).

**Fail-fast validation** (`OpenAiReasoningValidator` + family/server-tool checks, at top of each
request converter; threads `modelMatched` from `ModelCapabilitiesResolver.matches(...)`):
- `modelMatched == false` → pass through (unknown/custom models unchecked).
- `effort` set + model has no reasoning capability → **fail**.
- `effort` set + ∉ matrix `effort-levels` → **fail**.
- `enableWebSearch` / `enableCodeInterpreter` set while `apiFamily == completions` → **fail**
  ("server tools require the Responses API") — runtime backstop behind the template's
  `condition` hide.
- All failures → `ConnectorException(ERROR_CODE_FAILED_MODEL_CALL, …)`.

Server-tool gating is **family-level (Responses-only)** for the pilot (not per-model matrix).

## Acceptance-IT integration (`NativeProviderAcceptanceIT`)

New `Capability` enum values: `WEB_SEARCH`, `CODE_INTERPRETER`. Two rows, gated on `OPENAI_API_KEY`.

**Row 1 — `openai-responses / gpt-5.x` (full):**
- Base: `configuration.openai.apiFamily=responses`, `.backend.type=direct`, `.backend.apiKey`,
  `.model.model=gpt-5.x`.
- `capabilityProperties`: `STRUCTURED_OUTPUT`→`{}`, `REASONING`→`{…parameters.effort=high}` with
  `forcesReasoningTokens=true` (OpenAI reasoning models emit reasoning tokens at any effort ≥ low
  — a harder assertion than Anthropic adaptive), `PROMPT_CACHING`→`{}`,
  `MULTIMODAL_TOOL_RESULT`→`{}`, `WEB_SEARCH`→`{…enableWebSearch=true}`,
  `CODE_INTERPRETER`→`{…enableCodeInterpreter=true}`.

**Row 2 — `openai-completions / gpt-4o` (subset):**
- Base: `configuration.openai.apiFamily=completions`, direct backend.
- `capabilityProperties`: `STRUCTURED_OUTPUT`, `PROMPT_CACHING`, `MULTIMODAL_TOOL_RESULT` (via
  `<doc/>` text fallback — verify at impl the fallback surfaces readable text; else drop this
  cap). **No `REASONING`, no server tools.** Plus the always-run tool-call loop = 4 scenarios.

**New scenarios (Responses-only, server-tool witnesses):**
- `codeInterpreterComputesDeterministicResult` — `enableCodeInterpreter=true`; prompt a
  computation the model offloads to code (specific large multiplication / Nth Fibonacci).
  **Deterministic**: assert exact numeric answer in `responseText` **and** a `code_interpreter`
  `ProviderContent` block in the persisted conversation (capture) **and** agent completed across
  the continuation (replay didn't 400).
- `webSearchSurfacesResultAndRoundTrips` — `enableWebSearch=true`. Web results non-deterministic,
  so assert **structurally**: agent completes + a `web_search` `ProviderContent` block present;
  LLM judge (anthropic/haiku, already configured) optionally backstops answer relevance.

**New assert helper:** `hasProviderContentBlockOfType(provider, blockType)` on
`JobWorkerAgentResponseAssert`, reading the persisted conversation's assistant-message content.

The server-tool scenarios' deterministic core is the **round-trip** (block captured + replayed
without error), not the model's free text — keeping them stable and matching the data-model goal.

## Testing strategy

**Unit tests (bulk, off the paid path):**
- Request + response converters, each family: message / tool-call / tool-result mapping,
  structured-output wiring, usage → metrics.
- `OpenAiContentConverter`: text + document parts; multimodal fallback per capability.
- Stream assemblers: events → one assembled response object.
- Capabilities + `OpenAiReasoningValidator`: all fail-fast rules.
- **Captured-fixture round-trip tests** (data-model witnesses): real payloads for (a) Responses
  reasoning `encrypted_content`, (b) `code_interpreter_call` + container file/image output,
  (c) `web_search_call` — deserialize → neutral → re-serialize to request items → assert
  **byte-identical**.

**Element template:** add `effort` + server-tool toggles to the config records, regenerate
`agenticai-ai-agent-task.v2.json` → subprocess variant via `bin/transform-…groovy` +
element-templates-cli (**outside the sandbox** — needs node/asdf). Add `condition` visibility
keyed on `configuration.openai.apiFamily` so reasoning + server-tool props appear only for
Responses (Modeler appear/disappear demo).

**Real-API acceptance:** gated behind `RUN_NATIVE_LLM_E2E=true`; run **only with explicit
permission** (cost-sensitive) via
`<with-LLM-credentials-in-env> env RUN_NATIVE_LLM_E2E=true mvn …`
(never read the secrets file). Compile-only / gated-skip runs are fine anytime.

**Build constraints:** separate `connectors-e2e-test-agentic-ai` module — cross-compile with
`-am`; **never `install` to `~/.m2`** (another worktree iterates the same SNAPSHOT); `mvn test` /
`test-compile` only; run Maven/git with the sandbox disabled (Mockito + network).

**Task 0 — SDK spike (first plan task):** confirm `com.openai:openai-java` version + exact builder
methods for: Responses `reasoning(effort)` / `include(encrypted_content)` / `store(false)`,
`code_interpreter` + `web_search` tool params, the `_additionalProperties` read/write API, both
accumulators, and `OpenAIClient` lifecycle (AutoCloseable?). De-risks all downstream tasks.

## Deferred items (out of this pilot)

1. **Generic Completions reasoning round-trip** — matrix-openable capture/replay for compatible
   servers. Seam stays open (neutral model is provider-agnostic; gating is matrix-driven);
   enabling later = converter enhancement (`_additionalProperties` capture/replay) + matrix entry.
2. **`NONE` effort value** — one-line enum + matrix `effort-levels` add when a target model needs
   explicit reasoning-off (distinct from unset/default).
3. **Output-document materialization (#7781)** — code_interpreter file/image outputs and
   image-generation → Camunda documents. Held opaquely in `ProviderContent` until then.
   `DocumentContent` (destination type) + `ProviderContent` (opaque carrier) already exist; the
   only missing piece is the #7781 persistence service. No architectural blocker.
4. **Full server-tool depth** — `file_search`, remote `mcp`, `computer_use`, `image_generation`,
   per-tool versioning knobs; folds into the Anthropic server-tools follow-up.
5. **Compatible + Responses** — ships unit-tested only (no real server in the IT).
6. **Per-model server-tool matrix gating** — pilot gates server tools family-level (Responses-only).

## Success criteria

- `framework/openai/**` runtime built; native OpenAI path selected via the registry for
  `OpenAiChatModel` configs (both families, both backends).
- Unit suite green, incl. the three captured-fixture round-trip witnesses.
- `NativeProviderAcceptanceIT` green on both OpenAI rows (with permission): Responses row passes
  all 7 scenarios (tool-call, structured output, reasoning, prompt caching, multimodal,
  code_interpreter, web_search); Completions row passes its 4-scenario subset.
- v2 element template regenerated with effort + server-tool toggles and family-gated visibility;
  demoable in Modeler.
- Everything local / unpushed pending a push decision; no `~/.m2` install.
