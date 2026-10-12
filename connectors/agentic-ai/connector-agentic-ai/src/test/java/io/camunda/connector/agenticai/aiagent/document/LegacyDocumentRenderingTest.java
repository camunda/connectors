/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.toolCallResultMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegacyDocumentRenderingTest {

  private final Document document =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
          .create(
              DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                  .contentType("text/plain")
                  .fileName("report.txt")
                  .build());

  @Test
  void marksTheDocumentsOfToolResultsAtAnyDepthAndNothingElse() {
    final var toolResult =
        toolCallResultMessage(
            List.of(
                ToolCallResult.builder()
                    .id("call-1")
                    .name("search")
                    .content(Map.of("files", List.of(document), "count", 1))
                    .build(),
                ToolCallResult.builder().id("call-2").name("download").content(document).build()));
    final var user = userMessage("hi");
    final var assistant = assistantMessage("ok");

    final var rendered = LegacyDocumentRendering.apply(List.of(user, toolResult, assistant));

    assertThat(rendered).hasSize(3);
    assertThat(rendered.get(0)).isSameAs(user);
    assertThat(rendered.get(2)).isSameAs(assistant);
    final var results = ((ToolCallResultMessage) rendered.get(1)).results();
    final var nested = (ObjectContent) results.get(0).content().getFirst();
    assertThat(((Map<?, ?>) nested.content()).get("files"))
        .asList()
        .singleElement()
        .isInstanceOfSatisfying(
            ReferenceRenderedDocument.class,
            marked -> assertThat(marked.delegate()).isSameAs(document));
    assertThat(((Map<?, ?>) nested.content()).get("count")).isEqualTo(1);
    assertThat(((DocumentContent) results.get(1).content().getFirst()).document())
        .isInstanceOf(ReferenceRenderedDocument.class);
  }

  @Test
  void returnsTheSameMessagesWhenNoToolResultHoldsADocument() {
    final List<Message> messages =
        List.of(
            userMessage("hi"),
            toolCallResultMessage(
                List.of(
                    ToolCallResult.builder()
                        .id("call-1")
                        .name("search")
                        .content(Map.of("answer", "42"))
                        .build())));

    assertThat(LegacyDocumentRendering.apply(messages)).isSameAs(messages);
  }
}
