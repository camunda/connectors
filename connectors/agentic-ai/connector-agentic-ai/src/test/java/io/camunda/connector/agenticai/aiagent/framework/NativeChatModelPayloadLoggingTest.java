/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class NativeChatModelPayloadLoggingTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void serializesPayloadAsJson() {
    final var payload = new TestPayload("value");

    final String json = NativeChatModelPayloadLogging.toJson(mapper, payload);

    assertThat(json).isEqualTo("{\"field\":\"value\"}");
  }

  @Test
  void returnsFallbackStringInsteadOfThrowingWhenSerializationFails() throws Exception {
    final ObjectMapper failingMapper = mock(ObjectMapper.class);
    when(failingMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new JsonProcessingException("boom") {});
    final var payload = new TestPayload("value");

    final String result = NativeChatModelPayloadLogging.toJson(failingMapper, payload);

    assertThat(result).contains("TestPayload").contains("boom");
  }

  private record TestPayload(String field) {}
}
