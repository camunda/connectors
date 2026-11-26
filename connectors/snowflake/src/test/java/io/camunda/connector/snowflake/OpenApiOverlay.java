/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.snowflake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Applies an <a href="https://spec.openapis.org/overlay/v1.0.0.html">OpenAPI Overlay 1.0</a>
 * document to an OpenAPI spec, so Camunda's additions to a vendor spec are kept apart from the
 * vendor's own text.
 *
 * <p>Supports both action kinds, {@code update} and {@code remove}. An update merges by the rules
 * of <a href="https://spec.openapis.org/overlay/v1.1.0.html">Overlay 1.1</a>, which clarify 1.0:
 * objects merge recursively, arrays are concatenated, a primitive replaces a primitive, and any
 * other combination is an error. Unlike the Overlay specification, an action whose target matches
 * nothing is an error: when the vendor changes its spec, the generator should fail rather than
 * silently drop a fix.
 */
final class OpenApiOverlay {

  private static final Configuration VALUES =
      Configuration.builder()
          .jsonProvider(new JacksonJsonNodeJsonProvider())
          .mappingProvider(new JacksonMappingProvider())
          .build();
  private static final Configuration PATHS = VALUES.addOptions(Option.AS_PATH_LIST);

  private OpenApiOverlay() {}

  /** Returns a copy of {@code spec} with every action of {@code overlay} applied in order. */
  static JsonNode apply(JsonNode spec, JsonNode overlay) {
    String version = overlay.path("overlay").asText();
    if (!version.startsWith("1.")) {
      throw new IllegalArgumentException("Unsupported overlay version '" + version + "'");
    }
    JsonNode result = spec.deepCopy();
    DocumentContext document = JsonPath.using(VALUES).parse(result);
    for (JsonNode action : overlay.path("actions")) {
      String target = action.path("target").asText();
      List<String> matches = matches(result, target);
      if (action.path("remove").asBoolean(false)) {
        // Remove the last match first, so array indices of earlier matches stay valid.
        List<String> reversed = new ArrayList<>(matches);
        Collections.reverse(reversed);
        reversed.forEach(document::delete);
      } else if (action.has("update")) {
        for (String match : matches) {
          JsonNode node = document.read(match);
          merge(node, action.get("update"), target);
        }
      } else {
        throw new IllegalArgumentException(
            "Overlay action for '" + target + "' has neither 'update' nor 'remove'");
      }
    }
    return result;
  }

  private static List<String> matches(JsonNode document, String target) {
    List<String> matches;
    try {
      matches = JsonPath.using(PATHS).parse(document).read(target, List.class);
    } catch (PathNotFoundException e) {
      matches = List.of();
    }
    if (matches == null || matches.isEmpty()) {
      throw new IllegalStateException(
          "Overlay target '" + target + "' matches nothing: has the vendor spec changed?");
    }
    return matches;
  }

  private static void merge(JsonNode node, JsonNode update, String target) {
    if (node instanceof ObjectNode object && update instanceof ObjectNode updateObject) {
      for (Map.Entry<String, JsonNode> field : updateObject.properties()) {
        String name = field.getKey();
        JsonNode existing = object.get(name);
        JsonNode value = field.getValue();
        if (existing == null || existing.isNull()) {
          object.set(name, value.deepCopy());
        } else if (existing.isObject() && value.isObject()) {
          merge(existing, value, target);
        } else if (existing instanceof ArrayNode existingArray && value.isArray()) {
          value.forEach(element -> existingArray.add(element.deepCopy()));
        } else if (existing.isValueNode() && value.isValueNode()) {
          object.set(name, value.deepCopy());
        } else {
          throw new IllegalStateException(
              "Overlay update for '"
                  + target
                  + "' cannot merge a "
                  + value.getNodeType()
                  + " into the existing "
                  + existing.getNodeType()
                  + " property '"
                  + name
                  + "'");
        }
      }
    } else if (node instanceof ArrayNode array) {
      if (update instanceof ArrayNode updateArray) {
        updateArray.forEach(element -> array.add(element.deepCopy()));
      } else {
        array.add(update.deepCopy());
      }
    } else {
      throw new IllegalStateException(
          "Overlay target '" + target + "' matched a value that is neither an object nor an array");
    }
  }
}
