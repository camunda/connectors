/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.TestMessagesFixture;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageSerializationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void messagesCanBeSerializedAndDeserialized() throws Exception {
    final var wrapper = new MessagesWrapper(TestMessagesFixture.testMessages());
    final var serialized =
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper);

    final var deserialized = objectMapper.readValue(serialized, MessagesWrapper.class);

    assertThat(deserialized).usingRecursiveComparison().isEqualTo(wrapper);

    assertThat(deserialized.messages())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactlyElementsOf(TestMessagesFixture.testMessages());
  }

  @Test
  void messagesCanBeDeserializedFromFixture() throws IOException {
    // test that representation stored in fixture file is stable
    final var fromFile = TestMessagesFixture.testMessagesFromFile();

    assertThat(fromFile)
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactlyElementsOf(TestMessagesFixture.testMessages());
  }

  private record MessagesWrapper(List<Message> messages) {}
}
