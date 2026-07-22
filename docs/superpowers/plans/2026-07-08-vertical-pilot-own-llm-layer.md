# Vertical-Pilot: Own the LLM Layer — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a native LLM layer end-to-end for Anthropic + OpenAI (Completions/Responses; direct/compatible/bedrock) — with capability matrix, native multimodality, and a turn-based continuation loop — running in parallel to the untouched LangChain4j path.

**Architecture:** A stack of ~11 dependency-ordered chunks, each a coherent commit that stays green and becomes one stacked PR. Foundational SPI/infra first (transport, continuation signal, capability matrix, tool-result format + BC, routing strategy), then the new wire-format-first config, then native provider implementations, then the v2 connector types/templates that make the native path reachable, then the Bedrock backend. The single `BaseAgentRequestHandler` is enriched (never forked); the L4J bridge stays as a low-precedence fallback with a conservative capability profile.

**Tech Stack:** Java 21 (`@NullMarked`, ErrorProne/NullAway strict), Spring Boot, Jackson, JUnit 5 + Mockito + AssertJ, WireMock (e2e). Vendor SDKs: `com.anthropic:anthropic-java`, `com.openai:openai-java`, AWS SDK v2 `bedrockruntime`. Reference implementation: PR #7151 (branch `agentic-ai/custom-llm-layer`).

**Spec:** `docs/superpowers/specs/2026-07-08-vertical-pilot-own-llm-layer-design.md` (design of record).

## Global Constraints

- **Backward compatibility on 8.9-persisted stored data is the top priority.** The only stored-format *shape* change is the tool-call-result model (Chunk 4). A fast-forward migration (lift on read, may re-persist upgraded) is acceptable **only if it never silently fails or drops data** — unmappable legacy shapes fail loud. Proven by golden 8.9 fixtures across in-process, Camunda-document, and AWS-AgentCore stores.
- Existing **v1 connector types and element templates are untouched** and keep running on the L4J bridge.
- The **single `BaseAgentRequestHandler` is enriched**, never forked. No parallel handler.
- Only `framework/langchain4j/**` may import `dev.langchain4j.*`.
- **v2 names are locked:** Task ET `io.camunda.connectors.agenticai.ai-agent-task.v2` / type `io.camunda.agenticai:aiagent:task:2`; Sub-process ET `io.camunda.connectors.agenticai.ai-agent-subprocess.v2` / type `io.camunda.agenticai:aiagent:subprocess:2`.
- **Auth:** fixed where the provider dictates (Anthropic direct = API key, bedrock = AWS creds, OpenAI direct = API key); only the OpenAI `compatible` backend gets an extensible dropdown (`none`/`apiKey` now, OAuth2-ready). No OAuth on Anthropic/Bedrock.
- **Keep the separate e2e module green (compile + pass) with every chunk.** Small smoke test per wire format; full variant coverage is a follow-up.
- Coherent-per-chunk commits; **never push without explicit approval**; implementation subagents run on **Sonnet** and never run git ops.

## Build / Test reference

```bash
# Module build (regenerates element templates during compile — commit the JSON diff)
mvn -q -Dmaven.build.cache.enabled=false -f connectors/agentic-ai/pom.xml clean install
# Module unit tests, single class
mvn -q -Dmaven.build.cache.enabled=false -f connectors/agentic-ai/pom.xml test -Dtest=<ClassName>
# Separate e2e module (repo root), single test; -am to rebuild deps
mvn -q -Dmaven.build.cache.enabled=false -am -pl connectors-e2e-test/connectors-e2e-test-agentic-ai test -Dtest=<ClassName>
```
Run Maven with `dangerouslyDisableSandbox: true` (Mockito MockMaker breaks under the sandbox). E2E harness needs `element-templates-cli` on PATH. Module base package: `io.camunda.connector.agenticai`; module root `connectors/agentic-ai/connector-agentic-ai`.

---

## The chunk stack (each = one commit = one future stacked PR)

