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

import io.camunda.connector.agenticai.aiagent.document.DocumentContentDelivery.Decision;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentDelivery.Handled;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentDelivery.Outcome;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.api.document.Document;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DocumentContentDeliveryTest {

  private final DocumentContentDelivery delivery = TestDocuments.documentContentDelivery();

  private final Document text = createDocument("text", "text.txt");
  private final Document other = createDocument("other", "other.txt");
  private final Document third = createDocument("third", "third.txt");

  private final AgentDocuments known =
      AgentDocuments.empty()
          .register(text, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID))
          .register(other, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID))
          .register(third, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID));

  private static AgentDocumentId idOf(Document document) {
    return AgentDocumentId.of(document);
  }

  private Decision decide(List<AgentDocumentId> requested, Handled handled) {
    return delivery.deliver(known, requested, handled);
  }

  private static Outcome delivered(Document document) {
    return new Outcome.Delivered(
        AgentDocumentContent.of(idOf(document)).withDelivery(DocumentDelivery.CONTENT));
  }

  @Test
  void deliversTheContentOfAKnownDocument() {
    assertThat(decide(List.of(idOf(text)), Handled.of(Set.of())))
        .isEqualTo(new Decision(List.of(delivered(text)), new Handled(Set.of(idOf(text)))));
  }

  @Test
  void decidesInTheOrderOfTheFirstRequestAndOnceForEveryDocument() {
    assertThat(
            decide(List.of(idOf(other), idOf(third), idOf(other), idOf(text)), Handled.of(Set.of()))
                .outcomes())
        .containsExactly(delivered(other), delivered(third), delivered(text));
  }

  @Test
  void reportsADocumentThatIsHandledAlreadyAsInTheConversation() {
    assertThat(
            decide(
                List.of(idOf(text), idOf(third), idOf(other)),
                Handled.of(Set.of(idOf(text), idOf(third)))))
        .isEqualTo(
            new Decision(
                List.of(
                    new Outcome.AlreadyInConversation(idOf(text)),
                    new Outcome.AlreadyInConversation(idOf(third)),
                    delivered(other)),
                new Handled(Set.of(idOf(text), idOf(third), idOf(other)))));
  }

  @Test
  void reportsADocumentThatAnEarlierRequestDeliveredAsInTheConversation() {
    final var first = decide(List.of(idOf(text)), Handled.of(Set.of()));

    assertThat(decide(List.of(idOf(text)), first.handled()))
        .isEqualTo(
            new Decision(List.of(new Outcome.AlreadyInConversation(idOf(text))), first.handled()));
  }

  @Test
  void aRequestForADocumentTheAgentDoesNotKnowIsADefect() {
    final var unknown = AgentDocumentId.of("unknown");

    assertThatThrownBy(() -> decide(List.of(unknown), Handled.of(Set.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The content of the document 'unknown' is asked for, but the agent does not know it");
  }
}
