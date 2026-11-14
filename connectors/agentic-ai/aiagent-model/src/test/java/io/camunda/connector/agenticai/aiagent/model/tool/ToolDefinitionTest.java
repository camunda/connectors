/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolDefinitionTest {

  private final ObjectMapper objectMapper = TestObjectMapperSupplier.INSTANCE;

  @Test
  void aProcessDefinitionIsSerializedWithoutKind() throws Exception {
    final var definition = new ToolDefinition("lookup", "Looks up", Map.of("type", "object"));

    final var json = objectMapper.writeValueAsString(definition);

    assertThat(json)
        .isEqualTo(
            "{\"name\":\"lookup\",\"description\":\"Looks up\",\"inputSchema\":{\"type\":\"object\"}}");
    assertThat(objectMapper.readValue(json, ToolDefinition.class)).isEqualTo(definition);
  }

  @Test
  void aRuntimeDefinitionIsSerializedWithKindAndRoundTrips() throws Exception {
    final var definition = ToolDefinition.runtime("lookup", "Looks up", Map.of("type", "object"));

    final var json = objectMapper.writeValueAsString(definition);

    assertThat(json).contains("\"kind\":\"runtime\"");
    assertThat(objectMapper.readValue(json, ToolDefinition.class)).isEqualTo(definition);
  }

  @Test
  void jsonWithoutKindReadsAsProcess() throws Exception {
    final var json = "{\"name\": \"lookup\", \"inputSchema\": {}}";

    assertThat(objectMapper.readValue(json, ToolDefinition.class).kind())
        .isEqualTo(ToolKind.PROCESS);
  }

  @Test
  void theBuilderAndTheConstructorDefaultToProcess() {
    assertThat(ToolDefinition.builder().name("n").inputSchema(Map.of()).build().kind())
        .isEqualTo(ToolKind.PROCESS);
    assertThat(new ToolDefinition("n", null, Map.of()).kind()).isEqualTo(ToolKind.PROCESS);
    assertThat(ToolDefinition.runtime("n", null, Map.of()).kind()).isEqualTo(ToolKind.RUNTIME);
  }
}