| # | Chunk | Sub-issue | Depends on | Behavior change? |
|---|---|---|---|---|
| C1 | Neutral HTTP transport seam | #7217 | — | none (extraction) |
| C2 | Continuation signal + handler loop | §4 | — | none (single pass) |
| C3 | `ModelCapabilities` + capability matrix | #7230/#7231 | — | none (unused yet) |
| C4 | Tool-result persisted format + BC deserializer | #7232 (storage) | — | storage shape (BC-safe) |
| C5 | `ToolCallResultStrategy` (capability-keyed routing) | #7232 (routing) | C3, C4 | none on bridge (fallback = today) |
| C6 | Wire-format-first config + `ChatModelApiConfiguration` | #7224 (partial) | — | none (new types, unwired) |
| C7 | Native Anthropic Messages (direct) | #7218 | C1,C2,C3,C5,C6 | native path only |
| C8 | Native OpenAI Chat Completions (direct + compatible) | #7219 | C1,C3,C5,C6 | native path only |
| C9 | Native OpenAI Responses (direct + compatible) | #7210 | C1,C3,C5,C6,C8 | native path only |
| C10 | v2 connector types + element templates | #7225 | C6,C7,C8,C9 | makes native path reachable |
| C11 | Anthropic Messages on Bedrock backend | #7227 | C1,C6,C7 | native path only |

Ordering may adjust at execution time; C1–C6 are strictly foundational and unlock the rest. Each subsequent chunk gets its **full bite-sized TDD task breakdown written just-in-time** immediately before we execute it (so steps stay accurate against the code as it evolves). Chunk 1 is fully detailed below.

---

## Per-chunk interfaces (locked names, for cross-chunk consistency)

These are the types each chunk **produces** and later chunks **consume**. Keep names/signatures exact.

**C1 — `io.camunda.connector.agenticai.aiagent.provider.transport`**
- `HttpTransportSupport` (provider-neutral): `java.net.http.HttpClient jdkHttpClient()`, `software.amazon.awssdk.http.apache.ApacheHttpClient.Builder awsHttpClientBuilder(@Nullable java.net.URI endpointOverride)`, `java.util.Optional<com.azure.core.http.ProxyOptions> azureProxyOptions(String endpoint)`. Built from the existing `AgenticAiHttpProxySupport`. The L4J `ChatModelHttpProxySupport` is refactored to delegate to it (keeps its `CloseableJdkHttpClientBuilder`/`JdkHttpClientBuilder` wrapper on top).

**C2 — `io.camunda.connector.agenticai.aiagent.provider.api`**
- `sealed interface ChatModelResult permits Completed, Continuation { AssistantMessage assistantMessage(); AgentMetrics metrics(); }` with nested `record Completed(AssistantMessage assistantMessage, AgentMetrics metrics)` and `record Continuation(AssistantMessage assistantMessage, AgentMetrics metrics)`.
- Handler loops over `ChatModelApi.call(...)` while the result is a `Continuation`.

**C3 — `io.camunda.connector.agenticai.aiagent.provider.capabilities`**
- `record ModelCapabilities(List<Modality> userMessageModalities, List<Modality> toolResultModalities, List<Modality> assistantMessageModalities, boolean supportsReasoning, boolean supportsReasoningSignatureRoundtrip, boolean supportsPromptCaching, boolean supportsParallelToolCalls, @Nullable Integer contextWindow, @Nullable Integer maxOutputTokens)` with `enum Modality { TEXT, IMAGE, DOCUMENT, AUDIO, VIDEO }`.
- `interface ModelCapabilitiesResolver { ModelCapabilities resolve(String apiFamily, String modelId, Optional<ModelCapabilities> override); }`
- `ChatModelApi` gains `ModelCapabilities capabilities();` (bridge returns the uniform conservative profile).

**C4 — `io.camunda.connector.agenticai.aiagent.model.message` / `...model.tool`**
- `record ToolCallResultContent(@Nullable String id, @Nullable String name, List<Content> content, @Nullable String elementId, @Nullable OffsetDateTime completedAt, Map<String,Object> properties)` with a lossless BC proxy builder.
- `ToolCallResultMessage(List<ToolCallResultContent> results, Map<String,Object> metadata)` (component type changes from `List<ToolCallResult>`).
- `ToolCallResult` (tool-return) is **unchanged**.

