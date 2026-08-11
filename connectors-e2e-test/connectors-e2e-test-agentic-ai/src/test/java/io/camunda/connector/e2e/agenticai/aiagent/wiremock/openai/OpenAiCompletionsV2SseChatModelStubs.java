/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stubs the OpenAI Chat Completions endpoint's streaming response ({@code POST
 * /v1/chat/completions}, {@code stream: true}) with real Server-Sent-Events framing, for the native
 * (own-LLM-layer) OpenAI provider, which always drives {@code
 * client.chat().completions().createStreaming(params)} and feeds the chunks to the vendor SDK's
 * {@code ChatCompletionAccumulator}. The legacy langchain4j-bridge fixture ({@link
 * OpenAiCompletionsChatModelStubs}) returns a single buffered JSON body instead, which the native
 * streaming parser cannot consume - hence this separate SSE stub.
 *
 * <p>Each turn's body is a chain of {@code data: <ChatCompletionChunk JSON>\n\n} lines terminated
 * by {@code data: [DONE]\n\n}: a role/content delta chunk, one tool-call delta chunk per tool call,
 * a finish-reason chunk (empty delta), and a trailing usage-only chunk ({@code "choices":[]} with
 * the final {@code usage}) - mirroring the real behavior of {@code
 * stream_options.include_usage=true}, which the native provider's request converter always sets.
 *
 * <p>{@link #stubConversation(TurnStub...)} always renders {@code prompt_tokens_details} / {@code
 * completion_tokens_details} with zero-valued sub-fields, since the generic {@link TurnStub} SPI
 * has no dial for them; use {@link #stubConversation(UsageDetailsTurnStub)} for a single turn whose
 * cache-read/reasoning token counts need to be non-zero.
 */
public final class OpenAiCompletionsV2SseChatModelStubs {

  private static final String SCENARIO_NAME = "llm-conversation-native-openai-completions-sse";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final AtomicInteger TURN_COUNTER = new AtomicInteger(0);

  private OpenAiCompletionsV2SseChatModelStubs() {}

  public static void stubConversation(TurnStub... turns) {
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

  /**
   * A single-turn conversation whose usage carries explicit {@code
   * prompt_tokens_details.cached_tokens} and/or {@code completion_tokens_details.reasoning_tokens}
   * values - the shape a prompt-cache-hit or reasoning-effort-enabled Completions call returns. The
   * generic {@link TurnStub} SPI (shared with every other provider's stubs) has no dial for these
   * fields, so {@link #stubConversation(TurnStub...)} always renders them as absent-equivalent
   * (mirroring real OpenAI behavior when a call has no cache hit / no reasoning spend); this
   * dedicated single-turn stub exists purely so e2e coverage can exercise the non-zero case (see
   * {@code OpenAiCompletionsResponseConverter#toTokenUsage}).
   *
   * <p>{@code inputTokens} is the expected <strong>post-subtraction, non-cached</strong> count -
   * i.e. what {@code AgentMetrics.TokenUsage#inputTokenCount()} should end up holding once the
   * converter subtracts {@code cachedTokens} from the wire's raw prompt total - not the raw wire
   * {@code prompt_tokens} value itself. {@link #sseBody(UsageDetailsTurnStub)} grosses it back up
   * ({@code inputTokens + cachedTokens}) when building the wire body, since cached tokens are
   * always a subset of the real API's {@code prompt_tokens} and a wire body with {@code
   * prompt_tokens < cached_tokens} is a combination OpenAI can never actually produce.
   */
  public record UsageDetailsTurnStub(
      String text, int inputTokens, int outputTokens, long cachedTokens, long reasoningTokens) {}

  public static void stubConversation(UsageDetailsTurnStub turn) {
    stubFor(post(urlPathEqualTo(CHAT_COMPLETIONS_PATH)).willReturn(sseResponse(sseBody(turn))));
  }

  private static String sseBody(UsageDetailsTurnStub turn) {
    final String id = "chatcmpl-test-" + TURN_COUNTER.getAndIncrement();

    final StringBuilder body = new StringBuilder();
    body.append(dataLine(chunkJson(id, deltaWithContent(turn.text()), null)));
    body.append(dataLine(chunkJson(id, "{}", "stop")));
    body.append(
        dataLine(
            usageChunkJson(
                id,
                // gross the non-cached inputTokens back up to a realistic raw wire
                // prompt_tokens total: cached tokens are always a subset of it.
                Math.toIntExact(turn.inputTokens() + turn.cachedTokens()),
                turn.outputTokens(),
                turn.cachedTokens(),
                turn.reasoningTokens())));
    body.append("data: [DONE]\n\n");
    return body.toString();
  }

  private static String sseBody(TurnStub turn) {
    final String id = "chatcmpl-test-" + TURN_COUNTER.getAndIncrement();
    final String text =
        (turn instanceof TurnStub.Text t) ? t.text() : ((TurnStub.ToolCalls) turn).text();
    final List<ToolCallStub> toolCalls =
        (turn instanceof TurnStub.ToolCalls tc) ? tc.toolCalls() : List.of();
    final int promptTokens =
        (turn instanceof TurnStub.Text t)
            ? t.inputTokens()
            : ((TurnStub.ToolCalls) turn).inputTokens();
    final int completionTokens =
        (turn instanceof TurnStub.Text t)
            ? t.outputTokens()
            : ((TurnStub.ToolCalls) turn).outputTokens();
    final boolean hasToolCalls = !toolCalls.isEmpty();

    final StringBuilder body = new StringBuilder();
    // 1. role + content delta (content optional/empty when there are only tool calls)
    body.append(dataLine(chunkJson(id, deltaWithContent(text), null)));
    // 2. one tool-call delta chunk per tool call (index i, id, function{name, arguments})
    int i = 0;
    for (final ToolCallStub tc : toolCalls) {
      body.append(dataLine(chunkJson(id, toolCallDelta(i++, tc), null)));
    }
    // 3. finish-reason chunk (empty delta)
    body.append(dataLine(chunkJson(id, "{}", hasToolCalls ? "tool_calls" : "stop")));
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

  private static String chunkJson(String id, String deltaJson, String finishReason) {
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
    return usageChunkJson(id, promptTokens, completionTokens, 0L, 0L);
  }

  private static String usageChunkJson(
      String id, int promptTokens, int completionTokens, long cachedTokens, long reasoningTokens) {
    return "{\"id\":"
        + quote(id)
        + ",\"object\":\"chat.completion.chunk\",\"created\":0,\"model\":\"test-model\","
        + "\"choices\":[],\"usage\":{\"prompt_tokens\":"
        + promptTokens
        + ",\"completion_tokens\":"
        + completionTokens
        + ",\"total_tokens\":"
        + (promptTokens + completionTokens)
        + ",\"prompt_tokens_details\":{\"cached_tokens\":"
        + cachedTokens
        + "},\"completion_tokens_details\":{\"reasoning_tokens\":"
        + reasoningTokens
        + "}}}";
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
