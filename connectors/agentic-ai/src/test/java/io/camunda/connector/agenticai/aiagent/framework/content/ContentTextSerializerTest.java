/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.content;

import static io.camunda.connector.agenticai.model.message.content.ObjectContent.objectContent;
import static io.camunda.connector.agenticai.model.message.content.ReasoningContent.reasoningContent;
import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContentTextSerializerTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JacksonModuleDocumentSerializer());

  @Test
  void textContentReturnsRawText() {
    assertThat(ContentTextSerializer.toText(textContent("hello"), objectMapper)).isEqualTo("hello");
  }

  @Test
  void objectContentWithStringReturnsRawString() {
    assertThat(ContentTextSerializer.toText(objectContent("plain string"), objectMapper))
        .isEqualTo("plain string");
  }

  @Test
  void objectContentWithMapReturnsJson() {
    final var content = new LinkedHashMap<String, Object>();
    content.put("key", "value");
    content.put("count", 42);

    assertThat(ContentTextSerializer.toText(objectContent(content), objectMapper))
        .isEqualTo("{\"key\":\"value\",\"count\":42}");
  }

  @Test
  void objectContentWithListReturnsJson() {
    assertThat(ContentTextSerializer.toText(objectContent(List.of("a", "b", "c")), objectMapper))
        .isEqualTo("[\"a\",\"b\",\"c\"]");
  }

  @Test
  void objectContentWithDocumentSerialisesViaDocumentModule() {
    final var documentStore = InMemoryDocumentStore.INSTANCE;
    documentStore.clear();
    final DocumentFactory factory = new DocumentFactoryImpl(documentStore);
    final Document document =
        factory.create(
            DocumentCreationRequest.from("hello".getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .fileName("greeting.txt")
                .build());

    final var nested = Map.of("attachment", document);

    final var json = ContentTextSerializer.toText(objectContent(nested), objectMapper);

    assertThat(json).contains("\"camunda.document.type\":\"camunda\"");
    assertThat(json).doesNotContain("hello");
  }

  @Test
  void unsupportedContentTypeThrows() {
    final var documentStore = InMemoryDocumentStore.INSTANCE;
    documentStore.clear();
    final DocumentFactory factory = new DocumentFactoryImpl(documentStore);
    final Document document =
        factory.create(
            DocumentCreationRequest.from("x".getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .build());

    assertThatThrownBy(
            () ->
                ContentTextSerializer.toText(
                    DocumentContent.documentContent(document), objectMapper))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("DocumentContent");

    assertThatThrownBy(
            () -> ContentTextSerializer.toText(reasoningContent("thinking"), objectMapper))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ReasoningContent");
  }
}
