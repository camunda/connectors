/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocument;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentContextResponseDocumentsTest {

  private static final DocumentFactoryImpl DOCUMENT_FACTORY =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  private static final AgentDocument REPORT = agentDocument("report");
  private static final AgentDocument SUMMARY = agentDocument("summary");

  private static final AgentContext CONTEXT =
      AgentContext.builder()
          .documents(AgentDocuments.empty().register(List.of(REPORT, SUMMARY)))
          .build();

  private static AgentDocument agentDocument(String content) {
    final var document =
        DOCUMENT_FACTORY.create(
            DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .fileName(content + ".txt")
                .build());
    return AgentDocument.of(document, AgentDocumentOrigin.toolCall("search", "call-1"));
  }

  @Test
  void hasNoSelectedResponseDocumentsWithoutSelections() {
    assertThat(CONTEXT.selectedResponseDocuments()).isEmpty();
  }

  @Test
  void pairsTheSelectionsInTheOrderTheyWereMadeWithTheirAgentDocuments() {
    final var context =
        CONTEXT
            .withResponseDocument(new ResponseDocumentSelection("second", SUMMARY.id(), null))
            .withResponseDocument(
                new ResponseDocumentSelection("first", REPORT.id(), "The report"));

    assertThat(context.selectedResponseDocuments())
        .containsExactly(
            new ResponseDocument("second", SUMMARY, null),
            new ResponseDocument("first", REPORT, "The report"));
  }

  @Test
  void aReplacedSelectionKeepsItsPosition() {
    final var context =
        CONTEXT
            .withResponseDocument(new ResponseDocumentSelection("a", REPORT.id(), null))
            .withResponseDocument(new ResponseDocumentSelection("b", REPORT.id(), null))
            .withResponseDocument(new ResponseDocumentSelection("a", SUMMARY.id(), "Replaced"));

    assertThat(context.selectedResponseDocuments())
        .containsExactly(
            new ResponseDocument("a", SUMMARY, "Replaced"),
            new ResponseDocument("b", REPORT, null));
  }

  @Test
  void failsForASelectionOfADocumentTheAgentDoesNotKnow() {
    final var unknown = AgentDocumentId.of("gone");
    final var context =
        CONTEXT.withResponseDocument(new ResponseDocumentSelection("lost", unknown, null));

    assertThatThrownBy(context::selectedResponseDocuments)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "The response document 'lost' is the document '%s', which the agent does not know"
                .formatted(unknown));
  }
}
