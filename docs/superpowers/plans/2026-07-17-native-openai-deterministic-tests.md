# Native OpenAI Deterministic Tests + Truncation-Error Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan is deliberately merged into 4 coherent tasks (fewer review gates); one Sonnet implementer per task, a single final whole-branch Sonnet review at the end.

**Goal:** Close the native OpenAI deterministic-test gap by mirroring the native Anthropic WireMock/SSE scaffolding for both OpenAI wire formats (Chat Completions + Responses), and make response truncation surface as a clear error instead of an opaque downstream parse failure.

**Architecture:** The native OpenAI provider always streams (`client.chat().completions().createStreaming(...)` / `client.responses().createStreaming(...)`) and accumulates via the vendor SDK helpers, exactly like the native Anthropic provider. So deterministic coverage requires WireMock stubs that serve real `text/event-stream` bodies the SDK's own stream parser + accumulator consume — not the buffered-JSON stubs the v1 langchain4j-bridge fixtures use. We add native fixtures to the existing `ProviderWireFormatSmokeTests` parameterized suite (Completions reuses the existing Chat-Completions request recording; Responses gets its own recording/adapter), plus a Responses parity IT for reasoning/encrypted-reasoning/server-tool round-trips. Separately, both response converters gain a truncation check.

**Tech Stack:** Java 21, JUnit 5, WireMock, AssertJ, Camunda Process Test; vendor SDK `com.openai:openai-java` 4.43.0 (`ChatCompletionChunk`/`ChatCompletionAccumulator`, `ResponseStreamEvent`/`ResponseAccumulator`, `com.openai.core.ObjectMappers.jsonMapper()`).

## Global Constraints

- **Search scope:** only this repo, `~/.m2/repository`, and known code repos (Camunda monorepo). NEVER scan `/` or the wider filesystem. Pass this to any sub-agent.
- **Models:** implementation subagents run on Sonnet; review subagents run on Sonnet. Opus only orchestrates.
- **Subagents may only `git add` / `git commit`** — never checkout/reset/revert/rebase/push. Coherent commit messages describing the actual change; never "task"/"review"/"crit"/"round" wording.
- **Never push** — leave all commits local.
- **Never run real-API e2e** (`RUN_NATIVE_LLM_E2E=true` / real keys). All tests in this plan are deterministic (WireMock) or pure unit; they must pass with no network and no API key.
- **Maven/git run with `dangerouslyDisableSandbox: true`** (Mockito MockMaker breaks in the sandbox).
- **Do NOT touch** the pre-existing uncommitted stray file `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/resources/native-provider-acceptance-agent.bpmn`.
- **e2e module prerequisite:** `element-templates-cli` must be on PATH (`npm i -g element-templates-cli`, via asdf/node). The v2 template is already generated + committed; no template regeneration is needed (no `@TemplateProperty` changes in this plan).
- **No `a local env loader`, secrets-file names, or absolute local paths** in any committed `.md`.
- **SSE framing (both OpenAI families):** the SDK stream parser is a W3C-SSE decoder. A stub body is one or more `data: {json}\n\n` blocks terminated by `data: [DONE]\n\n`. `event:` lines are read but ignored (the variant is chosen from the `"type"` field inside the `data:` JSON for Responses). Serialize SDK objects with `com.openai.core.ObjectMappers.jsonMapper()`.

---

## File Structure

New test files (module `connectors-e2e-test/connectors-e2e-test-agentic-ai`, package `io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai` unless noted):

- `NativeOpenAiCompletionsSseChatModelStubs.java` — serves the Chat-Completions streaming endpoint (`POST /v1/chat/completions`) as SSE; builds `ChatCompletionChunk` JSON and frames it.
- `NativeOpenAiCompletionsWireFormatFixture.java` — `ProviderWireFormatFixture` row driving the v2 native OpenAI provider (compatible backend, `apiFamily=completions`); reuses `OpenAiCompletionsRecordedConversation` + `OpenAiCompletionsRecordedChatRequestAdapter` for request recording.
- `NativeOpenAiResponsesSseChatModelStubs.java` — serves the Responses streaming endpoint (`POST /v1/responses`) as SSE; builds `Response` + terminal `ResponseStreamEvent` and frames it.
- `NativeOpenAiResponsesRecordedConversation.java` + `NativeOpenAiResponsesRecordedChatRequestAdapter.java` — parse the Responses request wire (`instructions`, `input[]`, `tools[]`, `text.format`) into the provider-agnostic `RecordedChatRequest` SPI.
- `NativeOpenAiResponsesWireFormatFixture.java` — `ProviderWireFormatFixture` row driving the v2 native OpenAI provider (compatible backend, `apiFamily=responses`).
- `NativeOpenAiResponsesAdvancedFeaturesIT.java` — parity IT: reasoning-effort on the wire, encrypted-reasoning round-trip, server-tool `ProviderContent` round-trip.

Modified files (module `connectors/agentic-ai/connector-agentic-ai`):

