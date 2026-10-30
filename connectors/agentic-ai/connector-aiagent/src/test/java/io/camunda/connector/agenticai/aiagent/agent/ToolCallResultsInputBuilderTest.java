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

import io.camunda.connector.agenticai.aiagent.agent.ToolCallResultsInputBuilder.Entry;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.api.document.Document;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolCallResultsInputBuilderTest {

  private final Document first = createDocument("first", "first.txt");
  private final Document nested = createDocument("nested", "nested.txt");
  private final Document third = createDocument("third", "third.txt");

  private static ToolCallResultContent result(String id, String name, Content... content) {
    return ToolCallResultContent.builder()
        .id(id)
        .name(name)
        .content(List.of(content))
        .completedAt(OffsetDateTime.parse("2026-10-05T10:00:00Z"))
        .build();
  }

  private List<Entry> entries() {
    return List.of(
        new Entry(
            result("c1", "alpha", textContent("one"), DocumentContent.documentContent(first)),
            List.of(first)),
        new Entry(result("c2", "beta", textContent("two")), List.of()),
        new Entry(
            result("c3", "gamma", objectContent(Map.of("file", nested, "again", third))),
            List.of(nested, third)));
  }

  @Test
  void withTheDocumentsFeatureTheResultsAreOneMessage() {
    final var messages = ToolCallResultsInputBuilder.build(entries(), true);

    assertThat(messages).singleElement().isInstanceOf(ToolCallResultMessage.class);
  }

  @Test
  void withTheDocumentsFeatureADocumentThatIsAWholeResultIsItsTagAndANestedOneStaysADocument() {
    final var messages = ToolCallResultsInputBuilder.build(entries(), true);

    final var results = ((ToolCallResultMessage) messages.getFirst()).results();
    assertThat(results.get(0).content())
        .containsExactly(textContent("one"), DocumentHandle.referenceFor(first));
    assertThat(results.get(1).content()).containsExactly(textContent("two"));
    assertThat(results.get(2).content())
        .containsExactly(objectContent(Map.of("file", nested, "again", third)));
  }

  @Test
  void withoutTheDocumentsFeatureTheDocumentsFollowTheResultsInAMessage() {
    final var messages = ToolCallResultsInputBuilder.build(entries(), false);

    assertThat(messages).hasSize(2);
    assertThat(messages.get(0)).isInstanceOf(ToolCallResultMessage.class);
    assertThat(messages.get(1))
        .isInstanceOfSatisfying(
            UserMessage.class,
            message -> {
              assertThat(message.metadata())
                  .containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
              assertThat(message.content())
                  .first()
                  .isEqualTo(textContent(ToolCallResultsInputBuilder.TOOL_CALL_DOCUMENTS_PREAMBLE));
              assertThat(message.content())
                  .contains(
                      DocumentContent.documentContent(first),
                      DocumentContent.documentContent(nested),
                      DocumentContent.documentContent(third));
            });
    final var results = ((ToolCallResultMessage) messages.getFirst()).results();
    assertThat(results.get(0).content())
        .containsExactly(textContent("one"), DocumentContent.documentContent(first));
  }
}
