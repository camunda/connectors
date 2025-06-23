/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.TestMessagesFixture;
import org.junit.jupiter.api.Test;

class InProcessConversationContextTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void canBeSerializedAndDeserialized() throws Exception {
    final var conversationRecord =
        InProcessConversationContext.builder()
            .id("test-conversation")
            .messages(TestMessagesFixture.testMessages())
            .build();

    final var serialized =
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(conversationRecord);

    final var deserialized = objectMapper.readValue(serialized, InProcessConversationContext.class);

    assertThat(deserialized).usingRecursiveComparison().isEqualTo(conversationRecord);
  }
}
