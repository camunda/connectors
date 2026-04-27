/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.errors.UnprocessableEntityException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.DirectCaller;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnthropicOnFoundryChatModelTest {

  private static final Headers EMPTY_HEADERS = Headers.builder().build();

  @Mock private AnthropicClient mockClient;
  @Mock private MessageService mockMessageService;

  private AnthropicOnFoundryChatModel adapter;

  @BeforeEach
  void setUp() {
    when(mockClient.messages()).thenReturn(mockMessageService);

    var modelConfig =
        new AnthropicModel(
            "claude-3-5-sonnet-20241022", new AnthropicModelParameters(1024, 0.7, 0.9, 10));
    adapter = new AnthropicOnFoundryChatModel(mockClient, modelConfig);
  }

  // -------------------------------------------------------------------------
  // Test fixture helpers — SDK builders are strict about required fields
  // -------------------------------------------------------------------------

  /** Build a minimal Usage with all required fields set. */
  private static Usage usage(long inputTokens, long outputTokens) {
    return Usage.builder()
        .inputTokens(inputTokens)
        .outputTokens(outputTokens)
        .cacheCreation(Optional.empty())
        .cacheCreationInputTokens(Optional.empty())
        .cacheReadInputTokens(Optional.empty())
        .inferenceGeo(Optional.empty())
        .serverToolUse(Optional.empty())
        .serviceTier(Optional.empty())
        .build();
  }

  /** Build a minimal TextBlock with all required fields set. */
  private static TextBlock textBlock(String text) {
    return TextBlock.builder().text(text).citations(Optional.empty()).build();
  }

  /** Build a minimal ToolUseBlock with all required fields set. */
  private static ToolUseBlock toolUseBlock(String id, String name, JsonValue input) {
    return ToolUseBlock.builder()
        .id(id)
        .name(name)
        .input(input)
        .caller(ToolUseBlock.Caller.ofDirect(DirectCaller.builder().build()))
        .build();
  }

  /** Build a Message with all required fields set. */
  private static Message message(
      String id, List<ContentBlock> blocks, StopReason stopReason, Usage usageValue) {
    var builder =
        Message.builder()
            .id(id)
            .model(Model.CLAUDE_SONNET_4_0)
            .stopReason(Optional.of(stopReason))
            .stopDetails(Optional.empty())
            .stopSequence(Optional.empty())
            .usage(usageValue);
    for (ContentBlock block : blocks) {
      builder.addContent(block);
    }
    return builder.build();
  }

  // -------------------------------------------------------------------------
  // Tests
  // -------------------------------------------------------------------------

  @Test
  void translates_text_response_to_ai_message() {
    // given
    var contentBlock = ContentBlock.ofText(textBlock("Hello, world!"));
    var message = message("msg_001", List.of(contentBlock), StopReason.END_TURN, usage(10, 5));
    when(mockMessageService.create(any(MessageCreateParams.class))).thenReturn(message);

    var request = ChatRequest.builder().messages(List.of(new UserMessage("Hi"))).build();

    // when
    ChatResponse response = adapter.chat(request);

    // then
    assertThat(response.aiMessage().text()).isEqualTo("Hello, world!");
    assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    TokenUsage tokenUsage = response.tokenUsage();
    assertThat(tokenUsage.inputTokenCount()).isEqualTo(10);
    assertThat(tokenUsage.outputTokenCount()).isEqualTo(5);
    assertThat(tokenUsage.totalTokenCount()).isEqualTo(15);
  }

  @Test
  void translates_tool_use_response_to_tool_execution_requests() {
    // given
    var toolUseBlock =
        toolUseBlock(
            "toolu_01XFDUDYJgAACTU9bRTQE47m",
            "get_weather",
            JsonValue.from(java.util.Map.of("location", "San Francisco, CA")));
    var contentBlock = ContentBlock.ofToolUse(toolUseBlock);
    var message = message("msg_002", List.of(contentBlock), StopReason.TOOL_USE, usage(20, 8));
    when(mockMessageService.create(any(MessageCreateParams.class))).thenReturn(message);

    var request =
        ChatRequest.builder().messages(List.of(new UserMessage("What is the weather?"))).build();

    // when
    ChatResponse response = adapter.chat(request);

    // then
    assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
    AiMessage aiMessage = response.aiMessage();
    assertThat(aiMessage.hasToolExecutionRequests()).isTrue();
    List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
    assertThat(toolRequests).hasSize(1);
    ToolExecutionRequest toolRequest = toolRequests.get(0);
    assertThat(toolRequest.id()).isEqualTo("toolu_01XFDUDYJgAACTU9bRTQE47m");
    assertThat(toolRequest.name()).isEqualTo("get_weather");
    assertThat(toolRequest.arguments()).contains("San Francisco, CA");
  }

  @Test
  void translates_max_tokens_stop_reason_to_LENGTH() {
    // given
    var contentBlock = ContentBlock.ofText(textBlock("Partial response..."));
    var message = message("msg_003", List.of(contentBlock), StopReason.MAX_TOKENS, usage(15, 1024));
    when(mockMessageService.create(any(MessageCreateParams.class))).thenReturn(message);

    var request =
        ChatRequest.builder().messages(List.of(new UserMessage("Tell me everything"))).build();

    // when
    ChatResponse response = adapter.chat(request);

    // then
    assertThat(response.finishReason()).isEqualTo(FinishReason.LENGTH);
    assertThat(response.aiMessage().text()).isEqualTo("Partial response...");
  }

  @Test
  void translates_system_and_tool_result_messages_in_request() {
    // given
    var contentBlock = ContentBlock.ofText(textBlock("Done"));
    var message = message("msg_004", List.of(contentBlock), StopReason.END_TURN, usage(30, 10));
    when(mockMessageService.create(any(MessageCreateParams.class))).thenReturn(message);

    var toolRequest =
        ToolExecutionRequest.builder().id("toolu_001").name("do_thing").arguments("{}").build();
    var toolResult = ToolExecutionResultMessage.from(toolRequest, "result text");

    var request =
        ChatRequest.builder()
            .messages(
                List.of(
                    SystemMessage.from("You are a helpful assistant"),
                    new UserMessage("Do a thing"),
                    AiMessage.from(toolRequest),
                    toolResult))
            .toolSpecifications(
                List.of(
                    ToolSpecification.builder()
                        .name("do_thing")
                        .description("Does the thing")
                        .build()))
            .build();

    // when
    ChatResponse response = adapter.chat(request);

    // then
    assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    assertThat(response.aiMessage().text()).isEqualTo("Done");
  }

  @Test
  void bad_request_becomes_connector_input_exception() {
    // given
    var ex =
        BadRequestException.builder().headers(EMPTY_HEADERS).body(JsonValue.from(null)).build();
    when(mockMessageService.create(any(MessageCreateParams.class))).thenThrow(ex);

    var request = ChatRequest.builder().messages(List.of(new UserMessage("hello"))).build();

    // when / then
    assertThatThrownBy(() -> adapter.chat(request))
        .isInstanceOf(ConnectorInputException.class)
        .hasCause(ex);
  }

  @Test
  void unauthorized_becomes_connector_input_exception() {
    // given
    var ex =
        UnauthorizedException.builder().headers(EMPTY_HEADERS).body(JsonValue.from(null)).build();
    when(mockMessageService.create(any(MessageCreateParams.class))).thenThrow(ex);

    var request = ChatRequest.builder().messages(List.of(new UserMessage("hello"))).build();

    // when / then
    assertThatThrownBy(() -> adapter.chat(request))
        .isInstanceOf(ConnectorInputException.class)
        .hasCause(ex);
  }

  @Test
  void rate_limit_becomes_retryable_connector_exception() {
    // given
    var ex = RateLimitException.builder().headers(EMPTY_HEADERS).body(JsonValue.from(null)).build();
    when(mockMessageService.create(any(MessageCreateParams.class))).thenThrow(ex);

    var request = ChatRequest.builder().messages(List.of(new UserMessage("hello"))).build();

    // when / then
    assertThatThrownBy(() -> adapter.chat(request))
        .isInstanceOf(ConnectorException.class)
        .isNotInstanceOf(ConnectorInputException.class)
        .hasCause(ex);
  }

  @Test
  void internal_server_error_becomes_retryable_connector_exception() {
    // given
    var ex =
        InternalServerException.builder()
            .statusCode(500)
            .headers(EMPTY_HEADERS)
            .body(JsonValue.from(null))
            .build();
    when(mockMessageService.create(any(MessageCreateParams.class))).thenThrow(ex);

    var request = ChatRequest.builder().messages(List.of(new UserMessage("hello"))).build();

    // when / then
    assertThatThrownBy(() -> adapter.chat(request))
        .isInstanceOf(ConnectorException.class)
        .isNotInstanceOf(ConnectorInputException.class)
        .hasCause(ex);
  }

  @Test
  void permission_denied_becomes_connector_input_exception() {
    // given
    var ex =
        PermissionDeniedException.builder()
            .headers(EMPTY_HEADERS)
            .body(JsonValue.from(null))
            .build();
    when(mockMessageService.create(any(MessageCreateParams.class))).thenThrow(ex);

    var request = ChatRequest.builder().messages(List.of(new UserMessage("hello"))).build();

    // when / then
    assertThatThrownBy(() -> adapter.chat(request))
        .isInstanceOf(ConnectorInputException.class)
        .hasCause(ex);
  }

  @Test
  void not_found_becomes_connector_input_exception() {
    // given
    var ex = NotFoundException.builder().headers(EMPTY_HEADERS).body(JsonValue.from(null)).build();
    when(mockMessageService.create(any(MessageCreateParams.class))).thenThrow(ex);

    var request = ChatRequest.builder().messages(List.of(new UserMessage("hello"))).build();

    // when / then
    assertThatThrownBy(() -> adapter.chat(request))
        .isInstanceOf(ConnectorInputException.class)
        .hasCause(ex);
  }

  @Test
  void unprocessable_entity_becomes_connector_input_exception() {
    // given
    var ex =
        UnprocessableEntityException.builder()
            .headers(EMPTY_HEADERS)
            .body(JsonValue.from(null))
            .build();
    when(mockMessageService.create(any(MessageCreateParams.class))).thenThrow(ex);

    var request = ChatRequest.builder().messages(List.of(new UserMessage("hello"))).build();

    // when / then
    assertThatThrownBy(() -> adapter.chat(request))
        .isInstanceOf(ConnectorInputException.class)
        .hasCause(ex);
  }

  @Test
  void transport_io_failure_becomes_retryable_connector_exception() {
    // given
    var cause = new java.io.IOException("connect refused");
    var ex = new AnthropicIoException("net down", cause);
    when(mockMessageService.create(any(MessageCreateParams.class))).thenThrow(ex);

    var request = ChatRequest.builder().messages(List.of(new UserMessage("hello"))).build();

    // when / then
    assertThatThrownBy(() -> adapter.chat(request))
        .isInstanceOf(ConnectorException.class)
        .isNotInstanceOf(ConnectorInputException.class)
        .hasCause(ex)
        .satisfies(
            thrown -> {
              ConnectorException connectorEx = (ConnectorException) thrown;
              assertThat(connectorEx.getErrorCode()).isEqualTo("TRANSPORT_ERROR");
            });
  }
}
