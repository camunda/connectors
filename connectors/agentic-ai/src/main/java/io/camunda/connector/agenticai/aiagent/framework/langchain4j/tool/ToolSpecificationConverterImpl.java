/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaElementModule;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.util.ObjectMapperConstants;
import java.util.Map;

/**
 * Validates and converts a JSON schema to a tool specification. Conversion logic is based on
 * dev.langchain4j.mcp.client.ToolSpecificationHelper.
 */
public class ToolSpecificationConverterImpl implements ToolSpecificationConverter {

  private final ObjectMapper objectMapper;

  public ToolSpecificationConverterImpl(ObjectMapper objectMapper) {
    this.objectMapper = configuredObjectMapperCopy(objectMapper);
  }

  private ObjectMapper configuredObjectMapperCopy(ObjectMapper objectMapper) {
    return objectMapper.copy().registerModule(new JsonSchemaElementModule());
  }

  @Override
  public ToolSpecification asToolSpecification(ToolDefinition toolDefinition) {
    final var jsonSchema = parseSchema(toolDefinition);
    if (!(jsonSchema instanceof JsonObjectSchema jsonObjectSchema)) {
      throw new ParseSchemaException(
          "Failed to parse input schema for tool '%s'. Input schema must be of type object."
              .formatted(toolDefinition.name()));
    }

    return ToolSpecification.builder()
        .name(toolDefinition.name())
        .description(toolDefinition.description())
        .parameters(jsonObjectSchema)
        .build();
  }

  private JsonSchemaElement parseSchema(ToolDefinition toolDefinition) {
    try {
      return objectMapper.convertValue(toolDefinition.inputSchema(), JsonSchemaElement.class);
    } catch (Exception e) {
      throw new ParseSchemaException(
          "Failed to parse input schema for tool '%s'".formatted(toolDefinition.name()), e);
    }
  }

  @Override
  public ToolDefinition asToolDefinition(ToolSpecification toolSpecification) {
    return ToolDefinition.builder()
        .name(toolSpecification.name())
        .description(toolSpecification.description())
        .inputSchema(schemaToMap(toolSpecification))
        .build();
  }

  private Map<String, Object> schemaToMap(ToolSpecification toolSpecification) {
    try {
      return objectMapper.convertValue(
          toolSpecification.parameters(), ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE);
    } catch (Exception e) {
      throw new ParseSchemaException(
          "Failed to convert JSON schema for tool specification '%s'"
              .formatted(toolSpecification.name()),
          e);
    }
  }

  public static class ParseSchemaException extends RuntimeException {
    public ParseSchemaException(String message) {
      super(message);
    }

    public ParseSchemaException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
