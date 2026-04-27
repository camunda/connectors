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
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
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
    var jsonSchemaConverter = new JsonSchemaConverter(new ObjectMapper());
    adapter = new AnthropicOnFoundryChatModel(mockClient, modelConfig, jsonSchemaConverter);
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
  // Smoke test — end-to-end through adapter (request + response path)
  // -------------------------------------------------------------------------

  @Test
  void translatesSystemAndToolResultMessagesInRequest() {
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

  // -------------------------------------------------------------------------
  // Exception translation tests
  // -------------------------------------------------------------------------

  @Test
  void badRequestBecomesConnectorInputException() {
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
  void unauthorizedBecomesConnectorInputException() {
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
  void permissionDeniedBecomesConnectorInputException() {
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
  void notFoundBecomesConnectorInputException() {
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
  void unprocessableEntityBecomesConnectorInputException() {
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
  void rateLimitBecomesRetryableConnectorException() {
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
  void internalServerErrorBecomesRetryableConnectorException() {
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
  void transportIoFailureBecomesRetryableConnectorException() {
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
