/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.ExternalDocument;
import io.camunda.connector.runtime.core.document.InlineDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UnsupportedDocumentReferenceExceptionTest {

  private static final Document EXTERNAL =
      new ExternalDocument("https://example.com/report.pdf?token=secret", "report.pdf", u -> null);
  private static final Document INLINE =
      new InlineDocument("secret content", "report.txt", "text/plain");

  @Test
  void onlyCamundaDocumentsAreSupported() {
    final var camundaDocument =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
            .create(DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8)).build());

    assertThat(AgentDocument.isSupported(camundaDocument)).isTrue();
    assertThat(AgentDocument.isSupported(camundaDocument.reference())).isTrue();
    assertThat(AgentDocument.isSupported(EXTERNAL)).isFalse();
    assertThat(AgentDocument.isSupported(INLINE)).isFalse();
  }

  @Test
  void namesTheKindOfTheDocumentAndTheUserPrompt() {
    assertThat(UnsupportedDocumentReferenceException.inUserPrompt(EXTERNAL))
        .hasMessage(
            "Only Camunda documents can be passed to the AI Agent, but the user prompt contains an external document.");
    assertThat(UnsupportedDocumentReferenceException.inUserPrompt(INLINE))
        .hasMessage(
            "Only Camunda documents can be passed to the AI Agent, but the user prompt contains an inline document.");
  }

  @Test
  void namesTheToolOfTheResultWhenItHasOne() {
    assertThat(UnsupportedDocumentReferenceException.inToolCallResult("getReport", INLINE))
        .hasMessage(
            "Only Camunda documents can be passed to the AI Agent, but the result of the tool 'getReport' contains an inline document.");
    assertThat(UnsupportedDocumentReferenceException.inToolCallResult("getReport", EXTERNAL))
        .hasMessage(
            "Only Camunda documents can be passed to the AI Agent, but the result of the tool 'getReport' contains an external document.");
  }

  @Test
  void leavesOutTheToolOfAResultWithoutAName() {
    assertThat(UnsupportedDocumentReferenceException.inToolCallResult(null, EXTERNAL))
        .hasMessage(
            "Only Camunda documents can be passed to the AI Agent, but the result of a tool contains an external document.");
    assertThat(UnsupportedDocumentReferenceException.inToolCallResult(" ", INLINE))
        .hasMessage(
            "Only Camunda documents can be passed to the AI Agent, but the result of a tool contains an inline document.");
  }

  @Test
  void namesTheEvent() {
    assertThat(UnsupportedDocumentReferenceException.inEvent(EXTERNAL))
        .hasMessage(
            "Only Camunda documents can be passed to the AI Agent, but an event contains an external document.");
  }
}
