/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.TestMessagesFixture;
import java.io.IOException;
import java.util.List;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
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
        .containsExactlyElementsOf(wrapper.messages());
  }

  @Test
  void messagesCanBeDeserializedFromFixture() throws IOException {
    // fixture predates Message#id(); ids are backfilled on load, so ignore them in the content
    // comparison below, but still assert every message actually got one.
    final var fromFile = TestMessagesFixture.testMessagesFromFile();

    assertThat(fromFile).allSatisfy(message -> assertThat(message.id()).isNotBlank());

    assertThat(fromFile)
        .usingRecursiveFieldByFieldElementComparator(
            RecursiveComparisonConfiguration.builder().withIgnoredFields("id").build())
        .containsExactlyElementsOf(TestMessagesFixture.testMessages());
  }

  private record MessagesWrapper(List<Message> messages) {}
}