- `src/main/java/io/camunda/connector/agenticai/aiagent/agent/AgentErrorCodes.java` — add `ERROR_CODE_RESPONSE_TRUNCATED`.
- `src/main/java/io/camunda/connector/agenticai/aiagent/framework/openai/family/completions/OpenAiCompletionsResponseConverter.java` — truncation check in `toResult`.
- `src/main/java/io/camunda/connector/agenticai/aiagent/framework/openai/family/responses/OpenAiResponsesResponseConverter.java` — truncation check in `toResult`.
- `src/test/java/.../family/completions/OpenAiCompletionsResponseConverterTest.java` — truncation unit test.
- `src/test/java/.../family/responses/OpenAiResponsesResponseConverterTest.java` — truncation unit test.
- `docs/reference/ai-agent.md` — §15 error-code table: add the new code.

Modified (test harness, module `connectors-e2e-test/connectors-e2e-test-agentic-ai`):

- `src/test/java/io/camunda/connector/e2e/agenticai/aiagent/wiremock/ProviderWireFormatSmokeTests.java` — register the two new native OpenAI fixture rows.

---

## Task 1: Response-truncation error (both families)

**Files:**
- Modify: `connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/agent/AgentErrorCodes.java`
- Modify: `connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/openai/family/completions/OpenAiCompletionsResponseConverter.java:52`
- Modify: `connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/openai/family/responses/OpenAiResponsesResponseConverter.java:72`
- Test: `connectors/agentic-ai/connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/framework/openai/family/completions/OpenAiCompletionsResponseConverterTest.java`
- Test: `connectors/agentic-ai/connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/framework/openai/family/responses/OpenAiResponsesResponseConverterTest.java`
- Doc: `connectors/agentic-ai/docs/reference/ai-agent.md` (§15 error codes)

**Interfaces:**
- Produces: `AgentErrorCodes.ERROR_CODE_RESPONSE_TRUNCATED = "RESPONSE_TRUNCATED"` — a new String constant used by both converters.
- Consumes: `io.camunda.connector.api.error.ConnectorException` (constructor `ConnectorException(String errorCode, String message)`).

**Background (verified):** neither converter reads the stop signal today; both always return `ChatModelResult.Completed`. A `finish_reason=length` (Completions) or `status=incomplete`/`incomplete_details.reason=max_output_tokens` (Responses) response with truncated JSON therefore sails through and dies later with a generic `FAILED_TO_PARSE_RESPONSE_CONTENT` (final-answer JSON) or `FAILED_MODEL_CALL` (truncated tool-call args). This task makes truncation an explicit, actionable error. It backstops the deliberately-skipped `gpt-5 + Chat Completions + structured output` acceptance row (see `NativeProviderAcceptanceIT.java:302-317`).

- [ ] **Step 1: Add the error-code constant.** In `AgentErrorCodes.java`, add alongside the existing constants:

```java
  String ERROR_CODE_RESPONSE_TRUNCATED = "RESPONSE_TRUNCATED";
```

- [ ] **Step 2: Write the failing Completions unit test.** In `OpenAiCompletionsResponseConverterTest.java`, mirror the existing `responseFromJson`/`chunk` helpers already in that test class (they build a `ChatCompletion` via `ObjectMappers.jsonMapper().readValue(json, ChatCompletion.class)`). Add:

```java
  @Test
  void raisesTruncationErrorWhenFinishReasonIsLength() {
    final ChatCompletion completion =
        ObjectMappers.jsonMapper()
            .readValue(
                """
                {
                  "id": "chatcmpl_1", "object": "chat.completion", "created": 0, "model": "gpt-5",
                  "choices": [
                    {"index": 0, "finish_reason": "length",
                     "message": {"role": "assistant", "content": "{\\"partial\\":"}}
                  ],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                }
                """,
                ChatCompletion.class);

    assertThatThrownBy(() -> converter.toResult(completion, Duration.ZERO))
        .isInstanceOf(ConnectorException.class)
        .hasFieldOrPropertyWithValue("errorCode", ERROR_CODE_RESPONSE_TRUNCATED)
        .hasMessageContaining("truncated");
  }
```

Adjust the `converter` field name / `toResult` signature to match the existing test's setup. Add imports: `io.camunda.connector.api.error.ConnectorException`, `static ...AgentErrorCodes.ERROR_CODE_RESPONSE_TRUNCATED`, `java.time.Duration`, `static org.assertj.core.api.Assertions.assertThatThrownBy`.

- [ ] **Step 3: Run it — expect FAIL** (no truncation branch yet):

```bash
cd <repo-root>
mvn -q -pl connectors/agentic-ai/connector-agentic-ai test \
  -Dtest=OpenAiCompletionsResponseConverterTest#raisesTruncationErrorWhenFinishReasonIsLength \
  -DfailIfNoTests=false
```
Expected: FAIL (returns `Completed`, no exception thrown).

- [ ] **Step 4: Implement the Completions check.** At the very top of `toResult(ChatCompletion completion, Duration executionTime)` (currently line 52), before building the `Completed` result:

