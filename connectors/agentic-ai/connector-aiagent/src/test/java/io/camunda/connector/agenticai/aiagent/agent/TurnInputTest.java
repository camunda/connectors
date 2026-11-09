/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static io.camunda.connector.agenticai.testutil.TestDocuments.createDocument;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import java.util.List;
import org.junit.jupiter.api.Test;

class TurnInputTest {

  private static final AgentContext CONTEXT =
      AgentContext.empty().withFeatures(List.of(AgentFeatures.DOCUMENTS));

  @Test
  void appliesItsDocumentsToTheDocumentsOfTheContextInOrder() {
    final var first = createDocument("one", "one.txt");
    final var second = createDocument("two", "two.txt");
    final var input =
        new TurnInput(
            List.of(userMessage("hi")),
            List.of(
                AgentDocument.of(first, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID)),
                AgentDocument.of(second, AgentDocumentOrigin.toolCall("search", "call-1"))));

    final var applied = input.applyTo(CONTEXT);

    assertThat(applied.documents().all())
        .extracting(AgentDocument::id)
        .containsExactly(AgentDocumentId.of(first), AgentDocumentId.of(second));
    assertThat(applied.features()).isEqualTo(CONTEXT.features());
    // the same input gives the same context
    assertThat(input.applyTo(CONTEXT)).isEqualTo(applied);
  }

  @Test
  void appliesNothingWithoutDocuments() {
    assertThat(new TurnInput(List.of(userMessage("hi"))).applyTo(CONTEXT)).isSameAs(CONTEXT);
  }

  @Test
  void followedByKeepsTheDocumentsOfBothInputsInOrder() {
    final var first = createDocument("one", "one.txt");
    final var second = createDocument("two", "two.txt");

    final var input =
        new TurnInput(
                List.of(userMessage("a")),
                List.of(
                    AgentDocument.of(
                        first, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID))))
            .followedBy(
                new TurnInput(
                    List.of(userMessage("b")),
                    List.of(
                        AgentDocument.of(
                            second, AgentDocumentOrigin.event(TestDocuments.MESSAGE_ID)))));

    assertThat(input.messages()).hasSize(2);
    assertThat(input.documents())
        .extracting(AgentDocument::id)
        .containsExactly(AgentDocumentId.of(first), AgentDocumentId.of(second));
  }
}
