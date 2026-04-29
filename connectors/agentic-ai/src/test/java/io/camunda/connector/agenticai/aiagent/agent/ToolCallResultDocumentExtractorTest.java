/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandler;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistryImpl;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ToolCallResultDocumentExtractorTest {

  private final InMemoryDocumentStore documentStore = InMemoryDocumentStore.INSTANCE;
  private final DocumentFactory documentFactory = new DocumentFactoryImpl(documentStore);

  @Mock private GatewayToolHandlerRegistry registry;

  private ToolCallResultDocumentExtractor extractor;

  @BeforeEach
  void setUp() {
    documentStore.clear();
    extractor = new ToolCallResultDocumentExtractor(registry);
  }

  @Test
  void groupsDocumentsByToolCall_routedThroughRegistry() {
    final var doc1 = createDocument("hello", "text/plain", "test.txt");
    final var doc2 = createDocument("<pdf>", "application/pdf", "report.pdf");

    final var result1 =
        ToolCallResult.builder().id("call_1").name("tool_a").content(Map.of("file", doc1)).build();
    final var result2 =
        ToolCallResult.builder()
            .id("call_2")
            .name("tool_b")
            .content(Map.of("report", doc2))
            .build();

    when(registry.extractDocuments(result1)).thenReturn(List.of(doc1));
    when(registry.extractDocuments(result2)).thenReturn(List.of(doc2));

    final var extracted = extractor.extractDocuments(List.of(result1, result2));

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

    final var withDoc =
        ToolCallResult.builder().id("call_1").name("tool_a").content(Map.of("file", doc)).build();
    final var withoutDoc =
        ToolCallResult.builder().id("call_2").name("tool_b").content("plain text result").build();

    when(registry.extractDocuments(withDoc)).thenReturn(List.of(doc));
    when(registry.extractDocuments(withoutDoc)).thenReturn(List.of());

    final var extracted = extractor.extractDocuments(List.of(withDoc, withoutDoc));

    assertThat(extracted).hasSize(1);
    assertThat(extracted.getFirst().toolCallId()).isEqualTo("call_1");
  }

  @Test
  void returnsEmptyWhenNoToolCallsContainDocuments() {
    final var result =
        ToolCallResult.builder().id("call_1").name("tool_a").content("text result").build();
    when(registry.extractDocuments(result)).thenReturn(List.of());

    assertThat(extractor.extractDocuments(List.of(result))).isEmpty();
  }

  @Test
  void handlesNullNameAndId() {
    final var doc = createDocument("hello", "text/plain", "test.txt");
    final var result = ToolCallResult.builder().content(Map.of("file", doc)).build();
    when(registry.extractDocuments(result)).thenReturn(List.of(doc));

    final var extracted = extractor.extractDocuments(List.of(result));

    assertThat(extracted).hasSize(1);
    assertThat(extracted.getFirst())
        .satisfies(
            e -> {
              assertThat(e.toolCallId()).isEmpty();
              assertThat(e.toolCallName()).isEqualTo("unknown");
            });
  }

  @Test
  void returnsEmptyForEmptyInputWithoutTouchingRegistry() {
    final var extracted = extractor.extractDocuments(List.of());

    assertThat(extracted).isEmpty();
    verifyNoInteractions(registry);
  }

  @Test
  void integrationWithRealRegistry_fallsBackToContentTreeWalkerWhenNoHandlerMatches() {
    // Use a real (empty) registry: no handlers -> default walker fallback.
    final var realRegistry = new GatewayToolHandlerRegistryImpl(List.of());
    final var integrationExtractor = new ToolCallResultDocumentExtractor(realRegistry);

    final var doc = createDocument("hello", "text/plain", "test.txt");
    final var result =
        ToolCallResult.builder()
            .id("call_1")
            .name("plain_bpmn_tool")
            .content(Map.of("attachment", doc))
            .build();

    final var extracted = integrationExtractor.extractDocuments(List.of(result));

    assertThat(extracted).hasSize(1);
    assertThat(extracted.getFirst().documents()).containsExactly(doc);
  }

  @Test
  void integrationWithRealRegistry_routesToHandlerOverridingExtraction(
      @Mock GatewayToolHandler handler) {
    final var doc = createDocument("typed", "text/plain", "typed.txt");
    final var typedContent = new TypedHandlerContent(doc);

    when(handler.type()).thenReturn("typed");
    when(handler.isGatewayManaged("typed_tool")).thenReturn(true);
    when(handler.extractDocuments(any(ToolCallResult.class))).thenReturn(List.of(doc));

    final var realRegistry = new GatewayToolHandlerRegistryImpl(List.of(handler));
    final var integrationExtractor = new ToolCallResultDocumentExtractor(realRegistry);

    final var result =
        ToolCallResult.builder().id("call_1").name("typed_tool").content(typedContent).build();

    final var extracted = integrationExtractor.extractDocuments(List.of(result));

    assertThat(extracted).hasSize(1);
    assertThat(extracted.getFirst().documents()).containsExactly(doc);
    verify(handler).extractDocuments(result);
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
