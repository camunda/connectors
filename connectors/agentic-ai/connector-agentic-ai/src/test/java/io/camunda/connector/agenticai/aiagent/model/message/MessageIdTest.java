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
import org.junit.jupiter.api.Test;

class MessageIdTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void systemMessageBackfillsIdWhenDeserializedWithoutOne() throws Exception {
    var message = objectMapper.readValue("{\"role\":\"system\",\"content\":[]}", Message.class);
    assertThat(message.id()).isNotBlank();
  }

  @Test
  void userMessageBackfillsIdWhenDeserializedWithoutOne() throws Exception {
    var message = objectMapper.readValue("{\"role\":\"user\",\"content\":[]}", Message.class);
    assertThat(message.id()).isNotBlank();
  }

  @Test
  void assistantMessageBackfillsIdWhenDeserializedWithoutOne() throws Exception {
    var message = objectMapper.readValue("{\"role\":\"assistant\",\"content\":[]}", Message.class);
    assertThat(message.id()).isNotBlank();
  }

  @Test
  void toolCallResultMessageBackfillsIdWhenDeserializedWithoutOne() throws Exception {
    var message =
        objectMapper.readValue("{\"role\":\"tool_call_result\",\"results\":[]}", Message.class);
    assertThat(message.id()).isNotBlank();
  }

  @Test
  void systemMessageGetsRandomIdWhenNotSet() {
    assertThat(SystemMessage.builder().build().id()).isNotBlank();
  }

  @Test
  void systemMessagePreservesExplicitlySetId() {
    assertThat(SystemMessage.builder().id("explicit-id").build().id()).isEqualTo("explicit-id");
  }

  @Test
  void userMessageGetsRandomIdWhenNotSet() {
    assertThat(UserMessage.builder().build().id()).isNotBlank();
  }

  @Test
  void userMessagePreservesExplicitlySetId() {
    assertThat(UserMessage.builder().id("explicit-id").build().id()).isEqualTo("explicit-id");
  }

  @Test
  void assistantMessageGetsRandomIdWhenNotSet() {
    assertThat(AssistantMessage.builder().build().id()).isNotBlank();
  }

  @Test
  void assistantMessagePreservesExplicitlySetId() {
    assertThat(AssistantMessage.builder().id("explicit-id").build().id()).isEqualTo("explicit-id");
  }

  @Test
  void toolCallResultMessageGetsRandomIdWhenNotSet() {
    assertThat(
            ToolCallResultMessage.builder().results(List.<ToolCallResultContent>of()).build().id())
        .isNotBlank();
  }

  @Test
  void toolCallResultMessagePreservesExplicitlySetId() {
    assertThat(
            ToolCallResultMessage.builder()
                .results(List.<ToolCallResultContent>of())
                .id("explicit-id")
                .build()
                .id())
        .isEqualTo("explicit-id");
  }

  @Test
  void consecutiveBuildsWithoutExplicitIdProduceDifferentIds() {
    var first = SystemMessage.builder().build().id();
    var second = SystemMessage.builder().build().id();

    assertThat(first).isNotEqualTo(second);
  }
}
