/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.model.tool.ToolCallResult;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ToolCallResultDocumentExtractorTest {

  private final InMemoryDocumentStore documentStore = InMemoryDocumentStore.INSTANCE;
  private final DocumentFactory documentFactory = new DocumentFactoryImpl(documentStore);
  private final ToolCallResultDocumentExtractor extractor = new ToolCallResultDocumentExtractor();

  @BeforeEach
  void setUp() {
    documentStore.clear();
  }

  @Nested
  class ExtractFromContentTree {

    @Test
    void extractsRootLevelDocument() {
      final var doc = createDocument("hello", "text/plain", "test.txt");
      final var result = extractor.extractDocuments((Object) doc);
      assertThat(result).containsExactly(doc);
    }

    @Test
    void extractsDocumentFromMapValue() {
      final var doc = createDocument("hello", "text/plain", "test.txt");
      final var result = extractor.extractDocuments((Object) Map.of("file", doc, "key", "value"));
      assertThat(result).containsExactly(doc);
    }

    @Test
    void extractsDocumentFromList() {
      final var doc = createDocument("hello", "text/plain", "test.txt");
      final var result = extractor.extractDocuments((Object) List.of("text", doc, 42));
      assertThat(result).containsExactly(doc);
    }

    @Test
    void extractsDeeplyNestedDocuments() {
      final var doc = createDocument("hello", "text/plain", "test.txt");
      final var nested = Map.of("level1", Map.of("level2", List.of(Map.of("file", doc))));
      final var result = extractor.extractDocuments((Object) nested);
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

      final var result = extractor.extractDocuments((Object) content);
      assertThat(result).containsExactly(doc1, doc2);
    }

    @Test
    void returnsEmptyForContentWithoutDocuments() {
      final var result =
          extractor.extractDocuments((Object) Map.of("key", "value", "list", List.of(1, 2, 3)));
      assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyForNullContent() {
      final var result = extractor.extractDocuments((Object) null);
      assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyForScalarContent() {
      assertThat(extractor.extractDocuments((Object) "text")).isEmpty();
      assertThat(extractor.extractDocuments((Object) 42)).isEmpty();
      assertThat(extractor.extractDocuments((Object) true)).isEmpty();
    }

    @Test
    void extractsDocumentFromArray() {
      final var doc = createDocument("hello", "text/plain", "test.txt");
      final var result = extractor.extractDocuments((Object) new Object[] {"text", doc, 42});
      assertThat(result).containsExactly(doc);
    }

    @Test
    void extractsDocumentFromNestedArray() {
      final var doc = createDocument("hello", "text/plain", "test.txt");
      final var nested = Map.of("items", new Object[] {doc});
      final var result = extractor.extractDocuments((Object) nested);
      assertThat(result).containsExactly(doc);
    }

    @Test
    void handlesNullValuesInMap() {
      final var doc = createDocument("hello", "text/plain", "test.txt");
      final var content = new LinkedHashMap<String, Object>();
      content.put("file", doc);
      content.put("missing", null);

      final var result = extractor.extractDocuments((Object) content);
      assertThat(result).containsExactly(doc);
    }

    @Test
    void handlesNullElementsInList() {
      final var doc = createDocument("hello", "text/plain", "test.txt");
      final var content = new ArrayList<>();
      content.add(doc);
      content.add(null);
      content.add("text");

      final var result = extractor.extractDocuments((Object) content);
      assertThat(result).containsExactly(doc);
    }
  }

  @Nested
  class ExtractFromToolCallResults {

    @Test
    void groupsDocumentsByToolCall() {
      final var doc1 = createDocument("hello", "text/plain", "test.txt");
      final var doc2 = createDocument("<pdf>", "application/pdf", "report.pdf");

      final var results =
          List.of(
              ToolCallResult.builder()
                  .id("call_1")
                  .name("tool_a")
                  .content(Map.of("file", doc1))
                  .build(),
              ToolCallResult.builder()
                  .id("call_2")
                  .name("tool_b")
                  .content(Map.of("report", doc2))
                  .build());

      final var extracted = extractor.extractDocuments(results);

      assertThat(extracted).hasSize(2);
      assertThat(extracted.get(0))
          .satisfies(
              e -> {
                assertThat(e.toolCallId()).isEqualTo("call_1");
                assertThat(e.toolCallName()).isEqualTo("tool_a");
                assertThat(e.documents()).containsExactly(doc1);
              });
      assertThat(extracted.get(1))
          .satisfies(
              e -> {
                assertThat(e.toolCallId()).isEqualTo("call_2");
                assertThat(e.toolCallName()).isEqualTo("tool_b");
                assertThat(e.documents()).containsExactly(doc2);
              });
    }

    @Test
    void excludesToolCallsWithoutDocuments() {
      final var doc = createDocument("hello", "text/plain", "test.txt");

      final var results =
          List.of(
              ToolCallResult.builder()
                  .id("call_1")
                  .name("tool_a")
                  .content(Map.of("file", doc))
                  .build(),
              ToolCallResult.builder()
                  .id("call_2")
                  .name("tool_b")
                  .content("plain text result")
                  .build());

      final var extracted = extractor.extractDocuments(results);

      assertThat(extracted).hasSize(1);
      assertThat(extracted.getFirst().toolCallId()).isEqualTo("call_1");
    }

    @Test
    void returnsEmptyWhenNoToolCallsContainDocuments() {
      final var results =
          List.of(
              ToolCallResult.builder().id("call_1").name("tool_a").content("text result").build());

      assertThat(extractor.extractDocuments(results)).isEmpty();
    }

    @Test
    void handlesNullNameAndId() {
      final var doc = createDocument("hello", "text/plain", "test.txt");

      final var results = List.of(ToolCallResult.builder().content(Map.of("file", doc)).build());

      final var extracted = extractor.extractDocuments(results);

      assertThat(extracted).hasSize(1);
      assertThat(extracted.getFirst())
          .satisfies(
              e -> {
                assertThat(e.toolCallId()).isEmpty();
                assertThat(e.toolCallName()).isEqualTo("unknown");
              });
    }
  }

  private Document createDocument(String content, String contentType, String filename) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(filename)
            .build());
  }
}
