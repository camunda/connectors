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

class ProviderContentTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void roundTripsAsConcreteType() throws Exception {
    final var payload = Map.of("id", "srvtoolu_01", "input", Map.of("query", "search term"));
    final var providerContent =
        new ProviderContent("anthropic", "server_tool_use", payload, Map.of("foo", "bar"));

    final var serialized = objectMapper.writeValueAsString(providerContent);
    final var deserialized = objectMapper.readValue(serialized, ProviderContent.class);

    assertThat(deserialized).isEqualTo(providerContent);
  }

  @Test
  void roundTripsThroughContentInterfaceTypeWithTypeDiscriminator() throws Exception {
    final Content providerContent =
        ProviderContent.providerContent(
            "anthropic", "server_tool_use", Map.of("id", "srvtoolu_01"));

    final var serialized = objectMapper.writeValueAsString(providerContent);

    assertThat(serialized).contains("\"type\":\"provider\"");

    final var deserialized = objectMapper.readValue(serialized, Content.class);

    assertThat(deserialized).isInstanceOf(ProviderContent.class).isEqualTo(providerContent);
  }
}
