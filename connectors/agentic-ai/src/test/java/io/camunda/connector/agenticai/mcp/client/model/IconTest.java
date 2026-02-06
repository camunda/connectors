/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.util.List;
import org.junit.jupiter.api.Test;

class IconTest {

  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();

  @Test
  void shouldSerializeAndDeserializeIcon() throws Exception {
    var icon = new Icon("https://example.com/icon.png", "image/png", List.of("48x48", "96x96"));

    String json = objectMapper.writeValueAsString(icon);
    Icon deserialized = objectMapper.readValue(json, Icon.class);

    assertThat(deserialized).isEqualTo(icon);
    assertThat(deserialized.src()).isEqualTo("https://example.com/icon.png");
    assertThat(deserialized.mimeType()).isEqualTo("image/png");
    assertThat(deserialized.sizes()).containsExactly("48x48", "96x96");
  }

  @Test
  void shouldSerializeNullIcon() throws Exception {
    Icon icon = null;

    String json = objectMapper.writeValueAsString(icon);

    assertThat(json).isEqualTo("null");
  }

  @Test
  void shouldHandlePartialIcon() throws Exception {
    // Only src and mimeType set
    var icon = new Icon("data:image/svg+xml;base64,ABC123", "image/svg+xml", null);

    String json = objectMapper.writeValueAsString(icon);
    Icon deserialized = objectMapper.readValue(json, Icon.class);

    assertThat(deserialized.src()).isEqualTo("data:image/svg+xml;base64,ABC123");
    assertThat(deserialized.mimeType()).isEqualTo("image/svg+xml");
    assertThat(deserialized.sizes()).isNull();
  }

  @Test
  void shouldDeserializeFromJson() throws Exception {
    String json =
        """
        {
          "src": "https://example.com/tool-icon.svg",
          "mimeType": "image/svg+xml",
          "sizes": ["any"]
        }
        """;

    Icon icon = objectMapper.readValue(json, Icon.class);

    assertThat(icon.src()).isEqualTo("https://example.com/tool-icon.svg");
    assertThat(icon.mimeType()).isEqualTo("image/svg+xml");
    assertThat(icon.sizes()).containsExactly("any");
  }

  @Test
  void shouldExcludeNullFieldsFromSerialization() throws Exception {
    var icon = new Icon("https://example.com/icon.png", null, null);

    String json = objectMapper.writeValueAsString(icon);

    assertThat(json).contains("\"src\":\"https://example.com/icon.png\"");
    assertThat(json).doesNotContain("mimeType");
    assertThat(json).doesNotContain("sizes");
  }

  @Test
  void shouldHandleEmptySizesList() throws Exception {
    var icon = new Icon("https://example.com/icon.png", "image/png", List.of());

    String json = objectMapper.writeValueAsString(icon);
    Icon deserialized = objectMapper.readValue(json, Icon.class);

    assertThat(deserialized.sizes()).isEmpty();
  }

  @Test
  void shouldHandleDataUri() throws Exception {
    var dataUri = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciLz4=";
    var icon = new Icon(dataUri, "image/svg+xml", List.of("any"));

    String json = objectMapper.writeValueAsString(icon);
    Icon deserialized = objectMapper.readValue(json, Icon.class);

    assertThat(deserialized.src()).isEqualTo(dataUri);
    assertThat(deserialized.mimeType()).isEqualTo("image/svg+xml");
  }
}
