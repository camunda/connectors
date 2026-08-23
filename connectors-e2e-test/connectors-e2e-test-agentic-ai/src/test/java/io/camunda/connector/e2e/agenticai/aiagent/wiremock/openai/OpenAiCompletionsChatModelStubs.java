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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.openai.core.ObjectMappers;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.Delta;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.Delta.Role;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.FinishReason;
import com.openai.models.completions.CompletionUsage;
import com.openai.models.completions.CompletionUsage.CompletionTokensDetails;
import com.openai.models.completions.CompletionUsage.PromptTokensDetails;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stubs the OpenAI Chat Completions endpoint's <em>streaming</em> response ({@code POST
 * /v1/chat/completions}, {@code stream: true}) with real Server-Sent-Events framing.
 *
 * <p>This is the single OpenAI Chat Completions stub for the whole agentic-ai e2e suite. The native
 * (own-LLM-layer) OpenAI provider always drives {@code
 * client.chat().completions().createStreaming(params)} and feeds the chunks to the vendor SDK's
 * {@code ChatCompletionAccumulator}, so a plain buffered JSON body would not parse. Since the v1
 * {@code openaiCompatible} provider config is rewritten to the native provider at the connector
 * boundary (see {@code camunda.connector.agenticai.aiagent.rewrite-v1-provider-config-to-v2},
 * default on), the v1 element templates the behavioral suites drive also reach this native
 * streaming path.
 *
 * <p>A conversation is expressed as an ordered list of {@link Turn turns}. Each model call advances
 * a {@link Scenario} state, so sequential calls deterministically receive the next turn's response
 * regardless of request body. The ad-hoc sub-process tools execute between turns, so tool
 * <em>results</em> are produced by the engine; only the assistant turns (text and tool-call
 * requests) are stubbed here.
 *
 * <p>Each chunk is built using the vendor SDK's own {@link ChatCompletionChunk} builder (rather
 * than hand-rolled JSON) and serialized with the SDK's own {@link ObjectMappers#jsonMapper()}, so
 * the bytes are guaranteed to parse exactly as real OpenAI would send them. Each turn's body is a
 * chain of {@code data: <ChatCompletionChunk JSON>\n\n} lines terminated by {@code data:
 * [DONE]\n\n}: a role/content delta chunk, one tool-call delta chunk per tool call, a finish-reason
 * chunk (empty delta), and a trailing usage-only chunk ({@code choices: []} with the final {@code
 * usage}) - mirroring real {@code stream_options.include_usage=true} behavior, which the native
 * provider's request converter always sets.
 */
public final class OpenAiCompletionsChatModelStubs {

  public static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

  private static final String SCENARIO_NAME = "llm-conversation";
  private static final AtomicInteger TURN_COUNTER = new AtomicInteger(0);

  private OpenAiCompletionsChatModelStubs() {}

  /**
   * Stubs the endpoint to always return the same response regardless of how many times it is
   * called.
   */
  public static void stubRepeatingTurn(Turn turn) {
    stubFor(post(urlPathEqualTo(CHAT_COMPLETIONS_PATH)).willReturn(sseResponse(turn)));
  }

  /** Wires the scenario chain returning each turn's response in order. */
  public static void stubConversation(Turn... turns) {
    stubConversation(CHAT_COMPLETIONS_PATH, turns);
  }

  /**
   * Wires the scenario chain returning each turn's response in order, for a conversation expressed
   * in the provider-agnostic {@link TurnStub} SPI shape (used by the {@code
   * ProviderWireFormatFixture} rows).
   */
  public static void stubConversation(TurnStub... turns) {
    stubConversation(
        Arrays.stream(turns).map(OpenAiCompletionsChatModelStubs::toTurn).toArray(Turn[]::new));
  }

  /**
   * Wires the scenario chain returning each turn's response in order, at the given path. Used by
   * {@code AzureOpenAiCompletionsWireFormatFixture} to stub the same wire format at Azure's
   * deployment-based path instead of {@link #CHAT_COMPLETIONS_PATH}.
   */
  public static void stubConversation(String path, Turn... turns) {
    final var turnList = Arrays.asList(turns);
    if (turnList.isEmpty()) {
      throw new IllegalArgumentException("At least one conversation turn is required");
    }

    for (int i = 0; i < turnList.size(); i++) {
      final String fromState = i == 0 ? Scenario.STARTED : stateName(i);

      ScenarioMappingBuilder mapping =
          post(urlPathEqualTo(path))
              .inScenario(SCENARIO_NAME)
              .whenScenarioStateIs(fromState)
              .willReturn(sseResponse(turns[i]));

      // Advance to the next state unless this is the final turn.
      if (i < turnList.size() - 1) {
        mapping = mapping.willSetStateTo(stateName(i + 1));
      }

      stubFor(mapping);
    }
  }

