/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool;

import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_ADDITIONAL_PROPERTIES;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_ENUM;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_ITEMS;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_PROPERTIES;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_REQUIRED;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_ARRAY;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_BOOLEAN;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_INTEGER;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_NUMBER;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_OBJECT;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_STRING;
import static io.camunda.connector.agenticai.util.JacksonExceptionMessageExtractor.humanReadableJsonProcessingExceptionMessage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.serialization.JsonMapperFactory;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.Map;

/**
 * Validates and converts a json schema to a tool specification. Conversion logic is based on
 * dev.langchain4j.mcp.client.ToolSpecificationHelper.
 */
public class ToolSpecificationConverterImpl implements ToolSpecificationConverter {

  private final JsonSchemaFactory jsonSchemaFactory;

  public ToolSpecificationConverterImpl() {
    this(JsonSchemaFactory.getInstance(VersionFlag.V202012));
  }

  public ToolSpecificationConverterImpl(JsonSchemaFactory jsonSchemaFactory) {
    this.jsonSchemaFactory = jsonSchemaFactory;
  }

  @Override
  public ToolSpecification asToolSpecification(ToolDefinition toolDefinition) {
    JsonNode schemaNode = JsonMapperFactory.getInstance().valueToTree(toolDefinition.inputSchema());
    final var inputSchema = convertToJsonObjectSchema(schemaNode);

    return ToolSpecification.builder()
        .name(toolDefinition.name())
        .description(toolDefinition.description())
        .parameters(inputSchema)
        .build();
  }

  private JsonObjectSchema convertToJsonObjectSchema(JsonNode schemaNode) {
    if (!schemaNode.has(PROPERTY_TYPE)
        || !TYPE_OBJECT.equals(schemaNode.get(PROPERTY_TYPE).textValue())) {
      throw new ParseSchemaException(
          "Failed to read input schema. Root input schema must be type object.");
    }

    return (JsonObjectSchema) jsonNodeToJsonSchemaElement(schemaNode);
  }

  private JsonSchemaElement jsonNodeToJsonSchemaElement(JsonNode node) {
    String nodeType = node.get(PROPERTY_TYPE).asText();
    switch (nodeType) {
      case TYPE_OBJECT -> {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        JsonNode required = node.get(PROPERTY_REQUIRED);
        if (required != null) {
          builder.required(toStringArray((ArrayNode) required));
        }

        if (node.has(PROPERTY_ADDITIONAL_PROPERTIES)) {
          builder.additionalProperties(node.get(PROPERTY_ADDITIONAL_PROPERTIES).asBoolean(false));
        }

        JsonNode description = node.get(PROPERTY_DESCRIPTION);
        if (description != null) {
          builder.description(description.asText());
        }

        JsonNode properties = node.get(PROPERTY_PROPERTIES);
        if (properties != null) {
          ObjectNode propertiesObject = (ObjectNode) properties;

          for (Map.Entry<String, JsonNode> property : propertiesObject.properties()) {
            builder.addProperty(
                property.getKey(), jsonNodeToJsonSchemaElement(property.getValue()));
          }
        }

        return builder.build();
      }
      case TYPE_STRING -> {
        if (node.has(PROPERTY_ENUM)) {
          JsonEnumSchema.Builder builder = JsonEnumSchema.builder();
          if (node.has(PROPERTY_DESCRIPTION)) {
            builder.description(node.get(PROPERTY_DESCRIPTION).asText());
          }

          builder.enumValues(toStringArray((ArrayNode) node.get(PROPERTY_ENUM)));
          return builder.build();
        } else {
          JsonStringSchema.Builder builder = JsonStringSchema.builder();
          if (node.has(PROPERTY_DESCRIPTION)) {
            builder.description(node.get(PROPERTY_DESCRIPTION).asText());
          }

          return builder.build();
        }
      }
      case TYPE_NUMBER -> {
        JsonNumberSchema.Builder builder = JsonNumberSchema.builder();
        if (node.has(PROPERTY_DESCRIPTION)) {
          builder.description(node.get(PROPERTY_DESCRIPTION).asText());
        }

        return builder.build();
      }
      case TYPE_INTEGER -> {
        JsonIntegerSchema.Builder builder = JsonIntegerSchema.builder();
        if (node.has(PROPERTY_DESCRIPTION)) {
          builder.description(node.get(PROPERTY_DESCRIPTION).asText());
        }

        return builder.build();
      }
      case TYPE_BOOLEAN -> {
        JsonBooleanSchema.Builder builder = JsonBooleanSchema.builder();
        if (node.has(PROPERTY_DESCRIPTION)) {
          builder.description(node.get(PROPERTY_DESCRIPTION).asText());
        }

        return builder.build();
      }
      case TYPE_ARRAY -> {
        JsonArraySchema.Builder builder = JsonArraySchema.builder();
        if (node.has(PROPERTY_DESCRIPTION)) {
          builder.description(node.get(PROPERTY_DESCRIPTION).asText());
        }

        builder.items(jsonNodeToJsonSchemaElement(node.get(PROPERTY_ITEMS)));
        return builder.build();
      }
      default -> throw new IllegalArgumentException("Unknown element type: " + nodeType);
    }
  }

  private static String[] toStringArray(ArrayNode jsonArray) {
    String[] result = new String[jsonArray.size()];

    for (int i = 0; i < jsonArray.size(); ++i) {
      result[i] = jsonArray.get(i).asText();
    }

    return result;
  }

  private JsonSchema loadSchema(String inputSchemaJson) {
    try {
      return jsonSchemaFactory.getSchema(inputSchemaJson);
    } catch (Exception e) {
      if (e.getCause() instanceof com.fasterxml.jackson.core.JsonParseException jpe) {
        String sb =
            "Failed to read input schema. " + humanReadableJsonProcessingExceptionMessage(jpe);
        throw new ParseSchemaException(sb, e);
      }

      throw new ParseSchemaException("Failed to read input schema.", e);
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
