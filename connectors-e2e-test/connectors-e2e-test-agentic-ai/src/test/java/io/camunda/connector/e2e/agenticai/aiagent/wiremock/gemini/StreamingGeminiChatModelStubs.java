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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.gemini;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.google.genai.types.GenerateContentResponse;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ToolCallStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.Arrays;
import java.util.List;

/**
 * Stubs the Gemini Developer API's <strong>streaming</strong> generate-content endpoint ({@code
 * POST {baseUrl}/v1beta/models/{model}:streamGenerateContent?alt=sse}) with real Server-Sent-Events
 * framing. This is the only endpoint the Gemini provider ever calls: {@code GeminiChatModel} drives
 * {@code generateContentStream} for every request, streaming or not.
 *
 * <p>Each chunk is a real SDK {@link GenerateContentResponse} (built via {@link
 * GeminiResponseChunks}) serialized with the SDK's own mapper, so the bytes are guaranteed to
 * deserialize exactly as real Gemini traffic would. Unlike Anthropic's SSE, Gemini emits <b>no
 * {@code event:} lines and no terminator event</b> — the whole stream is bare {@code data: {...}}
 * lines, each a complete {@code GenerateContentResponse}, and the stream simply ends. {@code
 * ResponseStream#readNextJson} ignores any non-{@code data} field name, so emitting only {@code
 * data:} is both correct and what real Gemini sends.
 *
 * <p>Multi-turn conversations use the same WireMock {@link Scenario} state chaining as {@code
 * StreamingAnthropicMessagesSseChatModelStubs}: turn <i>n</i>'s mapping returns turn <i>n</i>'s
 * chunks and advances the scenario to state {@code turn-(n+1)}.
 *
 * <p>The URL is matched by <b>pattern</b>, not by a fixed model id, so a single test class can
 * drive several model ids (e.g. a Gemini 2.5 id for {@code thinkingBudget} and a Gemini 3.x id for
 * {@code thinkingLevel}) against the same stub. Tests that care about which model was requested
 * assert it from the recorded request URL via {@link
 * GeminiStreamGenerateContentRequests#requestedModel}.
 */
public final class StreamingGeminiChatModelStubs {

  /**
   * URL-path pattern for the streaming generate-content endpoint. The SDK builds {@code baseUrl +
   * "/" + apiVersion + "/" + "{model}:streamGenerateContent?alt=sse"}, where {@code {model}} is
   * resolved to {@code models/<id>} for the Gemini Developer API.
   */
  public static final String STREAM_GENERATE_CONTENT_PATH_PATTERN =
      "/v1beta/models/[^/]+:streamGenerateContent";

  /** Query parameter the SDK appends to request SSE framing ({@code ?alt=sse}). */
  public static final String ALT_QUERY_PARAM = "alt";

  public static final String ALT_SSE = "sse";

  private static final String SCENARIO_NAME = "gemini-llm-conversation-sse";

  private StreamingGeminiChatModelStubs() {}

  /**
   * A single stubbed turn as its ordered SSE chunks. More than one chunk is how a test exercises
   * the stream assembler against genuinely chunked traffic.
   */
  public record GeminiTurnStub(List<GenerateContentResponse> chunks) {

    public GeminiTurnStub {
      if (chunks.isEmpty()) {
        // GeminiContentStreamAssemblerImpl rejects an empty stream outright; a turn with no chunks
        // could never be a realistic stub.
        throw new IllegalArgumentException("A stubbed turn requires at least one chunk");
      }
    }

    public static GeminiTurnStub of(GenerateContentResponse... chunks) {
      return new GeminiTurnStub(List.of(chunks));
    }
  }

  /**
   * Wires the scenario chain returning each turn's chunks in order, with full control over how each
   * turn is split into chunks.
   */
  public static void stubTurns(GeminiTurnStub... turns) {
    if (turns.length == 0) {
      throw new IllegalArgumentException("At least one conversation turn is required");
    }
    stubScenario(Arrays.stream(turns).map(StreamingGeminiChatModelStubs::sseBody).toList());
  }

  /**
   * Wires the scenario chain from the provider-agnostic {@link TurnStub} shape, one single chunk
   * per turn. Tool calls are stubbed <b>without</b> {@code FunctionCall.id}, matching the Gemini
   * Developer API (which never populates it) and therefore exercising the converter's UUID
   * synthesis; use {@link #stubTurns} with {@link GeminiResponseChunks} when a test needs
   * server-provided ids.
   */
  public static void stubConversation(TurnStub... turns) {
    stubTurns(
        Arrays.stream(turns)
            .map(StreamingGeminiChatModelStubs::toTurnStub)
            .toArray(GeminiTurnStub[]::new));
  }

  private static GeminiTurnStub toTurnStub(TurnStub turn) {
    return switch (turn) {
      case TurnStub.Text text ->
          GeminiTurnStub.of(
              GeminiResponseChunks.text(text.text(), text.inputTokens(), text.outputTokens()));
      case TurnStub.ToolCalls toolCalls ->
          GeminiTurnStub.of(
              GeminiResponseChunks.toolCalls(
                  toolCalls.text(),
                  toolCalls.inputTokens(),
                  toolCalls.outputTokens(),
                  toolCalls.toolCalls(),
                  false));
    };
  }

  /**
   * Convenience for the single most common bespoke turn: a tool-calling turn whose {@code
   * functionCall} parts carry server-provided ids (the Vertex AI shape) rather than relying on the
   * converter's UUID synthesis.
   */
  public static GeminiTurnStub toolCallTurnWithIds(
      String text, int promptTokens, int candidateTokens, ToolCallStub... toolCalls) {
    return GeminiTurnStub.of(
        GeminiResponseChunks.toolCalls(
            text, promptTokens, candidateTokens, List.of(toolCalls), true));
  }

  /** Shared scenario-chaining plumbing: returns each pre-rendered SSE body in order. */
  private static void stubScenario(List<String> bodies) {
    for (int i = 0; i < bodies.size(); i++) {
      final String fromState = i == 0 ? Scenario.STARTED : stateName(i);

      ScenarioMappingBuilder mapping =
          post(urlPathMatching(STREAM_GENERATE_CONTENT_PATH_PATTERN))
              // Matching the query parameter too, not just the path: the SDK must request SSE
              // framing explicitly (Gemini returns a buffered JSON array without it), so a
              // regression that dropped `alt=sse` would leave this stub unmatched and fail a test
              // rather than silently still matching.
              .withQueryParam(ALT_QUERY_PARAM, equalTo(ALT_SSE))
              .inScenario(SCENARIO_NAME)
              .whenScenarioStateIs(fromState)
              .willReturn(sseResponse(bodies.get(i)));

      if (i < bodies.size() - 1) {
        mapping = mapping.willSetStateTo(stateName(i + 1));
      }

      stubFor(mapping);
    }
  }

  private static String stateName(int index) {
    return "turn-" + index;
  }

  private static ResponseDefinitionBuilder sseResponse(String body) {
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "text/event-stream")
        .withBody(body);
  }

  /**
   * Frames a turn's chunks as bare {@code data: {...}} SSE lines, Gemini's own streaming format.
   */
  private static String sseBody(GeminiTurnStub turn) {
    final StringBuilder body = new StringBuilder();
    for (final GenerateContentResponse chunk : turn.chunks()) {
      body.append("data: ").append(chunk.toJson()).append("\n\n");
    }
    return body.toString();
  }
}
