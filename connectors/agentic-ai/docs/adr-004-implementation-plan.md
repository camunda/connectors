# ADR-004 Phase 1 — Incremental Implementation Plan

## Context

[ADR-004](connectors/agentic-ai/docs/adr/004-replace-langchain4j-framework.md) (on branch
`origin/agentic-ai/custom-llm-layer`) replaces the LangChain4j-backed `AiFrameworkAdapter` with
a native provider layer over official vendor SDKs. The ADR names this "Phase 1 — one shipping
unit," but it is large enough to review and merge in chunks. This plan breaks it into six
green-build checkpoints (Phases A–F) on the working branch.

**Actual starting state** (`origin/agentic-ai/custom-llm-layer`, 4 commits ahead of `main`):
- Phase 0 done: `AssistantMessage` gains `modelId`/`apiId`/`stopReason`/`usage`; `TokenUsage`
  gains cache and reasoning fields; `ReasoningContent` added to `Content` hierarchy;
  `contentBlocks` added to `ToolCallResult`; `AiFrameworkChatResponse#rawChatResponse()` dropped.
  LangChain4j adapter populates the new fields from `ChatResponseMetadata`.
- Wire-format e2e regression tests added for Anthropic Messages API and OpenAI Responses API
  (WireMock-based, currently exercising LangChain4j).
- ADR-004 document committed.
- **SPI under `framework/api/` does NOT exist yet.** The plan starts here.

**First action**: merge `origin/agentic-ai/custom-llm-layer` into
`claude/refine-local-plan-e5Asz` to carry Phase 0 forward, then implement Phases A–F on top.

**End state**: `BaseAgentRequestHandler` calls `ChatClient`, not `AiFrameworkAdapter`. Five native
`ChatModelApiFactory` families ship. LangChain4j survives only as an opt-in bridge.

---

## Architecture overview

```
BaseAgentRequestHandler
        │
        ▼
   ChatClient (facade)         resolves factory, applies ToolCallResultStrategy
        │                      updates agentContext metrics, returns ChatClientResult
        ▼
ChatModelApiRegistry            Map<providerType → ChatModelApiFactory>
        │
        ▼
ChatModelApiFactory<C>          singleton bean per wire-protocol family (or bridge)
        │ create(config) → ChatModelApi (per-job)
        ▼
ChatModelApi                    capabilities() + complete(request, options, listener)
        │
        ▼
   Vendor SDK                   Anthropic / AWS Bedrock / OpenAI / Google GenAI
```

**Data flow through ChatClient.chat():**
1. Resolve factory from `executionContext.provider()` via registry → `ChatModelApi`
2. Query `ChatModelApi.capabilities()` → `ModelCapabilities`
3. Apply `ToolCallResultStrategy` to route `toolCallResults.contentBlocks` (native vs fallback)
4. Build `ChatRequest` (messages + tools + response format) + `ChatOptions` (cache, reasoning)
5. `chatModelApi.complete(request, options, NOOP_LISTENER)` → `CompletableFuture<ChatResponse>`
6. Wrap into `ChatClientResult` (updated `agentContext` + `assistantMessage`)

---

## SPI types (all in `framework/api/`)

Defined in Phase A; referenced by all subsequent phases:

| Type | Role |
|------|------|
| `ChatRequest` | `List<Message> messages`, `List<ToolDefinition> toolDefinitions`, `ResponseFormatConfiguration responseFormat` |
| `ChatResponse` | `AssistantMessage assistantMessage` (carries `stopReason`, `usage` etc. per Phase 0) |
| `ChatOptions` | `@Nullable Integer maxOutputTokens`, `@Nullable ReasoningConfig reasoning`, `@Nullable CacheRetention cacheRetention`, `Map<String,Object> providerOptions` |
| `CacheRetention` | enum `NONE \| SHORT \| LONG` |
| `ReasoningConfig` | sealed: `ReasoningEffort(Effort)`, `ReasoningBudget(int)`, `ReasoningDisabled` |
| `ModelCapabilities` | modality lists per location, `supports_*` flags, context window, max output tokens |
| `ChatModelEvent` | sealed hierarchy per ADR §"Stream event hierarchy" |
| `ChatStreamListener` | single method per event type; static `NOOP` constant |
| `ChatModelApi` | `ModelCapabilities capabilities()`, `CompletableFuture<ChatResponse> complete(ChatRequest, ChatOptions, ChatStreamListener)` |
| `ChatModelApiFactory<C>` | `String providerType()`, `Class<C> configurationType()`, `ChatModelApi create(C config)` |
| `ChatModelApiRegistry` | `Map<String, ChatModelApiFactory<?>>` keyed by `providerType()` string; mirrors `ChatModelProviderRegistry` exactly |
| `ChatClient` | `ChatClientResult chat(AgentExecutionContext, AgentContext, RuntimeMemory)` |
| `ChatClientResult` | `AgentContext agentContext()`, `AssistantMessage assistantMessage()` — replaces `AiFrameworkChatResponse` in call sites |

**Registry dispatch** mirrors `ChatModelProviderRegistry`:  
`providerConfiguration.providerType()` → factory lookup → `factory.create(config)`.  
The bridge `Langchain4JChatModelApiFactory` is parameterised on a single discriminator (`providerType()` returns one string) and registered as **one Spring bean per discriminator** in `AgenticAiLangchain4JFrameworkConfiguration` — six beans total, each named `langchain4J<Provider>ChatModelApiFactory` and gated with `@ConditionalOnMissingBean(name = ...)` so a customer or a native impl can replace any individual one. The registry collects all `ChatModelApiFactory<?>` beans and indexes by `providerType()`; duplicate discriminators fail at startup. Phase E swaps each bridge bean out by registering a native factory under the same name.

---

## Phase A — Bridge cutover (behavior-identical)

**Goal**: every chat call routes through the new SPI. LangChain4j is still the only
implementation. Proves SPI shape is right before any native provider lands.

**Files to create** (`framework/api/`):
- All SPI types listed above (interfaces + records, no impls)

**Files to create** (`framework/langchain4j/`):
- `Langchain4JChatModelApi` + `Langchain4JChatModelApiFactory<C extends ProviderConfiguration>` —
  the API wraps a pre-resolved L4J `ChatModel` and translates `ChatRequest`/`ChatOptions` to/from
  the L4J shape; the factory is parameterised on a single discriminator and produced as one bean
  per provider in `AgenticAiLangchain4JFrameworkConfiguration` (six beans total). Returns
  conservative `ModelCapabilities` (text-only, no caching/reasoning, parallel tool calls true,
  null context window) from `capabilities()`.
- `ChatModelApiRegistryImpl` — identical structure to `ChatModelProviderRegistry` (lines 16–57).
- `ChatClientImpl` — steps 1–6 from the data-flow above; wraps the future with `.get()` (sync for
  now, async for Phase C+); extracts `assistantMessage` → updates `agentContext.metrics`.

**Files to modify**:
- `BaseAgentRequestHandler`: field `AiFrameworkAdapter<?> framework` → `ChatClient chatClient`;
  lines 171–172: `framework.executeChatRequest(...)` → `chatClient.chat(...)`. Return type changes
  from `AiFrameworkChatResponse` to `ChatClientResult` — update 3 usages on lines 173–178.
- `AgenticAiConnectorsAutoConfiguration`: inject `ChatClient` instead of `AiFrameworkAdapter<?>`
  into the two request handler beans; add beans for `ChatModelApiRegistry` and `ChatClientImpl`.
  The LangChain4j config still produces the `Langchain4JChatModelApiFactory` bean.
- `OutboundConnectorAgentRequestHandlerTest` + `JobWorkerAgentRequestHandlerTest`: change
  `@Mock AiFrameworkAdapter<?>` → `@Mock ChatClient`; update stub return type.

