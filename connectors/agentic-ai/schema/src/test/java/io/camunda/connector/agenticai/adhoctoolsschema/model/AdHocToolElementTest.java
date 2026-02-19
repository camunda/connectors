/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;

class AdHocToolElementTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final AdHocToolElement ELEMENT =
      AdHocToolElement.builder()
          .elementId("My_Tool")
          .elementName("My Tool")
          .documentation("Tool documentation")
          .build();

  @Test
  void documentationWithNameFallback_returnsDocumentationIfPresent() {
    assertThat(ELEMENT.documentationWithNameFallback()).isEqualTo("Tool documentation");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "  ")
  void documentationWithNameFallback_returnsNameIfDocumentationNullOrBlank(String documentation) {
    assertThat(ELEMENT.withDocumentation(documentation).documentationWithNameFallback())
        .isEqualTo("My Tool");
  }

  @Test
  void canBeDeserializedAndSerializedBack() throws IOException, JSONException {
    final var json =
        new String(
            getClass()
                .getClassLoader()
                .getResourceAsStream("ad-hoc-tool-elements.json")
                .readAllBytes(),
            StandardCharsets.UTF_8);

    final List<AdHocToolElement> elements = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});

    assertThat(elements)
        .hasSize(3)
        .extracting(AdHocToolElement::elementId)
        .containsExactly("GetDateAndTime", "A_Complex_Tool", "A_MCP_Client");

    assertThat(elements.get(0).parameters()).isEmpty();
    assertThat(elements.get(1).parameters()).hasSize(9);

    assertThat(elements.get(2).parameters()).isEmpty();
    assertThat(elements.get(2).properties())
        .containsEntry("io.camunda.agenticai.gateway.type", "mcpClient");

    final var serializedJson = OBJECT_MAPPER.writeValueAsString(elements);

    JSONAssert.assertEquals(json, serializedJson, true);
  }
}
