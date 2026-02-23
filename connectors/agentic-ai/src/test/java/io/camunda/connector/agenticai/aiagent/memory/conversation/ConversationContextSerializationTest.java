/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationContextSerializationTest {

  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();

  @Test
  void inProcessContext_roundTripsWithVersion() throws Exception {
    final var context =
        InProcessConversationContext.builder("conv-1").version(5).messages(List.of()).build();

    final var json = objectMapper.writeValueAsString(context);
    assertThat(json).contains("\"version\":5");
    assertThat(json).contains("\"type\":\"in-process\"");

    final var deserialized = objectMapper.readValue(json, ConversationContext.class);

    assertThat(deserialized).isInstanceOf(InProcessConversationContext.class);
    assertThat(deserialized.conversationId()).isEqualTo("conv-1");
    assertThat(deserialized.version()).isEqualTo(5);
    assertThat(((InProcessConversationContext) deserialized).messages()).isEmpty();
  }

  @Test
  void inProcessContext_deserializesWithoutVersionField() throws Exception {
    final var json =
        """
        {"type": "in-process", "conversationId": "conv-1", "messages": []}
        """;

    final var deserialized = objectMapper.readValue(json, ConversationContext.class);

    assertThat(deserialized).isInstanceOf(InProcessConversationContext.class);
    assertThat(deserialized.conversationId()).isEqualTo("conv-1");
    assertThat(deserialized.version()).isEqualTo(0L);
  }

  @Test
  void inProcessContext_versionDefaultsToZeroForNewBuilds() {
    final var context = InProcessConversationContext.builder("conv-1").messages(List.of()).build();

    assertThat(context.version()).isEqualTo(0L);
  }

  @Test
  void inProcessContext_ignoresUnknownFields() throws Exception {
    final var json =
        """
        {"type": "in-process", "conversationId": "conv-1", "messages": [], "unknownField": "value"}
        """;

    final var deserialized = objectMapper.readValue(json, ConversationContext.class);

    assertThat(deserialized).isInstanceOf(InProcessConversationContext.class);
    assertThat(deserialized.conversationId()).isEqualTo("conv-1");
  }
}