  /**
   * A single-turn conversation whose usage carries explicit {@code
   * prompt_tokens_details.cached_tokens} and/or {@code completion_tokens_details.reasoning_tokens}
   * values - the shape a prompt-cache-hit or reasoning-effort-enabled Completions call returns. The
   * generic {@link Turn} API has no dial for these fields, so the other {@code stubConversation}
   * overloads always render them as absent-equivalent (mirroring real OpenAI behavior when a call
   * has no cache hit / no reasoning spend); this dedicated single-turn stub exists purely so e2e
   * coverage can exercise the non-zero case.
   *
   * <p>{@code inputTokens} is the expected <strong>post-subtraction, non-cached</strong> count -
   * what {@code AgentMetrics.TokenUsage#inputTokenCount()} should end up holding once the converter
   * subtracts {@code cachedTokens} from the wire's raw prompt total - not the raw wire {@code
   * prompt_tokens} value itself. The wire body grosses it back up ({@code inputTokens +
   * cachedTokens}), since cached tokens are always a subset of the real API's {@code
   * prompt_tokens}.
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
   * response.
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

  private static ResponseDefinitionBuilder sseResponse(Turn turn) {
    final var response = sseResponse(turn.toSseBody());
    if (turn.requestDelay != null) {
      return response.withFixedDelay((int) turn.requestDelay.toMillis());
    }
    return response;
  }

  private static ResponseDefinitionBuilder sseResponse(String body) {
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "text/event-stream")
        .withBody(body);
  }

  private static Turn toTurn(TurnStub turn) {
    return switch (turn) {
      case TurnStub.Text text -> Turn.text(text.text(), text.inputTokens(), text.outputTokens());
      case TurnStub.ToolCalls toolCalls ->
          Turn.toolCalls(
              toolCalls.text(),
              toolCalls.inputTokens(),
              toolCalls.outputTokens(),
              toolCalls.toolCalls().stream()
                  .map(tc -> ToolCall.of(tc.id(), tc.name(), tc.argumentsJson()))
                  .toArray(ToolCall[]::new));
    };
  }

  private static Delta contentDelta(String text) {
    final Delta.Builder delta = Delta.builder().role(Role.ASSISTANT);
    if (text != null && !text.isBlank()) {
      delta.content(text);
    }
    return delta.build();
  }

  private static Delta toolCallDelta(int index, ToolCall tc) {
    return Delta.builder()
        .toolCalls(
            List.of(
                Delta.ToolCall.builder()
                    .index(index)
                    .id(tc.id())
                    .type(Delta.ToolCall.Type.FUNCTION)
                    .function(
                        Delta.ToolCall.Function.builder()
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

  private static String stateName(int index) {
    return "turn-" + index;
  }

  /** A single assistant turn in the conversation. */
  public static final class Turn {

    private final String text;
    private final List<ToolCall> toolCalls;
    private final int promptTokens;
    private final int completionTokens;
    private Duration requestDelay;

    private Turn(String text, List<ToolCall> toolCalls, int promptTokens, int completionTokens) {
      this.text = text;
      this.toolCalls = toolCalls;
      this.promptTokens = promptTokens;
      this.completionTokens = completionTokens;
    }

    /** A plain text response that ends the turn ({@code finish_reason: "stop"}). */
    public static Turn text(String text, int promptTokens, int completionTokens) {
      return new Turn(text, List.of(), promptTokens, completionTokens);
    }

    /**
     * A tool-call response ({@code finish_reason: "tool_calls"}). The optional assistant text is
     * included alongside the tool calls, matching how providers return reasoning text with tool
     * calls.
     */
    public static Turn toolCalls(
        String text, int promptTokens, int completionTokens, ToolCall... toolCalls) {
      return new Turn(text, Arrays.asList(toolCalls), promptTokens, completionTokens);
    }

    public Turn withRequestDelay(Duration duration) {
      this.requestDelay = duration;
      return this;
    }

    private String toSseBody() {
      final String id = "chatcmpl-test-" + TURN_COUNTER.getAndIncrement();
      final boolean hasToolCalls = !toolCalls.isEmpty();

      final StringBuilder body = new StringBuilder();
      // 1. role + content delta (content omitted when there are only tool calls)
      body.append(dataLine(chunk(id, contentDelta(text), null)));
      // 2. one tool-call delta chunk per tool call
      int i = 0;
      for (final ToolCall tc : toolCalls) {
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
  }

  /** A tool call requested by the stubbed model. */
  public record ToolCall(String id, String name, String argumentsJson) {
    public static ToolCall of(String id, String name, String argumentsJson) {
      return new ToolCall(id, name, argumentsJson);
    }
  }
}
