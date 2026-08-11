/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j;

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.GoogleVertexAiCloseableChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolCallConverter;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.api.document.Document;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatMessageConverterTest {

  @Mock private ToolCallConverter toolCallConverter;
  @Mock private ContentConverter contentConverter;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private ChatMessageConverterImpl chatMessageConverter;

  // every chat model drops attributes by default (CloseableChatModel's decorateOnRead/Write
  // defaults) unless it overrides them, like GoogleVertexAiCloseableChatModel does below
  private final CloseableChatModel chatModel =
      mock(CloseableChatModel.class, Answers.CALLS_REAL_METHODS);

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
  void fromUserMessage_withTextContent_returnsUserMessage() throws JsonProcessingException {
    TextContent textContent = textContent("Test user message");
    Content convertedTextContent =
        new dev.langchain4j.data.message.TextContent("Test user message");
    doReturn(convertedTextContent).when(contentConverter).convertToContent(textContent);

    UserMessage userMessage =
        UserMessage.builder().name("User").content(List.of(textContent)).build();

    dev.langchain4j.data.message.UserMessage result =
        chatMessageConverter.fromUserMessage(userMessage);

    assertThat(result.name()).isEqualTo("User");
    assertThat(result.contents()).hasSize(1).containsExactly(convertedTextContent);
  }

  @Test
  void fromUserMessage_withDocumentContent_convertsDocument() throws JsonProcessingException {
    Document document = mock(Document.class);
    DocumentContent documentContent = DocumentContent.documentContent(document);

    UserMessage userMessage =
        UserMessage.builder()
            .content(List.of(textContent("Tell me about this document"), documentContent))
            .build();

    Content convertedTextContent =
        new dev.langchain4j.data.message.TextContent("Tell me about this document");
    doReturn(convertedTextContent)
        .when(contentConverter)
        .convertToContent(textContent("Tell me about this document"));

    Content convertedDocumentContent =
        new dev.langchain4j.data.message.PdfFileContent("<base64-encoded-pdf>", "application/pdf");
    when(contentConverter.convertToContent(documentContent)).thenReturn(convertedDocumentContent);

    dev.langchain4j.data.message.UserMessage result =
        chatMessageConverter.fromUserMessage(userMessage);

    assertThat(result.contents())
        .hasSize(2)
        .containsExactly(convertedTextContent, convertedDocumentContent);
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

    AiMessage result = chatMessageConverter.fromAssistantMessage(assistantMessage, chatModel);

    assertThat(result.text()).isEqualTo("Test assistant message");
    assertThat(result.toolExecutionRequests()).isEmpty();
  }

  @Test
  void fromAssistantMessage_withoutAnyContent_returnsAiMessage() {
    AssistantMessage assistantMessage = AssistantMessage.builder().build();

    AiMessage result = chatMessageConverter.fromAssistantMessage(assistantMessage, chatModel);

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

    AiMessage result = chatMessageConverter.fromAssistantMessage(assistantMessage, chatModel);

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
                    DocumentContent.documentContent(mock(Document.class))))
            .build();

    assertThatThrownBy(() -> chatMessageConverter.fromAssistantMessage(assistantMessage, chatModel))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "AiMessage currently only supports a single TextContent block, 3 content blocks found instead.");
  }

  /**
   * Provider tool call metadata carries data the provider requires us to echo back verbatim on the
   * next request - most notably Gemini 3 thought signatures, without which a follow-up request
   * containing function calls is rejected with a 400. It must survive the round trip through the
   * conversation history, attached to the specific tool call it belongs to. Only the {@code
   * thought_signature_*} entry for this tool call survives - a decoy key and an unrelated tool
   * call's signature prove the Google Vertex AI chat model narrows the dump to this one tool call,
   * it does not pass everything through unfiltered.
   */
  @Test
  void assistantMessageRoundTrip_preservesToolCallThoughtSignature() {
    final var googleChatModel =
        new GoogleVertexAiCloseableChatModel(
            mock(dev.langchain4j.model.chat.ChatModel.class), mock(AutoCloseable.class));

    final var toolExecutionRequest =
        ToolExecutionRequest.builder().id("toolCallId").name("toolName").build();
    final var attributes =
        Map.<String, Object>of(
            "thought_signature_toolCallId",
            "c2lnbmF0dXJl",
            "thought_signature_someOtherToolCallId",
            "unrelated-signature",
            "raw_http_response",
            "should-be-dropped");
    final var aiMessage =
        AiMessage.builder()
            .text("AI response")
            .toolExecutionRequests(List.of(toolExecutionRequest))
            .attributes(attributes)
            .build();
    final var chatResponse =
        new ChatResponse.Builder()
            .aiMessage(aiMessage)
            .metadata(ChatResponseMetadata.builder().finishReason(FinishReason.STOP).build())
            .build();

    final var toolCall = ToolCall.builder().id("toolCallId").name("toolName").build();
    when(toolCallConverter.asToolCall(toolExecutionRequest)).thenReturn(toolCall);

    final var assistantMessage =
        chatMessageConverter.toAssistantMessage(chatResponse, googleChatModel);

    assertThat(assistantMessage.toolCalls()).hasSize(1);

    final var decoratedToolCall = assistantMessage.toolCalls().getFirst();
    assertThat(decoratedToolCall.id()).isEqualTo("toolCallId");
    assertThat(decoratedToolCall.metadata())
        .containsExactly(
            entry(
                GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID,
                Map.of("thoughtSignature", "c2lnbmF0dXJl")));

    when(toolCallConverter.asToolExecutionRequest(decoratedToolCall))
        .thenReturn(toolExecutionRequest);

    assertThat(
            chatMessageConverter
                .fromAssistantMessage(assistantMessage, googleChatModel)
                .attributes())
        .isEqualTo(Map.of("thought_signature_toolCallId", "c2lnbmF0dXJl"));
  }

  @Test
  void toAssistantMessage_toolCallWithoutMatchingThoughtSignature_hasNoMetadata() {
    final var googleChatModel =
        new GoogleVertexAiCloseableChatModel(
            mock(dev.langchain4j.model.chat.ChatModel.class), mock(AutoCloseable.class));

    final var toolExecutionRequest =
        ToolExecutionRequest.builder().id("toolCallId").name("toolName").build();
    final var aiMessage =
        AiMessage.builder()
            .text("AI response")
            .toolExecutionRequests(List.of(toolExecutionRequest))
            .build();
    final var chatResponse =
        new ChatResponse.Builder()
            .aiMessage(aiMessage)
            .metadata(ChatResponseMetadata.builder().finishReason(FinishReason.STOP).build())
            .build();

    final var toolCall = ToolCall.builder().id("toolCallId").name("toolName").build();
    when(toolCallConverter.asToolCall(toolExecutionRequest)).thenReturn(toolCall);

    final var result = chatMessageConverter.toAssistantMessage(chatResponse, googleChatModel);

    assertThat(result.toolCalls()).hasSize(1);
    assertThat(result.toolCalls().getFirst().metadata()).isNullOrEmpty();
  }

  @Test
  void toAssistantMessage_forDefaultChatModel_neverAddsToolCallMetadata() {
    final var toolExecutionRequest =
        ToolExecutionRequest.builder().id("toolCallId").name("toolName").build();
    final var attributes =
        Map.<String, Object>of(
            "thought_signature_toolCallId", "c2lnbmF0dXJl", "raw_http_response", "leak-risk");
    final var aiMessage =
        AiMessage.builder()
            .text("AI response")
            .toolExecutionRequests(List.of(toolExecutionRequest))
            .attributes(attributes)
            .build();
    final var chatResponse =
        new ChatResponse.Builder()
            .aiMessage(aiMessage)
            .metadata(ChatResponseMetadata.builder().finishReason(FinishReason.STOP).build())
            .build();

    final var toolCall = ToolCall.builder().id("toolCallId").name("toolName").build();
    when(toolCallConverter.asToolCall(toolExecutionRequest)).thenReturn(toolCall);

    final var assistantMessage = chatMessageConverter.toAssistantMessage(chatResponse, chatModel);

    assertThat(assistantMessage.toolCalls()).hasSize(1);
    assertThat(assistantMessage.toolCalls().getFirst().metadata()).isNullOrEmpty();
  }

  @Test
  void fromAssistantMessage_forDefaultChatModel_neverRestoresAttributes() {
    final var toolCall =
        ToolCall.builder()
            .id("toolCallId")
            .name("toolName")
            .metadata(
                Map.of(
                    GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID,
                    Map.of("thoughtSignature", "c2lnbmF0dXJl")))
            .build();
    final var assistantMessage =
        AssistantMessage.builder()
            .content(List.of(textContent("Test message")))
            .toolCalls(List.of(toolCall))
            .build();

    when(toolCallConverter.asToolExecutionRequest(toolCall))
        .thenReturn(mock(ToolExecutionRequest.class));

    assertThat(chatMessageConverter.fromAssistantMessage(assistantMessage, chatModel).attributes())
        .isEmpty();
  }

  @Test
  void toAssistantMessage_convertsFromChatResponse() {
    final var aiMessage = AiMessage.builder().text("AI response").build();

    final var chatResponseMetadata =
        ChatResponseMetadata.builder()
            .id("chatcmpl-123")
            .modelName("my-model")
            .finishReason(FinishReason.STOP)
            .tokenUsage(new TokenUsage(10, 20))
            .build();

    final var chatResponse =
        new ChatResponse.Builder().aiMessage(aiMessage).metadata(chatResponseMetadata).build();

    final var result = chatMessageConverter.toAssistantMessage(chatResponse, chatModel);

    assertThat(result.content())
        .hasSize(1)
        .satisfiesExactly(
            content -> {
              assertThat(content).isInstanceOf(TextContent.class);
              assertThat(((TextContent) content).text()).isEqualTo("AI response");
            });

    assertThat(result.modelId()).isEqualTo("my-model");
    assertThat(result.messageId()).isEqualTo("chatcmpl-123");

    assertThat(result.metadata()).containsKey("timestamp");
    assertThat((ZonedDateTime) result.metadata().get("timestamp"))
        .isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.SECONDS));
    assertThat(result.metadata()).containsKey("framework");
    assertThat(result.metadata().get("framework"))
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .containsExactly(
            entry("id", "chatcmpl-123"),
            entry("model", "my-model"),
            entry("finishReason", "STOP"),
            entry(
                "tokenUsage",
                Map.of("inputTokenCount", 10, "outputTokenCount", 20, "totalTokenCount", 30)));
  }

  @Test
  void toAssistantMessage_containsOnlyBasicMetadata() {
    final var aiMessage = AiMessage.builder().text("AI response").build();

    final var chatResponseMetadata =
        OpenAiChatResponseMetadata.builder()
            .id("chatcmpl-123")
            .modelName("gpt-4o")
            .finishReason(FinishReason.TOOL_EXECUTION)
            .tokenUsage(
                OpenAiTokenUsage.builder()
                    .inputTokenCount(10)
                    .inputTokensDetails(
                        OpenAiTokenUsage.InputTokensDetails.builder().cachedTokens(1).build())
                    .outputTokenCount(20)
                    .totalTokenCount(30)
                    .build())
            .serviceTier("super-premium")
            .rawHttpResponse(
                SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .headers(Map.of("x-my-header", List.of("dummy")))
                    .body("AI response")
                    .build())
            .build();

    final var chatResponse =
        new ChatResponse.Builder().aiMessage(aiMessage).metadata(chatResponseMetadata).build();

    final var result = chatMessageConverter.toAssistantMessage(chatResponse, chatModel);

    final var expectedTokenUsage = new LinkedHashMap<String, Object>();
    expectedTokenUsage.put("inputTokenCount", 10);
    expectedTokenUsage.put("inputTokensDetails", Map.of("cachedTokens", 1));
    expectedTokenUsage.put("outputTokenCount", 20);
    expectedTokenUsage.put("outputTokensDetails", null);
    expectedTokenUsage.put("totalTokenCount", 30);

    assertThat(result.metadata().get("framework"))
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .containsExactly(
            entry("id", "chatcmpl-123"),
            entry("model", "gpt-4o"),
            entry("finishReason", "TOOL_EXECUTION"),
            entry("tokenUsage", expectedTokenUsage))
        .doesNotContainKeys("serviceTier", "rawHttpResponse");
  }

  @Test
  void toAssistantMessage_omitsModelFromFrameworkMetadata_whenNotSet() {
    final var aiMessage = AiMessage.builder().text("AI response").build();

    final var chatResponseMetadata =
        ChatResponseMetadata.builder()
            .id("chatcmpl-123")
            .finishReason(FinishReason.STOP)
            .tokenUsage(new TokenUsage(10, 20))
            .build();

    final var chatResponse =
        new ChatResponse.Builder().aiMessage(aiMessage).metadata(chatResponseMetadata).build();

    final var result = chatMessageConverter.toAssistantMessage(chatResponse, chatModel);

    assertThat(result.metadata().get("framework"))
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .containsExactly(
            entry("id", "chatcmpl-123"),
            entry("finishReason", "STOP"),
            entry(
                "tokenUsage",
                Map.of("inputTokenCount", 10, "outputTokenCount", 20, "totalTokenCount", 30)));
  }

  @Test
  void toAssistantMessage_convertsFromChatResponse_withoutContentText() {
    final var aiMessage = AiMessage.builder().build();

    final var chatResponseMetadata =
        ChatResponseMetadata.builder()
            .id("chatcmpl-123")
            .modelName("my-model")
            .finishReason(FinishReason.CONTENT_FILTER)
            .tokenUsage(new TokenUsage(10, 0))
            .build();

    final var chatResponse =
        new ChatResponse.Builder().aiMessage(aiMessage).metadata(chatResponseMetadata).build();

    final var result = chatMessageConverter.toAssistantMessage(chatResponse, chatModel);

    assertThat(result.content()).isEmpty();

    assertThat(result.metadata()).containsKey("framework");
    assertThat(result.metadata().get("framework"))
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .containsExactly(
            entry("id", "chatcmpl-123"),
            entry("model", "my-model"),
            entry("finishReason", "CONTENT_FILTER"),
            entry(
                "tokenUsage",
                Map.of("inputTokenCount", 10, "outputTokenCount", 0, "totalTokenCount", 10)));
  }

  @Test
  void toAssistantMessage_ignoresBlankText() {
    final var aiMessage = AiMessage.builder().text("   ").build();

    final var chatResponse = new ChatResponse.Builder().aiMessage(aiMessage).build();

    final var result = chatMessageConverter.toAssistantMessage(chatResponse, chatModel);

    assertThat(result.content()).isEmpty();
  }

  @Test
  void toAssistantMessage_convertsFromChatResponse_withoutMetadata() {
    final var chatResponse =
        new ChatResponse.Builder().aiMessage(AiMessage.builder().build()).build();

    final var result = chatMessageConverter.toAssistantMessage(chatResponse, chatModel);

    assertThat(result.metadata()).containsKey("framework");
    assertThat(result.metadata().get("framework"))
        .isNotNull()
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .isEmpty();
  }

  @ParameterizedTest
  @MethodSource("finishReasonToStopReasonMappings")
  void toAssistantMessage_mapsFinishReasonToStopReason(
      FinishReason finishReason, StopReason expectedStopReason) {
    final var aiMessage = AiMessage.builder().text("AI response").build();

    final var chatResponseMetadata =
        ChatResponseMetadata.builder()
            .id("chatcmpl-123")
            .modelName("my-model")
            .finishReason(finishReason)
            .tokenUsage(new TokenUsage(10, 20))
            .build();

    final var chatResponse =
        new ChatResponse.Builder().aiMessage(aiMessage).metadata(chatResponseMetadata).build();

    final var result = chatMessageConverter.toAssistantMessage(chatResponse, chatModel);

    assertThat(result.stopReason()).isEqualTo(expectedStopReason);
  }

  private static Stream<Arguments> finishReasonToStopReasonMappings() {
    return Stream.of(
        Arguments.of(FinishReason.STOP, StopReason.STOP),
        Arguments.of(FinishReason.LENGTH, StopReason.LENGTH),
        Arguments.of(FinishReason.TOOL_EXECUTION, StopReason.TOOL_USE),
        Arguments.of(FinishReason.CONTENT_FILTER, StopReason.CONTENT_FILTERED),
        Arguments.of(FinishReason.OTHER, new StopReason.UnknownStopReason("OTHER")));
  }

  @Test
  void toAssistantMessage_toleratesMissingModelIdAndMessageIdInResponseMetadata() {
    final var aiMessage = AiMessage.builder().text("AI response").build();

    // no id()/modelName() set — only finish reason and token usage present
    final var chatResponseMetadata =
        ChatResponseMetadata.builder()
            .finishReason(FinishReason.STOP)
            .tokenUsage(new TokenUsage(10, 20))
            .build();

    final var chatResponse =
        new ChatResponse.Builder().aiMessage(aiMessage).metadata(chatResponseMetadata).build();

    final var result = chatMessageConverter.toAssistantMessage(chatResponse, chatModel);

    assertThat(result.modelId()).isNull();
    assertThat(result.messageId()).isNull();
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

    AssistantMessage result = chatMessageConverter.toAssistantMessage(chatResponse, chatModel);

    assertThat(result.toolCalls()).hasSize(1).containsExactly(toolCall);
  }

  @Test
  void fromToolCallResultMessage_convertsToolCallResults() {
    ToolCallResultContent toolCallResultContent =
        ToolCallResultContent.builder()
            .id("toolCallId")
            .name("toolName")
            .content(List.of(TextContent.textContent("Hello, world!")))
            .build();
    ToolCallResultMessage toolCallResultMessage =
        ToolCallResultMessage.builder().results(List.of(toolCallResultContent)).build();

    ToolExecutionResultMessage toolExecutionResultMessage =
        new ToolExecutionResultMessage("toolCallId", "toolName", "Hello, world!");
    when(toolCallConverter.asToolExecutionResultMessage(toolCallResultContent))
        .thenReturn(toolExecutionResultMessage);

    List<ToolExecutionResultMessage> result =
        chatMessageConverter.fromToolCallResultMessage(toolCallResultMessage);

    assertThat(result).hasSize(1).containsExactly(toolExecutionResultMessage);
  }

  @Test
  void map_withSystemMessage_returnsListWithSystemMessage() {
    SystemMessage systemMessage =
        SystemMessage.builder().content(List.of(textContent("Test system message"))).build();

    List<ChatMessage> result = chatMessageConverter.map(systemMessage, chatModel);

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

    List<ChatMessage> result =
        chatMessageConverter.map(List.of(systemMessage, userMessage), chatModel);

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

    assertThatThrownBy(() -> chatMessageConverter.map(unknownMessage, chatModel))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown message type");
  }
}
