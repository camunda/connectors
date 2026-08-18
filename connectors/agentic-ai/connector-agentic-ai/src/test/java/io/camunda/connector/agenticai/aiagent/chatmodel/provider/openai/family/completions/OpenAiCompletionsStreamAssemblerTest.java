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
import com.openai.core.ObjectMappers;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The vendor SDK's {@link com.openai.helpers.ChatCompletionAccumulator} accumulates a full sequence
 * of {@code ChatCompletionChunk} delta events; for this unit test's purposes a minimal two-chunk
 * sequence (a role/content delta chunk followed by a finish_reason chunk) is sufficient to exercise
 * the assembler's wiring without hand-rolling every delta a real streamed call would emit. Chunk
 * fixtures are deserialized from JSON (rather than built via the SDK's response-side builders,
 * which require every field -- including Optional-typed ones -- to be set explicitly) using the
 * SDK's own {@link ObjectMappers#jsonMapper()}, mirroring {@code
 * OpenAiCompletionsResponseConverterTest} and the Responses family's {@code
 * OpenAiResponsesStreamAssemblerTest}.
 */
class OpenAiCompletionsStreamAssemblerTest {

  private final OpenAiCompletionsStreamAssembler assembler =
      OpenAiCompletionsStreamAssembler.accumulating();

  private static ChatCompletionChunk chunkFromJson(String deltaJson, String finishReasonJson) {
    final String json =
        """
        {
          "id": "chatcmpl_123",
          "object": "chat.completion.chunk",
          "created": 0,
          "model": "gpt-4o",
          "choices": [
            {
              "index": 0,
              "finish_reason": %s,
              "delta": %s
            }
          ]
        }
        """
            .formatted(finishReasonJson, deltaJson);
    try {
      return ObjectMappers.jsonMapper().readValue(json, ChatCompletionChunk.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse test fixture JSON", e);
    }
  }

  @Test
  void assemblesChatCompletionFromStreamedChunks() {
    final ChatCompletionChunk roleAndContentChunk =
        chunkFromJson(
            """
            {"role": "assistant", "content": "Hello there"}
            """,
            "null");
    final ChatCompletionChunk finishChunk =
        chunkFromJson(
            """
            {}
            """,
            "\"stop\"");

    @SuppressWarnings("unchecked")
    final StreamResponse<ChatCompletionChunk> stream = mock(StreamResponse.class);
    when(stream.stream()).thenReturn(Stream.of(roleAndContentChunk, finishChunk));

    final ChatCompletion assembled = assembler.assemble(stream);

    assertThat(assembled.id()).isEqualTo("chatcmpl_123");
    assertThat(assembled.choices()).hasSize(1);
    assertThat(assembled.choices().get(0).message().content()).contains("Hello there");
  }
}