```java
    completion.choices().stream()
        .findFirst()
        .filter(
            choice ->
                choice.finishReason().value()
                    == ChatCompletion.Choice.FinishReason.Value.LENGTH)
        .ifPresent(
            choice -> {
              throw new ConnectorException(
                  ERROR_CODE_RESPONSE_TRUNCATED,
                  "The model response was truncated because it reached the maximum output"
                      + " token limit before completing. Increase 'maxCompletionTokens', or use"
                      + " the Responses API for reasoning models whose reasoning can exhaust the"
                      + " completion budget.");
            });
```

Add imports: `io.camunda.connector.api.error.ConnectorException`, `static ...AgentErrorCodes.ERROR_CODE_RESPONSE_TRUNCATED`. `ChatCompletion.Choice.finishReason()` returns a non-`Optional` `FinishReason`; compare via `.value() == FinishReason.Value.LENGTH` (forward-compatible, never throws on unknown values).

- [ ] **Step 5: Run it — expect PASS.** Same command as Step 3, expect PASS.

- [ ] **Step 6: Write the failing Responses unit test.** In `OpenAiResponsesResponseConverterTest.java`, using that class's existing `responseFromJson`/`baseResponse` helper (`ObjectMappers.jsonMapper().readValue(json, Response.class)`):

```java
  @Test
  void raisesTruncationErrorWhenResponseIsIncompleteDueToMaxOutputTokens() {
    final Response response =
        responseFromJson(
            """
            {
              "id": "resp_1", "object": "response", "created_at": 0, "model": "gpt-5",
              "status": "incomplete",
              "incomplete_details": {"reason": "max_output_tokens"},
              "output": [], "parallel_tool_calls": true, "tool_choice": "auto", "tools": []
            }
            """);

    assertThatThrownBy(() -> converter.toResult(response, Duration.ZERO))
        .isInstanceOf(ConnectorException.class)
        .hasFieldOrPropertyWithValue("errorCode", ERROR_CODE_RESPONSE_TRUNCATED)
        .hasMessageContaining("truncated");
  }
```

- [ ] **Step 7: Run it — expect FAIL:**

```bash
mvn -q -pl connectors/agentic-ai/connector-agentic-ai test \
  -Dtest=OpenAiResponsesResponseConverterTest#raisesTruncationErrorWhenResponseIsIncompleteDueToMaxOutputTokens \
  -DfailIfNoTests=false
```
Expected: FAIL.

- [ ] **Step 8: Implement the Responses check.** At the top of `toResult(Response response, Duration executionTime)` (currently line 72):

```java
    final boolean truncated =
        response
            .incompleteDetails()
            .flatMap(Response.IncompleteDetails::reason)
            .map(
                reason ->
                    reason.value()
                        == Response.IncompleteDetails.Reason.Value.MAX_OUTPUT_TOKENS)
            .orElse(false);
    if (truncated) {
      throw new ConnectorException(
          ERROR_CODE_RESPONSE_TRUNCATED,
          "The model response was truncated because it reached the maximum output token limit"
              + " before completing. Increase the model's max output tokens.");
    }
```

Add the same two imports. (Checking `incompleteDetails().reason() == MAX_OUTPUT_TOKENS` is sufficient and precise; it is only ever present when `status=incomplete`, so no separate status check is needed.)

- [ ] **Step 9: Run it — expect PASS.** Same command as Step 7, expect PASS.

- [ ] **Step 10: Update the error-code docs.** In `connectors/agentic-ai/docs/reference/ai-agent.md` §15 (error codes), add a row for `RESPONSE_TRUNCATED` matching the table's existing format, describing it as "the model stopped because it hit its maximum output token limit before completing the response".

- [ ] **Step 11: Run both converter test classes in full + verify no regressions:**

```bash
mvn -q -pl connectors/agentic-ai/connector-agentic-ai test \
  -Dtest=OpenAiCompletionsResponseConverterTest,OpenAiResponsesResponseConverterTest \
  -DfailIfNoTests=false
```
Expected: PASS.

- [ ] **Step 12: Commit:**

```bash
git add connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/agent/AgentErrorCodes.java \
        connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/openai/family/completions/OpenAiCompletionsResponseConverter.java \
        connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/openai/family/responses/OpenAiResponsesResponseConverter.java \
        connectors/agentic-ai/connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/framework/openai/family/completions/OpenAiCompletionsResponseConverterTest.java \
        connectors/agentic-ai/connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/framework/openai/family/responses/OpenAiResponsesResponseConverterTest.java \
        connectors/agentic-ai/docs/reference/ai-agent.md
git commit -m "feat(agentic-ai): surface native OpenAI response truncation as a clear error"
```

---

## Task 2: Native OpenAI Completions wire-format smoke coverage

**Files:**
- Create: `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/wiremock/openai/NativeOpenAiCompletionsSseChatModelStubs.java`
- Create: `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/wiremock/openai/NativeOpenAiCompletionsWireFormatFixture.java`
- Modify: `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/wiremock/ProviderWireFormatSmokeTests.java`

