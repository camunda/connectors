/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import io.camunda.connector.agenticai.util.ObjectMapperConstants;
import java.util.Map;

public class JsonSchemaConverter {
  private final ObjectMapper objectMapper;

  public JsonSchemaConverter(ObjectMapper objectMapper) {
    this.objectMapper = configuredObjectMapperCopy(objectMapper);
  }

  private ObjectMapper configuredObjectMapperCopy(ObjectMapper objectMapper) {
    return objectMapper.copy().registerModule(new JsonSchemaElementModule());
  }

  public JsonSchemaElement mapToSchema(Map<String, Object> schema) {
    return objectMapper.convertValue(schema, JsonSchemaElement.class);
  }

  public Map<String, Object> schemaToMap(JsonSchemaElement schema) {
    return objectMapper.convertValue(
        schema, ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE);
  }
}
