/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent.objectContent;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static io.camunda.connector.agenticai.testutil.TestDocuments.createDocument;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.document.DocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments.NewDocument;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.api.document.Document;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolCallResultsInputBuilderTest {

  private final Document first = createDocument("first", "first.txt");
  private final Document nested = createDocument("nested", "nested.txt");
  private final Document third = createDocument("third", "third.txt");

  private final ToolCallResultsInputBuilder builder = new ToolCallResultsInputBuilder();

  private static final AgentContext WITH_DOCUMENTS =
      AgentContext.builder().features(List.of(AgentFeatures.DOCUMENTS)).build();
  private static final AgentContext WITHOUT_DOCUMENTS = AgentContext.empty();

  private static ToolCallResultContent result(String id, String name, Content... content) {
    return ToolCallResultContent.builder()
        .id(id)
        .name(name)
        .content(List.of(content))
        .completedAt(OffsetDateTime.parse("2026-10-05T10:00:00Z"))
        .build();
  }

  private static ToolCallBatch batch(ToolCallBatch.Entry... entries) {
    final var calls =
        Arrays.stream(entries)
            .map(entry -> new ToolCall(entry.result().id(), entry.result().name(), Map.of()))
            .toList();
    return ToolCallBatch.of(calls).withEntries(List.of(entries));
  }

  private ToolCallBatch batch() {
    return batch(
        new ToolCallBatch.Entry(
            result("c1", "alpha", textContent("one"), DocumentContent.documentContent(first)),
            List.of(first)),
        new ToolCallBatch.Entry(result("c2", "beta", textContent("two")), List.of()),
        new ToolCallBatch.Entry(
            result("c3", "gamma", objectContent(Map.of("file", nested, "again", third))),
            List.of(nested, third)));
  }

  @Test
  void withTheDocumentsFeatureTheResultsAreOneMessageAndTheDocumentsAreReportedInCallOrder() {
    final var input = builder.build(WITH_DOCUMENTS, batch());

    assertThat(input.messages()).singleElement().isInstanceOf(ToolCallResultMessage.class);
    assertThat(input.documents())
        .containsExactly(
            new NewDocument(first, DocumentOrigin.toolCall("alpha", "c1")),
            new NewDocument(nested, DocumentOrigin.toolCall("gamma", "c3")),
            new NewDocument(third, DocumentOrigin.toolCall("gamma", "c3")));
  }

  @Test
  void withTheDocumentsFeatureADocumentThatIsAWholeResultIsItsTagAndANestedOneStaysADocument() {
    final var input = builder.build(WITH_DOCUMENTS, batch());

    final var results = ((ToolCallResultMessage) input.messages().getFirst()).results();
    assertThat(results.get(0).content())
        .containsExactly(textContent("one"), AgentDocumentContent.of(AgentDocumentId.of(first)));
    assertThat(results.get(1).content()).containsExactly(textContent("two"));
    assertThat(results.get(2).content())
        .containsExactly(objectContent(Map.of("file", nested, "again", third)));
  }

  @Test
  void withTheDocumentsFeatureAResultWithoutDocumentsReportsNone() {
    final var input =
        builder.build(
            WITH_DOCUMENTS,
            batch(new ToolCallBatch.Entry(result("c2", "beta", textContent("two")), List.of())));

    assertThat(input.messages()).singleElement().isInstanceOf(ToolCallResultMessage.class);
    assertThat(input.documents()).isEmpty();
  }

  @Test
  void withoutTheDocumentsFeatureTheDocumentsFollowTheResultsInAMessageAndNoneIsReported() {
    final var input = builder.build(WITHOUT_DOCUMENTS, batch());

    assertThat(input.documents()).isEmpty();
    assertThat(input.messages()).hasSize(2);
    assertThat(input.messages().get(0)).isInstanceOf(ToolCallResultMessage.class);
    assertThat(input.messages().get(1))
        .isInstanceOfSatisfying(
            UserMessage.class,
            message -> {
              assertThat(message.metadata())
                  .containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
              assertThat(message.content())
                  .containsExactly(
                      textContent(ToolCallResultsInputBuilder.TOOL_CALL_DOCUMENTS_PREAMBLE),
                      textContent(DocumentReferenceXmlTag.from(first, "c1", "alpha").toXml()),
                      DocumentContent.documentContent(first),
                      textContent(DocumentReferenceXmlTag.from(nested, "c3", "gamma").toXml()),
                      DocumentContent.documentContent(nested),
                      textContent(DocumentReferenceXmlTag.from(third, "c3", "gamma").toXml()),
                      DocumentContent.documentContent(third));
            });
    final var results = ((ToolCallResultMessage) input.messages().getFirst()).results();
    assertThat(results.get(0).content())
        .containsExactly(textContent("one"), DocumentContent.documentContent(first));
  }
}
