/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandler;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistryImpl;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ToolCallResultDocumentExtractorTest {

  private static final String MANAGED_TOOL = "typed_tool";

  private final InMemoryDocumentStore documentStore = InMemoryDocumentStore.INSTANCE;
  private final DocumentFactory documentFactory = new DocumentFactoryImpl(documentStore);

  @Mock private GatewayToolHandler handler;

  private ToolCallResultDocumentExtractor extractor;

  @BeforeEach
  void setUp() {
    documentStore.clear();
    when(handler.type()).thenReturn("typed");
    lenient()
        .when(handler.isGatewayManaged(any()))
        .thenAnswer(inv -> MANAGED_TOOL.equals(inv.getArgument(0)));
    extractor =
        new ToolCallResultDocumentExtractor(new GatewayToolHandlerRegistryImpl(List.of(handler)));
  }

  @Test
  void extractsDocumentsFromResultsNotMatchingAnyHandler() {
    final var doc = createDocument("hello", "text/plain", "test.txt");
    final var result = unmanagedResult("call_1", Map.of("file", doc));

    final var extracted = extractor.extractDocuments(List.of(result));

    assertThat(extracted).hasSize(1);
    assertThat(extracted.getFirst().documents()).containsExactly(doc);
    verify(handler, never()).extractDocuments(any());
  }

  @Test
  void extractsDocumentsFromManagedResultViaHandler() {
    final var doc = createDocument("typed", "text/plain", "typed.txt");
    final var result = managedResult("call_1", new TypedHandlerContent(doc));

    when(handler.extractDocuments(result)).thenReturn(List.of(doc));

    final var extracted = extractor.extractDocuments(List.of(result));

    assertThat(extracted).hasSize(1);
    assertThat(extracted.getFirst().documents()).containsExactly(doc);
    verify(handler).extractDocuments(result);
  }

  @Test
  void groupsDocumentsByToolCallAcrossManagedAndUnmanagedResults() {
    final var managedDoc = createDocument("typed", "text/plain", "typed.txt");
    final var unmanagedDoc = createDocument("plain", "text/plain", "plain.txt");

    final var managed = managedResult("call_1", new TypedHandlerContent(managedDoc));
    final var unmanaged = unmanagedResult("call_2", Map.of("file", unmanagedDoc));

    when(handler.extractDocuments(managed)).thenReturn(List.of(managedDoc));

    final var extracted = extractor.extractDocuments(List.of(managed, unmanaged));

    assertThat(extracted).hasSize(2);
    assertThat(extracted.get(0))
        .satisfies(
            e -> {
              assertThat(e.toolCallId()).isEqualTo("call_1");
              assertThat(e.toolCallName()).isEqualTo(MANAGED_TOOL);
              assertThat(e.documents()).containsExactly(managedDoc);
            });
    assertThat(extracted.get(1))
        .satisfies(
            e -> {
              assertThat(e.toolCallId()).isEqualTo("call_2");
              assertThat(e.toolCallName()).isEqualTo("plain_tool");
              assertThat(e.documents()).containsExactly(unmanagedDoc);
            });
  }

  @Test
  void extractsFromMixedResultsWithAndWithoutDocuments() {
    final var doc = createDocument("hello", "text/plain", "test.txt");

    final var withDoc = unmanagedResult("call_1", Map.of("file", doc));
    final var withoutDoc = unmanagedResult("call_2", "plain text result");

    final var extracted = extractor.extractDocuments(List.of(withDoc, withoutDoc));

    assertThat(extracted).hasSize(1);
    assertThat(extracted.getFirst().toolCallId()).isEqualTo("call_1");
  }

  @Test
  void returnsEmptyWhenNoResultsContainDocuments() {
    final var result = unmanagedResult("call_1", "plain text");

    assertThat(extractor.extractDocuments(List.of(result))).isEmpty();
    verify(handler, never()).extractDocuments(any());
  }

  @Test
  void filtersNullDocumentsReturnedByHandler() {
    final var doc1 = createDocument("first", "text/plain", "first.txt");
    final var doc2 = createDocument("second", "application/pdf", "second.pdf");
    final var result = managedResult("call_1", new TypedHandlerContent(doc1));

    when(handler.extractDocuments(result)).thenReturn(Arrays.asList(doc1, null, doc2));

    final var extracted = extractor.extractDocuments(List.of(result));

    assertThat(extracted).hasSize(1);
    assertThat(extracted.getFirst().documents()).containsExactly(doc1, doc2);
  }

  @Test
  void deduplicatesDocumentsReferencedMultipleTimesInTheSameResult() {
    // a single tool call result may reference the same document from multiple paths in its
    // content tree; the extracted documents list should contain it only once
    final var doc = createDocument("hello", "text/plain", "test.txt");
    final var other = createDocument("other", "text/plain", "other.txt");
    final var result = managedResult("call_1", new TypedHandlerContent(doc));

    when(handler.extractDocuments(result)).thenReturn(List.of(doc, other, doc));

    final var extracted = extractor.extractDocuments(List.of(result));

    assertThat(extracted).hasSize(1);
    assertThat(extracted.getFirst().documents()).containsExactly(doc, other);
  }

  @Test
  void extractsFromEventLikeResultWithNullIdAndName() {
    // events arrive as ToolCallResult with null id/name — no handler matches
    final var doc = createDocument("hello", "text/plain", "test.txt");
    final var result = ToolCallResult.builder().content(Map.of("file", doc)).build();

    final var extracted = extractor.extractDocuments(List.of(result));

    assertThat(extracted).hasSize(1);
    assertThat(extracted.getFirst())
        .satisfies(
            e -> {
              assertThat(e.toolCallId()).isNull();
              assertThat(e.toolCallName()).isNull();
              assertThat(e.documents()).containsExactly(doc);
            });
    verify(handler, never()).extractDocuments(any());
  }

  private ToolCallResult managedResult(String id, Object content) {
    return ToolCallResult.builder().id(id).name(MANAGED_TOOL).content(content).build();
  }

  private ToolCallResult unmanagedResult(String id, Object content) {
    return ToolCallResult.builder().id(id).name("plain_tool").content(content).build();
  }

  private Document createDocument(String content, String contentType, String filename) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(filename)
            .build());
  }

  private record TypedHandlerContent(Document document) {}
}
