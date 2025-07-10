/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolCallConverter;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.document.Document;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatMessageConverterTest {

  @Mock private ToolCallConverter toolCallConverter;
  @Mock private DocumentToContentConverter documentToContentConverter;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private ChatMessageConverterImpl chatMessageConverter;

  @Test
  void fromSystemMessage_withSingleTextContent_returnsSystemMessage() {
    SystemMessage systemMessage =
        SystemMessage.builder().content(List.of(textContent("Test system message"))).build();

    dev.langchain4j.data.message.SystemMessage result =
        chatMessageConverter.fromSystemMessage(systemMessage);

    assertThat(result.text()).isEqualTo("Test system message");
  }

  @Test
  void fromSystemMessage_withMultipleContents_throwsException() {
    SystemMessage systemMessage =
        SystemMessage.builder()
            .content(List.of(textContent("Content 1"), textContent("Content 2")))
            .build();

    assertThatThrownBy(() -> chatMessageConverter.fromSystemMessage(systemMessage))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SystemMessage currently only supports a single TextContent block.");
  }

  @Test
  void fromUserMessage_withTextContent_returnsUserMessage() {
    UserMessage userMessage =
        UserMessage.builder()
            .name("User")
            .content(List.of(textContent("Test user message")))
            .build();

    dev.langchain4j.data.message.UserMessage result =
        chatMessageConverter.fromUserMessage(userMessage);

    assertThat(result.name()).isEqualTo("User");
    assertThat(result.contents())
        .hasSize(1)
        .satisfiesExactly(
            content -> {
              assertThat(content).isInstanceOf(dev.langchain4j.data.message.TextContent.class);
              assertThat(((dev.langchain4j.data.message.TextContent) content).text())
                  .isEqualTo("Test user message");
            });
  }

  @Test
  void fromUserMessage_withDocumentContent_convertsDocument() {
    Document document = mock(Document.class);
    UserMessage userMessage =
        UserMessage.builder()
            .content(
                List.of(textContent("Tell me about this document"), new DocumentContent(document)))
            .build();

    Content convertedContent =
        new dev.langchain4j.data.message.PdfFileContent("<base64-encoded-pdf>", "application/pdf");
    when(documentToContentConverter.convert(document)).thenReturn(convertedContent);

    dev.langchain4j.data.message.UserMessage result =
        chatMessageConverter.fromUserMessage(userMessage);

    assertThat(result.contents())
        .hasSize(2)
        .satisfiesExactly(
            content -> {
              assertThat(content).isInstanceOf(dev.langchain4j.data.message.TextContent.class);
              assertThat(((dev.langchain4j.data.message.TextContent) content).text())
                  .isEqualTo("Tell me about this document");
            },
            content -> {
              assertThat(content).isSameAs(convertedContent);
            });
  }

  @Test
  void fromUserMessage_withEmptyContent_throwsException() {
    UserMessage userMessage = UserMessage.builder().content(Collections.emptyList()).build();

    assertThatThrownBy(() -> chatMessageConverter.fromUserMessage(userMessage))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("UserMessage content cannot be empty");
  }

  @Test
  void fromAssistantMessage_withTextContent_returnsAiMessage() {
    AssistantMessage assistantMessage =
        AssistantMessage.builder().content(List.of(textContent("Test assistant message"))).build();

    AiMessage result = chatMessageConverter.fromAssistantMessage(assistantMessage);

    assertThat(result.text()).isEqualTo("Test assistant message");
    assertThat(result.toolExecutionRequests()).isEmpty();
  }

  @Test
  void fromAssistantMessage_withoutAnyContent_returnsAiMessage() {
    AssistantMessage assistantMessage = AssistantMessage.builder().build();

    AiMessage result = chatMessageConverter.fromAssistantMessage(assistantMessage);

    assertThat(result.text()).isNull();
    assertThat(result.toolExecutionRequests()).isEmpty();
  }

  @Test
  void fromAssistantMessage_withToolCalls_includesToolExecutionRequests() {
    ToolCall toolCall = ToolCall.builder().id("toolCallId").name("toolName").build();
    AssistantMessage assistantMessage =
        AssistantMessage.builder()
            .content(List.of(textContent("Test message")))
            .toolCalls(List.of(toolCall))
            .build();

    ToolExecutionRequest toolExecutionRequest = mock(ToolExecutionRequest.class);
    when(toolCallConverter.asToolExecutionRequest(toolCall)).thenReturn(toolExecutionRequest);

    AiMessage result = chatMessageConverter.fromAssistantMessage(assistantMessage);

    assertThat(result.text()).isEqualTo("Test message");
    assertThat(result.toolExecutionRequests()).hasSize(1);
    assertThat(result.toolExecutionRequests().getFirst()).isSameAs(toolExecutionRequest);
  }

  @Test
  void fromAssistantMessage_withMultipleContents_throwsException() {
    AssistantMessage assistantMessage =
        AssistantMessage.builder()
            .content(
                List.of(
                    textContent("Content 1"),
                    textContent("Content 2"),
                    new DocumentContent(mock(Document.class))))
            .build();

    assertThatThrownBy(() -> chatMessageConverter.fromAssistantMessage(assistantMessage))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "AiMessage currently only supports a single TextContent block, 3 content blocks found instead.");
  }

  @Test
  void toAssistantMessage_convertsFromChatResponse() {
    final var aiMessage = AiMessage.builder().text("AI response").build();

    final var chatResponseMetadata =
        ChatResponseMetadata.builder()
            .finishReason(FinishReason.STOP)
            .tokenUsage(new TokenUsage(10, 20))
            .build();

    final var chatResponse =
        new ChatResponse.Builder().aiMessage(aiMessage).metadata(chatResponseMetadata).build();

    final var result = chatMessageConverter.toAssistantMessage(chatResponse);

    assertThat(result.content())
        .hasSize(1)
        .satisfiesExactly(
            content -> {
              assertThat(content)
                  .isInstanceOf(
                      io.camunda.connector.agenticai.model.message.content.TextContent.class);
              assertThat(
                      ((io.camunda.connector.agenticai.model.message.content.TextContent) content)
                          .text())
                  .isEqualTo("AI response");
            });

    assertThat(result.metadata()).containsKey("timestamp");
    assertThat((ZonedDateTime) result.metadata().get("timestamp"))
        .isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.SECONDS));
    assertThat(result.metadata()).containsKey("framework");
    assertThat(result.metadata().get("framework"))
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .containsEntry("finishReason", "STOP")
        .containsEntry(
            "tokenUsage",
            Map.of("inputTokenCount", 10, "outputTokenCount", 20, "totalTokenCount", 30));
  }

  @Test
  void toAssistantMessage_convertsFromChatResponse_withoutContentText() {
    final var aiMessage = AiMessage.builder().build();

    final var chatResponseMetadata =
        ChatResponseMetadata.builder()
            .finishReason(FinishReason.STOP)
            .tokenUsage(new TokenUsage(10, 0))
            .build();

    final var chatResponse =
        new ChatResponse.Builder().aiMessage(aiMessage).metadata(chatResponseMetadata).build();

    final var result = chatMessageConverter.toAssistantMessage(chatResponse);

    assertThat(result.content()).isEmpty();

    assertThat(result.metadata()).containsKey("framework");
    assertThat(result.metadata().get("framework"))
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .containsEntry("finishReason", "STOP")
        .containsEntry(
            "tokenUsage",
            Map.of("inputTokenCount", 10, "outputTokenCount", 0, "totalTokenCount", 10));
  }

  @Test
  void toAssistantMessage_withToolExecutionRequests_convertsToolCalls() {
    final var toolExecutionRequest =
        ToolExecutionRequest.builder().id("toolCallId").name("toolName").build();

    final var aiMessage =
        AiMessage.builder()
            .text("AI response")
            .toolExecutionRequests(List.of(toolExecutionRequest))
            .build();

    final var chatResponse = ChatResponse.builder().aiMessage(aiMessage).build();

    ToolCall toolCall = ToolCall.builder().id("toolCallId").name("toolName").build();
    when(toolCallConverter.asToolCall(toolExecutionRequest)).thenReturn(toolCall);

    AssistantMessage result = chatMessageConverter.toAssistantMessage(chatResponse);

    assertThat(result.toolCalls()).hasSize(1).containsExactly(toolCall);
  }

  @Test
  void fromToolCallResultMessage_convertsToolCallResults() {
    ToolCallResult toolCallResult =
        ToolCallResult.builder().id("toolCallId").name("toolName").content("Hello, world!").build();
    ToolCallResultMessage toolCallResultMessage =
        ToolCallResultMessage.builder().results(List.of(toolCallResult)).build();

    ToolExecutionResultMessage toolExecutionResultMessage =
        new ToolExecutionResultMessage("toolCallId", "toolName", "Hello, world!");
    when(toolCallConverter.asToolExecutionResultMessage(toolCallResult))
        .thenReturn(toolExecutionResultMessage);

    List<ToolExecutionResultMessage> result =
        chatMessageConverter.fromToolCallResultMessage(toolCallResultMessage);

    assertThat(result).hasSize(1).containsExactly(toolExecutionResultMessage);
  }

  @Test
  void map_withSystemMessage_returnsListWithSystemMessage() {
    SystemMessage systemMessage =
        SystemMessage.builder().content(List.of(textContent("Test system message"))).build();

    List<ChatMessage> result = chatMessageConverter.map(systemMessage);

    assertThat(result)
        .hasSize(1)
        .satisfiesExactly(
            chatMessage -> {
              assertThat(chatMessage)
                  .isInstanceOf(dev.langchain4j.data.message.SystemMessage.class);
              assertThat(((dev.langchain4j.data.message.SystemMessage) chatMessage).text())
                  .isEqualTo("Test system message");
            });
  }

  @Test
  void map_withListOfMessages_returnsListOfChatMessages() {
    SystemMessage systemMessage =
        SystemMessage.builder().content(List.of(textContent("System message"))).build();

    UserMessage userMessage =
        UserMessage.builder().content(List.of(textContent("User message"))).build();

    List<ChatMessage> result = chatMessageConverter.map(List.of(systemMessage, userMessage));

    assertThat(result).hasSize(2);
    assertThat(result.get(0)).isInstanceOf(dev.langchain4j.data.message.SystemMessage.class);
    assertThat(result.get(1)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
  }

  @Test
  void map_withUnknownMessageType_throwsException() {
    Message unknownMessage =
        new Message() {
          @Override
          public Map<String, Object> metadata() {
            return Collections.emptyMap();
          }
        };

    assertThatThrownBy(() -> chatMessageConverter.map(unknownMessage))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown message type");
  }
}
