/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import io.camunda.connector.agenticai.jsonschema.JsonSchemaElementModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

class ToolSpecificationMixinTest {

  private ObjectMapper objectMapper;
  private ToolSpecificationConverter toolSpecificationConverter;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JsonSchemaElementModule());
    objectMapper.addMixIn(ToolSpecification.class, ToolSpecificationMixin.class);
    toolSpecificationConverter = new ToolSpecificationConverter(objectMapper);
  }

  @Test
  void shouldSerializeAndDeserializeToolSpecification() throws Exception {
    // Given
    JsonObjectSchema parameters = createSimpleObjectSchema();
    ToolSpecification toolSpecification =
        ToolSpecification.builder()
            .name("testTool")
            .description("A test tool")
            .parameters(parameters)
            .build();

    // When
    String serialized = toolSpecificationConverter.asString(toolSpecification);

    // Then
    // Expected JSON structure
    String expectedJson =
        """
        {
          "name": "testTool",
          "description": "A test tool",
          "parameters": {
            "type": "object",
            "properties": {
              "message": {
                "type": "string",
                "description": "Message to echo"
              }
            },
            "required": [
              "message"
            ],
            "additionalProperties": false
          }
        }
        """;

    JSONAssert.assertEquals(expectedJson, serialized, true);
  }

  @Test
  void shouldUseConverterToSerializeAndDeserialize() throws JsonProcessingException {
    // Given
    JsonObjectSchema parameters = createSimpleObjectSchema();
    ToolSpecification toolSpecification =
        ToolSpecification.builder()
            .name("testTool")
            .description("A test tool")
            .parameters(parameters)
            .build();

    // When
    String serialized = toolSpecificationConverter.asString(toolSpecification);

    // Then
    assertThat(serialized).contains("\"name\":\"testTool\"");
    assertThat(serialized).contains("\"description\":\"A test tool\"");
    assertThat(serialized).contains("\"parameters\":");
  }

  private JsonObjectSchema createSimpleObjectSchema() {
    JsonStringSchema messageSchema =
        JsonStringSchema.builder().description("Message to echo").build();

    return JsonObjectSchema.builder()
        .addProperty("message", messageSchema)
        .required("message")
        .additionalProperties(false)
        .build();
  }
}
