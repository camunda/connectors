/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.Map;

/**
 * Validates and converts a JSON schema to a tool specification. Conversion logic is based on
 * dev.langchain4j.mcp.client.ToolSpecificationHelper.
 */
public class ToolSpecificationConverterImpl implements ToolSpecificationConverter {

  private final JsonSchemaConverter schemaConverter;

  public ToolSpecificationConverterImpl(JsonSchemaConverter schemaConverter) {
    this.schemaConverter = schemaConverter;
  }

  @Override
  public ToolSpecification asToolSpecification(ToolDefinition toolDefinition) {
    final var jsonSchema = parseSchema(toolDefinition);
    if (!(jsonSchema instanceof JsonObjectSchema jsonObjectSchema)) {
      throw new ParseSchemaException(
          "Failed to parse input schema for tool '%s': Input schema must be of type object"
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
      return schemaConverter.mapToSchema(toolDefinition.inputSchema());
    } catch (Exception e) {
      throw new ParseSchemaException(
          "Failed to parse input schema for tool '%s': %s"
              .formatted(toolDefinition.name(), e.getMessage()),
          e);
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
      return schemaConverter.schemaToMap(toolSpecification.parameters());
    } catch (Exception e) {
      throw new ParseSchemaException(
          "Failed to convert JSON schema for tool specification '%s': %s"
              .formatted(toolSpecification.name(), e.getMessage()),
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
