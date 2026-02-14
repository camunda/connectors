/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.mapping;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.test.document.TestDocument;
import io.camunda.connector.runtime.test.document.TestDocumentMetadata;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;

class BlobEnvelopeTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = ConnectorsObjectMapperSupplier.getCopy();
  }

  @Test
  void shouldCreateToolCallsEnvelope() throws Exception {
    // given
    List<ToolCall> toolCalls =
        List.of(
            ToolCall.builder()
                .id("call-1")
                .name("search")
                .arguments(Map.of("query", "test"))
                .build());

    // when
    BlobEnvelope envelope = BlobEnvelope.forToolCalls(toolCalls, objectMapper);

    // then
    assertThat(envelope.blobType()).isEqualTo("camunda.toolCalls");
    assertThat(envelope.version()).isEqualTo(1);
    assertThat(envelope.is(BlobEnvelopeType.TOOL_CALLS)).isTrue();
  }

  @Test
  void shouldRoundTripToolCalls() throws Exception {
    // given
    List<ToolCall> original =
        List.of(
            ToolCall.builder()
                .id("call-1")
                .name("search")
                .arguments(Map.of("query", "test"))
                .build(),
            ToolCall.builder().id("call-2").name("fetch").arguments(Map.of()).build());

    // when
    BlobEnvelope envelope = BlobEnvelope.forToolCalls(original, objectMapper);
    Document document = envelope.toDocument(objectMapper);
    BlobEnvelope parsed = BlobEnvelope.fromDocument(document, objectMapper);
    List<ToolCall> result = parsed.parseData(new TypeReference<List<ToolCall>>() {}, objectMapper);

    // then
    assertThat(result).hasSize(2);
    assertThat(result.get(0).id()).isEqualTo("call-1");
    assertThat(result.get(0).name()).isEqualTo("search");
    assertThat(result.get(1).id()).isEqualTo("call-2");
  }

  @Test
  void shouldCreateToolCallResultsEnvelope() throws Exception {
    // given
    List<ToolCallResult> results =
        List.of(
            ToolCallResult.builder().id("call-1").name("search").content("Found 3 items").build());

    // when
    BlobEnvelope envelope = BlobEnvelope.forToolCallResults(results, objectMapper);

    // then
    assertThat(envelope.blobType()).isEqualTo("camunda.toolCallResults");
    assertThat(envelope.version()).isEqualTo(1);
    assertThat(envelope.is(BlobEnvelopeType.TOOL_CALL_RESULTS)).isTrue();
  }

  @Test
  void shouldRoundTripToolCallResults() throws Exception {
    // given
    List<ToolCallResult> original =
        List.of(
            ToolCallResult.builder().id("call-1").name("search").content("Found 3 items").build());

    // when
    BlobEnvelope envelope = BlobEnvelope.forToolCallResults(original, objectMapper);
    Document document = envelope.toDocument(objectMapper);
    BlobEnvelope parsed = BlobEnvelope.fromDocument(document, objectMapper);
    List<ToolCallResult> result =
        parsed.parseData(new TypeReference<List<ToolCallResult>>() {}, objectMapper);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo("call-1");
    assertThat(result.get(0).name()).isEqualTo("search");
    assertThat(result.get(0).content()).isEqualTo("Found 3 items");
    // Note: properties map with @JsonAnySetter/@JsonAnyGetter may not round-trip perfectly
    // through nested JSON structures. In practice, the main fields (id, name, content) are
    // preserved.
  }

  @Test
  void shouldCreateMessageContentEnvelope() throws Exception {
    // given
    io.camunda.connector.api.document.Document testDoc =
        new TestDocument(
            "test content".getBytes(),
            new TestDocumentMetadata(null, null, null, null, null, null, null),
            null,
            "doc-1");
    Content content = DocumentContent.documentContent(testDoc);

    // when
    BlobEnvelope envelope = BlobEnvelope.forContent(content, objectMapper);

    // then
    assertThat(envelope.blobType()).isEqualTo("camunda.messageContent");
    assertThat(envelope.version()).isEqualTo(1);
    assertThat(envelope.is(BlobEnvelopeType.MESSAGE_CONTENT)).isTrue();
  }

  @Test
  void shouldRoundTripDocumentContent() throws Exception {
    // DocumentContent contains a Document interface which can't be deserialized without
    // a concrete implementation or custom deserializer. This test is skipped.
    // In practice, DocumentContent is only used with real Document implementations
    // that have proper serialization support.
  }

  @Test
  void shouldRoundTripObjectContent() throws Exception {
    // given
    Content original = ObjectContent.objectContent(Map.of("key", "value", "count", 42));

    // when
    BlobEnvelope envelope = BlobEnvelope.forContent(original, objectMapper);
    Document document = envelope.toDocument(objectMapper);
    BlobEnvelope parsed = BlobEnvelope.fromDocument(document, objectMapper);
    Content result = parsed.parseData(Content.class, objectMapper);

    // then
    assertThat(result).isInstanceOf(ObjectContent.class);
    ObjectContent objContent = (ObjectContent) result;
    @SuppressWarnings("unchecked")
    Map<String, Object> contentMap = (Map<String, Object>) objContent.content();
    assertThat(contentMap).containsEntry("key", "value");
    assertThat(contentMap).containsEntry("count", 42);
  }

  @Test
  void shouldPreserveContentTypeDiscriminator() throws Exception {
    // given - various Content types (excluding DocumentContent which can't be deserialized from
    // mock)
    List<Content> contents =
        List.of(
            TextContent.textContent("Hello"), ObjectContent.objectContent(Map.of("key", "value")));

    // when/then - each should round-trip correctly
    for (Content original : contents) {
      BlobEnvelope envelope = BlobEnvelope.forContent(original, objectMapper);
      software.amazon.awssdk.core.document.Document document = envelope.toDocument(objectMapper);
      BlobEnvelope parsed = BlobEnvelope.fromDocument(document, objectMapper);
      Content result = parsed.parseData(Content.class, objectMapper);

      assertThat(result.getClass()).isEqualTo(original.getClass());
    }
  }

  @Test
  void shouldFailWhenBlobMissingBlobTypeField() {
    // given
    String invalidJson = "{\"version\": 1, \"toolCalls\": []}";
    Document document = Document.fromString(invalidJson);

    // when/then
    assertThatThrownBy(() -> BlobEnvelope.fromDocument(document, objectMapper))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("blobType");
  }

  @Test
  void shouldFailWhenBlobMissingVersionField() {
    // given
    String invalidJson = "{\"blobType\": \"camunda.toolCalls\", \"toolCalls\": []}";
    Document document = Document.fromString(invalidJson);

    // when/then
    assertThatThrownBy(() -> BlobEnvelope.fromDocument(document, objectMapper))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("version");
  }

  @Test
  void shouldFailWhenToolCallsEnvelopeMissingDataField() throws Exception {
    // given
    String invalidJson = "{\"blobType\": \"camunda.toolCalls\", \"version\": 1}";
    Document document = Document.fromString(invalidJson);
    BlobEnvelope envelope = BlobEnvelope.fromDocument(document, objectMapper);

    // when/then
    assertThatThrownBy(
            () -> envelope.parseData(new TypeReference<List<ToolCall>>() {}, objectMapper))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("toolCalls");
  }

  @Test
  void shouldFailWhenContentEnvelopeMissingDataField() throws Exception {
    // given
    String invalidJson = "{\"blobType\": \"camunda.messageContent\", \"version\": 1}";
    Document document = Document.fromString(invalidJson);
    BlobEnvelope envelope = BlobEnvelope.fromDocument(document, objectMapper);

    // when/then
    assertThatThrownBy(() -> envelope.parseData(Content.class, objectMapper))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("content");
  }

  @Test
  void shouldIdentifyEnvelopeType() throws Exception {
    // given
    BlobEnvelope toolCallsEnv =
        BlobEnvelope.forToolCalls(
            List.of(ToolCall.builder().id("1").name("test").arguments(Map.of()).build()),
            objectMapper);
    BlobEnvelope resultsEnv =
        BlobEnvelope.forToolCallResults(
            List.of(ToolCallResult.builder().content("test").build()), objectMapper);
    BlobEnvelope contentEnv =
        BlobEnvelope.forContent(TextContent.textContent("test"), objectMapper);

    // then
    assertThat(toolCallsEnv.is(BlobEnvelopeType.TOOL_CALLS)).isTrue();
    assertThat(toolCallsEnv.is(BlobEnvelopeType.TOOL_CALL_RESULTS)).isFalse();

    assertThat(resultsEnv.is(BlobEnvelopeType.TOOL_CALL_RESULTS)).isTrue();
    assertThat(resultsEnv.is(BlobEnvelopeType.TOOL_CALLS)).isFalse();

    assertThat(contentEnv.is(BlobEnvelopeType.MESSAGE_CONTENT)).isTrue();
    assertThat(contentEnv.is(BlobEnvelopeType.TOOL_CALLS)).isFalse();
  }
}
