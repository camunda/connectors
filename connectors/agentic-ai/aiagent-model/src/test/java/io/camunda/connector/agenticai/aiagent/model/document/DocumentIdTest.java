/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentIdTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void serializesAsABareStringAlsoInsideAnObject() throws Exception {
    assertThat(objectMapper.writeValueAsString(DocumentId.of("doc-1"))).isEqualTo("\"doc-1\"");
    assertThat(objectMapper.writeValueAsString(Map.of("id", DocumentId.of("doc-1"))))
        .isEqualTo("{\"id\":\"doc-1\"}");
  }

  @Test
  void readsABareString() throws Exception {
    assertThat(objectMapper.readValue("\"doc-1\"", DocumentId.class))
        .isEqualTo(DocumentId.of("doc-1"));
  }

  @Test
  void rendersAsItsValue() {
    assertThat(DocumentId.of("doc-1")).hasToString("doc-1");
  }

  @Test
  void rejectsABlankId() {
    assertThatThrownBy(() -> DocumentId.of(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DocumentId.of(null)).isInstanceOf(IllegalArgumentException.class);
  }
}
