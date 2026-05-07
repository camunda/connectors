# ADR-005 Phase 1 — Incremental Implementation Plan

## Context

[ADR-005](connectors/agentic-ai/docs/adr/005-replace-langchain4j-framework.md) replaces the
LangChain4j-backed `AiFrameworkAdapter` with a native provider layer over official vendor SDKs.
The ADR names this "Phase 1 — one shipping unit," but it is large enough to review and merge in
chunks. This plan breaks it into eight green-build checkpoints (Phases A–H) on the working branch.

**Plan revision (2026-05-07)** — re-ordered to ship two real native providers ahead of the
capability/multimodality infrastructure. Validating the SPI against two divergent wire formats
before generalising leads to a smaller, better-shaped abstraction in Phase E. Reasoning, prompt
caching, Azure OpenAI, Anthropic cloud backends, Google GenAI and Bedrock-Converse are all
explicitly **deferred** out of the first native cut.

Phase E is split into three sub-phases (E1, E2 done; E3+E4 combined — see §"Phase E" below). Phase E
is layered on top of PR #6999 (`agentic-ai-document-tool-call-results`); our branch was rebased
onto that PR while it was in active review.

**Actual starting state** (`agentic-ai/custom-llm-layer`):
- Phase 0 done: `AssistantMessage` gains `modelId`/`apiId`/`stopReason`/`usage`; `TokenUsage`
  gains cache and reasoning fields; `ReasoningContent` added to `Content` hierarchy;
  `contentBlocks` added to `ToolCallResult`; `AiFrameworkChatResponse#rawChatResponse()` dropped.
- **Phase A done**: SPI under `framework/api/` shipped; `BaseAgentRequestHandler` routes through
  `ChatClient`; LangChain4j wired as the bridge `ChatModelApi` for all six provider discriminators
  (one factory bean per discriminator). `AiFrameworkAdapter` and `AiFrameworkChatResponse` already
  removed from the source tree (ahead of the original Phase F schedule).
- **Phases B / C / D done**: native `AnthropicMessagesChatModelApi` (text-only),
  `OpenAiChatCompletionsChatModelApi` (text-only), `OpenAiResponsesChatModelApi`
  (text-only) plus the `apiFamily` switch on the `openai` discriminator and element template
  bump 10 → 11.
- **Phase E1 / E2 done**: capability matrix loaded as Spring Boot config (bundled
  `model-capabilities.yaml` registered via `EnvironmentPostProcessor`); each native impl now
  consumes a `ModelCapabilities` resolved at factory time.
- Wire-format e2e regression tests added for the Anthropic Messages API, OpenAI Chat
  Completions API and OpenAI Responses API (WireMock-based, currently exercising the native
  impls).
- ADR-005 document committed.

**End state**: `BaseAgentRequestHandler` calls `ChatClient`. Native `ChatModelApi` impls ship for
Anthropic Messages (direct), OpenAI Chat Completions, OpenAI Responses, Azure OpenAI, Anthropic
cloud backends, Google GenAI and Bedrock-Converse. LangChain4j survives only as an opt-in bridge.

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

## Scope deferred out of native cut (Phases B–D)

The first native cut deliberately ignores these — they re-enter in Phase E or G:

| Topic | Re-enters |
|-------|-----------|
| Capability matrix YAML + resolver | E1 (done) |
| `ChatModelApi.capabilities()` resolved per call | E2 (done) |
| `ToolCallResultStrategy` (always inline-text in B–D) | E3+E4 (combined) |
| Multimodal user-message / tool-result content — **image + PDF only** | E3+E4 (combined) |
| Multimodal — audio / video | G+ (when matching native impls land) |
| Reasoning content (signed thinking blocks, encrypted reasoning items) | post-E (own sub-phase) |
| Prompt caching (`cache_control`, `prompt_cache_key`) | post-E (own sub-phase) |
| JDK `java.net.http.HttpClient` adapter for the Anthropic / OpenAI SDKs (replaces OkHttp transport) | post-E |
| Azure OpenAI native impl | G |
| Anthropic cloud backends (Bedrock / Vertex / Foundry) | G |
| Google GenAI native impl | G |
| Bedrock-Converse native impl (non-Anthropic models) | G |
| `ProviderConfiguration` discriminator restructure + Jackson migration | F |

