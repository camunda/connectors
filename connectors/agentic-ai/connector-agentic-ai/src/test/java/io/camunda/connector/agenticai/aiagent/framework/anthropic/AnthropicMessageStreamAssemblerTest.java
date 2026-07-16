/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.core.ObjectMappers;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The vendor SDK's {@link com.anthropic.helpers.BetaMessageAccumulator} accumulates a full sequence
 * of raw stream events (message_start, content_block_start, content_block_delta,
 * content_block_stop, message_delta, message_stop); for this unit test's purposes a minimal
 * sequence covering exactly those event kinds is sufficient to exercise the assembler's wiring
 * without hand-rolling every event variant a real streamed call would emit. Event fixtures are
 * deserialized from JSON using the SDK's own {@link ObjectMappers#jsonMapper()}, mirroring {@code
 * OpenAiResponsesStreamAssemblerTest} / {@code OpenAiCompletionsStreamAssemblerTest}.
 */
class AnthropicMessageStreamAssemblerTest {

  private final AnthropicMessageStreamAssembler assembler =
      AnthropicMessageStreamAssembler.accumulating();

  private static BetaRawMessageStreamEvent eventFromJson(String json) {
    try {
      return ObjectMappers.jsonMapper().readValue(json, BetaRawMessageStreamEvent.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse test fixture JSON", e);
    }
  }

  @Test
  void assemblesMessageFromStreamedEvents() {
    final BetaRawMessageStreamEvent messageStart =
        eventFromJson(
            """
            {
              "type": "message_start",
              "message": {
                "id": "msg_123",
                "type": "message",
                "role": "assistant",
                "model": "claude-test-model",
                "content": [],
                "stop_reason": null,
                "stop_sequence": null,
                "usage": {"input_tokens": 10, "output_tokens": 0}
              }
            }
            """);
    final BetaRawMessageStreamEvent contentBlockStart =
        eventFromJson(
            """
            {
              "type": "content_block_start",
              "index": 0,
              "content_block": {"type": "text", "text": ""}
            }
            """);
    final BetaRawMessageStreamEvent contentBlockDelta =
        eventFromJson(
            """
            {
              "type": "content_block_delta",
              "index": 0,
              "delta": {"type": "text_delta", "text": "Hello there"}
            }
            """);
    final BetaRawMessageStreamEvent contentBlockStop =
        eventFromJson(
            """
            {
              "type": "content_block_stop",
              "index": 0
            }
            """);
    final BetaRawMessageStreamEvent messageDelta =
        eventFromJson(
            """
            {
              "type": "message_delta",
              "delta": {"stop_reason": "end_turn", "stop_sequence": null},
              "usage": {"output_tokens": 5}
            }
            """);
    final BetaRawMessageStreamEvent messageStop =
        eventFromJson(
            """
            {
              "type": "message_stop"
            }
            """);

    @SuppressWarnings("unchecked")
    final StreamResponse<BetaRawMessageStreamEvent> stream = mock(StreamResponse.class);
    when(stream.stream())
        .thenReturn(
            Stream.of(
                messageStart,
                contentBlockStart,
                contentBlockDelta,
                contentBlockStop,
                messageDelta,
                messageStop));

    final BetaMessage assembled = assembler.assemble(stream);

    assertThat(assembled.id()).isEqualTo("msg_123");
    assertThat(assembled.model().asString()).isEqualTo("claude-test-model");
    assertThat(assembled.content()).hasSize(1);
    assertThat(assembled.content().get(0).text().orElseThrow().text()).isEqualTo("Hello there");
    assertThat(assembled.stopReason().orElseThrow().asString()).isEqualTo("end_turn");
    assertThat(assembled.usage().outputTokens()).isEqualTo(5);
  }
}