**Interfaces:**
- Consumes: `ProviderWireFormatFixture` SPI; `TurnStub` (`TurnStub.Text`/`TurnStub.ToolCalls`), `ToolCallStub`; `OpenAiCompletionsChatModelStubs.CHAT_COMPLETIONS_PATH` (`"/v1/chat/completions"`); `OpenAiCompletionsRecordedConversation.recorded()` + `OpenAiCompletionsRecordedChatRequestAdapter`; `AiAgentTestFixtures.AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH` and `...V2_ELEMENT_TEMPLATE_PROPERTIES`.
- Produces: `NativeOpenAiCompletionsWireFormatFixture` — added as a row in `ProviderWireFormatSmokeTests.fixtures()`.

**Key facts:**
- The native provider POSTs to `{baseUrl}/chat/completions` and does NOT re-inject `/v1`. Point the compatible endpoint at `wireMock.getHttpBaseUrl() + "/v1"` so the effective path is `/v1/chat/completions` — this lets the fixture reuse the existing `CHAT_COMPLETIONS_PATH` constant and `OpenAiCompletionsRecordedConversation.recorded()` (which reads that path). The request wire is the standard Chat Completions body, identical to the v1 fixture, so request recording is reused verbatim; only the response stubbing (SSE vs buffered) and provider config differ.
- The provider always sets `stream_options.include_usage=true`; the accumulator needs a chunk carrying `usage` (with `"choices":[]`) after the finish-reason chunk.

- [ ] **Step 1: Create the SSE stubs.** New `NativeOpenAiCompletionsSseChatModelStubs.java` — mirror the shape of `NativeAnthropicMessagesSseChatModelStubs` (scenario chaining) but emit Chat-Completions SSE. Build each `ChatCompletionChunk` as a JSON string (the SDK builders require every optional field be set; string JSON is what the existing `OpenAiCompletionsStreamAssemblerTest` uses) and frame as `data: <json>\n\n`, ending each turn body with `data: [DONE]\n\n`.

```java
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.CHAT_COMPLETIONS_PATH;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ToolCallStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stubs the OpenAI Chat Completions endpoint's streaming response ({@code POST
 * /v1/chat/completions}, {@code stream: true}) with real Server-Sent-Events framing, for the native
 * (own-LLM-layer) OpenAI provider, which always drives {@code
 * client.chat().completions().createStreaming(params)} and feeds the chunks to the vendor SDK's
 * {@code ChatCompletionAccumulator}. The v1 langchain4j-bridge fixture ({@link
 * OpenAiCompletionsChatModelStubs}) returns a single buffered JSON body instead, which the native
 * streaming parser cannot consume - hence this separate SSE stub.
 */
final class NativeOpenAiCompletionsSseChatModelStubs {

  private static final String SCENARIO_NAME = "llm-conversation-openai-sse";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final AtomicInteger TURN_COUNTER = new AtomicInteger(0);

  private NativeOpenAiCompletionsSseChatModelStubs() {}

  static void stubConversation(TurnStub... turns) {
    if (turns.length == 0) {
      throw new IllegalArgumentException("At least one conversation turn is required");
    }
    final List<String> bodies = new ArrayList<>();
    for (final TurnStub turn : turns) {
      bodies.add(sseBody(turn));
    }
    for (int i = 0; i < bodies.size(); i++) {
      final String fromState = i == 0 ? Scenario.STARTED : stateName(i);
      ScenarioMappingBuilder mapping =
          post(urlPathEqualTo(CHAT_COMPLETIONS_PATH))
              .inScenario(SCENARIO_NAME)
              .whenScenarioStateIs(fromState)
              .willReturn(sseResponse(bodies.get(i)));
      if (i < bodies.size() - 1) {
        mapping = mapping.willSetStateTo(stateName(i + 1));
      }
      stubFor(mapping);
    }
  }

  private static String sseBody(TurnStub turn) {
    final String id = "chatcmpl-test-" + TURN_COUNTER.getAndIncrement();
    final String text = (turn instanceof TurnStub.Text t) ? t.text() : ((TurnStub.ToolCalls) turn).text();
    final List<ToolCallStub> toolCalls =
        (turn instanceof TurnStub.ToolCalls tc) ? tc.toolCalls() : List.of();
    final int promptTokens = (turn instanceof TurnStub.Text t) ? t.inputTokens() : ((TurnStub.ToolCalls) turn).inputTokens();
    final int completionTokens = (turn instanceof TurnStub.Text t) ? t.outputTokens() : ((TurnStub.ToolCalls) turn).outputTokens();
    final boolean hasToolCalls = !toolCalls.isEmpty();

    final StringBuilder body = new StringBuilder();
    // 1. role + content delta (content optional/empty when there are only tool calls)
    body.append(dataLine(chunkJson(id, deltaWithContent(text), null, null)));
    // 2. one tool-call delta chunk per tool call (index i, id, function{name, arguments})
    int i = 0;
    for (final ToolCallStub tc : toolCalls) {
      body.append(dataLine(chunkJson(id, toolCallDelta(i++, tc), null, null)));
    }
    // 3. finish-reason chunk (empty delta)
    body.append(dataLine(chunkJson(id, "{}", hasToolCalls ? "tool_calls" : "stop", null)));
    // 4. usage-only chunk (empty choices), mirroring real OpenAI stream_options.include_usage
    body.append(dataLine(usageChunkJson(id, promptTokens, completionTokens)));
    body.append("data: [DONE]\n\n");
    return body.toString();
  }

  private static String deltaWithContent(String text) {
    if (text == null || text.isBlank()) {
      return "{\"role\":\"assistant\"}";
    }
    return "{\"role\":\"assistant\",\"content\":" + quote(text) + "}";
  }

  private static String toolCallDelta(int index, ToolCallStub tc) {
    return "{\"tool_calls\":[{\"index\":"
        + index
        + ",\"id\":"
        + quote(tc.id())
        + ",\"type\":\"function\",\"function\":{\"name\":"
        + quote(tc.name())
        + ",\"arguments\":"
        + quote(tc.argumentsJson())
        + "}}]}";
  }

  private static String chunkJson(String id, String deltaJson, String finishReason, String unused) {
    return "{\"id\":"
        + quote(id)
        + ",\"object\":\"chat.completion.chunk\",\"created\":0,\"model\":\"test-model\","
        + "\"choices\":[{\"index\":0,\"delta\":"
        + deltaJson
        + ",\"finish_reason\":"
        + (finishReason == null ? "null" : quote(finishReason))
        + "}]}";
  }

  private static String usageChunkJson(String id, int promptTokens, int completionTokens) {
    return "{\"id\":"
        + quote(id)
        + ",\"object\":\"chat.completion.chunk\",\"created\":0,\"model\":\"test-model\","
        + "\"choices\":[],\"usage\":{\"prompt_tokens\":"
        + promptTokens
        + ",\"completion_tokens\":"
        + completionTokens
        + ",\"total_tokens\":"
        + (promptTokens + completionTokens)
        + "}}";
  }

  private static String dataLine(String json) {
    return "data: " + json + "\n\n";
  }

  private static String quote(String raw) {
    try {
      return JSON.writeValueAsString(raw);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static ResponseDefinitionBuilder sseResponse(String body) {
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "text/event-stream")
        .withBody(body);
  }

  private static String stateName(int index) {
    return "turn-" + index;
  }
}
```

