/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

class AgentResponseTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Nested
  class ToolCall {

    private static final AgentResponse.ToolCall TOOL_CALL =
        new AgentResponse.ToolCall("123456", "toolName", Map.of("foo", "bar", "baz", "qux"));
    private static final String TOOL_CALL_JSON_VALUE =
        """
            {
              "_meta": {
                "id": "123456",
                "name": "toolName"
              },
              "foo": "bar",
              "baz": "qux"
            }
            """;

    @Test
    void canBeSerialized() throws Exception {
      final var json = objectMapper.writeValueAsString(TOOL_CALL);
      JSONAssert.assertEquals(TOOL_CALL_JSON_VALUE, json, true);
    }

    @Test
    void canBeDeserialized() throws Exception {
      final var toolCall =
          objectMapper.readValue(TOOL_CALL_JSON_VALUE, AgentResponse.ToolCall.class);
      assertThat(toolCall).usingRecursiveComparison().isEqualTo(TOOL_CALL);
    }
  }
}
