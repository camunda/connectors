# Own the LLM Layer — Implementation Plan (ADR 009)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the LangChain4j-backed `AiFrameworkAdapter` with a provider-neutral chat SPI dispatched to native implementations over the vendor Java SDKs, while preserving today's BPMN orchestration core and existing element templates.

**Architecture:** A new chat SPI (`ChatModelApi` drivers behind a `ChatModelApiRegistry`, invoked through a thin `ChatModelInvoker` seam) replaces the single `AiFrameworkAdapter` call in the request handler. Each driver performs exactly one provider round-trip and returns a `Completed | Continuation` result; the invoker owns a bounded continuation loop that emits per-round metrics/events. LangChain4j becomes one bridge driver, kept default-on until native parity, then opt-in. Design decisions are fixed in [ADR 009](adr/009-own-the-llm-layer.md); read it first.

**Tech Stack:** Java 21, Spring Boot auto-configuration, Jackson (`@AgenticAiRecord` + generated record builders), JUnit 5 + Mockito + AssertJ, vendor SDKs (`com.anthropic:anthropic-java`, `com.openai:openai-java`, AWS SDK v2 `bedrockruntime`, `com.google.genai:google-genai`).

## Global Constraints

- **Build:** `mvn clean install -f connectors/agentic-ai/pom.xml`. If deviating, `mvn spotless:apply -f connectors/agentic-ai/pom.xml` and `mvn license:format -f connectors/agentic-ai/pom.xml` must run clean (pre-commit hooks enforce these).
- **Separate e2e module (MANDATORY on every task):** `connectors-e2e-test/connectors-e2e-test-agentic-ai` is a DIFFERENT Maven module that depends on the built `connector-agentic-ai` artifact and constructs domain types directly (e.g. `new AgentMetrics.TokenUsage(...)`, `new AssistantMessage(...)`). `mvn ... -f connectors/agentic-ai/pom.xml` does NOT compile it, so any change to a shared model/record's constructor arity, a sealed hierarchy, or serialization silently breaks it. **After any task that touches `model/**` (records, enums, sealed interfaces) or serialization, you MUST also run `mvn test-compile -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am -f pom.xml` (from repo root) and fix every construction/assertion site there in the SAME commit.** Because this module resolves `connector-agentic-ai` from `~/.m2`, first (re)install it: `mvn -T8C clean install -DskipTests` (whole project) or at least `mvn install -f connectors/agentic-ai/pom.xml -DskipTests`; beware stale timestamped remote snapshots winning resolution — use `-am` so the e2e module builds against the just-built classes.
- **Null safety:** all production code is `@NullMarked` per package; annotate absent values with `@Nullable` (`org.jspecify.annotations`). Never suppress null errors.
- **Framework-agnostic core (invariant):** only `aiagent/framework/langchain4j/**` may import `dev.langchain4j.*`. Native drivers under `aiagent/framework/<provider>/**` may import their own vendor SDK only.
- **Interface + `*Impl` convention:** public collaborators are interfaces in the package root with a single `*Impl` alongside; wire impls as Spring beans, depend on the interface.
- **Records:** follow the module pattern — `@AgenticAiRecord`, `@JsonDeserialize(builder = X.XJacksonProxyBuilder.class)`, `@JsonPOJOBuilder(withPrefix = "")` proxy builder. Plain sealed-interface value records (e.g. `Content` subtypes) follow `TextContent` (no builder).
- **Commits:** Conventional Commits (`feat(agentic-ai): …`, `test(agentic-ai): …`, `refactor(agentic-ai): …`, `docs(agentic-ai): …`). Frequent, one deliverable per commit.
- **Do not push** without explicit confirmation.
- **Module root for paths below:** `connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai` (abbreviated `…/agenticai`). Tests mirror under `.../src/test/java/...`. E2E tests live in the separate `connectors-e2e-test/connectors-e2e-test-agentic-ai` module and require `element-templates-cli` on PATH.
- **ADR immutability:** ADR 009 may be revised only within this epic's PRs; after merge, supersede via a new ADR.

---

## Phase Roadmap (green-build checkpoints)

Each phase ends green (`mvn clean install -f connectors/agentic-ai/pom.xml` + relevant e2e) and is independently reviewable. Only **Phase 0** is broken into bite-sized tasks below; Phases 1–3 get their detailed task breakdown when their predecessor lands (their fine-grained code depends on APIs settled earlier).

### Phase 0 — Domain model + SPI skeleton + bridge cutover + de-risk spike

Additive and behaviour-identical. After this phase every chat call routes through the new SPI with LangChain4j as the only (bridge) implementation; existing e2e behaviour is unchanged; element templates untouched.