> Implementer note: prefer building each chunk via `com.openai.core.ObjectMappers.jsonMapper().writeValueAsString(ChatCompletionChunk-built-object)` if you find the hand-written JSON brittle — but the string form above is deliberately minimal and matches what `OpenAiCompletionsStreamAssemblerTest` proves the accumulator parses. Keep whichever you verify green.

- [ ] **Step 2: Create the fixture.** New `NativeOpenAiCompletionsWireFormatFixture.java`. Reuse the existing request recording; only override `stubConversation` (SSE) and provider config (v2 compatible backend). `TurnStub` is consumed directly by the stub, so no `toStubTurn` mapping is needed.

```java
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ProviderWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedChatRequest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Plugs the native (own-LLM-layer) OpenAI Chat Completions wire format into the provider-agnostic
 * {@link ProviderWireFormatFixture} SPI. The *request* wire is the standard Chat Completions body -
 * identical to the v1 langchain4j-bridge fixture - so request recording is reused via {@link
 * OpenAiCompletionsRecordedConversation}. The *response* wire differs (native streams SSE, v1
 * buffers JSON), so {@link #stubConversation} uses {@link NativeOpenAiCompletionsSseChatModelStubs}.
 * Drives the v2 element template with {@code configuration.openai.*} property ids, via the compatible
 * backend (the only OpenAI backend with a configurable endpoint) pointed at the WireMock host with a
 * trailing {@code /v1} so the SDK's {@code /chat/completions} path resolves to the recorded path.
 */
public final class NativeOpenAiCompletionsWireFormatFixture implements ProviderWireFormatFixture {

  @Override
  public String apiName() {
    return "NativeOpenAiCompletions";
  }

  @Override
  public String toString() {
    return apiName();
  }

  @Override
  public Function<ElementTemplate, ElementTemplate> configureProvider(
      WireMockRuntimeInfo wireMock) {
    return template ->
        template
            .property("configuration.type", "openai")
            .property("configuration.openai.apiFamily", "completions")
            .property("configuration.openai.backend.type", "compatible")
            .property(
                "configuration.openai.backend.endpoint", wireMock.getHttpBaseUrl() + "/v1")
            .property("configuration.openai.backend.authentication.type", "apiKey")
            .property("configuration.openai.backend.authentication.apiKey", "dummy")
            .property("configuration.openai.model.model", "test-model");
  }

  @Override
  public String elementTemplatePath(String defaultElementTemplatePath) {
    return AiAgentTestFixtures.AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH;
  }

  @Override
  public Map<String, String> elementTemplateBaselineProperties(
      Map<String, String> defaultProperties) {
    return AiAgentTestFixtures.AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PROPERTIES;
  }

  @Override
  public void stubConversation(TurnStub... turns) {
    NativeOpenAiCompletionsSseChatModelStubs.stubConversation(turns);
  }

  @Override
  public List<RecordedChatRequest> recordedRequests() {
    return OpenAiCompletionsRecordedConversation.recorded().requests().stream()
        .<RecordedChatRequest>map(OpenAiCompletionsRecordedChatRequestAdapter::new)
        .toList();
  }
}
```

