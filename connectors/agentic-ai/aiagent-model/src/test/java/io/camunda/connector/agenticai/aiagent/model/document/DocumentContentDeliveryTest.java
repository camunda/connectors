/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentContentDelivery.Decision;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentContentDelivery.Outcome;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DocumentContentDeliveryTest {

  private final AgentDocumentId text = AgentDocumentId.of("text");
  private final AgentDocumentId other = AgentDocumentId.of("other");
  private final AgentDocumentId third = AgentDocumentId.of("third");

  private static Outcome delivered(AgentDocumentId id) {
    return new Outcome.Delivered(
        AgentDocumentContent.of(id).withDelivery(DocumentDelivery.CONTENT));
  }

  @Test
  void deliversTheContentOfADocument() {
    assertThat(DocumentContentDelivery.of(Set.of()).deliver(List.of(text)))
        .isEqualTo(
            new Decision(List.of(delivered(text)), new DocumentContentDelivery(Set.of(text))));
  }

  @Test
  void decidesInTheOrderOfTheFirstRequestAndOnceForEveryDocument() {
    assertThat(
            DocumentContentDelivery.of(Set.of())
                .deliver(List.of(other, third, other, text))
                .outcomes())
        .containsExactly(delivered(other), delivered(third), delivered(text));
  }

  @Test
  void reportsADocumentThatIsInTheConversationAlreadyAsInTheConversation() {
    assertThat(DocumentContentDelivery.of(Set.of(text, third)).deliver(List.of(text, third, other)))
        .isEqualTo(
            new Decision(
                List.of(
                    new Outcome.AlreadyInConversation(text),
                    new Outcome.AlreadyInConversation(third),
                    delivered(other)),
                new DocumentContentDelivery(Set.of(text, third, other))));
  }

  @Test
  void reportsADocumentThatAnEarlierRequestDeliveredAsInTheConversation() {
    final var first = DocumentContentDelivery.of(Set.of()).deliver(List.of(text));

    assertThat(first.next().deliver(List.of(text)))
        .isEqualTo(new Decision(List.of(new Outcome.AlreadyInConversation(text)), first.next()));
  }
}
