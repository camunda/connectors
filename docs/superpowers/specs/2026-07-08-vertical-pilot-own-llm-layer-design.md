# Vertical-Pilot Design — Own the LLM Layer

**Status:** Proposed (design)
**Epic:** [#7211](https://github.com/camunda/connectors/issues/7211) — AI Agent: Own the LLM layer
**Builds on:** ADR-009 (`connectors/agentic-ai/docs/adr/009-own-the-llm-layer.md`) and the shipped Phase 0 (PR #7847). Reference implementation: PR #7151 (HACKDAYS) + ADR-005.

## Goal

Replace LangChain4j as the LLM layer **for two providers first, end to end**, instead of rolling all providers out horizontally phase by phase. Build a full native vertical slice (native SDK impls + turn-based loop + capability matrix + native multimodality + new connector types) for Anthropic and OpenAI, running **in parallel** to the untouched LangChain4j path. Existing processes keep working unchanged; the native path is developed and validated for early feedback; the legacy path is cut over provider-by-provider once we are happy.

## Why vertical instead of horizontal

- **Early, real feedback** on a working end-to-end path rather than a half-built layer across five providers.
- **Two providers with genuinely different APIs** (Anthropic Messages vs OpenAI Completions/Responses) surface cross-API and cross-backend discrepancies that a single-provider slice would hide.
- **Zero risk to existing users** — the legacy connector types and templates are never touched until an explicit, per-provider cutover.

## Global constraints

These apply to every section below.

1. **Backward compatibility on stored data is the top priority.** Conversations and `agentContext` persisted by the Camunda 8.9 connector MUST keep working transparently on the new runtime. The **only stored-format *shape* change** in this pilot is the tool-call-result model (§7); every other domain addition is additive/nullable and deserializes unchanged. A **fast-forward migration is acceptable** — legacy data is lifted to the new shape on read (and may be re-persisted in the upgraded shape) — **provided it never silently fails or drops data**: any unrecognized/unmappable legacy shape fails loud. Enforced by golden 8.9 fixtures per store (in-process process variable, Camunda document, AWS AgentCore).
2. Existing **v1 connector types and element templates are untouched** and keep running on the LangChain4j bridge.
3. The **single `BaseAgentRequestHandler` is enriched** to serve both entry points (legacy v1 + native v2). No parallel handler.
4. Only `framework/langchain4j/**` may import `dev.langchain4j.*`.

## 1. Native matrix (pilot scope)

| Provider / wire format | Backends |
|---|---|
| Anthropic Messages | `direct`, `bedrock` |
| OpenAI Chat Completions | `direct`, `compatible` |
| OpenAI Responses | `direct`, `compatible` |

`compatible` is a client-construction concern (custom base URL, headers, query/request params, and authentication — see §2), orthogonal to the wire format — so it applies to both OpenAI wire formats for free. Everything else (Azure, Bedrock-Converse for non-Anthropic models, Google) stays on the LangChain4j bridge.

## 2. Config and connector types

**Connector types + element templates (locked — the outbound-connector/job-worker naming split has caused trouble before, so we fix it now):**

| Variant | Element template ID | Connector (job worker) type |
|---|---|---|
| AI Agent Task v2 | `io.camunda.connectors.agenticai.ai-agent-task.v2` | `io.camunda.agenticai:aiagent:task:2` |
| AI Agent Sub-process v2 | `io.camunda.connectors.agenticai.ai-agent-subprocess.v2` | `io.camunda.agenticai:aiagent:subprocess:2` |

v2 splits the single v1 type (`io.camunda.agenticai:aiagent:1`) into explicit `task` / `subprocess` types. Real v2 templates (not hidden/experimental).

- New **wire-format-first sealed `ProviderConfiguration`** in the #7224 target shape, but only the `Anthropic` and `OpenAi` members present for now (others additive later, non-breaking). Each member carries a `backend` field; `OpenAi` also carries `apiFamily` (`completions | responses`); auth shape is conditional on backend.
- **Authentication is conditional on backend, with fixed schemes where the provider dictates them:** Anthropic `direct` = API key, `bedrock` = AWS credential chain, OpenAI `direct` = API key. **Only the OpenAI `compatible` backend gets an extensible auth dropdown** (sealed discriminator, modeled on the REST connector / MCP remote client): ship **`none` + `apiKey`** now, shape ready to add schemes (e.g. OAuth 2.0 client credentials) later without breaking existing configs. No OAuth on Anthropic/Bedrock.
- The **`compatible` backend** carries request customization beyond base URL — custom **headers**, **query parameters**, and additional **request (body) parameters** (same surface pattern as the REST connector) — so users can target arbitrary OpenAI-compatible gateways.
- The v2 request data surfaces a **new `ChatModelApiConfiguration`** implementation (distinct from the legacy `ProviderChatModelApiConfiguration`) that the registry dispatches on.

## 3. Routing / registry

- Native factories (Anthropic, OpenAI) register at `getOrder() < 1000` with `supports()` matching **only the new v2 config type**.
- v1 templates still produce the legacy `ProviderChatModelApiConfiguration`, which resolves to the LangChain4j bridge (`getOrder() == 1000`) unchanged.
- **Cutover (post-pilot, per-provider):** broaden the native factory's `supports()` to also claim the legacy config for that provider (or map the legacy config onto the native config). No handler or registry change required.

## 4. Shared handler + continuation loop

- Re-introduce a **continuation signal** on the SPI result: `sealed ChatModelResult = Completed | Continuation` (ADR-009 §2). The removed-in-Phase-0 continuation representation returns here, now with a real consumer.
- The enriched `BaseAgentRequestHandler` runs a **`while` loop** over `call(...)`: it keeps calling while the result is a `Continuation`, appending each interim assistant turn.
- The LangChain4j bridge and any non-pausing provider **never** return a `Continuation` → the loop runs exactly once → **byte-identical behavior** for existing v1 variants.
- Each `pause_turn` round is a **real persisted turn** with its own metrics/events; `maxModelCalls` counts each round; `notifyMetrics`/history emission happens per round.
- This is the one place the shared core changes; it degrades safely because continuation is opt-in per result.

## 5. Native implementations and transport

- New `framework/anthropic/**` and `framework/openai/**` implementations over the official vendor SDKs (`anthropic-java`, `openai-java`).
- **Neutral HTTP transport/proxy** (#7217): lift `ChatModelHttpProxySupport` out of `framework/langchain4j/` into a provider-neutral package; every native client is built through it (JDK `java.net.http.HttpClient`; AWS-Apache client for Bedrock). Proxy parity from day one.
- Anthropic backends: `direct` and `bedrock` (SDK platform module / AWS credential chain). OpenAI backends: `direct` and `compatible` (custom base URL, headers, query/request params, `none`/`apiKey` auth — see §2); `apiFamily` `completions` and `responses`.
- `ReasoningContent` is preserved on the assistant response and round-tripped (opaque provider payload) — the state the `pause_turn` loop replays. This is independent of the modality matrix.

## 6. Capability matrix (native path — full #7151 design)

The **native path gets the full capability matrix**; the bridge keeps a hardcoded profile (§8).

- **`ModelCapabilities`**: modality lists split by location — **input (`userMessage`)**, **`toolResult`**, and **output (`assistantMessage`)** — plus flags (`supportsReasoning`, `supportsReasoningSignatureRoundtrip`, `supportsPromptCaching`, `supportsParallelToolCalls`) and nullable `contextWindow` / `maxOutputTokens`. `Modality = {TEXT, IMAGE, DOCUMENT, AUDIO, VIDEO}`. The `assistantMessage` (output) location is declared but not consumed in the pilot — assistant responses stay text + tool calls (+ reasoning); no output-modality gating yet.
- **Matrix data scope:** start small — a per-family **generic fallback** entry (glob catch-all, conservative like the L4J default) plus **curated, accurate entries for the latest flagship models** (Claude Sonnet 4.6+, GPT-5.4+ class). Breadth across older/rarer models can grow later; correctness for current-generation models is what matters for the pilot.
- **`model-capabilities.yaml`**: bundled on the classpath as a **low-precedence Spring property source**; keyed `apiFamily → {defaults, models}` with `id` / `alias` / glob-`pattern` entries and a per-entry `capabilities` overlay. Ships three families: `anthropic-messages`, `openai-completions`, `openai-responses`.
- **`ModelCapabilitiesResolver.resolve(apiFamily, modelId, override)`**: 4-step chain — override → exact id/alias → longest-glob pattern → conservative defaults — deep-merging `conservativeBase → familyDefaults → modelOverrides`. Resolved at factory `create()` time and baked into the `ChatModelApi`; native impls emit blindly from the resolved caps.
- **Override precedence (three layers):** bundled yaml → operator `application.yml` (deep-merged under the same prefix) → **per-element override**.
- **Per-element override form:** one optional *"Model capability overrides"* property (Advanced group) on the v2 template = a **FEEL expression** evaluating to a sparse context/map with the `ModelCapabilities` shape (e.g. `{toolResult: ["text","image"], supportsReasoning: true}`), deep-merged as the highest-precedence layer feeding `resolve(...)`'s `override` argument. The matrix already resolves known models by id, so this is the escape hatch for unknown/custom models (and for SaaS users who cannot edit `application.yml`).
- Pulls **#7230** (matrix + resolver) and **#7231** (wire native impls to resolver) forward into the pilot.

## 7. Multimodality and tool-result model

**Concern separation (chosen):** the tool-return type and the conversation-storage type are distinct.

- **Tool-return `ToolCallResult` unchanged** — keeps its single `Object content`. Tool execution is unaffected.
- **Persisted / storage type (new, self-describing):** `ToolCallResultMessage(List<ToolCallResultContent> results, ...)`; `ToolCallResultContent(String id, String name, List<Content> content, String elementId, OffsetDateTime completedAt, Map<String,Object> properties)`. Content uses the existing `Content` hierarchy (`TextContent`, `DocumentContent`, `ReasoningContent`, …).

**Ingestion lift — `ToolCallResultStrategy`** (single pass, capability-keyed, #7232), driven by the resolved `ModelCapabilities`. Three outcomes:
- **Tool-result documents:** modality supported → **native inline** content blocks; not supported → **synthetic-`UserMessage` XML fallback** (the #6999 XML-tag shape, tagged `METADATA_TOOL_CALL_DOCUMENTS`, excluded from the memory window, persisted at the matching position for deterministic replay).
- **User / event-message documents:** supported → kept inline; unsupported → **fail-loud** (`ConnectorException(ERROR_CODE_FAILED_MODEL_CALL, ...)`) naming the reference, modality, and supported set. No synthesis fallback for user messages.
- `DocumentModality` maps MIME → `Modality` (`image/* → IMAGE`, `application/pdf → DOCUMENT`, `text/*`+`json`/`xml`/`yaml → TEXT`, `audio/* → AUDIO`, `video/* → VIDEO`).

**Backward compatibility (top priority):** a **lossless BC deserializer** lifts every 8.9-persisted shape into the new type — scalar/object `content` → `[TextContent]`, document-reference `content` → `[DocumentContent]` — preserving `id/name/elementId/completedAt/properties`. Proven transparent across in-process, Camunda-document, and AWS-AgentCore stores against golden 8.9 fixtures.

## 8. LangChain4j bridge fallback

- The three modality lists mean **"document modalities embeddable natively at that location"** (C5 refinement). The bridge reports a **uniform hardcoded `ModelCapabilities`**: `userMessage: [text, image, document]`, `toolResult: []`, `assistantMessage: [text]`, all advanced flags `false`.
  - `userMessage: [text, image, document]` (not `[text]`) prevents a regression — today's converter (`DocumentToContentConverterImpl`) embeds text/image/PDF documents in user/event messages provider-agnostically. A provider that cannot take a given modality still errors downstream at call time, exactly as today.
  - `toolResult: []` matches today — the bridge's `ToolCallConverterImpl` serializes any tool-result `DocumentContent` as a JSON reference (it embeds **no** document natively in a tool-result block), so every tool-result document takes the synthetic-`UserMessage` `<doc/>` fallback. The C5 `ToolCallResultStrategy` routes purely on `toolResultModalities.contains(modality)` — no per-modality special-case — so an empty list means "always fall back".
- The bridge tolerates-and-drops `ReasoningContent` (LangChain4j cannot emit it), flattens content blocks back to today's text/XML behavior, and leaves `modelId`/`messageId`/`stopReason` best-effort/null. Nothing downstream hard-depends on native-only fields (control flow keys off `hasToolCalls()`).
- **Model-aware bridge profiles** are not built now (the profile is uniform), but the C5 capability reframe makes them a clean future enhancement: as LangChain4j gains per-provider tool-result content-block support (e.g. images in Anthropic `tool_result`), a model-aware bridge profile can declare the corresponding `toolResultModalities` and `ToolCallConverterImpl` can emit those as native L4J tool-result content blocks — with no `ToolCallResultStrategy` change (strategy is already purely capability-driven).

## 9. Testing

- **Keep the separate `connectors-e2e-test/connectors-e2e-test-agentic-ai` module green with every chunk** — both compiling *and* passing. Never let it drift (model/serialization changes silently break it).
- Extend the WireMock **wire-format e2e harness** (#7400) with a **small smoke test per native wire format** (Anthropic Messages, OpenAI Completions, OpenAI Responses) — enough to prove the wire format end-to-end. **Full e2e coverage for the new variants is an explicit follow-up**, not part of the pilot.
- **BC golden fixtures:** real 8.9-persisted `agentContext` samples per store, asserted to deserialize and continue on the new runtime.
- Unit + module tests carry each chunk (the BC deserializer especially).

## 10. Sub-issue mapping

- **Done (PR #7847):** #7215 (domain-model extensions), #7216 (SPI + bridge).
- **Pulled forward (pilot):** #7217 (transport), #7218 (native Anthropic), #7219 (native OpenAI Completions), #7210 (native OpenAI Responses), #7227 (Anthropic on Bedrock), #7232 (multimodal tool results), #7230 + #7231 (capability matrix + resolver wiring), parts of #7224 + #7225 (v2 config + connector types), and the turn-based continuation loop.
- **Deferred (stay on bridge / later phases):** #7220 (native Azure), #7221 (native Bedrock-Converse non-Anthropic), #7222 (native Google), #7223 (remove L4J providers), #7226 (deprecate old types), #7229 (migration tool), #5547 (custom-provider SPI); and the v1→native routing cutover.

## Out of scope / deferred decisions

- Response/output multimodality (non-text model output) — `assistantMessage` modalities stay declared-but-unused.
- **Per-provider bridge capability profiles** — giving the L4J bridge *different* capability profiles per underlying provider (e.g. narrower modalities for a provider that rejects PDFs) instead of the single uniform profile in §8. Not needed now (today's converter has no per-provider gating); an accuracy enhancement for later.
- Deprecating or migrating v1 connector types — the two paths coexist; no migration during the pilot.
- The final cutover mechanism is designed (broaden `supports()` per provider) but executed post-pilot.

## Relationship to ADR-009

This design refines ADR-009's *execution strategy* from horizontal (phase-by-phase across providers) to a vertical pilot for two providers, and pulls the capability matrix (#7230/#7231) and multimodality (#7232) forward into the pilot rather than a later phase. The ADR-009 architecture (chat SPI, turn-based loop, capability matrix, opaque reasoning round-trip, config restructure on new connector types) is unchanged; only the ordering and slice shape differ. ADR-009 and its implementation plan should be updated to reflect this ordering once the pilot plan is approved.