> Implementer note: verify the compatible-auth property ids against `OpenAiChatModel.java` (`OpenAiCompatibleBackend`/`CompatibleAuthentication`, discriminator `type` with `none`/`apiKey`; the api-key variant carries `apiKey`). If the SDK client accepts no key with `authentication.type=none`, that is also acceptable; `apiKey`+`dummy` is the safe default.

- [ ] **Step 3: Register the row.** In `ProviderWireFormatSmokeTests.java`, import `NativeOpenAiCompletionsWireFormatFixture` and add `new NativeOpenAiCompletionsWireFormatFixture()` to the `fixtures()` stream.

- [ ] **Step 4: Run the smoke suite for this row — expect PASS on all four scenarios** (plain text, tool call, doc-in-user-prompt, structured output):

```bash
cd <repo-root>
mvn -q -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am \
  -Dtest=ProviderWireFormatSmokeTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
Expected: PASS, including the new `NativeOpenAiCompletions` parameterized rows (4 scenarios). Requires `element-templates-cli` on PATH.

- [ ] **Step 5: Commit:**

```bash
git add connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/wiremock/openai/NativeOpenAiCompletionsSseChatModelStubs.java \
        connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/wiremock/openai/NativeOpenAiCompletionsWireFormatFixture.java \
        connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/wiremock/ProviderWireFormatSmokeTests.java
git commit -m "test(agentic-ai): add native OpenAI Chat Completions wire-format smoke coverage"
```

---

## Task 3: Native OpenAI Responses wire-format smoke coverage

**Files:**
- Create: `.../wiremock/openai/NativeOpenAiResponsesRecordedConversation.java`
- Create: `.../wiremock/openai/NativeOpenAiResponsesRecordedChatRequestAdapter.java`
- Create: `.../wiremock/openai/NativeOpenAiResponsesSseChatModelStubs.java`
- Create: `.../wiremock/openai/NativeOpenAiResponsesWireFormatFixture.java`
- Modify: `.../wiremock/ProviderWireFormatSmokeTests.java`

**Interfaces:**
- Consumes: `ProviderWireFormatFixture`, `RecordedChatRequest`, `RecordedMessage`, `RecordedContentPart`, `RecordedToolCall`, `RecordedResponseFormat` (SPI records); `TurnStub`, `ToolCallStub`; `com.openai.core.ObjectMappers.jsonMapper()`; `com.openai.models.responses.{Response, ResponseStreamEvent, ResponseCompletedEvent}`; the v2 template constants.
- Produces: `NativeOpenAiResponsesWireFormatFixture` — a `fixtures()` row.

**Key facts (Responses wire):**
- **Endpoint:** `POST {baseUrl}/responses`; point the compatible endpoint at `wireMock.getHttpBaseUrl() + "/v1"` → path `/v1/responses`. Define a constant `RESPONSES_PATH = "/v1/responses"` in the stub.
- **Request body:** `instructions` (system prompt), `input[]` (items), `tools[]`, `text.format` (structured output). Item shapes: user text `{"type":"message","role":"user","content":[{"type":"input_text","text":...}]}`; document parts `{"type":"input_image",...}` / `{"type":"input_file",...}`; assistant `{"type":"message","role":"assistant","content":[{"type":"input_text",...}]}`; `{"type":"function_call","call_id","name","arguments"}`; `{"type":"function_call_output","call_id","output"}`. Tools: `{"type":"function","name","parameters","strict","description"}`. Structured output: `text.format = {"type":"json_schema","name","schema","strict"}`.
- **Response stream:** `ResponseAccumulator` only needs ONE terminal `response.completed` event carrying the full `Response`. Build the `Response` via `ObjectMappers.jsonMapper().readValue(json, Response.class)`, wrap with `ResponseStreamEvent.ofCompleted(ResponseCompletedEvent.builder().response(r).sequenceNumber(0L).build())`, serialize with `ObjectMappers.jsonMapper()`, frame as `data: <json>\n\n` + `data: [DONE]\n\n`.

- [ ] **Step 1: Create the request recording + adapter.** New `NativeOpenAiResponsesRecordedConversation.java` — model it on `OpenAiCompletionsRecordedConversation` but parse the Responses body. Read `POST /v1/responses` logged requests (sorted by logged date), parse each into a `RecordedChatRequest` with:
  - a synthetic `system` message from `instructions` (so the shared `ProviderWireFormatExpectedMessage.system(...)` assertion passes) followed by the `input[]` items mapped to `RecordedMessage`:
    - `message` items → role + text parts (`input_text` → `RecordedContentPart("text", text)`; `input_image`/`input_file` → `RecordedContentPart(kind, null)`).
    - `function_call` items → an assistant `RecordedMessage` with a `RecordedToolCall(call_id, name, arguments)`.
    - `function_call_output` items → a `tool`/`toolResult` `RecordedMessage` with `toolCallId = call_id`.
  - `toolDefinitions()` from `tools[]` (map `{name, description, parameters}` → `ToolDefinition.builder()`), mirroring `OpenAiCompletionsRecordedConversation`'s `ToolWire`.
  - `responseFormat()` from `text.format` → `RecordedResponseFormat(type, name, schema)`.

  Then `NativeOpenAiResponsesRecordedChatRequestAdapter.java` mapping the parsed record onto the SPI, mirroring `OpenAiCompletionsRecordedChatRequestAdapter`. (If you parse straight into the SPI records, a thin adapter may be unnecessary — keep the SPI mapping in one place either way; match the Completions pattern for consistency.)

  Provide the exact message ordering the four smoke scenarios assert (see `ProviderWireFormatSmokeTests`): system, user, then for the tool scenario assistant-with-tool-call + tool-result.

- [ ] **Step 2: Unit-test the parser directly** (fast feedback, no engine). Create `NativeOpenAiResponsesRecordedConversationTest` — feed a canned Responses request body string covering: `instructions`, a user `input_text` message, a `function_call`, a `function_call_output`, a `tools[]` entry, and a `text.format` json_schema; assert the parsed `RecordedChatRequest` exposes the system+user+assistant(tool-call)+tool-result messages, the tool definition, and the response format. Run:

```bash
mvn -q -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am \
  -Dtest=NativeOpenAiResponsesRecordedConversationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
