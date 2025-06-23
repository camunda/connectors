/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import io.camunda.connector.agenticai.util.ObjectMapperConstants;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

class JsonSchemaConverterTest {

  private static final String TEST_SCHEMA =
      """
        {
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
        """;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JsonSchemaConverter jsonSchemaConverter = new JsonSchemaConverter(objectMapper);

  @Test
  void convertsSchemaToMapAndBack() throws Exception {
    final var jsonSchemaStringAsMap =
        objectMapper.readValue(TEST_SCHEMA, ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE);

    final var jsonSchema = jsonSchemaConverter.mapToSchema(jsonSchemaStringAsMap);
    assertThat(jsonSchema)
        .isInstanceOfSatisfying(
            JsonObjectSchema.class,
            jsonObjectSchema -> {
              assertThat(jsonObjectSchema.properties()).containsOnlyKeys("message");
              assertThat(jsonObjectSchema.properties().get("message"))
                  .isInstanceOfSatisfying(
                      JsonStringSchema.class,
                      jsonStringSchema -> {
                        assertThat(jsonStringSchema.description()).isEqualTo("Message to echo");
                      });
              assertThat(jsonObjectSchema.required()).containsExactly("message");
              assertThat(jsonObjectSchema.additionalProperties()).isFalse();
            });

    final var jsonSchemaAsMap = jsonSchemaConverter.schemaToMap(jsonSchema);
    assertThat(jsonSchemaAsMap)
        .isNotSameAs(jsonSchemaStringAsMap)
        .usingRecursiveComparison()
        .isEqualTo(jsonSchemaStringAsMap);

    final var jsonSchemaString = objectMapper.writeValueAsString(jsonSchemaAsMap);
    JSONAssert.assertEquals(TEST_SCHEMA, jsonSchemaString, true);
  }
}
