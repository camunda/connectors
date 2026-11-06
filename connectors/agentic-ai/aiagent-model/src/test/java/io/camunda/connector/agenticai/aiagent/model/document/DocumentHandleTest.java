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
import io.camunda.connector.document.jackson.DocumentReferenceModel.InlineDocumentReferenceModel;
import org.junit.jupiter.api.Test;

class DocumentHandleTest {

  private static final String ID = "doc_0123456789ab";

  private final ObjectMapper objectMapper = new ObjectMapper();

  private String json(DocumentHandle handle) throws Exception {
    return objectMapper.writeValueAsString(handle);
  }

  @Test
  void writesAllFieldsInKeyOrderWithTheSizeAsANumber() throws Exception {
    final var handle =
        new DocumentHandle(
            DocumentHandle.TYPE,
            ID,
            "a.pdf",
            "application/pdf",
            123L,
            "The content is not loaded.");

    assertThat(json(handle))
        .isEqualTo(
            "{\"type\":\"camunda.agent_document\",\"id\":\"doc_0123456789ab\","
                + "\"fileName\":\"a.pdf\",\"contentType\":\"application/pdf\",\"size\":123,"
                + "\"note\":\"The content is not loaded.\"}");
  }

  @Test
  void writesTheIdOnly() throws Exception {
    assertThat(json(DocumentHandle.of(AgentDocumentId.of(ID))))
        .isEqualTo("{\"type\":\"camunda.agent_document\",\"id\":\"doc_0123456789ab\"}");
  }

  @Test
  void escapesTheFileName() throws Exception {
    final var handle =
        new DocumentHandle(DocumentHandle.TYPE, ID, "a \"b\" <c> \\ ä.txt", null, null, null);

    assertThat(json(handle))
        .isEqualTo(
            "{\"type\":\"camunda.agent_document\",\"id\":\"doc_0123456789ab\","
                + "\"fileName\":\"a \\\"b\\\" <c> \\\\ ä.txt\"}");
  }

  @Test
  void omitsBlankValuesAndKeepsASizeOfZero() throws Exception {
    final var handle = new DocumentHandle(DocumentHandle.TYPE, ID, " ", "", 0L, "  ");

    assertThat(json(handle))
        .isEqualTo("{\"type\":\"camunda.agent_document\",\"id\":\"doc_0123456789ab\",\"size\":0}");
  }

  @Test
  void readsTheDescriptorsOfAKnownDocumentWithoutItsContent() throws Exception {
    final var document =
        new AgentDocument(
            AgentDocumentId.of(ID),
            DocumentOrigin.userMessage(),
            new InlineDocumentReferenceModel("hello", "a.txt", "text/plain"));

    assertThat(json(DocumentHandle.of(document)))
        .isEqualTo(
            "{\"type\":\"camunda.agent_document\",\"id\":\"doc_0123456789ab\","
                + "\"fileName\":\"a.txt\",\"contentType\":\"text/plain\"}");
  }

  @Test
  void rejectsAnotherType() {
    assertThatThrownBy(() -> new DocumentHandle("other", ID, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