> **Re-scoped 2026-07-07 (post PR #7847 review).** Phase 0 is trimmed to the *minimal* behaviour-identical SPI + bridge. Three pieces that were built here but are **unused on the Phase-0 branch** were pulled out and moved to where they are actually exercised: (1) the continuation loop — `ChatModelInvoker`/`ChatModelInvokerImpl`, `ContinuationState`, `ChatModelResult.Continuation`, `ChatModelRoundListener` (the bridge never continues) → rebuilt in Phase 1 as a **turn-based** model (each `pause_turn` round is a real appended assistant turn; no continuation parameter); (2) `ToolCallResult.contentBlocks` (only a native provider implementation reads it) → the persisted block-list redesign + backward-compatible migration moves to Phase 2; (3) the confusing singular `ChatModelApiFactory.providerType()` removed (registry dispatches on the plural `providerTypes()`). Terminology: a `ChatModelApi` is a **"chat model"**; concrete classes are **"provider implementations"** (the word "driver" is retired).

- **Deliverables:** opaque-carrying `Content` subtype + store round-trip spike; `AssistantMessage.{modelId, messageId, stopReason}` + `StopReason`; `TokenUsage` cache/reasoning dimensions (+ 2-arg convenience constructor); chat SPI (`ChatModelApi` — one `call(ChatModelRequest)` → `ChatModelResult`, `ChatModelApiFactory`, `ChatModelApiRegistry`) + impls; LangChain4j bridge implementation; request handler calls the registry directly (no invoker seam).
- **Test plan:** unit tests per task (below); regression e2e in `connectors-e2e-test-agentic-ai` (`*ApiAiAgentJobWorker*` wire-format tests + a tool-calling test) must stay green.
- **Verification:** `mvn clean install -f connectors/agentic-ai/pom.xml` then `mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -Dtest='*ApiAiAgentJobWorker*'`.

### Phase 1 (#7212) — Native implementations, all five wire formats

- **Deliverables:** internal normalized provider model `(wireFormat, backend, apiFamily)` + resolver mapping existing discriminators (templates frozen); registry dispatch by `WireFormat` (bridge as the catch-all fallback); native `ChatModelApi` provider implementations: Anthropic-messages (direct) **first**, then OpenAI completions + responses, Bedrock Converse (non-Anthropic), Google GenAI, Anthropic cloud backends (Bedrock / Vertex / Foundry); **turn-based continuation** built here (Anthropic `pause_turn`: each round is a real appended assistant turn, looped internally by the request handler — no `ContinuationState` parameter; faithful re-serialization via the opaque provider-payload from Phase 0's `ReasoningContent` round-trip); deterministic tools/system serialization; **HTTP transport/proxy parity per provider** (prefer JDK `HttpClient`, fall back e.g. OkHttp where needed — reuse the parity baseline in `framework/langchain4j/ChatModelHttpProxySupport.java`); implementation-level cache/reasoning token capture. LangChain4j demoted to opt-in once parity proven; bridge still covers any not-yet-native discriminator.
- **Files (indicative):** `framework/anthropic/**`, `framework/openai/**`, `framework/bedrock/**`, `framework/google/**`, `framework/api/provider/**` (normalized model + resolver); pom SDK deps `@ConditionalOnClass`.
- **Test plan:** per-driver unit tests; wire-format e2e regression per provider under `connectors-e2e-test-agentic-ai/.../wireformat/` (extend the existing WireMock wire-format harness); a `pause_turn` continuation e2e for Anthropic.
- **Checkpoint:** each driver lands as its own green-build unit; L4J default flips off only after all five pass parity.

### Phase 2 (#7214) — Capability matrix + multimodal tool results

- **Deliverables:** cascading, specificity-weighted capability matrix (§4 of ADR 009) resolved per request, with resolution **debuggable** (log contributing layers); minimal initial properties (modalities, max output, context window where read); native multimodal tool-result emission per provider implementation; native-vs-fallback routing of tool-result documents; **`ToolCallResult` block-content redesign** (moved here from Phase 0): the persisted tool-call-result model becomes a list of content blocks while the process-returned result stays a single `content` object, with a **backward-compatible deserializer** lifting a legacy scalar/object `content` into a single-element block list (same pattern as the `Message.id` pre-upgrade marker + the `properties` `@JsonAnySetter` proxy builder) — no engine migration, pure serialization-compat, with round-trip BC tests; **converge tool-result rendering on the existing `<doc/>` scheme** (`model/message/content/DocumentReferenceXmlTag`), not a competing `<document/>` tag; keep the window/snapshot seam pluggable for compaction.
- **Files (indicative):** `framework/capabilities/**` (matrix, resolver, bundled `model-capabilities.yaml`), `framework/strategy/ToolCallResultStrategy*`, per-driver multimodal emission.
- **Test plan:** matrix resolution unit tests (specificity, merge, tie-break); multimodal tool-result e2e (image + PDF) per capable driver; fallback path e2e for non-multimodal.

### Phase 3 (#7213) — New connector types + config restructure + custom-provider SPI

- **Deliverables:** new "AI Agent Task" / "AI Agent Sub-process" connector types + element templates with wire-format-first config (backend + apiFamily selectors, consolidated OpenAI, `googleGenAi`, Anthropic backends); custom-provider SPI; old connector types become deprecated input-rewrite shims delegating to the internal normalized model; element-template version bump + README per `AGENTS.md`. **All breaking UX changes isolated here.**
- **Sequencing constraint:** the `bedrock` + Claude-model → Anthropic-backend migration must ship paired with (or gated behind) the native Anthropic cloud backends from Phase 1, or it breaks existing Claude-on-Bedrock users.
- **Test plan:** deserializer/shim migration unit tests (legacy config → internal model); new-connector-type e2e; element-template generation diff review.

### Cross-cutting (all phases)

- **Agent-instance API gap register:** maintain the table in ADR 009 → "Agent-instance API gap register" as gaps are hit; degrade-and-mark (omit unrepresentable content/metrics from history emission, log + code comment + register entry). **Definition of Done for the epic** includes the consolidated requirements summary for the agent-instance history/metrics API.
- **Docs:** update `docs/reference/ai-agent.md` §12 (framework abstraction) and §25 (extension points) as the SPI lands; keep `AGENTS.md` invariants current.

---

## Phase 0 — Detailed Tasks

> Package prefix `io.camunda.connector.agenticai.aiagent`. Paths below are relative to `…/agenticai`. New SPI types live under `framework/api/` (framework-neutral — no vendor imports).

### Task 0.1: Add `ReasoningContent` opaque-carrying content type

Proves the domain model can carry provider-opaque content losslessly. First consumer is reasoning (#7669, deferred), but the type + round-trip is Phase 0 structural groundwork.

**Files:**
- Create: `model/message/content/ReasoningContent.java`
- Modify: `model/message/content/Content.java` (add permit + `@JsonSubTypes`)
- Test: `model/message/content/ReasoningContentTest.java`

**Interfaces:**
- Produces: `ReasoningContent(@Nullable String text, @Nullable Object providerPayload, @Nullable Map<String,Object> metadata) implements Content` — `providerPayload` holds opaque vendor data (signature / encrypted reasoning / raw block) round-tripped verbatim; `text` is optional human-readable summary.

- [ ] **Step 1: Write the failing test**

```java
// ReasoningContentTest.java
package io.camunda.connector.agenticai.aiagent.model.message.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReasoningContentTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void roundTripsOpaquePayloadVerbatim() throws Exception {
    var original =
        new ReasoningContent(
            "summary text",
            Map.of("signature", "abc123", "encrypted", "opaque-blob"),
            Map.of("provider", "anthropic"));

    var json = mapper.writeValueAsString(original);
    var restored = mapper.readValue(json, Content.class);

    assertThat(restored).isInstanceOf(ReasoningContent.class);
    assertThat(restored).isEqualTo(original);
    assertThat(json).contains("\"type\":\"reasoning\"");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -f connectors/agentic-ai/pom.xml -Dtest=ReasoningContentTest`
Expected: FAIL — `ReasoningContent` does not exist / cannot deserialize type `reasoning`.

- [ ] **Step 3: Create `ReasoningContent`**

```java
// ReasoningContent.java
package io.camunda.connector.agenticai.aiagent.model.message.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReasoningContent(
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable String text,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Object providerPayload,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, Object> metadata)
    implements Content {

  public static ReasoningContent reasoningContent(String text) {
    return new ReasoningContent(text, null, null);
  }
}
```

- [ ] **Step 4: Register the subtype in `Content`**

Modify `Content.java`: add the import, the `@JsonSubTypes.Type`, and the `permits` entry.

```java
@JsonSubTypes({
  @JsonSubTypes.Type(value = TextContent.class, name = "text"),
  @JsonSubTypes.Type(value = DocumentContent.class, name = "document"),
  @JsonSubTypes.Type(value = ObjectContent.class, name = "object"),
  @JsonSubTypes.Type(value = ReasoningContent.class, name = "reasoning")
})
public sealed interface Content
    permits TextContent, DocumentContent, ObjectContent, ReasoningContent {
  Map<String, Object> metadata();
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -f connectors/agentic-ai/pom.xml -Dtest=ReasoningContentTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/model/message/content/ReasoningContent.java \
        connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/model/message/content/Content.java \
        connectors/agentic-ai/connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/model/message/content/ReasoningContentTest.java
git commit -m "feat(agentic-ai): add ReasoningContent opaque-carrying content type"
```

### Task 0.2: De-risk spike — opaque content round-trips through all conversation stores

Proves `ReasoningContent` survives persistence + reload through the in-process, document, and AWS AgentCore stores. **If the AgentCore mapping cannot represent it, do not fix it here** — record the gap in the ADR 009 gap register and raise it with the user; the spike's job is to surface it now.

**Files:**
- Test: `memory/conversation/inprocess/InProcessConversationStoreReasoningRoundTripTest.java`
- Test: `memory/conversation/document/DocumentConversationStoreReasoningRoundTripTest.java`
- Test: `memory/conversation/awsagentcore/AwsAgentCoreConversationStoreReasoningRoundTripTest.java`

**Interfaces:**
- Consumes: `ConversationStore.createSession(executionContext, agentContext)`; `ConversationSession.storeMessages(...)` / `loadMessages(agentContext)`; `ReasoningContent` (Task 0.1).

- [ ] **Step 1: Locate each store's existing test to mirror its setup**

Run: `ls connectors/agentic-ai/connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/memory/conversation/`
Expected: existing per-store test classes to copy fixture/setup from (mock `AgentExecutionContext`, build an `AgentContext`).

- [ ] **Step 2: Write the failing round-trip test (in-process first)**

```java
// InProcessConversationStoreReasoningRoundTripTest.java  (mirror the existing in-process store test's setup)
@Test
void reasoningContentSurvivesStoreAndLoad() {
  var assistant =
      AssistantMessage.builder()
          .content(
              List.of(
                  new ReasoningContent(
                      "why", Map.of("signature", "sig-xyz"), null),
                  TextContent.textContent("answer")))
          .build();

  try (var session = store.createSession(executionContext, agentContext)) {
    var ref = session.storeMessages(agentContext, ConversationStoreRequest.of(List.of(assistant)));
    var reloaded = session.loadMessages(agentContext.withConversation(ref.conversationContext())).messages();

    assertThat(reloaded).hasSize(1);
    var restored = (AssistantMessage) reloaded.get(0);
    assertThat(restored.content()).containsExactlyElementsOf(assistant.content());
  }
}
```

> Adapt `store`, `executionContext`, `agentContext`, and the `withConversation`/pointer plumbing to each store's actual test fixture (copy from the sibling test found in Step 1). The assertion — opaque `providerPayload` survives verbatim — is the invariant.

- [ ] **Step 3: Run it — in-process expected PASS, then replicate for document + AgentCore**

Run: `mvn test -f connectors/agentic-ai/pom.xml -Dtest='*ConversationStoreReasoningRoundTripTest'`
Expected: in-process + document PASS. **AgentCore:** if it FAILS (mapping drops `providerPayload`), stop — do not patch the mapper.

- [ ] **Step 4: If AgentCore fails, record the gap (do not fix)**

Add a row to the gap register table in `docs/adr/009-own-the-llm-layer.md` (§ Agent-instance API gap register — reuse for store-mapping gaps too, or add a short "Store-mapping gaps" note): describe what AgentCore cannot round-trip, mark the test `@Disabled("gap: AgentCore mapping cannot represent opaque reasoning payload — see ADR 009")`, and surface it to the user. Otherwise leave all three enabled.

- [ ] **Step 5: Commit**

```bash
git add connectors/agentic-ai/connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/memory/conversation/
git commit -m "test(agentic-ai): de-risk opaque content round-trip across conversation stores"
```

### Task 0.3: `StopReason` enum + `AssistantMessage` fields

**Files:**
- Create: `model/message/StopReason.java`
- Modify: `model/message/AssistantMessage.java` (add `modelId`, `messageId`, `stopReason`)
- Test: `model/message/AssistantMessageTest.java` (extend or create)

**Interfaces:**
- Produces: `enum StopReason { STOP, LENGTH, TOOL_USE, CONTENT_FILTERED, GUARDRAIL, ERROR, ABORTED, UNKNOWN }`; `AssistantMessage` gains `@Nullable String modelId`, `@Nullable String messageId`, `@Nullable StopReason stopReason`. Raw vendor value stays in `metadata`. **No `usage`** — per-turn usage stays on `AgentConversationTurn` via `AgentMetrics.tokenUsage`.

- [ ] **Step 1: Write the failing test**

```java
// AssistantMessageTest.java
@Test
void carriesStopReasonAndModelIdentityAndRoundTrips() throws Exception {
  var mapper = new ObjectMapper();
  var msg =
      AssistantMessage.builder()
          .content(List.of(TextContent.textContent("hi")))
          .modelId("claude-opus-4-8")
          .messageId("msg_123")
          .stopReason(StopReason.STOP)
          .metadata(Map.of("rawStopReason", "end_turn"))
          .build();

  var restored = mapper.readValue(mapper.writeValueAsString(msg), AssistantMessage.class);

  assertThat(restored.modelId()).isEqualTo("claude-opus-4-8");
  assertThat(restored.messageId()).isEqualTo("msg_123");
  assertThat(restored.stopReason()).isEqualTo(StopReason.STOP);
  assertThat(restored.metadata()).containsEntry("rawStopReason", "end_turn");
  assertThat(restored.hasToolCalls()).isFalse();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -f connectors/agentic-ai/pom.xml -Dtest=AssistantMessageTest`
Expected: FAIL — builder has no `modelId`/`messageId`/`stopReason`.

- [ ] **Step 3: Create `StopReason`**

```java
// StopReason.java
package io.camunda.connector.agenticai.aiagent.model.message;

/**
 * Provider-neutral, normalized finish reason. Diagnostics + a thin predicate surface only — never
 * load-bearing for control flow (that keys off {@link AssistantMessage#hasToolCalls()}). The raw
 * vendor value is always preserved in {@link AssistantMessage#metadata()}. Do NOT exhaustively
 * switch on this enum: it is part of the persisted message contract, so new values must remain
 * non-breaking. Continuation states (e.g. Anthropic {@code pause_turn}) are NOT represented here —
 * see the {@code Continuation} chat result. See ADR 009 §3.
 */
public enum StopReason {
  STOP,
  LENGTH,
  TOOL_USE,
  CONTENT_FILTERED,
  GUARDRAIL,
  ERROR,
  ABORTED,
  UNKNOWN
}
```

- [ ] **Step 4: Add the fields to `AssistantMessage`**

Add three record components (keep existing `content`, `toolCalls`, `metadata`); annotate with `@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable`. The `@AgenticAiRecord` builder + proxy builder regenerate automatically.

```java
public record AssistantMessage(
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Content> content,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ToolCall> toolCalls,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String modelId,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String messageId,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable StopReason stopReason,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, Object> metadata)
    implements AssistantMessageBuilder.With, Message, ContentMessage {
  // hasToolCalls(), builder(), proxy builder unchanged
}
```

- [ ] **Step 5: Fix compile fallout at construction sites**

Run: `mvn test-compile -f connectors/agentic-ai/pom.xml`
Expected: compile errors only where `new AssistantMessage(...)` is called positionally (converters/tests). Fix each to use `AssistantMessage.builder()...build()` or the new arity. The LangChain4j `ChatMessageConverterImpl.toAssistantMessage(...)` is the main production site — set `modelId`/`stopReason` from the L4J response where available, else leave null (bridge parity; full mapping lands per-driver in Phase 1).

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn test -f connectors/agentic-ai/pom.xml -Dtest=AssistantMessageTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat(agentic-ai): add StopReason and modelId/messageId/stopReason to AssistantMessage"
```

### Task 0.4: `TokenUsage` cache + reasoning dimensions

**Files:**
- Modify: `model/AgentMetrics.java` (nested `TokenUsage` record)
- Test: `model/AgentMetricsTest.java` (extend or create)

**Interfaces:**
- Produces: `TokenUsage(int inputTokenCount, int outputTokenCount, int cacheReadTokenCount, int cacheCreationTokenCount, int reasoningTokenCount)`. **Aggregation semantics (documented on the record):** `cacheReadTokenCount` and `cacheCreationTokenCount` are subsets *already included in* `inputTokenCount`; `reasoningTokenCount` is a subset *already included in* `outputTokenCount`. `totalTokenCount()` stays `input + output` (no double count). `add()` sums all five component-wise.

- [ ] **Step 1: Write the failing test**

```java
// AgentMetricsTest.java
@Test
void tokenUsageCarriesCacheAndReasoningDimensionsAndAggregates() {
  var a = AgentMetrics.TokenUsage.builder()
      .inputTokenCount(100).outputTokenCount(40)
      .cacheReadTokenCount(60).cacheCreationTokenCount(10).reasoningTokenCount(25).build();
  var b = AgentMetrics.TokenUsage.builder()
      .inputTokenCount(50).outputTokenCount(20)
      .cacheReadTokenCount(30).cacheCreationTokenCount(0).reasoningTokenCount(5).build();

  var sum = a.add(b);

  assertThat(sum.inputTokenCount()).isEqualTo(150);
  assertThat(sum.outputTokenCount()).isEqualTo(60);
  assertThat(sum.cacheReadTokenCount()).isEqualTo(90);
  assertThat(sum.cacheCreationTokenCount()).isEqualTo(10);
  assertThat(sum.reasoningTokenCount()).isEqualTo(30);
  // cache/reasoning are subsets of input/output — not added on top
  assertThat(sum.totalTokenCount()).isEqualTo(210);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -f connectors/agentic-ai/pom.xml -Dtest=AgentMetricsTest`
Expected: FAIL — builder has no cache/reasoning setters.

- [ ] **Step 3: Extend the `TokenUsage` record**

Add the three components with the documented semantics; extend `add()`; keep `totalTokenCount()` as `input + output`; keep `empty()`. Two-arg construction sites keep working via the builder — but the record's canonical constructor arity changes, so update the `empty()`/`builder()` usages if positional.

```java
/**
 * Token usage for a model interaction.
 *
 * <p>Aggregation semantics: {@code cacheReadTokenCount} and {@code cacheCreationTokenCount} are
 * subsets already counted within {@code inputTokenCount}; {@code reasoningTokenCount} is a subset
 * already counted within {@code outputTokenCount}. {@link #totalTokenCount()} is therefore
 * {@code input + output} and never double-counts. Populated per-round by the native drivers.
 */
@AgenticAiRecord
@JsonDeserialize(builder = TokenUsage.AgentMetricsTokenUsageJacksonProxyBuilder.class)
public record TokenUsage(
    int inputTokenCount,
    int outputTokenCount,
    int cacheReadTokenCount,
    int cacheCreationTokenCount,
    int reasoningTokenCount)
    implements AgentMetricsTokenUsageBuilder.With {

  public int totalTokenCount() {
    return inputTokenCount + outputTokenCount;
  }

  public TokenUsage add(TokenUsage o) {
    return with(
        b ->
            b.inputTokenCount(b.inputTokenCount() + o.inputTokenCount())
                .outputTokenCount(b.outputTokenCount() + o.outputTokenCount())
                .cacheReadTokenCount(b.cacheReadTokenCount() + o.cacheReadTokenCount())
                .cacheCreationTokenCount(b.cacheCreationTokenCount() + o.cacheCreationTokenCount())
                .reasoningTokenCount(b.reasoningTokenCount() + o.reasoningTokenCount()));
  }

  public static TokenUsage empty() {
    return builder().build();
  }

  public static AgentMetricsTokenUsageBuilder builder() {
    return AgentMetricsTokenUsageBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class AgentMetricsTokenUsageJacksonProxyBuilder
      extends AgentMetricsTokenUsageBuilder {}
}
```

- [ ] **Step 4: Fix construction fallout**

Run: `mvn test-compile -f connectors/agentic-ai/pom.xml`
Expected: fix any positional `new TokenUsage(in, out)` sites (e.g. the L4J adapter's `tokenUsage(...)` helper uses the builder already — leave cache/reasoning at default 0 for the bridge). Persisted old JSON without the new fields deserializes fine (defaults 0).

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -f connectors/agentic-ai/pom.xml -Dtest=AgentMetricsTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(agentic-ai): add cache/reasoning token dimensions to TokenUsage"
```

### Task 0.5: `ToolCallResult.contentBlocks`

> **REVERTED (2026-07-07, post-review).** `contentBlocks` was removed from Phase 0 — it had no reader on the Phase-0 branch. The block-content redesign (persisted block list + BC deserializer + migration) moves to Phase 2; see the Phase 2 deliverables above.

**Files:**
- Modify: `model/tool/ToolCallResult.java` (add `@Nullable List<Content> contentBlocks`)
- Test: `model/tool/ToolCallResultTest.java` (extend or create)

**Interfaces:**
- Produces: `ToolCallResult` gains `@JsonInclude(NON_EMPTY) @Nullable List<Content> contentBlocks` — structured multimodal tool-result content (consumed by Phase 2 native emission and by document flow #7781). `content` (the opaque `Object`) stays for backward compatibility.

- [ ] **Step 1: Write the failing test**

```java
// ToolCallResultTest.java
@Test
void carriesContentBlocksAndRoundTrips() throws Exception {
  var mapper = new ObjectMapper();
  var result =
      ToolCallResult.builder()
          .id("call_1").name("search")
          .contentBlocks(List.of(TextContent.textContent("found it")))
          .build();

  var restored = mapper.readValue(mapper.writeValueAsString(result), ToolCallResult.class);

  assertThat(restored.contentBlocks()).hasSize(1);
  assertThat(restored.contentBlocks().get(0)).isEqualTo(TextContent.textContent("found it"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -f connectors/agentic-ai/pom.xml -Dtest=ToolCallResultTest`
Expected: FAIL — no `contentBlocks`.

- [ ] **Step 3: Add the component**

Insert `@JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable List<Content> contentBlocks` into the `ToolCallResult` record (before `properties`, which must stay last because of its `@JsonAnySetter`/`@JsonAnyGetter`). Keep the existing proxy builder.

- [ ] **Step 4: Fix construction fallout & run**

Run: `mvn test -f connectors/agentic-ai/pom.xml -Dtest=ToolCallResultTest`
Expected: PASS (fix positional constructor sites via builder if any).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(agentic-ai): add contentBlocks to ToolCallResult"
```

### Task 0.6: Chat SPI interfaces

Framework-neutral. No vendor imports.

**Files:**
- Create: `framework/api/ChatModelRequest.java`, `framework/api/ContinuationState.java`, `framework/api/ChatModelResult.java`, `framework/api/ChatModelRoundListener.java`, `framework/api/ChatModelApi.java`, `framework/api/ChatModelApiFactory.java`, `framework/api/ChatModelApiRegistry.java`, `framework/api/ChatModelInvoker.java`, `framework/api/ChatModelInvocationResult.java`
- Test: `framework/api/ChatModelResultTest.java`

**Interfaces:**
- Produces (the SPI other tasks consume):

```java
// ChatModelRequest.java — what a driver needs for one round-trip
public record ChatModelRequest(AgentExecutionContext executionContext, ConversationSnapshot snapshot) {}

// ContinuationState.java — opaque, provider-specific resume marker
public interface ContinuationState {}

// ChatModelResult.java — tagged single-round outcome
public sealed interface ChatModelResult {
  AssistantMessage message();
  AgentMetrics metrics(); // per-round: modelCalls=1 + this round's tokenUsage + toolCalls
  record Completed(AssistantMessage message, AgentMetrics metrics) implements ChatModelResult {}
  record Continuation(AssistantMessage message, AgentMetrics metrics, ContinuationState resumeState)
      implements ChatModelResult {}
}

// ChatModelRoundListener.java — per-round observability hook (Phase 0: NOOP)
public interface ChatModelRoundListener {
  ChatModelRoundListener NOOP = (round, result) -> {};
  void onRound(int round, ChatModelResult result);
}

// ChatModelApi.java — per-provider driver; ONE round-trip per call, no vendor auto-loop
public interface ChatModelApi {
  ChatModelResult call(ChatModelRequest request, @Nullable ContinuationState continuation);
}

// ChatModelApiFactory.java — builds a driver from a provider configuration
public interface ChatModelApiFactory<C extends ProviderConfiguration> {
  String providerType();
  default List<String> providerTypes() { return List.of(providerType()); }
  Class<C> configurationType();
  ChatModelApi create(C configuration);
}

// ChatModelApiRegistry.java — dispatch by discriminator
public interface ChatModelApiRegistry {
  ChatModelApi resolve(ProviderConfiguration configuration);
}

// ChatModelInvocationResult.java — what the invoker returns to the request handler (mirrors AiFrameworkChatResponse)
public record ChatModelInvocationResult(AssistantMessage assistantMessage, AgentMetrics metrics) {}

// ChatModelInvoker.java — the seam that replaces AiFrameworkAdapter
public interface ChatModelInvoker {
  ChatModelInvocationResult invoke(AgentExecutionContext executionContext, ConversationSnapshot snapshot);
}
```

- [ ] **Step 1: Write the failing test (sealed result exhaustiveness)**

```java
// ChatModelResultTest.java
@Test
void resultVariantsExposeMessageAndMetrics() {
  var msg = AssistantMessage.builder().content(List.of(TextContent.textContent("x"))).build();
  var metrics = new AgentMetrics(1, AgentMetrics.TokenUsage.empty(), 0);

  ChatModelResult completed = new ChatModelResult.Completed(msg, metrics);
  ChatModelResult continuation =
      new ChatModelResult.Continuation(msg, metrics, new ContinuationState() {});

  assertThat(completed.message()).isSameAs(msg);
  assertThat(continuation.metrics()).isSameAs(metrics);
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -f connectors/agentic-ai/pom.xml -Dtest=ChatModelResultTest`
Expected: FAIL — types do not exist.

- [ ] **Step 3: Create all SPI files** as shown in the Interfaces block above (one file each, `@NullMarked` package with a `package-info.java` for `framework/api`). `message()`/`metrics()` on the sealed interface are satisfied by the record components.

- [ ] **Step 4: Run to verify it passes**

Run: `mvn test -f connectors/agentic-ai/pom.xml -Dtest=ChatModelResultTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(agentic-ai): add chat model SPI interfaces (framework/api)"
```

### Task 0.7: Registry + invoker impls (continuation loop + per-round metrics)

> **RE-SCOPED (2026-07-07, post-review).** Only `ChatModelApiRegistryImpl` remains in Phase 0. `ChatModelInvokerImpl` + the continuation loop / `ChatModelRoundListener` were removed (the bridge never continues); the turn-based continuation model is built in Phase 1. The registry dispatches on `providerTypes()` in Phase 0 and is re-worked to `WireFormat` dispatch in Phase 1.

**Files:**
- Create: `framework/ChatModelApiRegistryImpl.java`, `framework/ChatModelInvokerImpl.java`
- Test: `framework/ChatModelInvokerImplTest.java`, `framework/ChatModelApiRegistryImplTest.java`

**Interfaces:**
- Consumes: `ChatModelApi`, `ChatModelApiFactory`, `ChatModelResult`, `ChatModelRoundListener`, `ChatModelInvoker` (Task 0.6); `AgentExecutionContext.configuration().provider()`.
- Produces: `ChatModelInvokerImpl(ChatModelApiRegistry, ChatModelRoundListener, int maxContinuations)`; `ChatModelApiRegistryImpl(List<ChatModelApiFactory<?>>)` — fail-loud on duplicate `providerType`.

- [ ] **Step 1: Write the failing invoker test (loop + summed metrics + guard)**

```java
// ChatModelInvokerImplTest.java
@Test
void loopsOnContinuationAndSumsPerRoundMetrics() {
  var msg1 = AssistantMessage.builder().content(List.of(TextContent.textContent("thinking"))).build();
  var msg2 = AssistantMessage.builder().content(List.of(TextContent.textContent("done"))).build();
  var m = new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 5, 0, 0, 0), 0);

  var driver = mock(ChatModelApi.class);
  when(driver.call(any(), isNull()))
      .thenReturn(new ChatModelResult.Continuation(msg1, m, new ContinuationState() {}));
  when(driver.call(any(), notNull()))
      .thenReturn(new ChatModelResult.Completed(msg2, m));

  var registry = mock(ChatModelApiRegistry.class);
  when(registry.resolve(any())).thenReturn(driver);

  var invoker = new ChatModelInvokerImpl(registry, ChatModelRoundListener.NOOP, 5);
  var result = invoker.invoke(executionContext, snapshot);

  assertThat(result.assistantMessage()).isSameAs(msg2);          // terminal message returned
  assertThat(result.metrics().modelCalls()).isEqualTo(2);         // both rounds counted
  assertThat(result.metrics().tokenUsage().inputTokenCount()).isEqualTo(20);
  assertThat(result.metrics().executionTime()).isNotNull();       // whole loop timed
}

@Test
void throwsWhenMaxContinuationsExceeded() {
  var msg = AssistantMessage.builder().content(List.of(TextContent.textContent("x"))).build();
  var m = new AgentMetrics(1, AgentMetrics.TokenUsage.empty(), 0);
  var driver = mock(ChatModelApi.class);
  when(driver.call(any(), any()))
      .thenReturn(new ChatModelResult.Continuation(msg, m, new ContinuationState() {}));
  var registry = mock(ChatModelApiRegistry.class);
  when(registry.resolve(any())).thenReturn(driver);

  var invoker = new ChatModelInvokerImpl(registry, ChatModelRoundListener.NOOP, 3);

  assertThatThrownBy(() -> invoker.invoke(executionContext, snapshot))
      .isInstanceOf(ConnectorException.class);
}
```

> Set up `executionContext` (Mockito mock; `configuration().provider()` returns any `ProviderConfiguration`) and `snapshot` (mock `ConversationSnapshot`) in `@BeforeEach`.

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -f connectors/agentic-ai/pom.xml -Dtest=ChatModelInvokerImplTest`
Expected: FAIL — `ChatModelInvokerImpl` does not exist.

- [ ] **Step 3: Implement the invoker**

```java
// ChatModelInvokerImpl.java
public class ChatModelInvokerImpl implements ChatModelInvoker {
  private final ChatModelApiRegistry registry;
  private final ChatModelRoundListener roundListener;
  private final int maxContinuations;

  public ChatModelInvokerImpl(
      ChatModelApiRegistry registry, ChatModelRoundListener roundListener, int maxContinuations) {
    this.registry = registry;
    this.roundListener = roundListener;
    this.maxContinuations = maxContinuations;
  }

  @Override
  public ChatModelInvocationResult invoke(
      AgentExecutionContext executionContext, ConversationSnapshot snapshot) {
    final var driver = registry.resolve(executionContext.configuration().provider());
    final var request = new ChatModelRequest(executionContext, snapshot);
    final long start = System.nanoTime();

    ContinuationState continuation = null;
    AgentMetrics aggregate = AgentMetrics.empty();
    int round = 0;
    while (true) {
      final var result = driver.call(request, continuation);
      aggregate = aggregate.add(result.metrics());
      roundListener.onRound(round, result);
      if (result instanceof ChatModelResult.Completed completed) {
        final var timed = aggregate.withExecutionTime(Duration.ofNanos(System.nanoTime() - start));
        return new ChatModelInvocationResult(completed.message(), timed);
      }
      continuation = ((ChatModelResult.Continuation) result).resumeState();
      if (++round > maxContinuations) {
        throw new ConnectorException(
            AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL,
            "Provider continuation exceeded maxContinuations (%d)".formatted(maxContinuations));
      }
    }
  }
}
```

> Note: `AgentMetrics.add(...)` does not accumulate `executionTime` (per its javadoc), so timing the whole loop and setting it once via `withExecutionTime(...)` is correct. Continuation rounds re-send the same windowed `request`; provider-specific resume detail rides `ContinuationState`.

- [ ] **Step 4: Implement the registry**

```java
// ChatModelApiRegistryImpl.java
public class ChatModelApiRegistryImpl implements ChatModelApiRegistry {
  private final Map<String, ChatModelApiFactory<?>> factoriesByType;

  public ChatModelApiRegistryImpl(List<ChatModelApiFactory<?>> factories) {
    this.factoriesByType = new HashMap<>();
    for (var f : factories) {
      for (var type : f.providerTypes()) {
        var prev = factoriesByType.putIfAbsent(type, f);
        if (prev != null) {
          throw new IllegalStateException(
              "Duplicate ChatModelApiFactory for providerType '%s': %s and %s"
                  .formatted(type, prev.getClass(), f.getClass()));
        }
      }
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public ChatModelApi resolve(ProviderConfiguration configuration) {
    var factory = factoriesByType.get(configuration.providerType());
    if (factory == null) {
      throw new ConnectorException(
          AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL,
          "No chat model provider registered for '%s'".formatted(configuration.providerType()));
    }
    if (!factory.configurationType().isInstance(configuration)) {
      throw new IllegalStateException(
          "Configuration %s does not match factory expected type %s"
              .formatted(configuration.getClass(), factory.configurationType()));
    }
    return ((ChatModelApiFactory<ProviderConfiguration>) factory).create(configuration);
  }
}
```

- [ ] **Step 5: Write + run the registry test (duplicate fail-loud, resolve, unknown)**

Add `ChatModelApiRegistryImplTest` asserting: resolve returns the created driver; duplicate `providerType` throws `IllegalStateException` at construction; unknown discriminator throws `ConnectorException`.

Run: `mvn test -f connectors/agentic-ai/pom.xml -Dtest='ChatModelInvokerImplTest,ChatModelApiRegistryImplTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(agentic-ai): add chat model registry and invoker with continuation loop"
```

### Task 0.8: LangChain4j bridge driver

Wrap the existing `Langchain4JAiFrameworkAdapter` behind a `ChatModelApi`. L4J has no continuation, so it always returns `Completed`.

**Files:**
- Create: `framework/langchain4j/Langchain4JChatModelApi.java`, `framework/langchain4j/Langchain4JChatModelApiFactory.java`
- Test: `framework/langchain4j/Langchain4JChatModelApiTest.java`

**Interfaces:**
- Consumes: `Langchain4JAiFrameworkAdapter.executeChatRequest(ctx, snapshot)` (returns `AiFrameworkChatResponse` with `assistantMessage()` + `metrics()`); `ChatModelApi`, `ChatModelApiFactory`, `ChatModelResult` (Task 0.6).
- Produces: a factory claiming all six existing discriminators (`anthropic`, `bedrock`, `azureOpenAi`, `googleVertexAi`, `openai`, `openaiCompatible`) so every provider routes through the bridge in Phase 0.

- [ ] **Step 1: Write the failing test**

```java
// Langchain4JChatModelApiTest.java
@Test
void wrapsAdapterResponseAsCompletedResult() {
  var msg = AssistantMessage.builder().content(List.of(TextContent.textContent("hi"))).build();
  var metrics = new AgentMetrics(1, AgentMetrics.TokenUsage.empty(), 0);
  var adapter = mock(Langchain4JAiFrameworkAdapter.class);
  when(adapter.executeChatRequest(any(), any())).thenReturn(chatResponseOf(msg, metrics));

  var api = new Langchain4JChatModelApi(adapter);
  var result = api.call(new ChatModelRequest(executionContext, snapshot), null);

  assertThat(result).isInstanceOf(ChatModelResult.Completed.class);
  assertThat(result.message()).isSameAs(msg);
  assertThat(result.metrics()).isSameAs(metrics);
}
```

> `chatResponseOf(...)` returns a stub `AiFrameworkChatResponse` (a small test double or a Mockito mock returning `assistantMessage()`/`metrics()`).

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -f connectors/agentic-ai/pom.xml -Dtest=Langchain4JChatModelApiTest`
Expected: FAIL — type missing.

- [ ] **Step 3: Implement driver + factory**

```java
// Langchain4JChatModelApi.java
public class Langchain4JChatModelApi implements ChatModelApi {
  private final Langchain4JAiFrameworkAdapter adapter;

  public Langchain4JChatModelApi(Langchain4JAiFrameworkAdapter adapter) {
    this.adapter = adapter;
  }

  @Override
  public ChatModelResult call(ChatModelRequest request, @Nullable ContinuationState continuation) {
    var response = adapter.executeChatRequest(request.executionContext(), request.snapshot());
    return new ChatModelResult.Completed(response.assistantMessage(), response.metrics());
  }
}
```

```java
// Langchain4JChatModelApiFactory.java — bridge claims all built-in discriminators
public class Langchain4JChatModelApiFactory implements ChatModelApiFactory<ProviderConfiguration> {
  private final Langchain4JAiFrameworkAdapter adapter;

  public Langchain4JChatModelApiFactory(Langchain4JAiFrameworkAdapter adapter) {
    this.adapter = adapter;
  }

  @Override public String providerType() { return "langchain4j"; }

  @Override
  public List<String> providerTypes() {
    return List.of("anthropic", "bedrock", "azureOpenAi", "googleVertexAi", "openai", "openaiCompatible");
  }

  @Override public Class<ProviderConfiguration> configurationType() { return ProviderConfiguration.class; }

  @Override public ChatModelApi create(ProviderConfiguration configuration) {
    return new Langchain4JChatModelApi(adapter);
  }
}
```

> Confirm the six discriminator string constants against `AnthropicProviderConfiguration.ANTHROPIC_ID` etc. and use those constants rather than string literals.

- [ ] **Step 4: Run to verify it passes**

Run: `mvn test -f connectors/agentic-ai/pom.xml -Dtest=Langchain4JChatModelApiTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(agentic-ai): add LangChain4j bridge chat model driver"
```

### Task 0.9: Cut the request handler over to `ChatModelInvoker`

> **RE-SCOPED (2026-07-07, post-review).** The handler cuts over to `ChatModelApiRegistry` directly (`registry.resolve(provider).call(request)`), not to a `ChatModelInvoker` seam. The bridge implementation calls `AiFrameworkAdapter.executeMeasuringTime(...)`, so metrics keep their measured `executionTime`. Still a behaviour-identical swap of the single LLM call.

Behaviour-identical swap of the single LLM call. This is the phase's integration checkpoint.

**Files:**
- Modify: `aiagent/agent/BaseAgentRequestHandler.java` (field + call site)
- Modify: `aiagent/framework/langchain4j/configuration/AgenticAiLangchain4JFrameworkConfiguration.java` (register bridge factory bean)
- Modify: `autoconfigure/AgenticAiConnectorsAutoConfiguration.java` (register `ChatModelApiRegistry` + `ChatModelInvoker` beans; inject invoker into the request handlers)
- Modify: `aiagent/agent/OutboundConnectorAgentRequestHandlerTest.java`, `aiagent/agent/JobWorkerAgentRequestHandlerTest.java` (mock `ChatModelInvoker` instead of `AiFrameworkAdapter`)

**Interfaces:**
- Consumes: `ChatModelInvoker.invoke(ctx, snapshot)` (Task 0.6/0.7); `ChatModelApiRegistryImpl`, `Langchain4JChatModelApiFactory` (Task 0.7/0.8).

- [ ] **Step 1: Update the handler tests first (red)**

In both handler tests, replace the `AiFrameworkAdapter` mock with a `ChatModelInvoker` mock:
```java
when(chatModelInvoker.invoke(any(), any()))
    .thenReturn(new ChatModelInvocationResult(assistantMessage, metrics));
```
Run: `mvn test -f connectors/agentic-ai/pom.xml -Dtest='OutboundConnectorAgentRequestHandlerTest,JobWorkerAgentRequestHandlerTest'`
Expected: FAIL to compile — handler still takes `AiFrameworkAdapter`.

- [ ] **Step 2: Swap the field and call site in `BaseAgentRequestHandler`**

Replace the `AiFrameworkAdapter<?> framework` constructor param/field with `ChatModelInvoker chatModelInvoker`. Change the call site in `proceed(...)`:
```java
final var result = chatModelInvoker.invoke(executionContext, conversation.window(agentConfiguration.contextWindowSize()));
final var updatedConversation = conversation.ingest(result.assistantMessage(), result.metrics());
```
Propagate the constructor change to `JobWorkerAgentRequestHandler` and `OutboundConnectorAgentRequestHandler` subclasses.

- [ ] **Step 3: Register beans**

In `AgenticAiLangchain4JFrameworkConfiguration`, add a `@Bean Langchain4JChatModelApiFactory` (depends on the existing `Langchain4JAiFrameworkAdapter` bean). In `AgenticAiConnectorsAutoConfiguration`, add `@Bean ChatModelApiRegistry chatModelApiRegistry(List<ChatModelApiFactory<?>> factories)` → `new ChatModelApiRegistryImpl(factories)` and `@Bean ChatModelInvoker chatModelInvoker(ChatModelApiRegistry registry)` → `new ChatModelInvokerImpl(registry, ChatModelRoundListener.NOOP, <maxContinuations default, e.g. 10>)`. Inject `ChatModelInvoker` into the request-handler bean definitions instead of `AiFrameworkAdapter`.

- [ ] **Step 4: Run unit tests (green)**

Run: `mvn test -f connectors/agentic-ai/pom.xml`
Expected: PASS. Fix any remaining construction sites.

- [ ] **Step 5: Run wire-format e2e regression (parity gate)**

Run:
```bash
mvn install -pl connectors/agentic-ai -DskipTests -f pom.xml
mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -Dtest='*ApiAiAgentJobWorker*'
```
Expected: PASS — behaviour identical to before the cutover. (Requires `element-templates-cli` on PATH.)

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "refactor(agentic-ai): route chat requests through ChatModelInvoker (LangChain4j bridge)"
```

### Task 0.10: Docs — record the SPI in the module reference

**Files:**
- Modify: `docs/reference/ai-agent.md` (§12 framework abstraction: describe `ChatModelApi`/registry/invoker as the seam, LangChain4j as bridge driver; §25 extension points: adding a provider = implement `ChatModelApiFactory`)
- Modify: `AGENTS.md` (invariants: native drivers under `framework/<provider>/**` import only their vendor SDK; framework-agnostic-core wording updated to allow `framework/api/**` as neutral)

- [ ] **Step 1: Update `ai-agent.md` §12 and §25** with the SPI description and the "add a provider" playbook (implement `ChatModelApiFactory`, register a bean; capabilities + native emission arrive in Phases 1–2).

- [ ] **Step 2: Update `AGENTS.md`** framework-agnostic-core invariant to name `framework/api/**` as the neutral SPI package and `framework/<provider>/**` as vendor-scoped.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "docs(agentic-ai): document chat model SPI and provider extension point"
```

---

## Self-review checklist (before executing)

- **Spec coverage:** every ADR 009 decision (§1 seam, §2 continuation, §3 StopReason, §4 matrix, §5 domain model, §6 config, §7 bridge, §8 transport) maps to a Phase task or a roadmap phase. Phase 0 covers §1/§2/§3/§5/§7; §4 → Phase 2; §6 → Phases 1+3; §8 → Phase 1.
- **Gap register:** Task 0.2 wires the first entry path; the epic DoD requires the consolidated agent-instance API requirements summary.
- **Cross-module e2e:** every model/serialization task (0.1, 0.3, 0.4, 0.5, and any later record-shape change) must test-compile `connectors-e2e-test-agentic-ai` (`-am` from repo root) and fix construction sites there in the same commit — the module builds outside `connectors/agentic-ai/pom.xml`. Task 0.4's `TokenUsage` arity change was the first to hit this.
- **Type consistency:** `ChatModelResult`, `ChatModelInvocationResult`, `ChatModelInvoker`, `ChatModelApiRegistry`, `ChatModelApiFactory`, `TokenUsage` (5 components), `StopReason` (8 values) are used with identical names/signatures across Tasks 0.6–0.9.