Under this scope each native impl returns a hardcoded `ModelCapabilities` (text-only, no
reasoning, no caching, parallel tool calls true). `ChatOptions.cacheRetention` and
`ChatOptions.reasoning` are accepted but ignored. `ToolCallResult.contentBlocks` carries text
parts only — image / PDF / audio / video parts get rejected (or pass through unchanged where the
existing handler already drops them) until Phase E lands the strategy.

---

## Phase B — Native `anthropic-messages` (direct backend, text-only)

**Goal**: replace the L4J bridge for the `anthropic` discriminator with a direct vendor-SDK impl.
Validates streaming-first internal pattern + content-block accumulation against one wire format.

**Files to create** (`framework/anthropic/`):
- `AnthropicMessagesChatModelApi` — drives `anthropic-java` SDK streaming endpoint. Accumulates
  content blocks by index, materialises `AssistantMessage`, maps `stop_reason` → `StopReason`,
  populates `TokenUsage` (cache / reasoning fields left at zero). Emits `ChatModelEvent`s as the
  stream progresses. Reasoning blocks parsed structurally but **dropped** in this phase (or kept
  as opaque text content per ADR — we'll decide during impl). Error mapping: model-side terminal
  → `ChatResponse{stopReason=ERROR}`; transport/auth → exceptional future with
  `ConnectorException(ERROR_CODE_FAILED_MODEL_CALL, ...)`.
- `AnthropicMessagesChatModelApiFactory` — `providerType() = AnthropicProviderConfiguration.ANTHROPIC_ID`;
  `configurationType() = AnthropicProviderConfiguration.class`. Direct backend only.
- `AnthropicMessagesApiConfiguration` — `@ConditionalOnClass(AnthropicClient.class)` Spring config
  bean. Registers the factory under bean name `langchain4JAnthropicChatModelApiFactory` (same name
  as the bridge bean), so `@ConditionalOnMissingBean(name = ...)` in
  `AgenticAiLangchain4JFrameworkConfiguration` skips registering the bridge for this discriminator.

**Files to modify**:
- `connectors/agentic-ai/pom.xml`: add `com.anthropic:anthropic-java` SDK.
- `AgenticAiConnectorsAutoConfiguration`: `@Import(AnthropicMessagesApiConfiguration.class)`.

**Tests to add**:
- `AnthropicMessagesChatModelApiTest` — mocked SDK client; verify message conversion, tool
  conversion, content-block accumulation, stop-reason mapping, usage accounting, error path.
- `AnthropicMessagesChatModelApiFactoryTest` — providerType/configurationType wiring.

**Verification**: `mvn clean test -pl connectors/agentic-ai`; the existing
`AnthropicMessagesApiAiAgentJobWorkerTests` wire-format e2e should pass against the native impl
(skip if it asserts on bridge-specific behaviour — flag for later).

---

## Phase C — Native OpenAI Chat Completions (`openai` + `openaiCompatible`)

**Goal**: replace the L4J bridge for `openai` and `openaiCompatible` discriminators. Azure
deferred to G.

**Files to create** (`framework/openai/`):
- `OpenAiChatCompletionsChatModelApi` — drives `openai-java` SDK chat-completions endpoint
  (streaming-first internal). Same accumulation / stop-reason / error patterns as Phase B's
  Anthropic impl.
- `OpenAiToolConverter` — small shared converter: `ToolDefinition` → openai-java `ChatTool`
  (JSON schema). Lives in `framework/openai/` — reused by Phase D's Responses impl.
- `OpenAiChatModelApiFactory` — registered under bean name
  `langchain4JOpenAiChatModelApiFactory`. Builds an `OpenAIClient` from
  `OpenAiProviderConfiguration` and instantiates `OpenAiChatCompletionsChatModelApi`.
  (Phase D wraps an apiFamily branch around the impl-class choice.)
- `OpenAiCompatibleChatModelApiFactory` — registered under bean name
  `langchain4JOpenAiCompatibleChatModelApiFactory`. Builds an `OpenAIClient` with custom baseUrl
  and optional auth, hands to `OpenAiChatCompletionsChatModelApi`.
- `OpenAiChatModelApiConfiguration` — `@ConditionalOnClass(OpenAIClient.class)` Spring config bean
  registering both factories.

**Files to modify**:
- `connectors/agentic-ai/pom.xml`: add `com.openai:openai-java` SDK.
- `AgenticAiConnectorsAutoConfiguration`: `@Import(OpenAiChatModelApiConfiguration.class)`.

**Tests to add**:
- `OpenAiChatCompletionsChatModelApiTest` — mocked SDK client; conversion + accumulation +
  errors.
- `OpenAiChatModelApiFactoryTest` / `OpenAiCompatibleChatModelApiFactoryTest`.
- `OpenAiToolConverterTest` — JSON schema fidelity.

**Verification**: `mvn clean test -pl connectors/agentic-ai`. The existing
`OpenAiChatCompletionsApiAiAgentJobWorkerTests` (or analogous wire-format e2e) should pass.

---

## Phase D — Add OpenAI Responses (`apiFamily` switch on `openai`)

**Goal**: ship native `openai-responses` without doing the Phase F config restructure.
Azure stays bridged; openaiCompatible stays Completions-only.

**Files to create** (`framework/openai/`):
- `OpenAiResponsesChatModelApi` — drives `openai-java` SDK responses endpoint. Handles input-item
  shape, output-item shape, and the different streaming format. Reuses `OpenAiToolConverter` for
  the `tools[]` JSON schema. Encrypted reasoning items parsed structurally but dropped in this
  phase (Phase E re-enables roundtripping).

**Files to modify**:
- `OpenAiProviderConfiguration`:
  - Add `ApiFamily { COMPLETIONS, RESPONSES }`.
  - Add `apiFamily` field, defaulting to `COMPLETIONS` so saved process state from v10 element
    template instances deserializes unchanged. **No Jackson migration deserializer** needed for
    this — the canonical restructure (with multi-row migration table) is Phase F.
- `OpenAiChatModelApiFactory` (from Phase C): branch on `config.apiFamily()`. Same client built
  from auth/baseUrl is handed to either impl class.
- `element-templates/agenticai-aiagent-outbound-connector.json`: bump `version` 10 → 11; add
  `apiFamily` dropdown (Completions / Responses) under the OpenAI provider section. Maven's
  `gmavenplus` step regenerates the versioned snapshot and the job-worker template automatically.
- `element-templates/README.md`: update top row for AI Agent (Task + Sub-process tables, both
  reflect v11) per AGENTS.md §"Version index README" rule.

**Tests to add**:
- `OpenAiResponsesChatModelApiTest` — mocked SDK client; input-item / output-item conversion;
  tool-call extraction; streaming accumulation; errors.
- `OpenAiChatModelApiFactoryTest`: extend with `apiFamily=RESPONSES` branch.
- `OpenAiProviderConfigurationTest`: round-trip a config without `apiFamily` (defaults applied).

**Verification**: `mvn clean install -pl connectors/agentic-ai` (regenerates element templates);
verify `versioned/agenticai-aiagent-outbound-connector-10.json` exists alongside the new v11 file
in the main folder. The existing `OpenAiResponsesApiAiAgentJobWorkerTests` wire-format e2e should
pass against the native impl.

---

## Phase E — Capability matrix + tool-result strategy + multimodality

**Plan revision (2026-05-07)** — Phase E is split into three sub-phases. E1 + E2 ship as
independent commits; **E3 and E4 ship as one combined commit** because the strategy and the
native multimodal emission depend on each other (the bundled matrix declares modalities that
the impls have to actually emit, otherwise `ToolCallResult.contentBlocks` entries would be
silently dropped between phases). Reasoning and prompt caching are explicitly **deferred out
of Phase E** to keep the cut focused; the matrix already declares the flags so the slot is
reserved.

Phase E is layered on top of the work from PR #6999 (`agentic-ai-document-tool-call-results`),
which contributes the `ToolCallResultDocumentExtractor` (recursive walker over lists / maps /
MCP-shaped content), per-handler extraction hooks on `GatewayToolHandler`, the synthetic
`UserMessage` injection with `METADATA_TOOL_CALL_DOCUMENTS`, the XML document tags inserted in
tool result message text, and the window-count handling that excludes synthetic document
messages. Our branch was rebased onto that PR while it was in active review.

### Sub-phase E1 — Capability matrix + resolver (done)

Spring-Boot-native configuration: bundled YAML registered as a low-precedence
`PropertySource` via `EnvironmentPostProcessor`, library consumers override via their own
`application.yml` under the same prefix.

**Configuration prefix**: `camunda.connector.agenticai.aiagent.framework.capabilities`.

**Bundled YAML location**: `resources/capabilities/model-capabilities.yaml`.

**Structure under `capabilities`** (each api family):
- `defaults`: capability block applied to every model entry in the family
- `models`: map of opaque identifiers → entries. Each entry has one of:
  - explicit `id` (defaults to the map key when neither field is set), or
  - explicit `pattern` — string OR list of strings, glob using `*` only.
  Plus an optional `aliases` list (id entries only) and a `capabilities` overlay.

  Note: `*` and `.` cannot appear in the map key (Spring Boot's `MapBinder` strips them).
  Pattern entries always declare the glob in the `pattern` field while the map key stays a
  stable, override-friendly identifier.

**Merge semantics** (Spring Boot config + ADR-005 capability matrix):
- Maps merge recursively (sub-keys of `input-modalities` / `output-modalities` are inherited
  individually)
- Lists replace wholesale
- Scalars and booleans replace

**Resolution chain**:
1. Connector config override (per-call, future hook on `ChatOptions`)
2. Exact id or alias match
3. Pattern (longest matching glob across entries; entry score = longest matching glob in its
   pattern list)
4. Conservative defaults (text-only, all flags false)

**Files added** (`framework/capabilities/`):
- `AgenticAiFrameworkProperties` — `@ConfigurationProperties` record (sparse fields)
- `ModelCapabilitiesYaml` — sparse capability block bound by Spring Boot, projected onto
  `ModelCapabilities` after deep-merge
- `CapabilityMatrixEnvironmentPostProcessor` — registers the bundled YAML at lowest precedence
- `CapabilityMatrixFactory` — derives `id`/`pattern` from the entry shape, validates entries,
  converts capability sub-trees to `JsonNode` for the resolver
- `CapabilityMatrix` — built matrix used by the resolver
- `ModelCapabilitiesResolver` — 4-step chain with INFO logs on pattern / default fall-throughs
- `AgenticAiCapabilitiesConfiguration` — Spring config wiring

**Tests added**: `ModelCapabilitiesResolverTest` (13 unit cases),
`BundledCapabilityMatrixTest` (Spring `ApplicationContextRunner` integration, 9 cases),
`CapabilityMatrixOverrideTest` (override deep-merge via `withPropertyValues`, 4 cases).

### Sub-phase E2 — Wire `ChatModelApi.capabilities()` through the resolver (done)

Each native impl (`AnthropicMessagesChatModelApi`,
`OpenAiChatCompletionsChatModelApi`, `OpenAiResponsesChatModelApi`) accepts a
`ModelCapabilities` via constructor instead of holding a hardcoded conservative profile. The
factories (`AnthropicMessagesChatModelApiFactory`, `OpenAiChatModelApiFactory`,
`OpenAiCompatibleChatModelApiFactory`) take a `ModelCapabilitiesResolver` dependency and resolve
at `create()` time. The OpenAI native factory branches on `OpenAiConnection.apiFamily()` for the
resolver lookup (`openai-completions` vs. `openai-responses`); the openaiCompatible factory
always resolves under `openai-completions`. The L4J bridge keeps its own conservative defaults
(it stays as a fallback path; replacing it through the resolver is not worth the change at this
point).

### Sub-phase E3 + E4 — `ToolCallResultStrategy` + native multimodal emission (combined)

**Goal**: single-pass per-block routing for every document in a chat request (E3), plus
the native multimodal emission paths in each provider impl that consume the routed
`ToolCallResult.contentBlocks` and emit images / PDFs on the wire (E4). Shipped together so
the bundled capability matrix is honest — every modality the matrix declares for a family
has a working emitter on the same commit.

> **TODO (post-Phase E):** revisit the `ChatClient`↔`BaseAgentRequestHandler` boundary
> once E3+E4 land. `ChatClient` will then own three responsibilities (request assembly,
> strategy routing with memory mutation, dispatch + metrics). If routing complexity grows
> further, consider extracting routing into a step `BARQ` owns directly, or collapsing
> `ChatClient` into `BARQ`. Don't chase this in Phase E.

**Files to create** (`framework/strategy/`):
- `ToolCallResultStrategy` — interface. Single method:
  ```java
  StrategyResult apply(ChatRequest request, ModelCapabilities capabilities);
  ```
  with `record StrategyResult(ChatRequest request, List<UserMessage> syntheticContextMessages)`.
- `ToolCallResultStrategyImpl` — single-pass walker. Per document found via the existing
  `ContentTreeDocumentWalker` / per-handler `extractDocuments` hook (PR #6999 reused
  unchanged), routes:
  1. **Tool-result-message docs** vs. `capabilities.toolResultModalities()`:
     - `INLINE`: append `DocumentContent` to `ToolCallResult.contentBlocks`; document stays
       in the tree for textual rendering.
     - `FALLBACK`: replace document with `DocumentXmlTag.from(doc, toolCallId, toolName).toXml()`
       inline, append the original `DocumentContent` to a per-message bucket. After the
       walk, emit one synthetic `UserMessage` per affected tool-result message — header
       text + XML tag + DocumentContent pairs, `METADATA_TOOL_CALL_DOCUMENTS=true`. Same
       shape as PR #6999's `createDocumentMessageForToolResults` output.
  2. **User-message / event-message docs** vs. `capabilities.userMessageModalities()`:
     - `SUPPORTED`: leave the document where it sits (today's inlining in the same
       `UserMessage` content list — no change).
     - `UNSUPPORTED`: throw `ConnectorException(ERROR_CODE_FAILED_MODEL_CALL, ...)` with
       a message naming the document, modality, and resolved model.

**Files to modify**:
- `ChatClientImpl` — inject `ToolCallResultStrategy`. After `registry.resolve(provider)`
  and before `api.complete(...)`:
  ```java
  var capabilities = api.capabilities();
  var initialRequest = new ChatRequest(runtimeMemory.filteredMessages(), ...);
  var routed = strategy.apply(initialRequest, capabilities);
  routed.syntheticContextMessages().forEach(runtimeMemory::addMessage);
  var chatResponse = joinChat(api.complete(routed.request(), options, listener));
  ```
- `AgentMessagesHandlerImpl` — **remove** the `documentExtractor` field, the
  `ToolCallResultDocumentExtractor` constructor parameter, the
  `createDocumentMessageForToolResults` private method, and the call from `addUserMessages`
  (line 134 in the post-rebase code). Tool-result messages now reach `ChatClientImpl` with
  unmodified content trees and empty `ToolCallResult.contentBlocks`.
- `AgenticAiConnectorsAutoConfiguration` — drop the `ToolCallResultDocumentExtractor`
  argument from the `AgentMessagesHandlerImpl` bean wiring; add a `ToolCallResultStrategy`
  bean and inject into `ChatClientImpl`. The `ToolCallResultDocumentExtractor` bean stays
  (now consumed by `ToolCallResultStrategyImpl`).

**Behavior preserved on the bridge path**: Bridge-served providers report a conservative
capability profile (`tool-result: [text]`, `user-message: [text]`); every document falls
back to the synthetic `UserMessage`, identical to today's PR #6999 output. Bridge e2e
tests stay green without modification.

**Tests to add**:
- `ToolCallResultStrategyImplTest` — pure-function table-driven cases. Covers:
  - Tool-result image with `tool-result: [text, image, document]` → `INLINE`, no synthetic.
  - Tool-result PDF with `tool-result: [text, image]` → `FALLBACK`, one synthetic UM with
    one DocumentContent; XML placeholder substituted in tool result body.
  - Mixed result (one image + one PDF in same tool result, capability `[text, image]`) →
    image inline, PDF fallback, single synthetic UM with the PDF only.
  - Multiple tool-result messages in one request, each with documents → one synthetic UM
    per affected tool-result message, ordered.
  - User-message PDF with `user-message: [text, image]` → throws `ConnectorException`
    naming the doc + modality + model.
  - Event-message image with `user-message: [text]` → throws.
  - User-message text only → no-op, same `ChatRequest` returned.
  - Nested documents in MCP / A2A tool result content (delegated to per-handler
    `extractDocuments`) — image inline, PDF fallback works inside lists/maps.
- `AgentMessagesHandlerImplTest` — adjust existing test cases that asserted on the synthetic
  `UserMessage`: those move to `ToolCallResultStrategyImplTest`. Add a new test asserting
  `addUserMessages` no longer produces a synthetic UM (extraction is no longer its
  responsibility).
- `ChatClientImplTest` — extend with: (a) strategy invoked with resolved capabilities,
  (b) returned synthetic messages added to `RuntimeMemory` before dispatch, (c) request
  passed to `api.complete` is the strategy's modified request.

**Multimodal emission (E4 portion)** — scope matches L4J parity
(`DocumentToContentConverterImpl` supports text + image + PDF). Audio and video are **out of
scope for the combined E3+E4 phase**; the capability matrix slot remains for Phase G+.

- **Modality detection**: shared utility maps Camunda `Document.metadata().contentType()`
  (MIME) → `ModelCapabilities.Modality`. Sealed `Content` hierarchy stays as-is — no new
  `ImageContent` / `PdfContent` subtypes; dispatch is MIME-based at conversion time. Used by
  both the strategy (capability check) and the per-impl emitters (block construction).
- **Anthropic Messages**: `ContentBlockParam.ofImage(...)` (base64 data + media_type) and
  `ContentBlockParam.ofDocument(...)` for PDFs in user messages;
  `ToolResultBlockParam.Content.Block.ofImage(...)` and `.ofDocument(...)` for tool results.
- **OpenAI Chat Completions**: `ChatCompletionContentPart.ofImageUrl(...)` with data URL on
  user messages. Tool messages are text-only (SDK enforces
  `ChatCompletionContentPartText`-only for tool message content arrays). Bundled matrix:
  `tool-result: [text]` for openai-completions — strategy always falls back tool-result
  documents on this family.
- **OpenAI Responses**: `ResponseInputContent.ofInputImage(...)` (base64) and
  `ofInputFile(...)` (PDF base64) on user messages. Multimodal tool results via
  `ResponseInputItem.FunctionCallOutput.Output.ofResponseFunctionCallOutputItemList(...)`.

**User-message capability mismatch** (image in user prompt for a text-only model): the
strategy fails loud with `ConnectorException(ERROR_CODE_FAILED_MODEL_CALL, ...)` and a clear
message — this is the strategy's responsibility (E3); native impls do **not** consult
capabilities and emit blindly.

**Multimodal tests** (in addition to the strategy tests above):
- `AnthropicMessagesChatModelApiTest` — image + PDF in user message; image + PDF in tool
  result; mixed text + image + PDF tool-result content list.
- `OpenAiResponsesChatModelApiTest` — same coverage as Anthropic.
- `OpenAiChatCompletionsChatModelApiTest` — image in user message; assert tool-result with
  any non-text block routes via the strategy fallback (no inline emission attempted).
- Wire-format e2e per native family that exercises the inline-native path; existing PR #6999
  e2e covers the fallback path.

### Deferred out of Phase E

- **Reasoning** (extended thinking blocks, signed reasoning roundtrip, encrypted reasoning
  items): kept on the matrix flag list but no impl work in E. Phase F or its own sub-phase.
- **Prompt caching** (`cache_control` markers, `prompt_cache_key`): same.
- **JDK `java.net.http.HttpClient` adapter** (replacing OkHttp): same — purely a transport
  swap, no behavioural change.
- **Audio + video modalities**: Phase G+ when the corresponding native impl lands (gpt-4o-audio
  on Completions, Gemini for video).

**Verification**: `mvn clean test -pl connectors/agentic-ai` after each sub-phase; the three
wire-format e2e tests stay green; new multimodal cases under `wireformat/` exercise the
inline-native path in E4.

---

## Phase F — `ProviderConfiguration` restructure + Jackson migration

**Goal**: introduce the canonical discriminator scheme without breaking saved process state.
Element template version bump 11 → 12 (after D's bump).

**Files to modify**:
- `AnthropicProviderConfiguration`: add `AnthropicBackend { DIRECT, BEDROCK, VERTEX, FOUNDRY }`;
  conditional auth fields per backend.
- `OpenAiProviderConfiguration`: `apiFamily` already added in Phase D — extend handling here as
  needed.
- `AzureOpenAiProviderConfiguration`: add `apiFamily` matching the OpenAI shape.
- `OpenAiCompatibleProviderConfiguration`: stays Completions-only (no `apiFamily` field).
- `GoogleVertexAiProviderConfiguration` → `GoogleGenAiProviderConfiguration`: add
  `GoogleBackend { DEVELOPER_API, VERTEX }`; rename discriminator `googleVertexAi` → `googleGenAi`.
- `BedrockProviderConfiguration`: validate at construction that model ID is non-Anthropic
  (forwarding to the Anthropic factory if it is).
- New `ProviderConfigurationDeserializer extends StdDeserializer<ProviderConfiguration>`:
  pre-processes JSON node before delegating. Migration rules per ADR table. Pattern:
  `JsonSchemaElementDeserializer.java:52` (tree-walking dispatch). Register via Jackson `Module`.
- `element-templates/agenticai-aiagent-outbound-connector.json`: bump 11 → 12; add conditional UI
  groups for `backend`. Maven regenerates the versioned snapshot + job-worker template.
- `element-templates/README.md`: replace top row again (or insert new row if Camunda min version
  changes).

**Tests to add**:
- `ProviderConfigurationDeserializerTest` — every row of the migration table round-trips to new
  shape; forward serialization writes new shape.

**Verification**: `mvn clean install -pl connectors/agentic-ai`; manual inspect
`versioned/agenticai-aiagent-outbound-connector-11.json` created; deserialization smoke test
covers a saved `agentContext` from a v10/v11 instance.

---

## Phase G — Remaining native impls

**Goal**: native impls for the last four families. Each follows the Phase B pattern.

1. **Anthropic cloud backends** — extend `AnthropicMessagesChatModelApiFactory` to honour
   `backend = bedrock | vertex | foundry` (SDK modules: `anthropic-java-bedrock`,
   `anthropic-java-vertex`, `anthropic-java-foundry`). Same wire format; only client construction
   differs.
2. **Native Azure OpenAI** — `AzureOpenAiChatModelApiFactory` builds the Azure-flavored
   `OpenAIClient` (API key or Entra/AAD auth) and hands to the existing `OpenAiChatCompletions`
   / `OpenAiResponses` impl classes. Branches on `config.apiFamily()` exactly like the OpenAI
   factory. (If openai-java's Azure variant lags Responses endpoint coverage, Azure stays
   Completions-only for one release.)
3. **Native Google GenAI** — `google-genai-java` SDK; backend toggle (`developer-api` / `vertex`).
   Reasoning via `thoughtSignature`; thinking budget via `ThinkingConfig.thinkingBudget`.
4. **Native Bedrock-Converse** — AWS SDK v2 `bedrockruntime`. Non-Anthropic models only.
   Multimodal tool results via `ToolResultContentBlock`; cache via `cachePoint` blocks.

**Per-impl checklist** (same as Phase B):
- SDK dependency with `@ConditionalOnClass`
- Streaming-first internal driver
- Full `ChatModelEvent` emission
- Content + usage accumulation
- Error classification per ADR table
- `model-capabilities.yaml` entries
- Unit tests (mocked SDK client)
- Wire-format e2e regression test under `connectors-e2e-test-agentic-ai/.../wireformat/`

**Verification**: after each impl, corresponding wire-format e2e test passes; full unit suite
green.

---

## Phase H — Cleanup + LangChain4j demotion

**Goal**: bridge stays as opt-in only.

**Files to delete**: already removed during Phase A.

**Files to modify**:
- `Langchain4JChatModelApiFactory` / `AgenticAiLangchain4JFrameworkConfiguration`: drop default
  Spring registration; gate behind `camunda.connector.agenticai.framework.langchain4j.bridge.enabled=false`
  by default. Document in release notes / `docs/reference/ai-agent.md`.
- `AgenticAiConnectorsAutoConfiguration`: drop `AgenticAiLangchain4JFrameworkConfiguration`
  default import.
- ADR-005 status: Proposed → Implemented (final date set when shipping).
- `docs/reference/ai-agent.md` + `AGENTS.md` (agentic-ai): `ChatModelApi` is the framework;
  LangChain4j bridge is legacy opt-in.

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
| `BaseAgentRequestHandler.java:172–176` | A (cutover site, done) |
| `framework/api/` SPI package | A (done) |
| `framework/ChatClientImpl.java` | A (done), E (capability + strategy wiring) |
| `framework/anthropic/` (new) | B, G (cloud backends) |
| `framework/openai/` (new) | C (Completions + factories), D (Responses), G (Azure) |
| `OpenAiProviderConfiguration.java` | D (apiFamily field) |
| `model/request/provider/*ProviderConfiguration.java` | F (canonical restructure) |
| `element-templates/agenticai-aiagent-outbound-connector.json` | D (v11), F (v12) |
| `element-templates/README.md` | D, F |
| `AgenticAiConnectorsAutoConfiguration.java` | A (done), B–G (provider imports) |
| `docs/adr/005-replace-langchain4j-framework.md` | H (status update) |

## Reusable existing code

| Code | Used in |
|------|---------|
| `ChatModelProviderRegistry.java:16–57` | registry pattern for `ChatModelApiRegistryImpl` (Phase A, done) |
| `JsonSchemaElementDeserializer.java:52` | tree-walking deserializer pattern for `ProviderConfigurationDeserializer` (Phase F) |
| `AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL` | transport/auth error wrapping in each native impl (B onwards) |
| `AgentMessagesHandlerImpl` tool-result fallback path (PR #6999) | `ToolCallResultStrategy` fallback (Phase E) |

## End-to-end verification (after Phase H)

1. `mvn clean install -pl connectors/agentic-ai` — all unit tests green, element templates
   regenerate, `AI_AGENT.md` regenerates.
2. `mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -Dtest=*Wireformat*` — both
   wire-format tests pass via native Anthropic and OpenAI implementations.
3. `mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai` — full suite green.
4. Manual: `git diff element-templates/` shows the latest source + previous in `versioned/` +
   README updated.
5. Stale-process smoke: deserialize a saved `agentContext` from a pre-restructure instance with
   the new `ProviderConfigurationDeserializer` — every migration table row covered by tests.
