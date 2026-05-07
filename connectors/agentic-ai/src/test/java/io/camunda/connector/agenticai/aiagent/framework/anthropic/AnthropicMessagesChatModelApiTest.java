/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.toolCallResultMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextCitation;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatOptions;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatStreamListener;
import io.camunda.connector.agenticai.aiagent.framework.api.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.api.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnthropicMessagesChatModelApiTest {

  private static final String MODEL_ID = "claude-sonnet-4-6";

  private static final ModelCapabilities CAPABILITIES =
      new ModelCapabilities(
          List.of(Modality.TEXT, Modality.IMAGE, Modality.PDF),
          List.of(Modality.TEXT, Modality.IMAGE),
          List.of(Modality.TEXT),
          true,
          true,
          true,
          true,
          200000,
          64000);

  @Mock private AnthropicClient client;
  @Mock private MessageService messageService;

  @Captor private ArgumentCaptor<MessageCreateParams> paramsCaptor;

  private AnthropicMessagesChatModelApi api;

  @BeforeEach
  void setUp() {
    when(client.messages()).thenReturn(messageService);
    api =
        new AnthropicMessagesChatModelApi(
            client,
            MODEL_ID,
            new com.fasterxml.jackson.databind.ObjectMapper(),
            CAPABILITIES,
            1024L,
            null,
            null,
            null);
  }

  @Test
  void capabilitiesReturnsConfiguredInstance() {
    assertThat(api.capabilities()).isSameAs(CAPABILITIES);
  }

  @Test
  void buildsExpectedMessageCreateParamsForSimpleConversation() {
    when(messageService.create((MessageCreateParams) paramsCaptor.capture()))
        .thenReturn(textOnlyResponse("Hi there!"));

    var request =
        new ChatRequest(
            List.of(systemMessage("be helpful"), userMessage("hello")), List.of(), null);

    api.complete(request, defaultOptions(), ChatStreamListener.NOOP).join();

    var params = paramsCaptor.getValue();
    assertThat(params.model().asString()).isEqualTo(MODEL_ID);
    assertThat(params.maxTokens()).isEqualTo(1024L);
    assertThat(params.system()).isPresent();
    assertThat(params.system().get().string()).hasValue("be helpful");
    assertThat(params.messages()).hasSize(1);
    var first = params.messages().getFirst();
    assertThat(first.role()).isEqualTo(MessageParam.Role.USER);
  }

  @Test
  void appliesChatOptionsMaxOutputTokensOverride() {
    when(messageService.create((MessageCreateParams) paramsCaptor.capture()))
        .thenReturn(textOnlyResponse("ok"));

    var options = new ChatOptions(2048, null, null, Map.of());
    api.complete(
            new ChatRequest(List.of(userMessage("hi")), List.of(), null),
            options,
            ChatStreamListener.NOOP)
        .join();

    assertThat(paramsCaptor.getValue().maxTokens()).isEqualTo(2048L);
  }

  @Test
  void mapsAssistantToolCallsBackToContentBlocks() {
    when(messageService.create(any(MessageCreateParams.class)))
        .thenReturn(toolUseResponse("getWeather", "abc", Map.of("location", "MUC")));

    var response =
        api.complete(
                new ChatRequest(List.of(userMessage("weather?")), tools(), null),
                defaultOptions(),
                ChatStreamListener.NOOP)
            .join();

    var assistant = response.assistantMessage();
    assertThat(assistant.toolCalls())
        .extracting(ToolCall::id, ToolCall::name)
        .containsExactly(Tuple.tuple("abc", "getWeather"));
    assertThat(assistant.toolCalls().getFirst().arguments()).containsEntry("location", "MUC");
    assertThat(assistant.stopReason())
        .isEqualTo(io.camunda.connector.agenticai.model.message.StopReason.TOOL_USE);
  }

  @Test
  void priorAssistantToolCallsAndResultsRoundTripIntoParams() {
    when(messageService.create((MessageCreateParams) paramsCaptor.capture()))
        .thenReturn(textOnlyResponse("ack"));

    var prior =
        assistantMessage(
            "let me check",
            List.of(
                ToolCall.builder()
                    .id("abc")
                    .name("getWeather")
                    .arguments(Map.of("location", "MUC"))
                    .build()));
    var results =
        toolCallResultMessage(
            List.of(
                ToolCallResult.builder().id("abc").name("getWeather").content("Sunny").build()));

    api.complete(
            new ChatRequest(
                List.of(userMessage("weather?"), prior, results, userMessage("thanks")),
                tools(),
                null),
            defaultOptions(),
            ChatStreamListener.NOOP)
        .join();

    var params = paramsCaptor.getValue();
    assertThat(params.messages()).hasSize(4);
    assertThat(params.messages().get(1).role()).isEqualTo(MessageParam.Role.ASSISTANT);
    assertThat(params.messages().get(2).role()).isEqualTo(MessageParam.Role.USER);
  }

  @Test
  void wrapsSdkExceptionInConnectorException() {
    when(messageService.create(any(MessageCreateParams.class)))
        .thenThrow(new RuntimeException("boom"));

    var future =
        api.complete(
            new ChatRequest(List.of(userMessage("hi")), List.of(), null),
            defaultOptions(),
            ChatStreamListener.NOOP);

    assertThatThrownBy(future::join)
        .isInstanceOf(CompletionException.class)
        .cause()
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Anthropic Messages call failed");
  }

  private static ChatOptions defaultOptions() {
    return new ChatOptions(null, null, null, Map.of());
  }

  private static List<ToolDefinition> tools() {
    return List.of(
        ToolDefinition.builder()
            .name("getWeather")
            .description("Returns the current weather")
            .inputSchema(
                Map.of(
                    "type", "object",
                    "properties", Map.of("location", Map.of("type", "string")),
                    "required", List.of("location")))
            .build());
  }

  private static com.anthropic.models.messages.Message textOnlyResponse(String text) {
    return baseResponseBuilder("msg_1", StopReason.END_TURN)
        .addContent(
            TextBlock.builder().text(text).citations((java.util.List<TextCitation>) null).build())
        .usage(usage(10, 5))
        .build();
  }

  private static com.anthropic.models.messages.Message toolUseResponse(
      String name, String id, Map<String, Object> input) {
    var inputBuilder = ToolUseBlock.builder().id(id).name(name);
    inputBuilder.input(JsonValue.from(input));
    inputBuilder.caller(com.anthropic.models.messages.DirectCaller.builder().build());
    return baseResponseBuilder("msg_2", StopReason.TOOL_USE)
        .addContent(ContentBlock.ofToolUse(inputBuilder.build()))
        .usage(usage(15, 8))
        .build();
  }

  private static com.anthropic.models.messages.Message.Builder baseResponseBuilder(
      String id, StopReason stopReason) {
    return com.anthropic.models.messages.Message.builder()
        .id(id)
        .model(Model.of(MODEL_ID))
        .container(java.util.Optional.empty())
        .stopReason(stopReason)
        .stopSequence(java.util.Optional.empty());
  }

  private static Usage usage(long input, long output) {
    return Usage.builder()
        .inputTokens(input)
        .outputTokens(output)
        .cacheCreation(java.util.Optional.empty())
        .cacheCreationInputTokens(java.util.Optional.empty())
        .cacheReadInputTokens(java.util.Optional.empty())
        .inferenceGeo(java.util.Optional.empty())
        .serverToolUse(java.util.Optional.empty())
        .serviceTier(java.util.Optional.empty())
        .build();
  }
}
