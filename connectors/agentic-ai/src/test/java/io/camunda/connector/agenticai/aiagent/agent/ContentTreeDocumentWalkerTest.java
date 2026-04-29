/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContentTreeDocumentWalkerTest {

  private final InMemoryDocumentStore documentStore = InMemoryDocumentStore.INSTANCE;
  private final DocumentFactory documentFactory = new DocumentFactoryImpl(documentStore);
  private final ContentTreeDocumentWalker walker = ContentTreeDocumentWalker.INSTANCE;

  @BeforeEach
  void setUp() {
    documentStore.clear();
  }

  @Test
  void extractsRootLevelDocument() {
    final var doc = createDocument("hello", "text/plain", "test.txt");
    final var result = walker.extractDocumentsFromContent(doc);
    assertThat(result).containsExactly(doc);
  }

  @Test
  void extractsDocumentFromMapValue() {
    final var doc = createDocument("hello", "text/plain", "test.txt");
    final var result = walker.extractDocumentsFromContent(Map.of("file", doc, "key", "value"));
    assertThat(result).containsExactly(doc);
  }

  @Test
  void extractsDocumentFromList() {
    final var doc = createDocument("hello", "text/plain", "test.txt");
    final var result = walker.extractDocumentsFromContent(List.of("text", doc, 42));
    assertThat(result).containsExactly(doc);
  }

  @Test
  void extractsDeeplyNestedDocuments() {
    final var doc = createDocument("hello", "text/plain", "test.txt");
    final var nested = Map.of("level1", Map.of("level2", List.of(Map.of("file", doc))));
    final var result = walker.extractDocumentsFromContent(nested);
    assertThat(result).containsExactly(doc);
  }

  @Test
  void extractsMultipleDocuments() {
    final var doc1 = createDocument("hello", "text/plain", "test.txt");
    final var doc2 = createDocument("<pdf>", "application/pdf", "report.pdf");
    final var content = new LinkedHashMap<String, Object>();
    content.put("text", doc1);
    content.put("report", doc2);
    content.put("other", "value");

    final var result = walker.extractDocumentsFromContent(content);
    assertThat(result).containsExactly(doc1, doc2);
  }

  @Test
  void returnsEmptyForContentWithoutDocuments() {
    final var result =
        walker.extractDocumentsFromContent(Map.of("key", "value", "list", List.of(1, 2, 3)));
    assertThat(result).isEmpty();
  }

  @Test
  void returnsEmptyForNullContent() {
    final var result = walker.extractDocumentsFromContent(null);
    assertThat(result).isEmpty();
  }

  @Test
  void returnsEmptyForScalarContent() {
    assertThat(walker.extractDocumentsFromContent("text")).isEmpty();
    assertThat(walker.extractDocumentsFromContent(42)).isEmpty();
    assertThat(walker.extractDocumentsFromContent(true)).isEmpty();
  }

  @Test
  void extractsDocumentFromArray() {
    final var doc = createDocument("hello", "text/plain", "test.txt");
    final var result = walker.extractDocumentsFromContent(new Object[] {"text", doc, 42});
    assertThat(result).containsExactly(doc);
  }

  @Test
  void extractsDocumentFromNestedArray() {
    final var doc = createDocument("hello", "text/plain", "test.txt");
    final var nested = Map.of("items", new Object[] {doc});
    final var result = walker.extractDocumentsFromContent(nested);
    assertThat(result).containsExactly(doc);
  }

  @Test
  void handlesNullValuesInMap() {
    final var doc = createDocument("hello", "text/plain", "test.txt");
    final var content = new LinkedHashMap<String, Object>();
    content.put("file", doc);
    content.put("missing", null);

    final var result = walker.extractDocumentsFromContent(content);
    assertThat(result).containsExactly(doc);
  }

  @Test
  void handlesNullElementsInList() {
    final var doc = createDocument("hello", "text/plain", "test.txt");
    final var content = new ArrayList<>();
    content.add(doc);
    content.add(null);
    content.add("text");

    final var result = walker.extractDocumentsFromContent(content);
    assertThat(result).containsExactly(doc);
  }

  private Document createDocument(String content, String contentType, String filename) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(filename)
            .build());
  }
}
