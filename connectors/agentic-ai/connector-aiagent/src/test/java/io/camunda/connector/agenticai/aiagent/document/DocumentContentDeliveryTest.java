/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static io.camunda.connector.agenticai.testutil.TestDocuments.createDocument;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.document.DocumentContentDelivery.Decision;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentDelivery.Outcome;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.api.document.Document;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DocumentContentDeliveryTest {

  private final Document text = createDocument("text", "text.txt");
  private final Document other = createDocument("other", "other.txt");
  private final Document third = createDocument("third", "third.txt");

  private static AgentDocumentId idOf(Document document) {
    return AgentDocumentId.of(document);
  }

  private static Outcome delivered(Document document) {
    return new Outcome.Delivered(
        AgentDocumentContent.of(idOf(document)).withDelivery(DocumentDelivery.CONTENT));
  }

  @Test
  void deliversTheContentOfADocument() {
    assertThat(DocumentContentDelivery.of(Set.of()).deliver(List.of(idOf(text))))
        .isEqualTo(
            new Decision(
                List.of(delivered(text)), new DocumentContentDelivery(Set.of(idOf(text)))));
  }

  @Test
  void decidesInTheOrderOfTheFirstRequestAndOnceForEveryDocument() {
    assertThat(
            DocumentContentDelivery.of(Set.of())
                .deliver(List.of(idOf(other), idOf(third), idOf(other), idOf(text)))
                .outcomes())
        .containsExactly(delivered(other), delivered(third), delivered(text));
  }

  @Test
  void reportsADocumentThatIsInTheConversationAlreadyAsInTheConversation() {
    assertThat(
            DocumentContentDelivery.of(Set.of(idOf(text), idOf(third)))
                .deliver(List.of(idOf(text), idOf(third), idOf(other))))
        .isEqualTo(
            new Decision(
                List.of(
                    new Outcome.AlreadyInConversation(idOf(text)),
                    new Outcome.AlreadyInConversation(idOf(third)),
                    delivered(other)),
                new DocumentContentDelivery(Set.of(idOf(text), idOf(third), idOf(other)))));
  }

  @Test
  void reportsADocumentThatAnEarlierRequestDeliveredAsInTheConversation() {
    final var first = DocumentContentDelivery.of(Set.of()).deliver(List.of(idOf(text)));

    assertThat(first.next().deliver(List.of(idOf(text))))
        .isEqualTo(
            new Decision(List.of(new Outcome.AlreadyInConversation(idOf(text))), first.next()));
  }
}
