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
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.openai.core.ObjectMappers;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.Delta;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.Delta.Role;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.Delta.ToolCall;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.FinishReason;
import com.openai.models.completions.CompletionUsage;
import com.openai.models.completions.CompletionUsage.CompletionTokensDetails;
import com.openai.models.completions.CompletionUsage.PromptTokensDetails;
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
 * {@code ChatCompletionAccumulator}. The legacy fixture ({@link OpenAiCompletionsChatModelStubs})
 * returns a single buffered JSON body instead, which the native streaming parser cannot consume -
 * hence this separate SSE stub.
 *
 * <p>Each chunk is built using the vendor SDK's own {@link ChatCompletionChunk} builder (rather
 * than hand-rolled JSON) and serialized with the SDK's own {@link ObjectMappers#jsonMapper()}, so
 * the bytes are guaranteed to parse exactly as real OpenAI would send them - mirroring how {@code
 * StreamingAnthropicMessagesSseChatModelStubs} builds its events. Each turn's body is a chain of
 * {@code data: <ChatCompletionChunk JSON>\n\n} lines terminated by {@code data: [DONE]\n\n}: a
 * role/content delta chunk, one tool-call delta chunk per tool call, a finish-reason chunk (empty
 * delta), and a trailing usage-only chunk ({@code choices: []} with the final {@code usage}) -
 * mirroring real {@code stream_options.include_usage=true} behavior, which the native provider's
 * request converter always sets.
 *
 * <p>{@link #stubConversation(TurnStub...)} always renders {@code prompt_tokens_details} / {@code
 * completion_tokens_details} with zero-valued sub-fields, since the generic {@link TurnStub} SPI
 * has no dial for them; use {@link #stubConversation(UsageDetailsTurnStub)} for a single turn whose
 * cache-read/reasoning token counts need to be non-zero.
 */
public final class OpenAiCompletionsV2SseChatModelStubs {

  private static final String SCENARIO_NAME = "llm-conversation-native-openai-completions-sse";
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
   * dedicated single-turn stub exists purely so e2e coverage can exercise the non-zero case.
   *
   * <p>{@code inputTokens} is the expected <strong>post-subtraction, non-cached</strong> count -
   * what {@code AgentMetrics.TokenUsage#inputTokenCount()} should end up holding once the converter
   * subtracts {@code cachedTokens} from the wire's raw prompt total - not the raw wire {@code
   * prompt_tokens} value itself. {@link #sseBody(UsageDetailsTurnStub)} grosses it back up ({@code
   * inputTokens + cachedTokens}) when building the wire body, since cached tokens are always a
   * subset of the real API's {@code prompt_tokens}.
   */
  public record UsageDetailsTurnStub(
      String text, int inputTokens, int outputTokens, long cachedTokens, long reasoningTokens) {}

  public static void stubConversation(UsageDetailsTurnStub turn) {
    stubFor(post(urlPathEqualTo(CHAT_COMPLETIONS_PATH)).willReturn(sseResponse(sseBody(turn))));
  }

  private static String sseBody(UsageDetailsTurnStub turn) {
    final String id = "chatcmpl-test-" + TURN_COUNTER.getAndIncrement();

    final StringBuilder body = new StringBuilder();
    body.append(dataLine(chunk(id, contentDelta(turn.text()), null)));
    body.append(dataLine(chunk(id, Delta.builder().build(), FinishReason.STOP)));
    body.append(
        dataLine(
            usageChunk(
                id,
                // gross the non-cached inputTokens back up to a realistic raw wire
                // prompt_tokens total: cached tokens are always a subset of it.
                turn.inputTokens() + turn.cachedTokens(),
                turn.outputTokens(),
                turn.cachedTokens(),
                turn.reasoningTokens())));
    body.append("data: [DONE]\n\n");
    return body.toString();
  }

  /**
   * A single-turn conversation whose final chunk carries {@code finish_reason: content_filter}
   * after some assistant text - the shape OpenAI returns when its content filtering blocks the
   * response, mirroring how {@link UsageDetailsTurnStub} wires a distinct single-turn shape.
   */
  public record ContentFilteredTurnStub(String text, int inputTokens, int outputTokens) {}

  public static void stubConversation(ContentFilteredTurnStub turn) {
    stubFor(post(urlPathEqualTo(CHAT_COMPLETIONS_PATH)).willReturn(sseResponse(sseBody(turn))));
  }

  private static String sseBody(ContentFilteredTurnStub turn) {
    final String id = "chatcmpl-test-" + TURN_COUNTER.getAndIncrement();

    final StringBuilder body = new StringBuilder();
    body.append(dataLine(chunk(id, contentDelta(turn.text()), null)));
    body.append(dataLine(chunk(id, Delta.builder().build(), FinishReason.CONTENT_FILTER)));
    body.append(dataLine(usageChunk(id, turn.inputTokens(), turn.outputTokens(), 0L, 0L)));
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
    // 1. role + content delta (content omitted when there are only tool calls)
    body.append(dataLine(chunk(id, contentDelta(text), null)));
    // 2. one tool-call delta chunk per tool call
    int i = 0;
    for (final ToolCallStub tc : toolCalls) {
      body.append(dataLine(chunk(id, toolCallDelta(i++, tc), null)));
    }
    // 3. finish-reason chunk (empty delta)
    body.append(
        dataLine(
            chunk(
                id,
                Delta.builder().build(),
                hasToolCalls ? FinishReason.TOOL_CALLS : FinishReason.STOP)));
    // 4. usage-only chunk (empty choices), mirroring real OpenAI stream_options.include_usage
    body.append(dataLine(usageChunk(id, promptTokens, completionTokens, 0L, 0L)));
    body.append("data: [DONE]\n\n");
    return body.toString();
  }

  private static Delta contentDelta(String text) {
    final Delta.Builder delta = Delta.builder().role(Role.ASSISTANT);
    if (text != null && !text.isBlank()) {
      delta.content(text);
    }
    return delta.build();
  }

  private static Delta toolCallDelta(int index, ToolCallStub tc) {
    return Delta.builder()
        .toolCalls(
            List.of(
                ToolCall.builder()
                    .index(index)
                    .id(tc.id())
                    .type(ToolCall.Type.FUNCTION)
                    .function(
                        ToolCall.Function.builder()
                            .name(tc.name())
                            .arguments(tc.argumentsJson())
                            .build())
                    .build()))
        .build();
  }

  private static ChatCompletionChunk chunk(String id, Delta delta, FinishReason finishReason) {
    return ChatCompletionChunk.builder()
        .id(id)
        .created(0L)
        .model("test-model")
        .choices(List.of(Choice.builder().index(0).delta(delta).finishReason(finishReason).build()))
        .build();
  }

  private static ChatCompletionChunk usageChunk(
      String id,
      long promptTokens,
      long completionTokens,
      long cachedTokens,
      long reasoningTokens) {
    return ChatCompletionChunk.builder()
        .id(id)
        .created(0L)
        .model("test-model")
        .choices(List.of())
        .usage(
            CompletionUsage.builder()
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(promptTokens + completionTokens)
                .promptTokensDetails(
                    PromptTokensDetails.builder().cachedTokens(cachedTokens).build())
                .completionTokensDetails(
                    CompletionTokensDetails.builder().reasoningTokens(reasoningTokens).build())
                .build())
        .build();
  }

  private static String dataLine(ChatCompletionChunk chunk) {
    return "data: " + serialize(chunk) + "\n\n";
  }

  private static String serialize(ChatCompletionChunk chunk) {
    try {
      return ObjectMappers.jsonMapper().writeValueAsString(chunk);
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
