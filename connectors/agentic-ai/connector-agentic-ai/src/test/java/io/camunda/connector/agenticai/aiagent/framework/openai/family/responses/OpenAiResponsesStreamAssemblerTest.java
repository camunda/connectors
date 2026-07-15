/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai.family.responses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.openai.core.ObjectMappers;
import com.openai.core.http.StreamResponse;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCompletedEvent;
import com.openai.models.responses.ResponseCreatedEvent;
import com.openai.models.responses.ResponseStreamEvent;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The vendor SDK's {@link com.openai.helpers.ResponseAccumulator} accumulates a full sequence of
 * delta events (created, output_item.added, content deltas, output_item.done, completed, ...); for
 * this unit test's purposes a minimal two-event sequence (a {@code created} event carrying the
 * in-progress response, followed by the {@code completed} event carrying the final response) is
 * sufficient to exercise the assembler's wiring without hand-rolling every delta event a real
 * streamed call would emit. {@link Response} fixtures are deserialized from JSON (rather than built
 * via the SDK's response-side builders, which require every field -- including Optional-typed ones
 * -- to be set explicitly) using the SDK's own {@link ObjectMappers#jsonMapper()}, mirroring {@code
 * OpenAiResponsesResponseConverterTest}.
 */
class OpenAiResponsesStreamAssemblerTest {

  private final OpenAiResponsesStreamAssembler assembler =
      OpenAiResponsesStreamAssembler.accumulating();

  private static Response responseFromJson(String json) {
    try {
      return ObjectMappers.jsonMapper().readValue(json, Response.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse test fixture JSON", e);
    }
  }

  private static Response baseResponse(String outputJson) {
    return responseFromJson(
        """
        {
          "id": "resp_123",
          "object": "response",
          "created_at": 0,
          "model": "gpt-5",
          "output": %s,
          "parallel_tool_calls": true,
          "tool_choice": "auto",
          "tools": []
        }
        """
            .formatted(outputJson));
  }

  @Test
  void assemblesResponseFromStreamedEvents() {
    final Response inProgress = baseResponse("[]");
    final Response completed =
        baseResponse(
            """
            [
              {
                "type": "message",
                "id": "msg_1",
                "role": "assistant",
                "status": "completed",
                "content": [
                  {"type": "output_text", "text": "Hello there", "annotations": []}
                ]
              }
            ]
            """);

    final ResponseStreamEvent createdEvent =
        ResponseStreamEvent.ofCreated(
            ResponseCreatedEvent.builder().response(inProgress).sequenceNumber(0).build());
    final ResponseStreamEvent completedEvent =
        ResponseStreamEvent.ofCompleted(
            ResponseCompletedEvent.builder().response(completed).sequenceNumber(1).build());

    @SuppressWarnings("unchecked")
    final StreamResponse<ResponseStreamEvent> stream = mock(StreamResponse.class);
    when(stream.stream()).thenReturn(Stream.of(createdEvent, completedEvent));

    final Response assembled = assembler.assemble(stream);

    assertThat(assembled.id()).isEqualTo("resp_123");
    assertThat(assembled.output()).hasSize(1);
    assertThat(assembled.output().get(0).message().orElseThrow().content().get(0).outputText())
        .isPresent();
  }
}
