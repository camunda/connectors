/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.openai.core.ObjectMappers;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link ChunkedContentChatCompletionAccumulator} directly (rather than through {@link
 * OpenAiCompletionsStreamAssembler#chunkedContentAware()}) since it is the class carrying the
 * actual accumulation logic. The chunked-content fixtures mirror the exact delta shape observed in
 * a real streamed {@code magistral-medium-latest} response with {@code reasoning_effort=high}: a
 * run of array deltas each carrying one {@code thinking} chunk fragment, a single transition delta
 * whose array carries both the closing thinking fragment (marked {@code closed: true}) and the
 * opening {@code text} chunk fragment together, then a run of plain-string deltas for the rest of
 * the final answer.
 */
class ChunkedContentChatCompletionAccumulatorTest {

  private final OpenAiCompletionsStreamAssembler assembler =
      OpenAiCompletionsStreamAssembler.chunkedContentAware();

  private static ChatCompletionChunk deltaChunk(String deltaJson) {
    return chunkFromJson(deltaJson, "null", "null");
  }

  private static ChatCompletionChunk finishChunk(String finishReasonJson) {
    return chunkFromJson("{}", finishReasonJson, "null");
  }

  private static ChatCompletionChunk finishChunkWithUsage(
      String finishReasonJson, String usageJson) {
    return chunkFromJson("{}", finishReasonJson, usageJson);
  }

  private static ChatCompletionChunk usageChunk(String usageJson) {
    final String json =
        """
        {
          "id": "chatcmpl_123",
          "object": "chat.completion.chunk",
          "created": 0,
          "model": "magistral-medium-latest",
          "choices": [],
          "usage": %s
        }
        """
            .formatted(usageJson);
    return fromJson(json);
  }

  private static ChatCompletionChunk chunkFromJson(
      String deltaJson, String finishReasonJson, String usageJson) {
    final String json =
        """
        {
          "id": "chatcmpl_123",
          "object": "chat.completion.chunk",
          "created": 0,
          "model": "magistral-medium-latest",
          "choices": [
            {
              "index": 0,
              "finish_reason": %s,
              "delta": %s
            }
          ],
          "usage": %s
        }
        """
            .formatted(finishReasonJson, deltaJson, usageJson);
    return fromJson(json);
  }

  private static ChatCompletionChunk fromJson(String json) {
    try {
      return ObjectMappers.jsonMapper().readValue(json, ChatCompletionChunk.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse test fixture JSON", e);
    }
  }

  private static ChatCompletion assemble(
      OpenAiCompletionsStreamAssembler assembler, ChatCompletionChunk... chunks) {
    @SuppressWarnings("unchecked")
    final StreamResponse<ChatCompletionChunk> stream = mock(StreamResponse.class);
    when(stream.stream()).thenReturn(Stream.of(chunks));
    return assembler.assemble(stream);
  }

  @Test
  void assemblesPlainStringContentIdenticallyToTheVendorAccumulator() {
    final ChatCompletion assembled =
        assemble(
            assembler,
            deltaChunk(
                """
                {"role": "assistant", "content": "Hello there"}
                """),
            finishChunk("\"stop\""));

    assertThat(assembled.id()).isEqualTo("chatcmpl_123");
    assertThat(assembled.choices().get(0).message().content()).contains("Hello there");
    assertThat(assembled.choices().get(0).message()._content().asArray()).isEmpty();
  }

  @Test
  void assemblesChunkedThinkingAndTextContentFromFragmentedArrayDeltas() {
    final ChatCompletion assembled =
        assemble(
            assembler,
            deltaChunk(
                """
                {"role": "assistant", "content": [
                  {"type": "thinking", "thinking": [{"type": "text", "text": "5 + 7 "}]}
                ]}
                """),
            deltaChunk(
                """
                {"content": [
                  {"type": "thinking", "thinking": [{"type": "text", "text": "is 12"}]}
                ]}
                """),
            // The transition delta: the thinking chunk closes and the text chunk opens together.
            deltaChunk(
                """
                {"content": [
                  {"type": "thinking", "thinking": [{"type": "text", "text": "."}], "closed": true},
                  {"type": "text", "text": "The"}
                ]}
                """),
            // Real traffic reverts to plain-string deltas once the text chunk has opened.
            deltaChunk(
                """
                {"content": " answer is 12."}
                """),
            finishChunk("\"stop\""));

    final var message = assembled.choices().get(0).message();
    // The typed accessor throws on a chunked array; the raw accessor is how both this connector's
    // OpenAiCompletionsResponseConverter and this assertion read it.
    final var chunks = message._content().asArray().orElseThrow();
    assertThat(chunks).hasSize(2);

    final var thinkingChunk = chunks.get(0).convert(new TypeReference<Map<String, Object>>() {});
    assertThat(thinkingChunk.get("type")).isEqualTo("thinking");
    assertThat(thinkingChunk.get("closed")).isEqualTo(true);
    assertThat(thinkingChunk.get("thinking"))
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .singleElement()
        .satisfies(item -> assertThat(((Map<?, ?>) item).get("text")).isEqualTo("5 + 7 is 12."));

    final var textChunk = chunks.get(1).convert(new TypeReference<Map<String, Object>>() {});
    assertThat(textChunk.get("type")).isEqualTo("text");
    assertThat(textChunk.get("text")).isEqualTo("The answer is 12.");
  }

  @Test
  void finalizesAnOpenThinkingChunkEvenWithoutATrailingTextChunk() {
    final ChatCompletion assembled =
        assemble(
            assembler,
            deltaChunk(
                """
                {"role": "assistant", "content": [
                  {"type": "thinking", "thinking": [{"type": "text", "text": "still thinking"}]}
                ]}
                """),
            finishChunk("\"length\""));

    final var chunks = assembled.choices().get(0).message()._content().asArray().orElseThrow();
    assertThat(chunks).hasSize(1);
    final var thinkingChunk = chunks.get(0).convert(new TypeReference<Map<String, Object>>() {});
    assertThat(thinkingChunk.get("type")).isEqualTo("thinking");
  }

  @Test
  void accumulatesToolCallArgumentsAcrossDeltasByIndex() {
    final ChatCompletion assembled =
        assemble(
            assembler,
            deltaChunk(
                """
                {"role": "assistant", "tool_calls": [
                  {"index": 0, "id": "call_1", "type": "function",
                   "function": {"name": "get_weather", "arguments": "{\\"city\\":"}}
                ]}
                """),
            deltaChunk(
                """
                {"tool_calls": [
                  {"index": 0, "function": {"arguments": "\\"Berlin\\"}"}}
                ]}
                """),
            finishChunk("\"tool_calls\""));

    final var toolCalls = assembled.choices().get(0).message().toolCalls().orElseThrow();
    assertThat(toolCalls).hasSize(1);
    final var function = toolCalls.get(0).asFunction();
    assertThat(function.id()).isEqualTo("call_1");
    assertThat(function.function().name()).isEqualTo("get_weather");
    assertThat(function.function().arguments()).isEqualTo("{\"city\":\"Berlin\"}");
  }

  @Test
  void includesUsageFromTheFinalUsageOnlyChunk() {
    final ChatCompletion assembled =
        assemble(
            assembler,
            deltaChunk(
                """
                {"role": "assistant", "content": "Hi"}
                """),
            finishChunk("\"stop\""),
            usageChunk(
                """
                {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                """));

    assertThat(assembled.usage()).isPresent();
    assertThat(assembled.usage().orElseThrow().promptTokens()).isEqualTo(10);
  }

  @Test
  void assemblesSuccessfullyWhenUsageArrivesOnTheSameChunkAsTheFinishReason() {
    // Mistral's wire shape, unlike OpenAI's separate trailing usage-only chunk: usage is reported
    // on the very same chunk that also carries the closing delta and finishReason.
    final ChatCompletion assembled =
        assemble(
            assembler,
            deltaChunk(
                """
                {"role": "assistant", "content": "Hi"}
                """),
            finishChunkWithUsage(
                "\"stop\"",
                """
                {"prompt_tokens": 21, "completion_tokens": 3, "total_tokens": 24}
                """));

    assertThat(assembled.choices()).hasSize(1);
    assertThat(assembled.choices().get(0).message().content()).contains("Hi");
    assertThat(assembled.usage()).isPresent();
    assertThat(assembled.usage().orElseThrow().promptTokens()).isEqualTo(21);
  }
}