**C5 — `io.camunda.connector.agenticai.aiagent.provider.multimodal`**
- `interface ToolCallResultStrategy { Result apply(ConversationSnapshot snapshot, ModelCapabilities capabilities); record Result(ConversationSnapshot snapshot, List<UserMessage> syntheticContextMessages) {} }`
- `DocumentModality` (MIME → `Modality`).

**C6 — `io.camunda.connector.agenticai.aiagent.model.request.chatmodel` (new package)**
- `sealed interface LlmProviderConfiguration permits AnthropicChatModel, OpenAiChatModel` (wire-format-first). Members carry `backend` + (OpenAI) `apiFamily` + auth. Compatible backend carries base URL/headers/queryParameters/requestParameters/auth-dropdown.
- `record LlmProviderChatModelApiConfiguration(LlmProviderConfiguration configuration) implements ChatModelApiConfiguration`.

**C7/C8/C9/C11 — native impls** under `framework.anthropic` / `framework.openai`, each a `ChatModelApi` + a `ChatModelApiFactory` (`getOrder()` = a named constant `< 1000`; `supports(cfg)` = `cfg instanceof LlmProviderChatModelApiConfiguration n && <member/backend/apiFamily match>`; `create(...)` resolves `ModelCapabilities` via the resolver at build time and constructs the SDK client through `HttpTransportSupport`).

**C10 — connector types** `AiAgentTaskV2` / `AiAgentSubProcessV2` in `io.camunda.connector.agenticai.aiagent`, request record surfacing `LlmProviderConfiguration` and mapping it into `LlmProviderChatModelApiConfiguration` at the handler boundary.

---

## Chunk 1 — Neutral HTTP transport seam (#7217)

**Why first:** every native SDK client (Anthropic/OpenAI/Bedrock) must be built through a proxy-correct, provider-neutral transport. Today `ChatModelHttpProxySupport` lives under `framework/langchain4j/` and mixes AWS/Azure/JDK builders with an L4J `JdkHttpClientBuilder` wrapper. Extract the provider-neutral core so natives can use it without importing `dev.langchain4j.*`, while the bridge keeps working unchanged.

**Files:**
- Create: `connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/transport/HttpTransportSupport.java`
- Modify: `.../aiagent/framework/langchain4j/ChatModelHttpProxySupport.java` (delegate to `HttpTransportSupport`; keep the L4J `CloseableJdkHttpClientBuilder`)
- Modify: `.../autoconfigure/AgenticAiConnectorsAutoConfiguration.java` (add an `HttpTransportSupport` `@Bean`, built from the existing `AgenticAiHttpProxySupport`)
- Modify: `.../framework/langchain4j/configuration/AgenticAiLangchain4JChatModelConfiguration.java` (build `ChatModelHttpProxySupport` from the shared `HttpTransportSupport` bean)
- Modify (only if `DefaultBedrockAgentCoreClientFactory` uses the proxy): `.../memory/conversation/awsagentcore/DefaultBedrockAgentCoreClientFactory.java` (switch to `HttpTransportSupport.awsHttpClientBuilder(...)`)
- Test: create `.../aiagent/framework/transport/HttpTransportSupportTest.java`; keep `.../framework/langchain4j/ChatModelHttpProxySupportTest.java` green.

**Interfaces:**
- Consumes: the existing `AgenticAiHttpProxySupport` (`getProxyConfiguration()`, `getJdkHttpClientProxyConfigurator()`), `io.camunda.connector.http.client.proxy.ProxyConfiguration`.
- Produces: `HttpTransportSupport` (signatures above), consumed by C7/C8/C9/C11.

- [ ] **Step 1: Read the current class.** Read `ChatModelHttpProxySupport.java` in full and note the three builder methods (`createJdkHttpClientBuilder`, `createAwsHttpClientBuilder`, `createAzureProxyOptions`), the `CloseableJdkHttpClientBuilder`/`CapturingBridge` internals, and how `ProxyConfiguration` + `JdkHttpClientProxyConfigurator` are used. Also read `ChatModelHttpProxySupportTest.java` to see what's already asserted.

- [ ] **Step 2: Write the failing test for the neutral seam.** Create `HttpTransportSupportTest.java`:

```java
package io.camunda.connector.agenticai.aiagent.provider.transport;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.http.client.proxy.ProxyConfiguration;

import java.net.http.HttpClient;

import org.junit.jupiter.api.Test;

class HttpTransportSupportTest {

	private final HttpTransportSupport transport =
		new HttpTransportSupport(ProxyConfiguration.NONE, /* configurator */ null);

	@Test
	void buildsAJdkHttpClientWithoutProxy() {
		final HttpClient client = transport.jdkHttpClient();
		assertThat(client).isNotNull();
		assertThat(client.proxy()).isEmpty();
	}

	@Test
	void buildsAnAwsApacheHttpClientBuilder() {
		assertThat(transport.awsHttpClientBuilder(null)).isNotNull();
	}

	@Test
	void azureProxyOptionsAbsentWhenNoProxy() {
		assertThat(transport.azureProxyOptions("https://example.test")).isEmpty();
	}
}
```
(Adjust the constructor/`null` configurator to match what Step 1 shows the current class needs — if `jdkHttpClient()` requires the configurator, pass a real/mocked one.)

- [ ] **Step 3: Run it to confirm it fails.**
Run: `mvn -q -Dmaven.build.cache.enabled=false -f connectors/agentic-ai/pom.xml test -Dtest=HttpTransportSupportTest` (with `dangerouslyDisableSandbox: true`).
Expected: FAIL — `HttpTransportSupport` does not exist (compile error).

- [ ] **Step 4: Create `HttpTransportSupport`.** Move the provider-neutral builder logic out of `ChatModelHttpProxySupport` into the new class in the `transport` package. Public API returns raw `java.net.http.HttpClient` (proxy-configured), the AWS `ApacheHttpClient.Builder`, and the Azure `Optional<ProxyOptions>` — **no `dev.langchain4j.*` imports**. Keep the AWS/Azure builder bodies identical to today (they are already vendor-neutral infra). Constructor takes the same `ProxyConfiguration` + `JdkHttpClientProxyConfigurator` the current class takes.

- [ ] **Step 5: Refactor `ChatModelHttpProxySupport` to delegate.** It now holds an `HttpTransportSupport` and keeps only the L4J-specific `CloseableJdkHttpClientBuilder` wrapper (which wraps `httpTransportSupport.jdkHttpClient()` / its builder). Its existing public methods keep the same signatures so no L4J provider class changes.

- [ ] **Step 6: Add the Spring bean.** In `AgenticAiConnectorsAutoConfiguration`, add:

```java
@Bean
@ConditionalOnMissingBean
public HttpTransportSupport httpTransportSupport(AgenticAiHttpProxySupport proxySupport) {
  return new HttpTransportSupport(
      proxySupport.getProxyConfiguration(), proxySupport.getJdkHttpClientProxyConfigurator());
}
```
Then make `AgenticAiLangchain4JChatModelConfiguration.langchain4JChatModelHttpProxySupport(...)` build `ChatModelHttpProxySupport` from the shared `HttpTransportSupport` bean (inject it) rather than re-deriving proxy config.

- [ ] **Step 7: Run the transport test + the existing bridge proxy test.**
Run: `mvn -q -Dmaven.build.cache.enabled=false -f connectors/agentic-ai/pom.xml test -Dtest=HttpTransportSupportTest,ChatModelHttpProxySupportTest`
Expected: PASS (both). If `ChatModelHttpProxySupportTest` asserts internals that moved, adjust it to assert delegation, not re-implementation.

- [ ] **Step 8: Full module build (catches NullAway/ErrorProne + template regen).**
Run: `mvn -q -Dmaven.build.cache.enabled=false -f connectors/agentic-ai/pom.xml clean install`
Expected: BUILD SUCCESS, no element-template JSON diff (this chunk changes no templates).

- [ ] **Step 9: Keep the e2e module green (compile).**
Run: `mvn -q -Dmaven.build.cache.enabled=false -am -pl connectors-e2e-test/connectors-e2e-test-agentic-ai test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 10: Commit.**

```bash
git add -A
git commit -m "feat(agentic-ai): extract provider-neutral HTTP transport seam

