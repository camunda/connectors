/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tools;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import java.io.IOException;

/**
 * Custom deserializer for ToolSpecification. This deserializer handles the deserialization of
 * ToolSpecification objects by using the builder pattern.
 */
public class ToolSpecificationDeserializer extends JsonDeserializer<ToolSpecification> {

  @Override
  public ToolSpecification deserialize(JsonParser jp, DeserializationContext ctxt)
      throws IOException {
    ObjectMapper mapper = (ObjectMapper) jp.getCodec();
    JsonNode node = mapper.readTree(jp);

    String name = node.has("name") ? node.get("name").asText() : null;
    String description = node.has("description") ? node.get("description").asText() : null;

    JsonObjectSchema parameters = null;
    if (node.has("parameters")) {
      JsonSchemaElement element = mapper.treeToValue(node.get("parameters"), JsonSchemaElement.class);
      if (element instanceof JsonObjectSchema) {
        parameters = (JsonObjectSchema) element;
      } else {
        throw new IOException("Parameters must be a JsonObjectSchema");
      }
    }

    return ToolSpecification.builder()
        .name(name)
        .description(description)
        .parameters(parameters)
        .build();
  }
}
