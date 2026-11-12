/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.testutil.TestObjectMapperSupplier;
import io.camunda.connector.agenticai.testutil.TestDocumentHandles;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.InlineDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ObjectHandleRendererTest {

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
  private final ObjectHandleRenderer renderer =
      new ObjectHandleRenderer(TestObjectMapperSupplier.INSTANCE);

  private Document camundaDocument(String content) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType("application/pdf")
            .fileName("report.pdf")
            .build());
  }

  @Test
  void rendersADocumentAtAnyDepthAsItsHandleObjectAndCollectsTheDocuments() {
    final var first = camundaDocument("one");
    final var second = camundaDocument("two");

    final var rendered =
        renderer.render(
            Map.of("status", 200, "files", List.of(Map.of("file", first), Map.of("file", second))));

    assertThat(rendered.tree())
        .isEqualTo(
            Map.of(
                "status",
                200,
                "files",
                List.of(
                    Map.of("file", TestDocumentHandles.handleObject(first)),
                    Map.of("file", TestDocumentHandles.handleObject(second)))));
    assertThat(rendered.documents()).containsExactly(first, second);
  }

  record Part(String text, Map<String, Object> data) {}

  @Test
  void collectsTheDocumentsItRendersAsHandlesAlsoInsideARecord() {
    final var first = camundaDocument("one");
    final var second = camundaDocument("two");

    final var documents =
        renderer.documentsIn(
            List.of(new Part("text", Map.of("file", first)), Map.of("files", List.of(second))));

    assertThat(documents).containsExactly(first, second);
  }

  @Test
  void collectsADocumentWhoseReferenceTheAgentDoesNotSupportWithoutFailing() {
    final var inline = new InlineDocument("secret content", "report.txt", "text/plain");

    assertThat(renderer.documentsIn(Map.of("report", Map.of("file", inline))))
        .containsExactly(inline);
  }

  @Test
  void writesTheHandleAsCompactTextWithTheKeysTheModelReads() {
    final var handle =
        new AgentDocumentHandle(
            AgentDocumentHandle.TYPE, "doc_0123456789ab", "a.pdf", "application/pdf", 123L);

    assertThat(renderer.text(handle))
        .isEqualTo(
            "{\"type\":\"camunda.agenticai.document\",\"id\":\"doc_0123456789ab\","
                + "\"fileName\":\"a.pdf\",\"contentType\":\"application/pdf\",\"size\":123}");
  }

  @Test
  void writesTheHandleAsATreeWithTheKeysOfTheTextInOrder() {
    final var handle =
        new AgentDocumentHandle(
            AgentDocumentHandle.TYPE, "doc_0123456789ab", "a.pdf", "application/pdf", 123L);

    final var tree = renderer.tree(handle);

    assertThat(tree.isObject()).isTrue();
    assertThat(tree.fieldNames())
        .toIterable()
        .containsExactly("type", "id", "fileName", "contentType", "size");
    assertThat(tree.get("size").isNumber()).isTrue();
    assertThat(tree.toString()).isEqualTo(renderer.text(handle));
  }

  @Test
  void omitsTheValuesTheHandleDoesNotKnow() {
    assertThat(renderer.text(AgentDocumentHandle.of(AgentDocumentId.of("doc_0123456789ab"))))
        .isEqualTo("{\"type\":\"camunda.agenticai.document\",\"id\":\"doc_0123456789ab\"}");
  }

  @Test
  void keepsTheKeyOrderOfTheHandleInTheRenderedTree() {
    final var rendered = renderer.render(Map.of("file", camundaDocument("one")));

    @SuppressWarnings("unchecked")
    final var handle = (Map<String, Object>) ((Map<String, Object>) rendered.tree()).get("file");
    assertThat(handle.keySet()).containsExactly("type", "id", "fileName", "contentType");
  }

  @Test
  void reportsNoDocumentsForAnObjectWithoutDocuments() {
    final var rendered = renderer.render(Map.of("status", 200));

    assertThat(rendered.tree()).isEqualTo(Map.of("status", 200));
    assertThat(rendered.documents()).isEmpty();
  }

  @Test
  void keepsAnObjectThatLooksLikeAnIntrinsicFunctionAsItIs() {
    final var value =
        Map.of(
            "status", 200, "call", Map.of("camunda.function.type", "getJson", "params", List.of()));

    final var rendered = renderer.render(value);

    assertThat(rendered.tree()).isEqualTo(value);
    assertThat(rendered.documents()).isEmpty();
  }

  @Test
  void keepsAnObjectThatLooksLikeADocumentReferenceAsItIs() {
    final var reference =
        Map.of(
            "camunda.document.type",
            "camunda",
            "storeId",
            "in-memory",
            "documentId",
            "an-id",
            "metadata",
            Map.of("fileName", "report.pdf"));

    final var rendered = renderer.render(Map.of("file", reference));

    assertThat(rendered.tree()).isEqualTo(Map.of("file", reference));
    assertThat(rendered.documents()).isEmpty();
  }
}
