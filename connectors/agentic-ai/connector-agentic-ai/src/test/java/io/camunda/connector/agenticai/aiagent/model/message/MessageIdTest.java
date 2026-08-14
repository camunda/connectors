/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageIdTest {

  private static final MessageId EXPLICIT_ID = MessageId.of(UUID.randomUUID());

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void systemMessageBackfillsIdWhenDeserializedWithoutOne() throws Exception {
    var message = objectMapper.readValue("{\"role\":\"system\",\"content\":[]}", Message.class);
    assertThat(message.id()).isNotNull();
  }

  @Test
  void userMessageBackfillsIdWhenDeserializedWithoutOne() throws Exception {
    var message = objectMapper.readValue("{\"role\":\"user\",\"content\":[]}", Message.class);
    assertThat(message.id()).isNotNull();
  }

  @Test
  void assistantMessageBackfillsIdWhenDeserializedWithoutOne() throws Exception {
    var message = objectMapper.readValue("{\"role\":\"assistant\",\"content\":[]}", Message.class);
    assertThat(message.id()).isNotNull();
  }

  @Test
  void toolCallResultMessageBackfillsIdWhenDeserializedWithoutOne() throws Exception {
    var message =
        objectMapper.readValue("{\"role\":\"tool_call_result\",\"results\":[]}", Message.class);
    assertThat(message.id()).isNotNull();
  }

  @Test
  void systemMessageGetsRandomIdWhenNotSet() {
    assertThat(SystemMessage.builder().build().id()).isNotNull();
  }

  @Test
  void systemMessagePreservesExplicitlySetId() {
    assertThat(SystemMessage.builder().id(EXPLICIT_ID).build().id()).isEqualTo(EXPLICIT_ID);
  }

  @Test
  void userMessageGetsRandomIdWhenNotSet() {
    assertThat(UserMessage.builder().build().id()).isNotNull();
  }

  @Test
  void userMessagePreservesExplicitlySetId() {
    assertThat(UserMessage.builder().id(EXPLICIT_ID).build().id()).isEqualTo(EXPLICIT_ID);
  }

  @Test
  void assistantMessageGetsRandomIdWhenNotSet() {
    assertThat(AssistantMessage.builder().build().id()).isNotNull();
  }

  @Test
  void assistantMessagePreservesExplicitlySetId() {
    assertThat(AssistantMessage.builder().id(EXPLICIT_ID).build().id()).isEqualTo(EXPLICIT_ID);
  }

  @Test
  void toolCallResultMessageGetsRandomIdWhenNotSet() {
    assertThat(
            ToolCallResultMessage.builder().results(List.<ToolCallResultContent>of()).build().id())
        .isNotNull();
  }

  @Test
  void toolCallResultMessagePreservesExplicitlySetId() {
    assertThat(
            ToolCallResultMessage.builder()
                .results(List.<ToolCallResultContent>of())
                .id(EXPLICIT_ID)
                .build()
                .id())
        .isEqualTo(EXPLICIT_ID);
  }

  @Test
  void consecutiveBuildsWithoutExplicitIdProduceDifferentIds() {
    var first = SystemMessage.builder().build().id();
    var second = SystemMessage.builder().build().id();

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void generatedIdIsUuidVersion7() {
    assertThat(SystemMessage.builder().build().id().value().version()).isEqualTo(7);
  }
}