Expected: PASS.

- [ ] **Step 3: Create the SSE stubs.** New `NativeOpenAiResponsesSseChatModelStubs.java` — scenario-chained like the Completions stub, one turn per `TurnStub`. For each turn build a `Response` JSON:
  - text turn → `output: [{"type":"message","id":"msg_N","role":"assistant","status":"completed","content":[{"type":"output_text","text":<text>,"annotations":[]}]}]`.
  - tool-call turn → `output` additionally contains `{"type":"function_call","id":"fc_N","call_id":<id>,"name":<name>,"arguments":<argsJson>,"status":"completed"}` per tool call; include the assistant text message when present.
  - always include `usage: {"input_tokens":P,"output_tokens":C,"total_tokens":P+C,"input_tokens_details":{"cached_tokens":0},"output_tokens_details":{"reasoning_tokens":0}}`, `status:"completed"`, and the `Response` scaffold fields (`id, object, created_at, model, parallel_tool_calls, tool_choice, tools`).
  Read the JSON into a `Response` via `ObjectMappers.jsonMapper()`, wrap in `ResponseStreamEvent.ofCompleted(ResponseCompletedEvent.builder().response(r).sequenceNumber(0L).build())`, serialize, and frame as `data: <json>\n\n` then `data: [DONE]\n\n`. Stub `POST /v1/responses`.

- [ ] **Step 4: Create the fixture.** New `NativeOpenAiResponsesWireFormatFixture.java` — like the Completions fixture but `configuration.openai.apiFamily = "responses"`, `recordedRequests()` via the Responses recording/adapter, and override `assertResponseFormatConfigured` to read `text.format` (type `json_schema`, name, schema) — the Responses wire DOES carry the schema name, so assert it (unlike Anthropic). Mirror the SPI default but source from the Responses record.

- [ ] **Step 5: Register the row.** Add `new NativeOpenAiResponsesWireFormatFixture()` to `ProviderWireFormatSmokeTests.fixtures()`.

- [ ] **Step 6: Run the smoke suite — expect PASS for the new row's four scenarios:**

```bash
mvn -q -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am \
  -Dtest=ProviderWireFormatSmokeTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
Expected: PASS including `NativeOpenAiResponses` rows.

- [ ] **Step 7: Commit:**

```bash
git add connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/wiremock/openai/NativeOpenAiResponses*.java \
        connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/wiremock/ProviderWireFormatSmokeTests.java
