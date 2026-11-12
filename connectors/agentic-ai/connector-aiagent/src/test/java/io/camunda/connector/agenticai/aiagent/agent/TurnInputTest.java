/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static io.camunda.connector.agenticai.aiagent.model.testutil.TestDocuments.createDocument;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.testutil.TestDocuments;
import java.util.List;
import org.junit.jupiter.api.Test;

class TurnInputTest {

  private static final AgentContext CONTEXT =
      AgentContext.empty().withFeatures(List.of(AgentFeatures.DOCUMENTS));

  @Test
  void appliesItsDocumentsToTheDocumentsOfTheContextInOrder() {
    final var first =
        AgentDocument.of(
            createDocument("one", "one.txt"),
            AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID));
    final var second =
        AgentDocument.of(
            createDocument("two", "two.txt"), AgentDocumentOrigin.toolCall("search", "call-1"));
    final var input = new TurnInput(List.of(userMessage("hi")), List.of(first, second));

    final var applied = input.applyTo(CONTEXT);

    assertThat(applied.documents().all()).containsExactly(first, second);
    assertThat(applied.features()).isEqualTo(CONTEXT.features());
    // the same input gives the same context
    assertThat(input.applyTo(CONTEXT)).isEqualTo(applied);
  }

  @Test
  void appliesNothingWithoutDocuments() {
    assertThat(new TurnInput(List.of(userMessage("hi"))).applyTo(CONTEXT)).isSameAs(CONTEXT);
  }

  @Test
  void followedByKeepsTheMessagesAndDocumentsOfBothInputsInOrder() {
    final var firstMessage = userMessage("a");
    final var secondMessage = userMessage("b");
    final var first =
        AgentDocument.of(
            createDocument("one", "one.txt"),
            AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID));
    final var second =
        AgentDocument.of(
            createDocument("two", "two.txt"), AgentDocumentOrigin.event(TestDocuments.MESSAGE_ID));

    final var input =
        new TurnInput(List.of(firstMessage), List.of(first))
            .followedBy(new TurnInput(List.of(secondMessage), List.of(second)));

    assertThat(input)
        .isEqualTo(new TurnInput(List.of(firstMessage, secondMessage), List.of(first, second)));
  }
}
