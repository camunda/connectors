/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.elevenlabs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenApiOverlayTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String SPEC =
      """
      {
        "openapi": "3.1.0",
        "servers": [{"url": "https://a.example"}],
        "components": {"schemas": {"A": {"type": "string"}}},
        "paths": {
          "/x": {"get": {"parameters": [
            {"name": "keep", "in": "query"},
            {"name": "drop", "in": "header"},
            {"name": "drop", "in": "query"}
          ]}}
        }
      }
      """;

  @Test
  void mergesObjectsAndAppendsToArrays() throws Exception {
    JsonNode result =
        apply(
            """
            {"overlay": "1.0.0", "actions": [
              {"target": "$", "update": {"servers": [{"url": "https://b.example"}]}},
              {"target": "$.components", "update": {"securitySchemes": {"k": {"type": "apiKey"}}}},
              {"target": "$.servers", "update": {"url": "https://c.example"}}
            ]}
            """);

    assertThat(result.at("/servers").findValuesAsText("url"))
        .containsExactly("https://b.example", "https://c.example");
    assertThat(result.at("/components/schemas/A/type").asText()).isEqualTo("string");
    assertThat(result.at("/components/securitySchemes/k/type").asText()).isEqualTo("apiKey");
  }

  @Test
  void removesEveryMatch() throws Exception {
    JsonNode result =
        apply(
            """
            {"overlay": "1.0.0", "actions": [
              {"target": "$.paths.*.*.parameters[?(@.name == 'drop')]", "remove": true}
            ]}
            """);

    assertThat(result.at("/paths/~1x/get/parameters").findValuesAsText("name"))
        .containsExactly("keep");
  }

  @Test
  void leavesTheInputUnchanged() throws Exception {
    JsonNode spec = MAPPER.readTree(SPEC);
    JsonNode overlay =
        MAPPER.readTree(
            """
            {"overlay": "1.0.0", "actions": [{"target": "$.servers", "remove": true}]}
            """);

    OpenApiOverlay.apply(spec, overlay);

    assertThat(spec).isEqualTo(MAPPER.readTree(SPEC));
  }

  @Test
  void failsWhenATargetMatchesNothing() {
    assertThatThrownBy(() ->
            apply(
                """
                {"overlay": "1.0.0", "actions": [
                  {"target": "$.paths.*.*.parameters[?(@.name == 'gone')]", "remove": true}
                ]}
                """))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("matches nothing");
  }

  @Test
  void rejectsUnsupportedOverlayVersions() {
    assertThatThrownBy(() -> apply("{\"overlay\": \"2.0.0\", \"actions\": []}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static JsonNode apply(String overlay) throws Exception {
    return OpenApiOverlay.apply(MAPPER.readTree(SPEC), MAPPER.readTree(overlay));
  }
}
