/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Map;

/**
 * Custom deserializer that handles both a proper JSON object and a JSON-encoded string for {@code
 * Map<String, String>} fields. This is needed because outbound connector variables may arrive as
 * strings when FEEL evaluation doesn't occur (e.g., missing '=' prefix, old template version, or
 * Zeebe serialization behavior).
 */
public class StringToMapDeserializer extends JsonDeserializer<Map<String, String>> {

  private static final TypeReference<Map<String, String>> MAP_TYPE_REF = new TypeReference<>() {};

  @Override
  public Map<String, String> deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException {
    JsonNode node = p.readValueAsTree();

    if (node.isObject()) {
      try (JsonParser nodeParser = node.traverse(p.getCodec())) {
        return p.getCodec().readValue(nodeParser, MAP_TYPE_REF);
      }
    }

    if (node.isTextual()) {
      String text = node.textValue().trim();
      try (JsonParser textParser = p.getCodec().getFactory().createParser(text)) {
        return p.getCodec().readValue(textParser, MAP_TYPE_REF);
      }
    }

    throw ctxt.wrongTokenException(
        p, Map.class, node.asToken(), "Expected a JSON object or a JSON string representing a map");
  }
}
