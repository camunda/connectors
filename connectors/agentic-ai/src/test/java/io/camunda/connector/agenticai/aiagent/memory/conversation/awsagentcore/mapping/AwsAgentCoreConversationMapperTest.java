/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.mapping;

import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.model.message.*;
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
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;

class AwsAgentCoreConversationMapperTest {

  private AwsAgentCoreConversationMapper mapper;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = TestObjectMapperSupplier.INSTANCE;
    mapper = new AwsAgentCoreConversationMapper(objectMapper);
  }

  // ==================== UserMessage Tests ====================

  @Test
  void shouldMapUserMessageWithTextOnly() {
    // given
    UserMessage message =
        UserMessage.builder().content(List.of(textContent("Hello world"))).build();

    // when
    List<PayloadType> payloads = mapper.toPayloads(message);

    // then
    assertThat(payloads).hasSize(1);
    assertThat(payloads.get(0).conversational()).isNotNull();
    assertThat(payloads.get(0).conversational().role()).isEqualTo(Role.USER);
    assertThat(payloads.get(0).conversational().content().text()).isEqualTo("Hello world");
  }

  @Test
  void shouldMapUserMessageWithMultipleTextParts() {
    // given
    UserMessage message =
        UserMessage.builder()
            .content(List.of(textContent("Part 1"), textContent("Part 2")))
            .build();

    // when
    List<PayloadType> payloads = mapper.toPayloads(message);

    // then
    assertThat(payloads).hasSize(2);
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
    List<PayloadType> payloads = mapper.toPayloads(message);

    // then
    assertThat(payloads).hasSize(2);
    assertThat(payloads.get(0).conversational()).isNotNull(); // text
    assertThat(payloads.get(1).blob()).isNotNull(); // document
  }

  @Test
  void shouldRoundTripUserMessageWithTextOnly() {
    // given
    UserMessage original =
        UserMessage.builder().content(List.of(textContent("Hello world"))).build();

    // when
    List<PayloadType> payloads = mapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = mapper.fromEvent(event);

    // then
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
    UserMessage reconstructed = (UserMessage) messages.get(0);
    assertThat(reconstructed.content()).hasSize(1);
    assertThat(reconstructed.content().get(0)).isInstanceOf(TextContent.class);
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
    List<PayloadType> payloads = mapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = mapper.fromEvent(event);

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

  // ==================== AssistantMessage Tests ====================

  @Test
  void shouldMapAssistantMessageWithTextOnly() {
    // given
    AssistantMessage message =
        AssistantMessage.builder().content(List.of(textContent("Here's the answer"))).build();

    // when
    List<PayloadType> payloads = mapper.toPayloads(message);

    // then
    assertThat(payloads).hasSize(1);
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
    List<PayloadType> payloads = mapper.toPayloads(message);

    // then
    assertThat(payloads).hasSize(1);
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
    List<PayloadType> payloads = mapper.toPayloads(message);

    // then
    assertThat(payloads).hasSize(2);
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
    List<PayloadType> payloads = mapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = mapper.fromEvent(event);

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
    List<PayloadType> payloads = mapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = mapper.fromEvent(event);

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
    List<PayloadType> payloads = mapper.toPayloads(message);

    // then
    assertThat(payloads).hasSize(2);
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
    List<PayloadType> payloads = mapper.toPayloads(message);

    // then
    assertThat(payloads.get(0).conversational().content().text()).isEqualTo("Result 1\nResult 2");
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
                        .build()))
            .build();

    // when
    List<PayloadType> payloads = mapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = mapper.fromEvent(event);

    // then
    assertThat(messages).hasSize(1);
    ToolCallResultMessage reconstructed = (ToolCallResultMessage) messages.get(0);
    assertThat(reconstructed.results()).hasSize(1);
    assertThat(reconstructed.results().get(0).id()).isEqualTo("call-1");
    assertThat(reconstructed.results().get(0).name()).isEqualTo("search");
    assertThat(reconstructed.results().get(0).content()).isEqualTo("Found 3 items");
    // Note: properties map may not round-trip perfectly - main fields are preserved
  }

  @Test
  void shouldFallbackToMinimalToolCallResultWhenNoBlobEnvelope() {
    // given - event with only conversational TOOL payload (legacy format)
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

    // when
    List<Message> messages = mapper.fromEvent(event);

    // then
    assertThat(messages).hasSize(1);
    ToolCallResultMessage message = (ToolCallResultMessage) messages.get(0);
    assertThat(message.results()).hasSize(1);
    assertThat(message.results().get(0).content()).isEqualTo("Tool result text");
  }

  // ==================== SystemMessage Tests ====================

  @Test
  void shouldRejectSystemMessage() {
    // given
    SystemMessage message = SystemMessage.builder().content(List.of(textContent("System"))).build();

    // when/then
    assertThatThrownBy(() -> mapper.toPayloads(message))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SystemMessage");
  }

  // ==================== Edge Cases ====================

  @Test
  void shouldHandleEmptyContentList() {
    // given
    UserMessage message = UserMessage.builder().content(List.of()).build();

    // when
    List<PayloadType> payloads = mapper.toPayloads(message);

    // then
    assertThat(payloads).isEmpty();
  }

  @Test
  void shouldHandleEventWithNoPayloads() {
    // given
    Event event = Event.builder().build();

    // when
    List<Message> messages = mapper.fromEvent(event);

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
    List<PayloadType> payloads = mapper.toPayloads(original);

    // then - verify DocumentContent is serialized as blob envelope
    assertThat(payloads).hasSize(1);
    assertThat(payloads.get(0).blob()).isNotNull();
  }

  @Test
  void shouldRoundTripAllContentTypes() {
    // given - message with various content types (excluding DocumentContent which can't round-trip)
    List<Content> originalContent =
        List.of(textContent("Text"), ObjectContent.objectContent(Map.of("key", "value")));

    UserMessage original = UserMessage.builder().content(originalContent).build();

    // when
    List<PayloadType> payloads = mapper.toPayloads(original);
    Event event = Event.builder().payload(payloads).build();
    List<Message> messages = mapper.fromEvent(event);

    // then
    UserMessage reconstructed = (UserMessage) messages.get(0);
    assertThat(reconstructed.content()).hasSize(2);
    assertThat(reconstructed.content().get(0)).isInstanceOf(TextContent.class);
    assertThat(reconstructed.content().get(1)).isInstanceOf(ObjectContent.class);
  }
}