Lift the JDK/AWS/Azure HTTP client + proxy builders out of the LangChain4j
ChatModelHttpProxySupport into a vendor-neutral HttpTransportSupport so native
provider clients can be built proxy-correctly without importing dev.langchain4j.
The LangChain4j bridge delegates to it; behavior is unchanged."
```
(Do not push. Milestone check-in with the user after this chunk.)

---

## Subsequent chunks (C2–C11)

Each is detailed just-in-time before execution, following the same TDD shape (failing test → run-fail → minimal impl → run-pass → module build → e2e compile → commit). Their scope, files, and produced/consumed interfaces are locked in the tables above. Key notes carried forward:

- **C2** — re-introduce `sealed ChatModelResult`; enrich `BaseAgentRequestHandler.proceed(...)` with a `while` loop that ingests each `Continuation` as a persisted turn (own metrics/events, `maxModelCalls` counted per round) and stops on `Completed`. Unit-test the loop with a fake `ChatModelApi` returning `Continuation` then `Completed`. Bridge returns `Completed` → existing paths run exactly once (assert byte-identical via existing handler tests).
- **C3** — port the #7151 matrix verbatim in spirit: `model-capabilities.yaml` (families `anthropic-messages`, `openai-completions`, `openai-responses`; per-family generic glob fallback + curated entries for latest flagship models), `ModelCapabilitiesResolver` 4-step chain (override → exact id/alias → longest glob → conservative defaults, deep-merge), low-precedence Spring property source. Add `ChatModelApi.capabilities()`; bridge returns uniform `[text,image,document]/[text]/[text]`, flags false.
- **C4** — the BC-critical chunk. New `ToolCallResultContent` + changed `ToolCallResultMessage`; update `AgentConversationTurnInputComposerImpl.createToolCallResultMessage(...)` to build the new type (default lift: `Object content` → `[TextContent]`); lossless BC proxy builder (legacy scalar/object → `[TextContent]`, doc-ref → `[DocumentContent]`, **fail loud on unmappable**); golden 8.9 fixtures per store (in-process inline messages, Camunda-document `DocumentContent`, AWS-AgentCore `BlobEnvelope`). Behavior otherwise unchanged (document extraction still via the existing fallback until C5).
- **C5** — `ToolCallResultStrategy` single-pass routing driven by `chatModel.capabilities()`; invoked in `proceed(...)` after resolve and before `call(...)`, transforming the windowed snapshot and persisting synthetic context messages. Replaces the eager `createDocumentMessageForToolResults` extraction. Bridge caps → all docs fall back → today's behavior (assert via e2e).
- **C6** — new wire-format-first `LlmProviderConfiguration` (Anthropic + OpenAi members only) + `LlmProviderChatModelApiConfiguration`. Model + validation + Jackson round-trip + per-element FEEL capability-override field. No connector/template yet.
- **C7** — native Anthropic Messages (direct) over `anthropic-java`; `pause_turn` → `ChatModelResult.Continuation`; native multimodal emission from the strategy's inline blocks; WireMock wire-format smoke test. **Consume the vendor API streamably (drive the streaming endpoint internally, assemble the full result)** — required, to stay open to a later listener/future SPI retrofit (OPEN decision, per #7151; see design §5).
- **C8/C9** — native OpenAI Completions (direct + compatible) and Responses (direct + compatible); shared tool/converter code; compatible = base URL/headers/query/request-params/auth-dropdown; per-format smoke test. **Same streamable-consumption requirement as C7.**
- **(OPEN, pre-C7 candidate) Streaming/observability SPI** — adopt #7151's `ChatStreamListener` + sealed `ChatModelEvent` taxonomy (text/reasoning/tool-call deltas, usage, done, error) on `ChatModelApi.call(...)`, keeping our `Completed | Continuation` result; possibly a `CompletableFuture` return (weak — needs an async handler, out of scope). No in-pilot consumer yet (feeds the future execution-tracing epic); cheap to add before C7, expensive to retrofit after. Decide before C7.
- **C10** — `AiAgentTaskV2` + `AiAgentSubProcessV2` (locked names), request → `LlmProviderChatModelApiConfiguration`; sub-process template derived via a new/extended groovy transform; e2e wire-format smoke through the harness.
- **C11** — Anthropic Messages on the Bedrock backend (AWS creds + `HttpTransportSupport.awsHttpClientBuilder`); backend=bedrock; smoke test.
