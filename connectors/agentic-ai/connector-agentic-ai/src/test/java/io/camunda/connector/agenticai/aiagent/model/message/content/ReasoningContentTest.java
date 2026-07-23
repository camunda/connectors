/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReasoningContentTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void roundTripsOpaqueProviderPayload() throws Exception {
    final var providerPayload =
        Map.of(
            "signature", "abc123", "nested", Map.of("thinking", "some internal reasoning trace"));
    final var reasoningContent = new ReasoningContent(providerPayload, Map.of("foo", "bar"));

    final var serialized = objectMapper.writeValueAsString(reasoningContent);
    final var deserialized = objectMapper.readValue(serialized, ReasoningContent.class);

    assertThat(deserialized.providerPayload()).isEqualTo(providerPayload);
    assertThat(deserialized.metadata()).isEqualTo(Map.of("foo", "bar"));
  }

  @Test
  void omitsMetadataWhenNullOrEmpty() throws Exception {
    final var nullMetadata = new ReasoningContent(Map.of("signature", "abc123"), null);
    final var emptyMetadata = new ReasoningContent(Map.of("signature", "abc123"), Map.of());

    final var serializedNullMetadata = objectMapper.writeValueAsString(nullMetadata);
    final var serializedEmptyMetadata = objectMapper.writeValueAsString(emptyMetadata);

    assertThat(serializedNullMetadata).doesNotContain("metadata");
    assertThat(serializedEmptyMetadata).doesNotContain("metadata");
  }
}
