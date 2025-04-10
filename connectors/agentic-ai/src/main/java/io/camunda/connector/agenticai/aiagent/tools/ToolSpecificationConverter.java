/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tools;

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
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;
import java.util.Map;

/**
 * Validates and converts a json schema to a tool specification. Conversion logic is based on
 * dev.langchain4j.mcp.client.ToolSpecificationHelper.
 */
public class ToolSpecificationConverter {

  private final JsonSchemaFactory jsonSchemaFactory;

  public ToolSpecificationConverter() {
    this(JsonSchemaFactory.getInstance(VersionFlag.V202012));
  }

  public ToolSpecificationConverter(JsonSchemaFactory jsonSchemaFactory) {
    this.jsonSchemaFactory = jsonSchemaFactory;
  }

  public ToolSpecification asToolSpecification(
      String name, String description, String inputSchemaJson) {
    final var inputSchema = convertToJsonObjectSchema(inputSchemaJson);

    return ToolSpecification.builder()
        .name(name)
        .description(description)
        .parameters(inputSchema)
        .build();
  }

  public ToolSpecification asToolSpecification(
      AdHocToolsSchemaResponse.AdHocToolDefinition toolDefinition) {
    JsonNode schemaNode = JsonMapperFactory.getInstance().valueToTree(toolDefinition.inputSchema());
    final var inputSchema = convertToJsonObjectSchema(schemaNode);

    return ToolSpecification.builder()
        .name(toolDefinition.name())
        .description(toolDefinition.description())
        .parameters(inputSchema)
        .build();
  }

  private JsonObjectSchema convertToJsonObjectSchema(String inputSchemaJson) {
    JsonSchema schema = loadSchema(inputSchemaJson);
    return convertToJsonObjectSchema(schema.getSchemaNode());
  }

  private JsonObjectSchema convertToJsonObjectSchema(JsonNode schemaNode) {
    if (!schemaNode.has("type") || !"object".equals(schemaNode.get("type").textValue())) {
      throw new ParseSchemaException(
          "Failed to read input schema. Root input schema must be type object.");
    }

    return (JsonObjectSchema) jsonNodeToJsonSchemaElement(schemaNode);
  }

  private JsonSchemaElement jsonNodeToJsonSchemaElement(JsonNode node) {
    String nodeType = node.get("type").asText();
    switch (nodeType) {
      case "object" -> {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        JsonNode required = node.get("required");
        if (required != null) {
          builder.required(toStringArray((ArrayNode) required));
        }

        if (node.has("additionalProperties")) {
          builder.additionalProperties(node.get("additionalProperties").asBoolean(false));
        }

        JsonNode description = node.get("description");
        if (description != null) {
          builder.description(description.asText());
        }

        JsonNode properties = node.get("properties");
        if (properties != null) {
          ObjectNode propertiesObject = (ObjectNode) properties;

          for (Map.Entry<String, JsonNode> property : propertiesObject.properties()) {
            builder.addProperty(
                property.getKey(), jsonNodeToJsonSchemaElement(property.getValue()));
          }
        }

        return builder.build();
      }
      case "string" -> {
        if (node.has("enum")) {
          JsonEnumSchema.Builder builder = JsonEnumSchema.builder();
          if (node.has("description")) {
            builder.description(node.get("description").asText());
          }

          builder.enumValues(toStringArray((ArrayNode) node.get("enum")));
          return builder.build();
        } else {
          JsonStringSchema.Builder builder = JsonStringSchema.builder();
          if (node.has("description")) {
            builder.description(node.get("description").asText());
          }

          return builder.build();
        }
      }
      case "number" -> {
        JsonNumberSchema.Builder builder = JsonNumberSchema.builder();
        if (node.has("description")) {
          builder.description(node.get("description").asText());
        }

        return builder.build();
      }
      case "integer" -> {
        JsonIntegerSchema.Builder builder = JsonIntegerSchema.builder();
        if (node.has("description")) {
          builder.description(node.get("description").asText());
        }

        return builder.build();
      }
      case "boolean" -> {
        JsonBooleanSchema.Builder builder = JsonBooleanSchema.builder();
        if (node.has("description")) {
          builder.description(node.get("description").asText());
        }

        return builder.build();
      }
      case "array" -> {
        JsonArraySchema.Builder builder = JsonArraySchema.builder();
        if (node.has("description")) {
          builder.description(node.get("description").asText());
        }

        builder.items(jsonNodeToJsonSchemaElement(node.get("items")));
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
