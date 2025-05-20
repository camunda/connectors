/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContentBlock.textContent;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.message.tools.ToolCallRequest;
import io.camunda.connector.agenticai.aiagent.model.message.tools.ToolCallResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageSerializationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void messagesCanBeSerializedAndDeserialized() throws Exception {
    final List<Message> messages =
        List.of(
            SystemMessage.builder()
                .content(List.of(textContent("You are a helpful assistant.")))
                .build(),
            UserMessage.builder()
                .name("user1")
                .content(List.of(textContent("What is the time?")))
                .build(),
            AssistantMessage.builder()
                .content(
                    List.of(textContent("<thinking>I should call the get_time tool</thinking>")))
                .toolCallRequests(
                    List.of(
                        ToolCallRequest.builder()
                            .id("123456")
                            .name("get_time")
                            .arguments(Map.of("when", "now"))
                            .build()))
                .build(),
            ToolCallResultMessage.builder()
                .results(
                    List.of(
                        ToolCallResult.builder()
                            .id("123456")
                            .name("get_time")
                            .data(Map.of("when", "now", "time", "12:00"))
                            .build()))
                .build(),
            AssistantMessage.builder()
                .content(List.of(textContent("The time now is 12:00")))
                .build(),
            UserMessage.builder()
                .name("user1")
                .content(List.of(textContent("Thank you!")))
                .build());

    final var serialized =
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(messages);

    final var deserialized =
        objectMapper.readValue(serialized, new TypeReference<List<Message>>() {});

    assertThat(deserialized)
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactlyElementsOf(messages);
  }
}
