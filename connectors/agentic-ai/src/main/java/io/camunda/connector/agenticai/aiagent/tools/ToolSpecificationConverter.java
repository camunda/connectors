/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tools;

import static io.camunda.connector.agenticai.util.JacksonExceptionMessageExtractor.humanReadableJsonProcessingExceptionMessage;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;
import io.camunda.connector.agenticai.jsonschema.JsonSchemaElementModule;
import io.camunda.connector.agenticai.util.ObjectMapperConstants;
import java.util.Map;

/**
 * Validates and converts a json schema to a tool specification. Conversion logic is based on
 * dev.langchain4j.mcp.client.ToolSpecificationHelper.
 */
public class ToolSpecificationConverter {

  private final ObjectMapper objectMapper;

  public ToolSpecificationConverter() {
    this(defaultObjectMapper());
  }

  public ToolSpecificationConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  private static ObjectMapper defaultObjectMapper() {
    final var objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JsonSchemaElementModule());
    objectMapper.addMixIn(ToolSpecification.class, ToolSpecificationMixin.class);
    return objectMapper;
  }

  public ToolSpecification asToolSpecification(
      String name, String description, String inputSchemaJson) {
    final var inputSchema =
        parseSchemaWithErrorHandling(
            name, () -> objectMapper.readValue(inputSchemaJson, JsonSchemaElement.class));

    return ToolSpecification.builder()
        .name(name)
        .description(description)
        .parameters(inputSchema)
        .build();
  }

  public ToolSpecification asToolSpecification(
      AdHocToolsSchemaResponse.AdHocToolDefinition toolDefinition) {
    final var inputSchema =
        parseSchemaWithErrorHandling(
            toolDefinition.name(),
            () -> objectMapper.convertValue(toolDefinition.inputSchema(), JsonSchemaElement.class));

    return ToolSpecification.builder()
        .name(toolDefinition.name())
        .description(toolDefinition.description())
        .parameters(inputSchema)
        .build();
  }

  public Map<String, Object> asMap(ToolSpecification toolSpecification) {
    return objectMapper.convertValue(
        toolSpecification, ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE);
  }

  public ToolSpecification fromMap(Map<String, Object> toolSpecification) {
    return objectMapper.convertValue(toolSpecification, ToolSpecification.class);
  }

  public String asString(ToolSpecification toolSpecification) throws JsonProcessingException {
    return objectMapper.writeValueAsString(toolSpecification);
  }

  public ToolSpecification fromString(String toolSpecification) throws JsonProcessingException {
    return objectMapper.readValue(toolSpecification, ToolSpecification.class);
  }

  private JsonObjectSchema parseSchemaWithErrorHandling(
      String toolName, JsonObjectSchemaSupplier supplier) {

    JsonSchemaElement schema;
    try {
      schema = supplier.getSchema();
    } catch (Exception e) {
      if (e instanceof JsonParseException jpe) {
        String sb =
            "Failed to read input schema. " + humanReadableJsonProcessingExceptionMessage(jpe);
        throw new ParseSchemaException(sb, e);
      }

      throw new ParseSchemaException("Failed to read input schema.", e);
    }

    if (!(schema instanceof JsonObjectSchema jsonObjectSchema)) {
      throw new ParseSchemaException(
          "Tool '%s' input schema is expected to be an object schema, but was %s instead."
              .formatted(toolName, schema.getClass().getSimpleName()));
    }

    return jsonObjectSchema;
  }

  @FunctionalInterface
  private interface JsonObjectSchemaSupplier {
    JsonSchemaElement getSchema() throws Exception;
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
