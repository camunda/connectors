/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.mapping;

import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.util.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.test.document.TestDocumentFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;

class AwsAgentCoreConversationMapperTest {

  private AwsAgentCoreConversationMapper conversationMapper;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = TestObjectMapperSupplier.INSTANCE;
    conversationMapper = new AwsAgentCoreConversationMapper(objectMapper);
  }

  // ==================== UserMessage Tests ====================

  @Test
  void shouldMapUserMessageWithTextOnly() {
    // given
    UserMessage message =
        UserMessage.builder().content(List.of(textContent("Hello world"))).build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(message);

    // then — conversational payload + metadata blob (with role)
    assertThat(payloads).hasSize(2);
    assertThat(payloads.get(0).conversational()).isNotNull();
    assertThat(payloads.get(0).conversational().role()).isEqualTo(Role.USER);
    assertThat(payloads.get(0).conversational().content().text()).isEqualTo("Hello world");
    assertThat(payloads.get(1).blob()).isNotNull(); // metadata blob with role
  }

  @Test
  void shouldMapUserMessageWithMultipleTextParts() {
    // given
    UserMessage message =
        UserMessage.builder()
            .content(List.of(textContent("Part 1"), textContent("Part 2")))
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(message);

    // then — 2 conversational payloads + metadata blob
    assertThat(payloads).hasSize(3);
    assertThat(payloads.get(0).conversational().content().text()).isEqualTo("Part 1");
    assertThat(payloads.get(1).conversational().content().text()).isEqualTo("Part 2");
  }

  @Test
  void shouldMapUserMessageWithDocumentContent() {
    // given
    Document testDocument =
        new TestDocumentFactory()
            .create(
                DocumentCreationRequest.from("Hello world".getBytes(StandardCharsets.UTF_8))
                    .build());

    UserMessage message =
        UserMessage.builder()
            .content(
                List.of(textContent("Check this"), DocumentContent.documentContent(testDocument)))
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(message);

    // then — conversational + document blob + metadata blob
    assertThat(payloads).hasSize(3);
    assertThat(payloads.get(0).conversational()).isNotNull(); // text
    assertThat(payloads.get(1).blob()).isNotNull(); // document
  }

  @Test
  void shouldRoundTripUserMessageWithTextOnly() {
    // given
    UserMessage original =
        UserMessage.builder().content(List.of(textContent("Hello world"))).build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = conversationMapper.fromEvent(event);

    // then
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
    UserMessage reconstructed = (UserMessage) messages.get(0);
    assertThat(reconstructed.content()).hasSize(1);
    assertThat(reconstructed.content().get(0)).isInstanceOf(TextContent.class);
    assertThat(((TextContent) reconstructed.content().get(0)).text()).isEqualTo("Hello world");
  }

  @Test
  void shouldRoundTripUserMessageWithName() {
    // given
    UserMessage original =
        UserMessage.builder().name("Alice").content(List.of(textContent("Hello world"))).build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = conversationMapper.fromEvent(event);

    // then
    assertThat(messages).hasSize(1);
    UserMessage reconstructed = (UserMessage) messages.get(0);
    assertThat(reconstructed.name()).isEqualTo("Alice");
    assertThat(reconstructed.metadata()).isNullOrEmpty();
    assertThat(((TextContent) reconstructed.content().get(0)).text()).isEqualTo("Hello world");
  }

  @Test
  void shouldRoundTripUserMessageWithMixedContent() {
    // given
    Document testDocument =
        new TestDocumentFactory()
            .create(
                DocumentCreationRequest.from("Hello world".getBytes(StandardCharsets.UTF_8))
                    .build());

    UserMessage original =
        UserMessage.builder()
            .content(
                List.of(textContent("Here's a doc"), DocumentContent.documentContent(testDocument)))
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = conversationMapper.fromEvent(event);

    // then
    assertThat(messages).hasSize(1);
    UserMessage reconstructed = (UserMessage) messages.get(0);
    assertThat(reconstructed.content()).hasSize(2);
    assertThat(reconstructed.content().get(0)).isInstanceOf(TextContent.class);
    assertThat(reconstructed.content().get(1)).isInstanceOf(DocumentContent.class);

    // Verify document is reconstructed
    DocumentContent docContent = (DocumentContent) reconstructed.content().get(1);
    assertThat(docContent.document()).isNotNull();
  }

  @Test
  void shouldRoundTripUserMessageWithOnlyDocumentContent() {
    // given - no TextContent, only DocumentContent (blob-only event, no conversational payload)
    Document testDocument =
        new TestDocumentFactory()
            .create(
                DocumentCreationRequest.from("binary data".getBytes(StandardCharsets.UTF_8))
                    .build());
    UserMessage original =
        UserMessage.builder()
            .content(List.of(DocumentContent.documentContent(testDocument)))
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = conversationMapper.fromEvent(event);

    // then - message must not be dropped; role recovered from metadata properties
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
    UserMessage reconstructed = (UserMessage) messages.get(0);
    assertThat(reconstructed.content()).hasSize(1);
    assertThat(reconstructed.content().get(0)).isInstanceOf(DocumentContent.class);
  }

  // ==================== AssistantMessage Tests ====================

  @Test
  void shouldMapAssistantMessageWithTextOnly() {
    // given
    AssistantMessage message =
        AssistantMessage.builder().content(List.of(textContent("Here's the answer"))).build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(message);

    // then — conversational + metadata blob
    assertThat(payloads).hasSize(2);
    assertThat(payloads.get(0).conversational().role()).isEqualTo(Role.ASSISTANT);
    assertThat(payloads.get(0).conversational().content().text()).isEqualTo("Here's the answer");
  }

  @Test
  void shouldMapAssistantMessageWithToolCallsOnly() {
    // given
    AssistantMessage message =
        AssistantMessage.builder()
            .content(List.of())
            .toolCalls(
                List.of(
                    ToolCall.builder()
                        .id("call-1")
                        .name("search")
                        .arguments(Map.of("query", "test"))
                        .build()))
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(message);

    // then — toolCalls blob + metadata blob
    assertThat(payloads).hasSize(2);
    assertThat(payloads.get(0).blob()).isNotNull();
  }

  @Test
  void shouldMapAssistantMessageWithTextAndToolCalls() {
    // given
    AssistantMessage message =
        AssistantMessage.builder()
            .content(List.of(textContent("Let me search for that")))
            .toolCalls(
                List.of(
                    ToolCall.builder()
                        .id("call-1")
                        .name("search")
                        .arguments(Map.of("query", "test"))
                        .build()))
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(message);

    // then — conversational + toolCalls blob + metadata blob
    assertThat(payloads).hasSize(3);
    assertThat(payloads.get(0).conversational()).isNotNull();
    assertThat(payloads.get(1).blob()).isNotNull();
  }

  @Test
  void shouldRoundTripAssistantMessageWithTextAndToolCalls() {
    // given
    AssistantMessage original =
        AssistantMessage.builder()
            .content(List.of(textContent("Let me search")))
            .toolCalls(
                List.of(
                    ToolCall.builder()
                        .id("call-1")
                        .name("search")
                        .arguments(Map.of("query", "test"))
                        .build()))
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = conversationMapper.fromEvent(event);

    // then
    assertThat(messages).hasSize(1);
    AssistantMessage reconstructed = (AssistantMessage) messages.get(0);
    assertThat(reconstructed.content()).hasSize(1);
    assertThat(((TextContent) reconstructed.content().get(0)).text()).isEqualTo("Let me search");
    assertThat(reconstructed.toolCalls()).hasSize(1);
    assertThat(reconstructed.toolCalls().get(0).id()).isEqualTo("call-1");
    assertThat(reconstructed.toolCalls().get(0).name()).isEqualTo("search");
  }

  @Test
  void shouldRoundTripAssistantMessageWithMixedContent() {
    // given
    AssistantMessage original =
        AssistantMessage.builder()
            .content(
                List.of(
                    textContent("Here's the data"),
                    ObjectContent.objectContent(Map.of("result", "success"))))
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = conversationMapper.fromEvent(event);

    // then
    assertThat(messages).hasSize(1);
    AssistantMessage reconstructed = (AssistantMessage) messages.get(0);
    assertThat(reconstructed.content()).hasSize(2);
    assertThat(reconstructed.content().get(0)).isInstanceOf(TextContent.class);
    assertThat(reconstructed.content().get(1)).isInstanceOf(ObjectContent.class);
  }

  // ==================== ToolCallResultMessage Tests ====================

  @Test
  void shouldMapToolCallResultMessage() {
    // given
    ToolCallResultMessage message =
        ToolCallResultMessage.builder()
            .results(
                List.of(
                    ToolCallResult.builder()
                        .id("call-1")
                        .name("search")
                        .content("Found 3 items")
                        .build()))
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(message);

    // then — conversational summary + toolCallResults blob + metadata blob
    assertThat(payloads).hasSize(3);
    // Conversational summary
    assertThat(payloads.get(0).conversational().role()).isEqualTo(Role.TOOL);
    assertThat(payloads.get(0).conversational().content().text()).isEqualTo("Found 3 items");
    // Blob with full structure
    assertThat(payloads.get(1).blob()).isNotNull();
  }

  @Test
  void shouldMapToolCallResultMessageWithMultipleResults() {
    // given
    ToolCallResultMessage message =
        ToolCallResultMessage.builder()
            .results(
                List.of(
                    ToolCallResult.builder().content("Result 1").build(),
                    ToolCallResult.builder().content("Result 2").build()))
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(message);

    // then
    assertThat(payloads.get(0).conversational().content().text()).isEqualTo("Result 1\nResult 2");
  }

  @Test
  void shouldSerializeNonStringToolCallResultContentAsJson() throws Exception {
    // given - content is a Map, not a String
    Map<String, Object> contentMap = Map.of("items", List.of("a", "b"), "count", 2);
    ToolCallResultMessage message =
        ToolCallResultMessage.builder()
            .results(List.of(ToolCallResult.builder().content(contentMap).build()))
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(message);

    // then - summary should be valid JSON matching the original map
    String summary = payloads.get(0).conversational().content().text();
    JSONAssert.assertEquals("{\"items\":[\"a\",\"b\"],\"count\":2}", summary, false);
  }

  @Test
  void shouldRoundTripToolCallResultMessageWithNullContent() {
    // given - all results have null content, so no conversational TOOL summary is emitted
    ToolCallResultMessage original =
        ToolCallResultMessage.builder()
            .results(
                List.of(ToolCallResult.builder().id("call-1").name("search").content(null).build()))
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = conversationMapper.fromEvent(event);

    // then - message must not be silently dropped
    assertThat(messages).hasSize(1);
    ToolCallResultMessage reconstructed = (ToolCallResultMessage) messages.get(0);
    assertThat(reconstructed.results()).hasSize(1);
    assertThat(reconstructed.results().get(0).id()).isEqualTo("call-1");
    assertThat(reconstructed.results().get(0).name()).isEqualTo("search");
  }

  @Test
  void shouldRoundTripToolCallResultMessage() {
    // given
    ToolCallResultMessage original =
        ToolCallResultMessage.builder()
            .results(
                List.of(
                    ToolCallResult.builder()
                        .id("call-1")
                        .name("search")
                        .content("Found 3 items")
                        .properties(Map.of("interrupted", true, "custom", "value"))
                        .build()))
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = conversationMapper.fromEvent(event);

    // then
    assertThat(messages).hasSize(1);
    ToolCallResultMessage reconstructed = (ToolCallResultMessage) messages.get(0);
    assertThat(reconstructed.results()).hasSize(1);
    assertThat(reconstructed.results().get(0).id()).isEqualTo("call-1");
    assertThat(reconstructed.results().get(0).name()).isEqualTo("search");
    assertThat(reconstructed.results().get(0).content()).isEqualTo("Found 3 items");
    assertThat(reconstructed.results().get(0).properties())
        .containsEntry("interrupted", true)
        .containsEntry("custom", "value");
  }

  @Test
  void shouldReturnEmptyForToolEventWithoutMetadataBlob() {
    // given - event with only conversational TOOL payload but no metadata blob (no role source)
    Event event =
        Event.builder()
            .payload(
                PayloadType.builder()
                    .conversational(
                        software.amazon.awssdk.services.bedrockagentcore.model.Conversational
                            .builder()
                            .role(Role.TOOL)
                            .content(
                                software.amazon.awssdk.services.bedrockagentcore.model.Content
                                    .fromText("Tool result text"))
                            .build())
                    .build())
            .build();

    // when — no metadata blob means role can't be resolved, so event is skipped
    List<Message> messages = conversationMapper.fromEvent(event);

    // then
    assertThat(messages).isEmpty();
  }

  // ==================== SystemMessage Tests ====================

  @Test
  void shouldRejectSystemMessage() {
    // given
    SystemMessage message = SystemMessage.builder().content(List.of(textContent("System"))).build();

    // when/then
    assertThatThrownBy(() -> conversationMapper.toPayloads(message))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SystemMessage");
  }

  // ==================== Edge Cases ====================

  @Test
  void shouldHandleEmptyContentList() {
    // given
    UserMessage message = UserMessage.builder().content(List.of()).build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(message);

    // then — only the metadata blob (with role), no content payloads
    assertThat(payloads).hasSize(1);
    assertThat(payloads.get(0).blob()).isNotNull();
  }

  @Test
  void shouldHandleEventWithNoPayloads() {
    // given
    Event event = Event.builder().build();

    // when
    List<Message> messages = conversationMapper.fromEvent(event);

    // then
    assertThat(messages).isEmpty();
  }

  // ==================== Content Type Tests ====================

  @Test
  void shouldSerializeDocumentContentToBlob() {
    // given
    Document testDocument =
        new TestDocumentFactory()
            .create(
                DocumentCreationRequest.from("Hello world".getBytes(StandardCharsets.UTF_8))
                    .build());

    UserMessage original =
        UserMessage.builder()
            .content(List.of(DocumentContent.documentContent(testDocument)))
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(original);

    // then — document blob + metadata blob (with role)
    assertThat(payloads).hasSize(2);
    assertThat(payloads.get(0).blob()).isNotNull();
  }

  @Test
  void shouldRoundTripAllContentTypes() {
    // given - message with various content types (excluding DocumentContent which can't round-trip)
    List<Content> originalContent =
        List.of(textContent("Text"), ObjectContent.objectContent(Map.of("key", "value")));

    UserMessage original = UserMessage.builder().content(originalContent).build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = conversationMapper.fromEvent(event);

    // then
    UserMessage reconstructed = (UserMessage) messages.get(0);
    assertThat(reconstructed.content()).hasSize(2);
    assertThat(reconstructed.content().get(0)).isInstanceOf(TextContent.class);
    assertThat(reconstructed.content().get(1)).isInstanceOf(ObjectContent.class);
  }

  // ==================== Metadata Tests ====================

  @Test
  void shouldPreserveMetadataInUserMessageRoundTrip() {
    // given
    Map<String, Object> metadata =
        Map.of("userId", "user123", "sessionId", "session456", "timestamp", "2024-01-15");

    UserMessage original =
        UserMessage.builder().content(List.of(textContent("Hello"))).metadata(metadata).build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = conversationMapper.fromEvent(event);

    // then
    assertThat(messages).hasSize(1);
    UserMessage reconstructed = (UserMessage) messages.get(0);
    assertThat(reconstructed.metadata())
        .hasSize(3)
        .containsEntry("userId", "user123")
        .containsEntry("sessionId", "session456")
        .containsEntry("timestamp", "2024-01-15");
  }

  @Test
  void shouldPreserveMetadataInAssistantMessageRoundTrip() {
    // given
    Map<String, Object> metadata = Map.of("modelName", "gpt-4", "temperature", "0.7");

    AssistantMessage original =
        AssistantMessage.builder()
            .content(List.of(textContent("Response")))
            .metadata(metadata)
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = conversationMapper.fromEvent(event);

    // then
    assertThat(messages).hasSize(1);
    AssistantMessage reconstructed = (AssistantMessage) messages.get(0);
    assertThat(reconstructed.metadata())
        .hasSize(2)
        .containsEntry("modelName", "gpt-4")
        .containsEntry("temperature", "0.7");
  }

  @Test
  void shouldPreserveMetadataInToolCallResultMessageRoundTrip() {
    // given
    Map<String, Object> metadata = Map.of("toolName", "search", "executionTime", "150ms");

    ToolCallResultMessage original =
        ToolCallResultMessage.builder()
            .results(List.of(ToolCallResult.builder().content("Result").build()))
            .metadata(metadata)
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = conversationMapper.fromEvent(event);

    // then
    assertThat(messages).hasSize(1);
    ToolCallResultMessage reconstructed = (ToolCallResultMessage) messages.get(0);
    assertThat(reconstructed.metadata())
        .hasSize(2)
        .containsEntry("toolName", "search")
        .containsEntry("executionTime", "150ms");
  }

  @Test
  void shouldHandleEmptyMetadataInRoundTrip() {
    // given
    UserMessage original =
        UserMessage.builder().content(List.of(textContent("Hello"))).metadata(Map.of()).build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = conversationMapper.fromEvent(event);

    // then
    assertThat(messages).hasSize(1);
    UserMessage reconstructed = (UserMessage) messages.get(0);
    assertThat(reconstructed.metadata()).isEmpty();
  }

  // ==================== Content Order Preservation Tests ====================

  @Test
  void shouldPreserveContentOrderInUserMessage_textBlobText() {
    // given: text, non-text, text — interleaved
    Document doc = createTestDocument();
    UserMessage original =
        UserMessage.builder()
            .content(
                List.of(
                    textContent("First"),
                    DocumentContent.documentContent(doc),
                    textContent("Third")))
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = conversationMapper.fromEvent(event);

    // then — order must be preserved
    assertThat(messages).hasSize(1);
    UserMessage reconstructed = (UserMessage) messages.get(0);
    assertThat(reconstructed.content()).hasSize(3);
    assertThat(reconstructed.content().get(0)).isInstanceOf(TextContent.class);
    assertThat(((TextContent) reconstructed.content().get(0)).text()).isEqualTo("First");
    assertThat(reconstructed.content().get(1)).isInstanceOf(DocumentContent.class);
    assertThat(reconstructed.content().get(2)).isInstanceOf(TextContent.class);
    assertThat(((TextContent) reconstructed.content().get(2)).text()).isEqualTo("Third");
  }

  @Test
  void shouldPreserveContentOrderInUserMessage_blobFirst() {
    // given: non-text before text
    Document doc = createTestDocument();
    UserMessage original =
        UserMessage.builder()
            .content(List.of(DocumentContent.documentContent(doc), textContent("After")))
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = conversationMapper.fromEvent(event);

    // then
    assertThat(messages).hasSize(1);
    UserMessage reconstructed = (UserMessage) messages.get(0);
    assertThat(reconstructed.content()).hasSize(2);
    assertThat(reconstructed.content().get(0)).isInstanceOf(DocumentContent.class);
    assertThat(reconstructed.content().get(1)).isInstanceOf(TextContent.class);
    assertThat(((TextContent) reconstructed.content().get(1)).text()).isEqualTo("After");
  }

  @Test
  void shouldPreserveContentOrderInAssistantMessage_textBlobText() {
    // given: interleaved text and non-text with toolCalls
    Document doc = createTestDocument();
    AssistantMessage original =
        AssistantMessage.builder()
            .content(
                List.of(
                    textContent("Before"),
                    DocumentContent.documentContent(doc),
                    textContent("After")))
            .toolCalls(
                List.of(
                    ToolCall.builder()
                        .id("call_1")
                        .name("search")
                        .arguments(Map.of("q", "test"))
                        .build()))
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = conversationMapper.fromEvent(event);

    // then — content order preserved, toolCalls separate
    assertThat(messages).hasSize(1);
    AssistantMessage reconstructed = (AssistantMessage) messages.get(0);
    assertThat(reconstructed.content()).hasSize(3);
    assertThat(reconstructed.content().get(0)).isInstanceOf(TextContent.class);
    assertThat(((TextContent) reconstructed.content().get(0)).text()).isEqualTo("Before");
    assertThat(reconstructed.content().get(1)).isInstanceOf(DocumentContent.class);
    assertThat(reconstructed.content().get(2)).isInstanceOf(TextContent.class);
    assertThat(((TextContent) reconstructed.content().get(2)).text()).isEqualTo("After");
    assertThat(reconstructed.toolCalls()).hasSize(1);
    assertThat(reconstructed.toolCalls().get(0).name()).isEqualTo("search");
  }

  @Test
  void shouldPreservePayloadOrderOnWire_userMessageWithMixedContent() {
    // given
    Document doc = createTestDocument();
    UserMessage message =
        UserMessage.builder()
            .content(
                List.of(textContent("A"), DocumentContent.documentContent(doc), textContent("B")))
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(message);

    // then — payloads must be in content order: conv, blob, conv, metadata blob
    assertThat(payloads).hasSize(4);
    assertThat(payloads.get(0).conversational()).isNotNull();
    assertThat(payloads.get(0).conversational().content().text()).isEqualTo("A");
    assertThat(payloads.get(1).blob()).isNotNull(); // DocumentContent blob
    assertThat(payloads.get(2).conversational()).isNotNull();
    assertThat(payloads.get(2).conversational().content().text()).isEqualTo("B");
  }

  @Test
  void shouldPreserveContentOrderWithMetadata() {
    // given: mixed content + metadata
    Document doc = createTestDocument();
    UserMessage original =
        UserMessage.builder()
            .content(List.of(textContent("Text"), DocumentContent.documentContent(doc)))
            .metadata(Map.of("key", "value"))
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = conversationMapper.fromEvent(event);

    // then — content order preserved, metadata round-trips
    assertThat(messages).hasSize(1);
    UserMessage reconstructed = (UserMessage) messages.get(0);
    assertThat(reconstructed.content()).hasSize(2);
    assertThat(reconstructed.content().get(0)).isInstanceOf(TextContent.class);
    assertThat(reconstructed.content().get(1)).isInstanceOf(DocumentContent.class);
    assertThat(reconstructed.metadata()).containsEntry("key", "value");
  }

  @Test
  void shouldHandleAssistantMessageWithToolCallsOnly_noContent() {
    // given: toolCalls only, no content
    AssistantMessage original =
        AssistantMessage.builder()
            .toolCalls(
                List.of(
                    ToolCall.builder()
                        .id("call_1")
                        .name("getWeather")
                        .arguments(Map.of("city", "Berlin"))
                        .build()))
            .build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = conversationMapper.fromEvent(event);

    // then
    assertThat(messages).hasSize(1);
    AssistantMessage reconstructed = (AssistantMessage) messages.get(0);
    assertThat(reconstructed.content()).isEmpty();
    assertThat(reconstructed.toolCalls()).hasSize(1);
    assertThat(reconstructed.toolCalls().get(0).name()).isEqualTo("getWeather");
  }

  @Test
  void shouldHandleUserMessageWithTextOnly_noBlobs() {
    // given: simple text-only message
    UserMessage original =
        UserMessage.builder().content(List.of(textContent("Simple message"))).build();

    // when
    List<PayloadType> payloads = conversationMapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = conversationMapper.fromEvent(event);

    // then
    assertThat(messages).hasSize(1);
    UserMessage reconstructed = (UserMessage) messages.get(0);
    assertThat(reconstructed.content()).hasSize(1);
    assertThat(((TextContent) reconstructed.content().get(0)).text()).isEqualTo("Simple message");
  }

  private Document createTestDocument() {
    return new TestDocumentFactory()
        .create(DocumentCreationRequest.from("test".getBytes(StandardCharsets.UTF_8)).build());
  }
}
