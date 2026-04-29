/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.agent.ContentTreeDocumentWalker.extractDocumentsFromContent;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ContentTreeDocumentWalkerTest {

  private final InMemoryDocumentStore documentStore = InMemoryDocumentStore.INSTANCE;
  private final DocumentFactory documentFactory = new DocumentFactoryImpl(documentStore);

  @BeforeEach
  void setUp() {
    documentStore.clear();
  }

  @Test
  void extractsRootLevelDocument() {
    final var doc = createDocument("hello", "text/plain", "test.txt");
    assertThat(extractDocumentsFromContent(doc)).containsExactly(doc);
  }

  @Test
  void extractsDocumentFromMapValue() {
    final var doc = createDocument("hello", "text/plain", "test.txt");
    assertThat(extractDocumentsFromContent(Map.of("file", doc, "key", "value")))
        .containsExactly(doc);
  }

  @Test
  void extractsDocumentFromList() {
    final var doc = createDocument("hello", "text/plain", "test.txt");
    assertThat(extractDocumentsFromContent(List.of("text", doc, 42))).containsExactly(doc);
  }

  @Test
  void extractsDeeplyNestedDocuments() {
    final var doc = createDocument("hello", "text/plain", "test.txt");
    final var nested = Map.of("level1", Map.of("level2", List.of(Map.of("file", doc))));
    assertThat(extractDocumentsFromContent(nested)).containsExactly(doc);
  }

  @Test
  void extractsMultipleDocuments() {
    final var doc1 = createDocument("hello", "text/plain", "test.txt");
    final var doc2 = createDocument("<pdf>", "application/pdf", "report.pdf");
    final var content = new LinkedHashMap<String, Object>();
    content.put("text", doc1);
    content.put("report", doc2);
    content.put("other", "value");

    assertThat(extractDocumentsFromContent(content)).containsExactly(doc1, doc2);
  }

  @Test
  void returnsEmptyForContentWithoutDocuments() {
    assertThat(extractDocumentsFromContent(Map.of("key", "value", "list", List.of(1, 2, 3))))
        .isEmpty();
  }

  @ParameterizedTest
  @MethodSource("nullAndScalars")
  void returnsEmptyForNullOrScalarContent(Object content) {
    assertThat(extractDocumentsFromContent(content)).isEmpty();
  }

  static Stream<Arguments> nullAndScalars() {
    return Stream.of(
        Arguments.of((Object) null), Arguments.of("text"), Arguments.of(42), Arguments.of(true));
  }

  @Test
  void extractsDocumentFromArray() {
    final var doc = createDocument("hello", "text/plain", "test.txt");
    assertThat(extractDocumentsFromContent(new Object[] {"text", doc, 42})).containsExactly(doc);
  }

  @Test
  void extractsDocumentFromNestedArray() {
    final var doc = createDocument("hello", "text/plain", "test.txt");
    final var nested = Map.of("items", new Object[] {doc});
    assertThat(extractDocumentsFromContent(nested)).containsExactly(doc);
  }

  @Test
  void handlesNullValuesInMap() {
    final var doc = createDocument("hello", "text/plain", "test.txt");
    final var content = new LinkedHashMap<String, Object>();
    content.put("file", doc);
    content.put("missing", null);

    assertThat(extractDocumentsFromContent(content)).containsExactly(doc);
  }

  @Test
  void handlesNullElementsInList() {
    final var doc = createDocument("hello", "text/plain", "test.txt");
    final var content = new ArrayList<>();
    content.add(doc);
    content.add(null);
    content.add("text");

    assertThat(extractDocumentsFromContent(content)).containsExactly(doc);
  }

  private Document createDocument(String content, String contentType, String filename) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(filename)
            .build());
  }
}
