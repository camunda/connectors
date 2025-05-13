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
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import io.camunda.connector.agenticai.jsonschema.JsonSchemaElementModule;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

class ToolSpecificationConverterTest {

  private ToolSpecificationConverter converter;

  @BeforeEach
  void setUp() {
    converter = new ToolSpecificationConverter();
  }

  @Test
  void shouldConvertToAndFromMap() {
    // Given
    JsonObjectSchema parameters = createSimpleObjectSchema();
    ToolSpecification original = ToolSpecification.builder()
        .name("testTool")
        .description("A test tool")
        .parameters(parameters)
        .build();

    // When
    Map<String, Object> map = converter.asMap(original);
    ToolSpecification result = converter.fromMap(map);

    // Then
    assertThat(result.name()).isEqualTo(original.name());
    assertThat(result.description()).isEqualTo(original.description());
    assertThat(result.parameters()).isNotNull();
    assertThat(result.parameters()).isInstanceOf(JsonObjectSchema.class);

    JsonObjectSchema resultParams = (JsonObjectSchema) result.parameters();
    JsonObjectSchema originalParams = (JsonObjectSchema) original.parameters();

    assertThat(resultParams.properties()).containsKey("message");
    assertThat(resultParams.required()).contains("message");
    assertThat(resultParams.additionalProperties()).isEqualTo(originalParams.additionalProperties());
  }

  @Test
  void shouldConvertToAndFromString() throws JsonProcessingException {
    // Given
    JsonObjectSchema parameters = createSimpleObjectSchema();
    ToolSpecification original = ToolSpecification.builder()
        .name("testTool")
        .description("A test tool")
        .parameters(parameters)
        .build();

    // When
    String json = converter.asString(original);
    ToolSpecification result = converter.fromString(json);

    // Then
    assertThat(result.name()).isEqualTo(original.name());
    assertThat(result.description()).isEqualTo(original.description());
    assertThat(result.parameters()).isNotNull();
    assertThat(result.parameters()).isInstanceOf(JsonObjectSchema.class);

    JsonObjectSchema resultParams = (JsonObjectSchema) result.parameters();
    JsonObjectSchema originalParams = (JsonObjectSchema) original.parameters();

    assertThat(resultParams.properties()).containsKey("message");
    assertThat(resultParams.required()).contains("message");
    assertThat(resultParams.additionalProperties()).isEqualTo(originalParams.additionalProperties());
  }

  @Test
  void shouldRoundTripViaString() throws Exception {
    // Given
    JsonObjectSchema parameters = createSimpleObjectSchema();
    ToolSpecification original = ToolSpecification.builder()
        .name("testTool")
        .description("A test tool")
        .parameters(parameters)
        .build();

    // When
    String json = converter.asString(original);
    ToolSpecification deserialized = converter.fromString(json);
    String jsonAfterRoundTrip = converter.asString(deserialized);

    // Then
    JSONAssert.assertEquals(json, jsonAfterRoundTrip, true);
  }

  @Test
  void shouldRoundTripViaMap() throws JsonProcessingException {
    // Given
    JsonObjectSchema parameters = createSimpleObjectSchema();
    ToolSpecification original = ToolSpecification.builder()
        .name("testTool")
        .description("A test tool")
        .parameters(parameters)
        .build();

    // When
    Map<String, Object> map = converter.asMap(original);
    ToolSpecification deserialized = converter.fromMap(map);
    Map<String, Object> mapAfterRoundTrip = converter.asMap(deserialized);

    // Then
    assertThat(mapAfterRoundTrip).containsAllEntriesOf(map);
    assertThat(map).containsAllEntriesOf(mapAfterRoundTrip);
  }

  @Test
  void shouldRoundTripFromStringToMapAndBack() throws Exception {
    // Given
    JsonObjectSchema parameters = createSimpleObjectSchema();
    ToolSpecification original = ToolSpecification.builder()
        .name("testTool")
        .description("A test tool")
        .parameters(parameters)
        .build();

    // When
    String json = converter.asString(original);
    ToolSpecification fromString = converter.fromString(json);
    Map<String, Object> map = converter.asMap(fromString);
    ToolSpecification fromMap = converter.fromMap(map);
    String jsonAfterRoundTrip = converter.asString(fromMap);

    // Then
    JSONAssert.assertEquals(json, jsonAfterRoundTrip, true);
  }

