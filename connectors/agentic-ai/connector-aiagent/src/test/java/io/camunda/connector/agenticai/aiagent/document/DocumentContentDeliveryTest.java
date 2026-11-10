/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static io.camunda.connector.agenticai.testutil.TestDocuments.createDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.document.DocumentContentDelivery.Outcome;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DocumentContentDeliveryTest {

  private final DocumentContentDelivery delivery = TestDocuments.documentContentDelivery();

  private final Document text = createDocument("text", "text.txt");
  private final Document other = createDocument("other", "other.txt");
  private final Document archive =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
          .create(
              DocumentCreationRequest.from("PK".getBytes(StandardCharsets.UTF_8))
                  .contentType("application/zip")
                  .fileName("archive.zip")
                  .build());

  private final AgentDocuments known =
      AgentDocuments.empty()
          .register(text, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID))
          .register(other, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID))
          .register(archive, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID));

  private static AgentDocumentId idOf(Document document) {
    return AgentDocumentId.of(document);
  }

  private static Outcome delivered(Document document) {
    return new Outcome.Delivered(
        AgentDocumentContent.of(idOf(document)).withDelivery(DocumentDelivery.CONTENT));
  }

  private Outcome withheldArchive() {
    return new Outcome.Withheld(
        idOf(archive),
        "Documents of type application/zip cannot be loaded. If one of your tools can process the document, pass the handle to it.");
  }

  @Test
  void deliversTheContentOfAnAllowedDocument() {
    assertThat(
            delivery.deliver(known, List.of(idOf(text)), Set.of(), TestDocuments.documentFactory()))
        .containsExactly(delivered(text));
  }

  @Test
  void restoresTheDocumentWithTheDocumentFactoryOfTheJob() {
    final var restored = new ArrayList<AgentDocumentId>();
    final var jobDocumentFactory =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE) {
          @Override
          public Document resolve(DocumentReference reference) {
            restored.add(AgentDocumentId.of(reference));
            return super.resolve(reference);
          }
        };

    assertThat(delivery.deliver(known, List.of(idOf(text)), Set.of(), jobDocumentFactory))
        .containsExactly(delivered(text));
    assertThat(restored).containsExactly(idOf(text));
  }

  @Test
  void withholdsTheContentOfARejectedDocumentWithAStandaloneNote() {
    assertThat(
            delivery.deliver(
                known, List.of(idOf(archive)), Set.of(), TestDocuments.documentFactory()))
        .containsExactly(withheldArchive());
  }

  @Test
  void decidesInTheOrderOfTheFirstRequestAndOnceForEveryDocument() {
    assertThat(
            delivery.deliver(
                known,
                List.of(idOf(other), idOf(archive), idOf(other), idOf(text)),
                Set.of(),
                TestDocuments.documentFactory()))
        .hasSize(3)
        .containsExactly(delivered(other), withheldArchive(), delivered(text));
  }

  @Test
  void reportsADocumentThatIsHandledAlreadyAsInTheConversation() {
    assertThat(
            delivery.deliver(
                known,
                List.of(idOf(text), idOf(archive), idOf(other)),
                Set.of(idOf(text), idOf(archive)),
                TestDocuments.documentFactory()))
        .containsExactly(
            new Outcome.AlreadyInConversation(idOf(text)),
            new Outcome.AlreadyInConversation(idOf(archive)),
            delivered(other));
  }

  @Test
  void aRequestForADocumentTheAgentDoesNotKnowIsADefect() {
    final var unknown = AgentDocumentId.of("unknown");

    assertThatThrownBy(() ->
            delivery.deliver(known, List.of(unknown), Set.of(), TestDocuments.documentFactory()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The content of the document 'unknown' is asked for, but the agent does not know it");
  }
}
