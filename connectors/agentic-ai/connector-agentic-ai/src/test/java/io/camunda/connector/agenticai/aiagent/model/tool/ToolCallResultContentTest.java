/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ToolCallResultContentTest {

  private final ObjectMapper objectMapper = TestObjectMapperSupplier.INSTANCE;

  // The compact constructor must tolerate null property values (using an unmodifiable HashMap view
  // rather than Map.copyOf, which rejects nulls) so that an old 8.9 flattened property persisted
  // with a JSON-null value does not NPE on construction of this BC-critical type. This goes through
  // builder(), which returns the proxy builder that bypasses the generated build()'s Map.copyOf.
  @Test
  void propertiesWithNullValueDoesNotThrowAndIsPreserved() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("key", null);

    ToolCallResultContent result =
        ToolCallResultContent.builder().id("call-1").properties(properties).build();

    assertThat(result.properties()).containsEntry("key", null);
  }

  @Nested
  class ContentFromObject {

    @Test
    void stringContentBecomesSingleTextContent() {
      assertThat(ToolCallResultContent.contentFromObject("hello"))
          .containsExactly(TextContent.textContent("hello"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void nullOrBlankStringContentBecomesEmptyList(String content) {
      assertThat(ToolCallResultContent.contentFromObject(content)).isEmpty();
    }

    @Test
    void mapContentBecomesSingleObjectContentPreservingTheMap() {
      Map<String, Object> map = Map.of("key", "value", "count", 3);
      assertThat(ToolCallResultContent.contentFromObject(map))
          .containsExactly(ObjectContent.objectContent(map));
    }

    @Test
    void listContentBecomesSingleObjectContentPreservingTheList() {
      List<String> list = List.of("a", "b", "c");
      assertThat(ToolCallResultContent.contentFromObject(list))
          .containsExactly(ObjectContent.objectContent(list));
    }

    @Test
    void documentContentBecomesSingleDocumentContent() {
      Document document = mock(Document.class);
      assertThat(ToolCallResultContent.contentFromObject(document))
          .containsExactly(DocumentContent.documentContent(document));
    }
  }

  @Nested
  class From {

    @Test
    void liftsToolCallResultFieldsAndContent() {
      OffsetDateTime completedAt = OffsetDateTime.parse("2026-07-02T11:55:00.522622+02:00");
      ToolCallResult toolCallResult =
          ToolCallResult.builder()
              .id("call-1")
              .name("search")
              .elementId("search-element")
              .content("Found 3 items")
              .completedAt(completedAt)
              .properties(Map.of("custom", "value"))
              .build();

      ToolCallResultContent result = ToolCallResultContent.from(toolCallResult);

      assertThat(result.id()).isEqualTo("call-1");
      assertThat(result.name()).isEqualTo("search");
      assertThat(result.elementId()).isEqualTo("search-element");
      assertThat(result.completedAt()).isEqualTo(completedAt);
      assertThat(result.properties()).isEqualTo(Map.of("custom", "value"));
      assertThat(result.content()).containsExactly(TextContent.textContent("Found 3 items"));
    }
  }

  @Nested
  class JacksonRoundTrip {

    @Test
    void newFormatContentRoundTrips() throws Exception {
      ToolCallResultContent original =
          ToolCallResultContent.builder()
              .id("call-1")
              .name("search")
              .content(List.of(TextContent.textContent("hi")))
              .build();

      String json = objectMapper.writeValueAsString(original);
      ToolCallResultContent deserialized =
          objectMapper.readValue(json, ToolCallResultContent.class);

      assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void legacyJsonWithFlattenedPropertyPreservesPropertiesAndLiftsContent() throws Exception {
      String json =
          """
          {
            "id": "call-1",
            "name": "search",
            "content": "x",
            "interrupted": true
          }
          """;

      ToolCallResultContent deserialized =
          objectMapper.readValue(json, ToolCallResultContent.class);

      assertThat(deserialized.id()).isEqualTo("call-1");
      assertThat(deserialized.name()).isEqualTo("search");
      assertThat(deserialized.properties()).containsEntry("interrupted", true);
      assertThat(deserialized.content()).containsExactly(TextContent.textContent("x"));
    }

    @Test
    void legacyJsonWithDocumentReferenceContentBecomesDocumentContent() throws Exception {
      // shaped like the real 8.9 golden fixtures' document objects (discriminator key +
      // storeId/documentId/contentHash/metadata)
      String json =
          """
          {
            "id": "call-1",
            "name": "download",
            "content": {
              "camunda.document.type": "camunda",
              "storeId": "in-memory",
              "documentId": "31127ad5-411e-485a-a67b-f7b4512bc075",
              "contentHash": "37aab54a0d7d35291088a50ff9095845cdd292bc7b811008625cab10e75d2d0d",
              "metadata": {
                "contentType": "application/json",
                "fileName": "test.json"
              }
            }
          }
          """;

      ToolCallResultContent deserialized =
          objectMapper.readValue(json, ToolCallResultContent.class);

      assertThat(deserialized.content()).singleElement().isInstanceOf(DocumentContent.class);
    }

    @Test
    void legacyJsonWithNullContentBecomesEmptyList() throws Exception {
      String json = "{\"id\": \"call-1\", \"name\": \"search\", \"content\": null}";

      ToolCallResultContent deserialized =
          objectMapper.readValue(json, ToolCallResultContent.class);

      assertThat(deserialized.content()).isEmpty();
    }

    @Test
    void legacyJsonWithObjectContentBecomesObjectContent() throws Exception {
      String json =
          """
          {
            "id": "call-1",
            "name": "listUsers",
            "content": [{"id": 1, "name": "Alice"}, {"id": 2, "name": "Bob"}]
          }
          """;

      ToolCallResultContent deserialized =
          objectMapper.readValue(json, ToolCallResultContent.class);

      assertThat(deserialized.content()).singleElement().isInstanceOf(ObjectContent.class);
    }

    @Test
    void completedAtRoundTrips() throws Exception {
      OffsetDateTime completedAt = OffsetDateTime.parse("2026-07-02T11:55:00.522622+02:00");
      ToolCallResultContent original =
          ToolCallResultContent.builder()
              .id("call-1")
              .content(List.of(TextContent.textContent("done")))
              .completedAt(completedAt)
              .build();

      String json = objectMapper.writeValueAsString(original);
      ToolCallResultContent deserialized =
          objectMapper.readValue(json, ToolCallResultContent.class);

      assertThat(deserialized.completedAt()).isEqualTo(completedAt);
    }
  }
}
