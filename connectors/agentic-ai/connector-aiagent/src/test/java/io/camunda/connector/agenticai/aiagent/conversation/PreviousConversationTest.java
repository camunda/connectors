/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.conversation;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreviousConversationTest {

  private static final AgentDocumentId LOADED = AgentDocumentId.of("loaded");

  private static AgentDocumentContent block(AgentDocumentId id, DocumentDelivery delivery) {
    return new AgentDocumentContent(id, delivery, null);
  }

  private static PreviousConversation conversationWithTheLoadedDocumentFirst() {
    return TurnReconstructor.reconstruct(
        List.of(
            UserMessage.builder()
                .content(
                    List.of(
                        block(LOADED, DocumentDelivery.CONTENT),
                        block(AgentDocumentId.of("referenced"), DocumentDelivery.REFERENCE)))
                .build(),
            assistantMessage("one"),
            UserMessage.builder().content(List.of()).build(),
            assistantMessage("two")));
  }

  @Test
  void documentIdsWithContent_holdsTheDocumentsWithTheDeliveryContentWithinTheWindow() {
    assertThat(conversationWithTheLoadedDocumentFirst().documentIdsWithContent(10, 0))
        .containsExactly(LOADED);
  }

  @Test
  void documentIdsWithContent_leavesOutTheDocumentsOfMessagesThatLeftTheWindow() {
    assertThat(conversationWithTheLoadedDocumentFirst().documentIdsWithContent(2, 0)).isEmpty();
  }

  @Test
  void documentIdsWithContent_isEmptyForAnEmptyConversation() {
    assertThat(TurnReconstructor.reconstruct(List.of()).documentIdsWithContent(10, 0)).isEmpty();
  }

  @Test
  void
      documentIdsWithContent_keepsTheDocumentsOfTheOldestMessageThatFitsTheWindowWithTheAddedMessages() {
    // four messages: the loaded document is in the oldest one
    assertThat(conversationWithTheLoadedDocumentFirst().documentIdsWithContent(5, 1))
        .containsExactly(LOADED);
  }

  @Test
  void documentIdsWithContent_leavesOutTheDocumentsOfTheOldestMessageThatTheAddedMessagesPushOut() {
    assertThat(conversationWithTheLoadedDocumentFirst().documentIdsWithContent(5, 2)).isEmpty();
  }

  @Test
  void documentIdsWithContent_isEmptyWhenTheAddedMessagesExceedTheWindow() {
    assertThat(conversationWithTheLoadedDocumentFirst().documentIdsWithContent(3, 5)).isEmpty();
  }

  @Test
  void
      documentIdsWithContent_keepsTheSystemMessageAndLeavesOutTheTurnsWhenTheAddedMessagesExceedTheWindow() {
    final var conversation =
        TurnReconstructor.reconstruct(
            List.of(
                systemMessage("system"),
                UserMessage.builder()
                    .content(List.of(block(LOADED, DocumentDelivery.CONTENT)))
                    .build(),
                assistantMessage("one")));

    assertThat(conversation.documentIdsWithContent(3, 5)).isEmpty();
    assertThat(conversation.documentIdsWithContent(3, 0)).containsExactly(LOADED);
  }
}
