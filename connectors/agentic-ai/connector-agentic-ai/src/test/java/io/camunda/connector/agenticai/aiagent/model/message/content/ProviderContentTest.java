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
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void roundTripsOpaquePayloadVerbatim() throws Exception {
    var original =
        new ProviderContent(
            "anthropic",
            "code_execution_tool_result",
            Map.of(
                "type",
                "code_execution_tool_result",
                "tool_use_id",
                "srvtoolu_1",
                "content",
                Map.of("stdout", "hi")),
            null);

    var json = mapper.writeValueAsString(original);
    var restored = mapper.readValue(json, ProviderContent.class);

    assertThat(restored).isEqualTo(original);
  }

  @Test
  void deserializesThroughContentBaseTypeWithProviderDiscriminator() throws Exception {
    var original =
        new ProviderContent(
            "anthropic",
            "code_execution_tool_result",
            Map.of(
                "type",
                "code_execution_tool_result",
                "tool_use_id",
                "srvtoolu_1",
                "content",
                Map.of("stdout", "hi")),
            null);

    var json = mapper.writeValueAsString(original);
    assertThat(json).contains("\"type\":\"provider\"");

    var restored = mapper.readValue(json, Content.class);

    assertThat(restored).isInstanceOf(ProviderContent.class);
    assertThat(restored).isEqualTo(original);
  }
}