  @Test
  void shouldRoundTripFromMapToStringAndBack() throws JsonProcessingException {
    // Given
    JsonObjectSchema parameters = createSimpleObjectSchema();
    ToolSpecification original = ToolSpecification.builder()
        .name("testTool")
        .description("A test tool")
        .parameters(parameters)
        .build();

    // When
    Map<String, Object> map = converter.asMap(original);
    ToolSpecification fromMap = converter.fromMap(map);
    String json = converter.asString(fromMap);
    ToolSpecification fromString = converter.fromString(json);
    Map<String, Object> mapAfterRoundTrip = converter.asMap(fromString);

    // Then
    assertThat(mapAfterRoundTrip).containsAllEntriesOf(map);
    assertThat(map).containsAllEntriesOf(mapAfterRoundTrip);
  }

  @Test
  void shouldDeserializeFromMultilineString() throws JsonProcessingException {
    // Given
    String jsonString = """
        {
          "name": "weatherTool",
          "description": "Get current weather in a given location",
          "parameters": {
            "type": "object",
            "properties": {
              "location": {
                "type": "string",
                "description": "The city and state, e.g. San Francisco, CA"
              },
              "unit": {
                "type": "string",
                "enum": ["celsius", "fahrenheit"],
                "description": "The unit of temperature"
              }
            },
            "required": ["location"],
            "additionalProperties": false
          }
        }
        """;

    // When
    ToolSpecification toolSpec = converter.fromString(jsonString);

    // Then
    assertThat(toolSpec.name()).isEqualTo("weatherTool");
    assertThat(toolSpec.description()).isEqualTo("Get current weather in a given location");
    assertThat(toolSpec.parameters()).isInstanceOf(JsonObjectSchema.class);

    JsonObjectSchema params = (JsonObjectSchema) toolSpec.parameters();
    assertThat(params.properties()).containsKeys("location", "unit");
    assertThat(params.required()).contains("location");
    assertThat(params.additionalProperties()).isFalse();
  }

  @Test
  void shouldRoundTripFromMultilineString() throws Exception {
    // Given
    String jsonString = """
        {
          "name": "weatherTool",
          "description": "Get current weather in a given location",
          "parameters": {
            "type": "object",
            "properties": {
              "location": {
                "type": "string",
                "description": "The city and state, e.g. San Francisco, CA"
              },
              "unit": {
                "type": "string",
                "enum": ["celsius", "fahrenheit"],
                "description": "The unit of temperature"
              }
            },
            "required": ["location"],
            "additionalProperties": false
          }
        }
        """;

    // When
    ToolSpecification toolSpec = converter.fromString(jsonString);
    String serialized = converter.asString(toolSpec);
    ToolSpecification deserializedAgain = converter.fromString(serialized);
    String serializedAgain = converter.asString(deserializedAgain);

    // Then
    JSONAssert.assertEquals(serialized, serializedAgain, true);

    // Verify the content is preserved
    assertThat(deserializedAgain.name()).isEqualTo("weatherTool");
    assertThat(deserializedAgain.description()).isEqualTo("Get current weather in a given location");
    assertThat(deserializedAgain.parameters()).isInstanceOf(JsonObjectSchema.class);

    JsonObjectSchema params = (JsonObjectSchema) deserializedAgain.parameters();
    assertThat(params.properties()).containsKeys("location", "unit");
    assertThat(params.required()).contains("location");
    assertThat(params.additionalProperties()).isFalse();
  }

  private JsonObjectSchema createSimpleObjectSchema() {
    JsonStringSchema messageSchema = JsonStringSchema.builder()
        .description("Message to echo")
        .build();

    return JsonObjectSchema.builder()
        .addProperty("message", messageSchema)
        .required("message")
        .additionalProperties(false)
        .build();
  }
}
