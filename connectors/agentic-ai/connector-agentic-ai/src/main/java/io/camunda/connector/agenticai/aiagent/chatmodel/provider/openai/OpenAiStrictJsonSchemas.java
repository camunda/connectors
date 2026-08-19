/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static io.camunda.connector.agenticai.common.JsonSchemaConstants.PROPERTY_ADDITIONAL_PROPERTIES;
import static io.camunda.connector.agenticai.common.JsonSchemaConstants.PROPERTY_ANYOF;
import static io.camunda.connector.agenticai.common.JsonSchemaConstants.PROPERTY_DEFINITIONS;
import static io.camunda.connector.agenticai.common.JsonSchemaConstants.PROPERTY_ITEMS;
import static io.camunda.connector.agenticai.common.JsonSchemaConstants.PROPERTY_PROPERTIES;
import static io.camunda.connector.agenticai.common.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.common.JsonSchemaConstants.TYPE_OBJECT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Recursively injects {@code additionalProperties: false} into a JSON schema, overriding any
 * explicit value already present at that level. OpenAI's strict structured-output mode requires
 * this on every object schema in the tree, including the root; a schema that omits it anywhere is
 * rejected with a 400 rather than defaulted.
 *
 * <p>Supports the same schema dialect subset as {@code JsonSchemaElementDeserializer} (in the
 * {@code langchain4j.jsonschema} package): object {@code properties} and {@code $defs}, array
 * {@code items}, and {@code anyOf} branches.
 */
public final class OpenAiStrictJsonSchemas {

  private OpenAiStrictJsonSchemas() {}

  public static JsonNode forStrictMode(Map<String, Object> schema, ObjectMapper objectMapper) {
    final JsonNode root = objectMapper.valueToTree(schema);
    enforceAdditionalPropertiesFalse(root);
    return root;
  }

  private static void enforceAdditionalPropertiesFalse(@Nullable JsonNode node) {
    if (!(node instanceof ObjectNode object)) {
      return;
    }

    if (TYPE_OBJECT.equals(object.path(PROPERTY_TYPE).asText(null))) {
      object.put(PROPERTY_ADDITIONAL_PROPERTIES, false);
    }

    if (object.get(PROPERTY_PROPERTIES) instanceof ObjectNode properties) {
      properties.properties().forEach(entry -> enforceAdditionalPropertiesFalse(entry.getValue()));
    }

    enforceAdditionalPropertiesFalse(object.get(PROPERTY_ITEMS));

    if (object.get(PROPERTY_ANYOF) instanceof ArrayNode anyOf) {
      anyOf.forEach(OpenAiStrictJsonSchemas::enforceAdditionalPropertiesFalse);
    }

    if (object.get(PROPERTY_DEFINITIONS) instanceof ObjectNode definitions) {
      definitions.properties().forEach(entry -> enforceAdditionalPropertiesFalse(entry.getValue()));
    }
  }
}