git commit -m "test(agentic-ai): add native OpenAI Responses wire-format smoke coverage"
```

---

## Task 4: Native OpenAI Responses parity — reasoning, encrypted reasoning, server tools

**Files:**
- Create: `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/wiremock/openai/NativeOpenAiResponsesAdvancedFeaturesIT.java`
- Reuse/extend: `NativeOpenAiResponsesSseChatModelStubs` (add reasoning + server-tool turn shapes), `NativeOpenAiResponsesRecordedConversation` (assert replayed reasoning/server-tool input items).

**Interfaces:**
- Consumes: everything from Task 3; the domain `ProviderContent` concept as witnessed by the Anthropic parity tests (`NativeAnthropicCodeExecutionServerToolE2eTest`, `NativeAnthropicReasoningEffortIT`); `configuration.openai.model.parameters.effort`, `configuration.openai.enableWebSearch`, `configuration.openai.enableCodeInterpreter` (Responses-only) property ids.
- Produces: no new production interfaces — this task is coverage only.

**Background:** Responses is the family that round-trips reasoning. On the response, a reasoning item carries `encrypted_content`; on the next request the connector must replay it byte-for-byte as an input item (`ObjectMappers.jsonMapper().convertValue(reasoning.providerPayload(), ResponseInputItem.class)`), and likewise replay `web_search_call`/`code_interpreter_call` server-tool items captured as `ProviderContent`. This is the OpenAI analogue of the Anthropic signed-thinking/redacted-thinking/server-tool round-trip tests, and a second witness for the encrypted-reasoning payload path.

This is one IT with three focused tests. Mirror the structure of `NativeAnthropicReasoningEffortIT` and `NativeAnthropicCodeExecutionServerToolE2eTest`; drive the v2 template via the `NativeOpenAiResponsesWireFormatFixture` provider config plus the capability properties above.

- [ ] **Step 1: Reasoning-effort on the wire.** Add a test that configures `configuration.openai.model.parameters.effort = "high"`, stubs a plain text turn, and asserts the recorded Responses request carries the reasoning-effort field (and, when reasoning round-trip is enabled, the `include: ["reasoning.encrypted_content"]` entry). Assert against the recorded request body via the Responses recording. Extend `NativeOpenAiResponsesRecordedConversation` if needed to expose the `reasoning`/`include` fields.

- [ ] **Step 2: Encrypted-reasoning round-trip.** Add a `ReasoningTurnStub`-style shape to `NativeOpenAiResponsesSseChatModelStubs` whose first turn's `Response.output` leads with a reasoning item carrying `encrypted_content` (e.g. `{"type":"reasoning","id":"rs_1","encrypted_content":"ENC-OPAQUE-1","summary":[]}`) followed by a `function_call` item; the second turn is a plain text turn. Assert that on the follow-up model call (after the tool result), the recorded request's `input[]` replays the reasoning item byte-identically (same `encrypted_content`, positioned before the function_call), proving `ReasoningContent.providerPayload` round-trips through the real `ResponseAccumulator`. This mirrors `NativeAnthropicReasoningEffortIT.roundTripsSignedThinkingBlock...`.

- [ ] **Step 3: Server-tool `ProviderContent` round-trip.** Add a server-tool turn shape whose `Response.output` contains a `web_search_call` (or `code_interpreter_call`) item, and assert the connector preserves it as an opaque `ProviderContent` block that is replayed on the follow-up request's `input[]`. Mirror `NativeAnthropicCodeExecutionServerToolE2eTest`'s conversation-scanning assertion (the server-tool block may live in an interim turn). Use `configuration.openai.enableWebSearch=true` (or `enableCodeInterpreter=true`).

- [ ] **Step 4: Run the IT — expect PASS:**

```bash
mvn -q -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am \
  -Dtest=NativeOpenAiResponsesAdvancedFeaturesIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
Expected: PASS (all three tests).

- [ ] **Step 5: Commit:**

```bash
git add connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/wiremock/openai/NativeOpenAiResponses*.java
git commit -m "test(agentic-ai): cover native OpenAI Responses reasoning, encrypted-reasoning and server-tool round-trips"
```

---

## Final verification (after all tasks)

- [ ] Run the connector-module OpenAI framework unit tests + the wire-format smoke suite + the parity IT together, confirm green:

```bash
cd <repo-root>
mvn -q -pl connectors/agentic-ai/connector-agentic-ai test \
  -Dtest='OpenAi*ResponseConverterTest' -DfailIfNoTests=false
mvn -q -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am \
  -Dtest='ProviderWireFormatSmokeTests,NativeOpenAiResponsesRecordedConversationTest,NativeOpenAiResponsesAdvancedFeaturesIT' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] Confirm `git status` shows the stray `native-provider-acceptance-agent.bpmn` still untouched.
- [ ] Single final whole-branch Sonnet review over the range of the four new commits.

---

## Self-Review notes (author)

- **Spec coverage:** Truncation fix (both families) = Task 1; native Completions smoke = Task 2; native Responses smoke = Task 3; parity depth (reasoning/encrypted-reasoning/server-tool) = Task 4. The memory plan's four smoke scenarios (text, tool, doc-in-prompt, structured output) are inherited automatically by adding fixture rows to `ProviderWireFormatSmokeTests`.
- **Structured-output deterministic wire test:** covered by the smoke suite's `jsonResponseSchemaStructuredOutput` scenario asserting `response_format.json_schema`/`text.format` on both native OpenAI rows; the `.strict(true)` flag is already unit-covered in `OpenAiCompletionsRequestConverterTest`/`OpenAiResponsesRequestConverterTest`, so no extra SPI plumbing is added (YAGNI).
- **Scope flag:** Task 1's Responses truncation check is symmetric hardening beyond the originally-discussed Completions case — confirm with the requester if it should ship in the same chunk or be dropped.
- **Type consistency:** property ids (`configuration.openai.apiFamily`, `configuration.openai.backend.type=compatible`, `.backend.endpoint`, `.backend.authentication.type`/`.apiKey`, `.model.model`, `.model.parameters.effort`, `enableWebSearch`, `enableCodeInterpreter`) verified against `OpenAiChatModel.java` and `NativeProviderAcceptanceIT`. `ERROR_CODE_RESPONSE_TRUNCATED` used identically across converters, tests, and docs.
