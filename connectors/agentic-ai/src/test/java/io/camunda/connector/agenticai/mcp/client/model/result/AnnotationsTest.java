/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnnotationsTest {

  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();

  @Test
  void shouldSerializeAndDeserializeAnnotations() throws Exception {
    var annotations =
        new Annotations(
            List.of(Annotations.AUDIENCE_USER, Annotations.AUDIENCE_ASSISTANT),
            0.9,
            "2025-11-25T10:30:00Z");

    String json = objectMapper.writeValueAsString(annotations);
    Annotations deserialized = objectMapper.readValue(json, Annotations.class);

    assertThat(deserialized).isEqualTo(annotations);
    assertThat(deserialized.audience())
        .containsExactly(Annotations.AUDIENCE_USER, Annotations.AUDIENCE_ASSISTANT);
    assertThat(deserialized.priority()).isEqualTo(0.9);
    assertThat(deserialized.lastModified()).isEqualTo("2025-11-25T10:30:00Z");
  }

  @Test
  void shouldSerializeNullAnnotations() throws Exception {
    Annotations annotations = null;

    String json = objectMapper.writeValueAsString(annotations);

    assertThat(json).isEqualTo("null");
  }

  @Test
  void shouldHandlePartialAnnotations() throws Exception {
    // Only audience set
    var annotations = new Annotations(List.of(Annotations.AUDIENCE_USER), null, null);

    String json = objectMapper.writeValueAsString(annotations);
    Annotations deserialized = objectMapper.readValue(json, Annotations.class);

    assertThat(deserialized.audience()).containsExactly(Annotations.AUDIENCE_USER);
    assertThat(deserialized.priority()).isNull();
    assertThat(deserialized.lastModified()).isNull();
  }

  @Test
  void shouldDeserializeFromJson() throws Exception {
    String json =
        """
        {
          "audience": ["user", "assistant"],
          "priority": 0.75,
          "lastModified": "2025-11-25T15:00:00Z"
        }
        """;

    Annotations annotations = objectMapper.readValue(json, Annotations.class);

    assertThat(annotations.audience()).containsExactly("user", "assistant");
    assertThat(annotations.priority()).isEqualTo(0.75);
    assertThat(annotations.lastModified()).isEqualTo("2025-11-25T15:00:00Z");
  }

  @Test
  void shouldExcludeNullFieldsFromSerialization() throws Exception {
    var annotations = new Annotations(null, 1.0, null);

    String json = objectMapper.writeValueAsString(annotations);

    assertThat(json).contains("\"priority\":1.0");
    assertThat(json).doesNotContain("audience");
    assertThat(json).doesNotContain("lastModified");
  }

  @Test
  void shouldHandleEmptyAudienceList() throws Exception {
    var annotations = new Annotations(List.of(), 0.5, "2025-12-01T00:00:00Z");

    String json = objectMapper.writeValueAsString(annotations);
    Annotations deserialized = objectMapper.readValue(json, Annotations.class);

    assertThat(deserialized.audience()).isEmpty();
    assertThat(deserialized.priority()).isEqualTo(0.5);
  }

  @Test
  void shouldValidateAudienceConstants() {
    assertThat(Annotations.AUDIENCE_USER).isEqualTo("user");
    assertThat(Annotations.AUDIENCE_ASSISTANT).isEqualTo("assistant");
  }
}