**Tests to add**:
- `ChatModelApiRegistryImplTest` — mirrors `ChatModelProviderRegistryTest` (resolution, duplicate
  detection, missing-key error).
- `ChatClientImplTest` — verifies request assembly (messages from `RuntimeMemory`, tools from
  `AgentContext`), dispatches to the mock `ChatModelApi`, updates `agentContext.metrics`.

**Verification**:
```bash
mvn clean test -pl connectors/agentic-ai
mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai \
  -Dtest="AnthropicMessagesApiAiAgentJobWorkerTests,OpenAiResponsesApiAiAgentJobWorkerTests"
```

---

## Phase B — Capability matrix + tool-result strategy

**Goal**: infrastructure native providers will depend on. Bridge returns conservative defaults;
`ToolCallResultStrategy` always falls back (current behavior unchanged).

**Files to create**:
- `resources/capabilities/model-capabilities.yaml` — empty entries initially; schema per ADR
  §"Capability Matrix".
- `ModelCapabilitiesResolver` — resolution chain: connector override → exact id/alias → glob
  (longest match) → conservative defaults. INFO log on pattern or default use.
- `ToolCallResultStrategy` — pure function `apply(ToolCallResult, ModelCapabilities)` returning
  per-block routing decision (inline `contentBlocks` vs. synthetic `UserMessage` fallback via
  existing `AgentMessagesHandlerImpl` path from PR #6999).

**ChatOptions** completes here: `CacheRetention` defaults to `SHORT` in `ChatClientImpl`; bridge
always uses `NONE` (no cache support), resolved from `ModelCapabilities.supportsPromptCaching()`.

**Tests to add**:
- `ModelCapabilitiesResolverTest` — all 4 resolution steps, alias match, glob longest-match.
- `ToolCallResultStrategyTest` — table-driven: every modality × every capability combo.
- YAML round-trip test (Jackson reads the resource, validates required fields).

**Verification**: `mvn clean test -pl connectors/agentic-ai` green; e2e wire-format tests still
pass.

---

## Phase C — First native: `anthropic-messages` (direct backend only)

**Goal**: prove the streaming-first internal pattern, signed reasoning roundtrip, and cache
breakpoints with one provider end-to-end.

**Files to create** (`framework/anthropic/`):
- `AnthropicMessagesChatModelApi` — drives `anthropic-java` SDK streaming endpoint. Emits
  `ChatModelEvent`s, accumulates content blocks by index, materialises `AssistantMessage`
  (including `ReasoningContent` with `signature`), maps `stop_reason` → `StopReason`, populates
  `TokenUsage` with cache/reasoning fields. Error mapping per ADR §"Error semantics": model-side
  terminal → `ChatResponse{stopReason=ERROR}`; transport/auth → exceptional future with
  `ConnectorException(ERROR_CODE_FAILED_MODEL_CALL, ...)`.
- `AnthropicMessagesChatModelApiFactory` — `providerType() = AnthropicProviderConfiguration.ANTHROPIC_ID`;
  `configurationType() = AnthropicProviderConfiguration.class`. Phase C: `backend = direct` only
  (other backends added in Phase E).
- `AnthropicMessagesApiConfiguration` — `@ConditionalOnClass(AnthropicClient.class)` Spring config
  bean.

**Files to modify**:
- `pom.xml`: add `com.anthropic:anthropic-java` SDK.
- `model-capabilities.yaml`: populate Anthropic models (Opus, Sonnet, Haiku families).
- `AgenticAiConnectorsAutoConfiguration`: import `AnthropicMessagesApiConfiguration`; the bridge
  factory falls back only for providers not covered natively (`@ConditionalOnMissingBean` scoped to
  `ChatModelApiFactory` bean named for each discriminator, or registry registration order with a
  `@Order` lower on the bridge).
- `ChatClientImpl`: switch from `.get()` to async completion (the `CompletableFuture` from Phase A
  was already wired, just blocked synchronously — make it truly async or keep sync if call-site
  doesn't need it).

**Tests to add**:
- `AnthropicMessagesChatModelApiTest` — mocked SDK client; verify event ordering, content-block
  accumulation, signature roundtrip, usage accounting, error path.
- Extend `AnthropicMessagesApiAiAgentJobWorkerTests` with a streaming variant and reasoning
  roundtrip case (WireMock stubs still in HTTP, now exercising the native implementation).

**Verification**: unit tests green; both wire-format e2e tests still green (Anthropic via native,
OpenAI still via bridge).

---

## Phase D — ProviderConfiguration restructure + Jackson migration

**Goal**: introduce new discriminators without breaking saved process state. Element template
version bump to 11.

**Files to modify**:
- `AnthropicProviderConfiguration`: add `AnthropicBackend { DIRECT, BEDROCK, VERTEX, FOUNDRY }`;
  conditional auth fields per backend.
- `OpenAiProviderConfiguration` / `AzureOpenAiProviderConfiguration` /
  `OpenAiCompatibleProviderConfiguration`: add `ApiFamily { RESPONSES, COMPLETIONS }` with
  provider-appropriate defaults.
- `GoogleVertexAiProviderConfiguration` → `GoogleGenAiProviderConfiguration`: add
  `GoogleBackend { DEVELOPER_API, VERTEX }`; discriminator `googleVertexAi` → `googleGenAi`.
  Update `ProviderConfiguration` sealed type + `@JsonSubTypes`.
- `BedrockProviderConfiguration`: validate at construction that model ID is non-Anthropic.
- New `ProviderConfigurationDeserializer extends StdDeserializer<ProviderConfiguration>`: pre-
  processes JSON node before delegating. Migration rules per ADR table (7 rows). Pattern:
  `JsonSchemaElementDeserializer.java:52` (tree-walking dispatch). Register via Jackson `Module`.
- `element-templates/agenticai-aiagent-outbound-connector.json`: bump `version` 10 → 11; add
  conditional UI groups for `backend` / `apiFamily`. Maven generates versioned snapshot and
  job-worker template automatically via existing `gmavenplus` step.
- `element-templates/README.md`: same Camunda minor (8.10) → replace top row per AGENTS.md
  §"Version index README" rule.

**Tests to add**:
- `ProviderConfigurationDeserializerTest` — every row of the migration table round-trips to new
  shape; forward serialization writes new shape.
- `AgentContextTest` round-trip with stored agent context (no provider config inside — sanity).

**Verification**:
```bash
mvn clean install -pl connectors/agentic-ai   # triggers element-template generation
```
Manual inspect: `versioned/agenticai-aiagent-outbound-connector-10.json` created correctly.

---

## Phase E — Remaining native implementations

**Goal**: four remaining `ChatModelApiFactory` families. Each follows the Phase C pattern.
Structured as five sub-steps (note: `anthropic-messages` cloud backends reuse the Phase C
factory, so it's not a full new impl):

1. **`anthropic-messages` cloud backends** — extend `AnthropicMessagesChatModelApiFactory` to
   honour `backend = bedrock | vertex | foundry` (SDK modules: `anthropic-java-bedrock`,
   `anthropic-java-vertex`, `anthropic-java-foundry`). Same wire format; only client construction
   differs.

2. **`openai-responses`** — `openai-java` SDK, Responses API. Encrypted reasoning item; cache via
   `prompt_cache_key`. Covers OpenAI direct + Azure when `apiFamily = responses`.

3. **`openai-completions`** — same SDK, Chat Completions endpoint. Covers legacy Chat models,
   OpenAI-compatible gateways (Ollama, vLLM). Default for `OpenAiCompatibleProviderConfiguration`.

4. **`google-genai`** — `google-genai-java` SDK; backend toggle (`developer-api` / `vertex`).
   Reasoning via `thoughtSignature`; thinking budget via `ThinkingConfig.thinkingBudget`.

5. **`bedrock-converse`** — AWS SDK v2 `bedrockruntime`. Non-Anthropic models only. Multimodal
   tool results via `ToolResultContentBlock`; cache via `cachePoint` blocks.

**Per-impl checklist** (same as Phase C):
- SDK dependency with `@ConditionalOnClass`
- Streaming-first internal driver
- Full `ChatModelEvent` emission
- Content + usage accumulation
- Error classification per ADR table
- `model-capabilities.yaml` entries
- Unit tests (mocked SDK client)
- Wire-format e2e regression test added under `connectors-e2e-test-agentic-ai/.../wireformat/`

**Verification**: after each impl, corresponding wire-format e2e test passes; full unit suite
green.

---

## Phase F — Cleanup + LangChain4j demotion

**Goal**: remove the legacy contract; bridge stays on shelf as opt-in.

**Files to delete**: already removed during Phase A
(`AiFrameworkAdapter`, `AiFrameworkChatResponse`, `Langchain4JAiFrameworkChatResponse` —
no leftover references in `connectors/agentic-ai/src`).

**Files to modify**:
- `Langchain4JChatModelApiFactory`: drop default Spring registration; gate behind
  `camunda.connector.agenticai.framework.langchain4j.bridge.enabled=false` by default. Document
  in release notes / `docs/reference/ai-agent.md`.
- `AgenticAiConnectorsAutoConfiguration`: drop `AgenticAiLangchain4JFrameworkConfiguration` import;
  add per-provider `@ConditionalOnClass` configurations (built in Phase C–E).
- ADR-004 status: Proposed → Implemented, dated 2026-05-07.
- `docs/reference/ai-agent.md` + `CLAUDE.md` (agentic-ai): describe `ChatModelApi` as the
  framework; LangChain4j bridge as legacy opt-in.

**Tests to add/update**:
- `AgenticAiConnectorsAutoConfigurationTest` updated for new wiring.
- Smoke test: bridge re-enabled via property → handler still resolves all provider types.

**Verification**:
```bash
mvn clean install -pl connectors/agentic-ai
mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -Dtest="*Wireformat*"
mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai   # full suite (slow — final gate)
```

---

## Critical files

| File | Phase |
|------|-------|
| `BaseAgentRequestHandler.java:171–172` | A (cutover site) |
| `AgenticAiConnectorsAutoConfiguration.java` | A, C–F (Spring wiring) |
| `framework/api/` (new SPI package) | A |
| `framework/langchain4j/Langchain4JAiFrameworkAdapter.java` | A (wrapped by bridge) |
| `framework/langchain4j/provider/ChatModelProviderRegistry.java` | A (registry pattern to mirror) |
| `model/request/provider/*ProviderConfiguration.java` | D |
| `element-templates/agenticai-aiagent-outbound-connector.json` | D |
| `element-templates/README.md` | D |
| `docs/adr/004-replace-langchain4j-framework.md` | F (status update) |

## Reusable existing code

| Code | Used in |
|------|---------|
| `ChatModelProviderRegistry.java:16–57` | registry pattern for `ChatModelApiRegistryImpl` (Phase A) |
| `JsonSchemaElementDeserializer.java:52` | tree-walking deserializer pattern for `ProviderConfigurationDeserializer` (Phase D) |
| `AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL` | transport/auth error wrapping in each native impl |
| `AgentMessagesHandlerImpl` tool-result fallback path (PR #6999) | `ToolCallResultStrategy` fallback (Phase B) |

## End-to-end verification (after Phase F)

1. `mvn clean install -pl connectors/agentic-ai` — all unit tests green, element templates
   regenerate to v11, `AI_AGENT.md` regenerates.
2. `mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -Dtest=*Wireformat*` — both
   wire-format tests pass via native Anthropic and OpenAI implementations.
3. `mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai` — full suite green.
4. Manual: `git diff element-templates/` shows v11 source + v10 in `versioned/` + README updated.
5. Stale-process smoke: deserialize a saved `agentContext` from a v10 instance with the new
   `ProviderConfigurationDeserializer` — all 7 ADR migration table rows covered by tests.
