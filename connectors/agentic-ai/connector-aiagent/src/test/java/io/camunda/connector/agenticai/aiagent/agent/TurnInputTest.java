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
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments.NewDocument;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
import io.camunda.connector.api.document.Document;
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
                new NewDocument(first, DocumentOrigin.userMessage()),
                new NewDocument(second, DocumentOrigin.toolCall("search", "call-1"))));

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
                List.of(new NewDocument(first, DocumentOrigin.userMessage())))
            .followedBy(
                new TurnInput(
                    List.of(userMessage("b")),
                    List.of(new NewDocument(second, DocumentOrigin.event()))));

    assertThat(input.messages()).hasSize(2);
    assertThat(input.documents()).extracting(NewDocument::document).containsExactly(first, second);
  }

  private static ResponseDocumentSelection selection(
      String key, Document document, String description) {
    return new ResponseDocumentSelection(key, AgentDocumentId.of(document), description);
  }

  @Test
  void appliesTheSelectionsInOrderAndRegistersTheDocuments() {
    final var report = createDocument("report", "report.txt");
    final var summary = createDocument("summary", "summary.txt");
    final var input =
        new TurnInput(
            List.of(userMessage("hi")),
            List.of(
                new NewDocument(report, DocumentOrigin.toolCall("search", "call-1")),
                new NewDocument(summary, DocumentOrigin.userMessage())),
            List.of(selection("b", summary, null), selection("a", report, "first")));

    final var applied = input.applyTo(CONTEXT);

    assertThat(applied.responseDocuments())
        .containsExactly(selection("b", summary, null), selection("a", report, "first"));
    assertThat(applied.documents().find(AgentDocumentId.of(report))).isPresent();
    assertThat(applied.documents().find(AgentDocumentId.of(summary))).isPresent();
    assertThat(CONTEXT.responseDocuments()).isEmpty();
  }

  @Test
  void aLaterSelectionUnderTheSameKeyReplacesTheEarlierOneInPlace() {
    final var report = createDocument("report", "report.txt");
    final var summary = createDocument("summary", "summary.txt");
    final var input =
        new TurnInput(
            List.of(),
            List.of(),
            List.of(
                selection("b", report, null),
                selection("a", summary, "later"),
                selection("a", report, "latest")));

    final var applied =
        input.applyTo(CONTEXT.withResponseDocument(selection("a", report, "earlier")));

    assertThat(applied.responseDocuments())
        .containsExactly(selection("a", report, "latest"), selection("b", report, null));
  }

  @Test
  void aSelectionCanNameADocumentTheSameInputRegisters() {
    final var report = createDocument("report", "report.txt");
    final var input =
        new TurnInput(
            List.of(userMessage("hi")),
            List.of(new NewDocument(report, DocumentOrigin.toolCall("search", "call-1"))),
            List.of(selection("a", report, null)));

    final var applied = input.applyTo(CONTEXT);

    assertThat(applied.responseDocuments()).containsExactly(selection("a", report, null));
    assertThat(applied.documents().find(AgentDocumentId.of(report))).isPresent();
  }

  @Test
  void followedByKeepsTheSelectionsOfBothInputsInOrder() {
    final var report = createDocument("report", "report.txt");
    final var summary = createDocument("summary", "summary.txt");
    final var input =
        new TurnInput(List.of(userMessage("a")), List.of(), List.of(selection("a", report, "x")))
            .followedBy(
                new TurnInput(
                    List.of(userMessage("b")), List.of(), List.of(selection("a", summary, "y"))));

    assertThat(input.responseDocuments())
        .containsExactly(selection("a", report, "x"), selection("a", summary, "y"));
    assertThat(input.applyTo(CONTEXT).responseDocuments())
        .containsExactly(selection("a", summary, "y"));
  }
}
