/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ToolCallResultContentTest {

  private final ObjectMapper objectMapper = TestObjectMapperSupplier.INSTANCE;

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
    void unknownTopLevelPropertyIsPreservedWhenContentIsCurrentShape() throws Exception {
      // the @JsonAnySetter mechanism for flattened 8.9 top-level properties (e.g. interrupted)
      // is independent of the content shape and must keep working once content is current-shape
      String json =
          """
          {
            "id": "call-1",
            "name": "search",
            "content": [{"type": "text", "text": "x"}],
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
    void legacyFlatContentFailsToDeserializeDirectly() {
      // ToolCallResultContent's own `content` field only binds the current structured shape now
      // (see ConversationSchemaMigrationTest for the lift-on-read coverage of legacy shapes); a
      // legacy flat value reaching this type un-upcasted is the intended safety net firing loud
      String json = "{\"id\": \"call-1\", \"name\": \"search\", \"content\": \"x\"}";

      assertThatThrownBy(() -> objectMapper.readValue(json, ToolCallResultContent.class))
          .isInstanceOf(MismatchedInputException.class);
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
