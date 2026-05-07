/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.UnresolvedModelServerException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatOptions;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatStreamListener;
import io.camunda.connector.agenticai.aiagent.framework.api.ResponseFormat;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Langchain4JChatModelApiTest {

  private static final String SYSTEM_PROMPT = "You are a helpful assistant. Be nice.";
  private static final String USER_PROMPT = "Write a haiku about the sea.";
  private static final String RESPONSE_TEXT =
      "Endless waves whisper | moonlight dances on the tide | secrets drift below.";

  private static final List<Message> INPUT_MESSAGES =
      List.of(systemMessage(SYSTEM_PROMPT), userMessage(USER_PROMPT));

  private static final List<ChatMessage> L4J_MESSAGES =
      List.of(
          dev.langchain4j.data.message.SystemMessage.systemMessage(SYSTEM_PROMPT),
          dev.langchain4j.data.message.UserMessage.userMessage(USER_PROMPT));

  private static final AssistantMessage ASSISTANT_MESSAGE = assistantMessage(RESPONSE_TEXT);

  private static final List<ToolDefinition> TOOL_DEFINITIONS =
      List.of(
          ToolDefinition.builder().name("GetTime").description("Returns the current time").build());

  private static final List<ToolSpecification> L4J_TOOL_SPECIFICATIONS =
      List.of(
          ToolSpecification.builder()
              .name("GetTime")
              .description("Returns the current time")
              .build());

  @Mock private ChatModel chatModel;
  @Mock private ChatMessageConverter chatMessageConverter;
  @Mock private ToolSpecificationConverter toolSpecificationConverter;
  @Mock private JsonSchemaConverter jsonSchemaConverter;

  @Mock private ChatResponse chatResponse;

  @Captor private ArgumentCaptor<ChatRequest> chatRequestCaptor;

  private Langchain4JChatModelApi api;

  @BeforeEach
  void setUp() {
    when(chatMessageConverter.map(INPUT_MESSAGES)).thenReturn(L4J_MESSAGES);
    when(toolSpecificationConverter.asToolSpecifications(TOOL_DEFINITIONS))
        .thenReturn(L4J_TOOL_SPECIFICATIONS);
    when(chatModel.chat(chatRequestCaptor.capture())).thenReturn(chatResponse);
    when(chatMessageConverter.toAssistantMessage(chatResponse))
        .thenReturn(
            ASSISTANT_MESSAGE.withUsage(
                AgentMetrics.TokenUsage.builder().inputTokenCount(5).outputTokenCount(6).build()));

    api =
        new Langchain4JChatModelApi(
            chatModel, chatMessageConverter, toolSpecificationConverter, jsonSchemaConverter);
  }

  @Test
  void modelRequestContainsMessagesAndToolSpecifications() {
    complete(textOptions());

    final var chatRequest = chatRequestCaptor.getValue();
    assertThat(chatRequest.messages()).containsExactlyElementsOf(L4J_MESSAGES);
    assertThat(chatRequest.toolSpecifications()).containsExactlyElementsOf(L4J_TOOL_SPECIFICATIONS);
  }

  @Test
  void wrapsUnderlyingExceptionsInConnectorException() {
    reset(chatModel, chatResponse, chatMessageConverter);
    when(chatMessageConverter.map(INPUT_MESSAGES)).thenReturn(L4J_MESSAGES);

    final var cause = new ModelNotFoundException("Model 'dummy' was not found");
    doThrow(cause).when(chatModel).chat(any(ChatRequest.class));

    assertThatThrownBy(() -> complete(textOptions()).join())
        .isInstanceOfSatisfying(
            CompletionException.class,
            wrapper ->
                assertThat(wrapper.getCause())
                    .isInstanceOfSatisfying(
                        ConnectorException.class,
                        ex -> {
                          assertThat(ex.getErrorCode()).isEqualTo("FAILED_MODEL_CALL");
                          assertThat(ex.getMessage())
                              .isEqualTo("Model call failed: Model 'dummy' was not found");
                          assertThat(ex.getCause()).isEqualTo(cause);
                        }));
  }

  @Test
  void usesExceptionClassIfNoMessageIncludedInException() {
    reset(chatModel, chatResponse, chatMessageConverter);
    when(chatMessageConverter.map(INPUT_MESSAGES)).thenReturn(L4J_MESSAGES);

    final var cause = new UnresolvedModelServerException((String) null);
    doThrow(cause).when(chatModel).chat(any(ChatRequest.class));

    assertThatThrownBy(() -> complete(textOptions()).join())
        .isInstanceOfSatisfying(
            CompletionException.class,
            wrapper ->
                assertThat(wrapper.getCause())
                    .isInstanceOfSatisfying(
                        ConnectorException.class,
                        ex -> {
                          assertThat(ex.getErrorCode()).isEqualTo("FAILED_MODEL_CALL");
                          assertThat(ex.getMessage())
                              .isEqualTo("Model call failed: UnresolvedModelServerException");
                          assertThat(ex.getCause()).isEqualTo(cause);
                        }));
  }

  @Test
  void doesNotExplicitlyConfigureResponseFormatForText() {
    complete(textOptions());

    assertThat(chatRequestCaptor.getValue().responseFormat()).isNull();
  }

  @Test
  void doesNotExplicitlyConfigureResponseFormatWhenNull() {
    complete(options(null));

    assertThat(chatRequestCaptor.getValue().responseFormat()).isNull();
  }

  @Test
  void requestsJsonResponseWhenConfigured() {
    complete(options(new ResponseFormat.Json(null, null)));

    final var format = chatRequestCaptor.getValue().responseFormat();
    assertThat(format.type()).isEqualTo(ResponseFormatType.JSON);
    assertThat(format.jsonSchema()).isNull();
  }

  @Test
  void requestsJsonResponseWithSchemaWhenConfigured() {
    final Map<String, Object> schema = Map.of("type", "object", "description", "My schema");
    final var schemaName = "Foo";

    final var jsonObjectSchema = JsonObjectSchema.builder().description("My schema").build();
    when(jsonSchemaConverter.mapToSchema(schema)).thenReturn(jsonObjectSchema);

    complete(options(new ResponseFormat.Json(schemaName, schema)));

    final var format = chatRequestCaptor.getValue().responseFormat();
    assertThat(format.type()).isEqualTo(ResponseFormatType.JSON);
    assertThat(format.jsonSchema()).isNotNull();
    assertThat(format.jsonSchema().name()).isEqualTo(schemaName);
    assertThat(format.jsonSchema().rootElement()).isEqualTo(jsonObjectSchema);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void usesDefaultSchemaNameIfNullOrBlank(String schemaName) {
    final Map<String, Object> schema = Map.of("type", "object", "description", "My schema");

    final var jsonObjectSchema = JsonObjectSchema.builder().description("My schema").build();
    when(jsonSchemaConverter.mapToSchema(schema)).thenReturn(jsonObjectSchema);

    complete(options(new ResponseFormat.Json(schemaName, schema)));

    final var format = chatRequestCaptor.getValue().responseFormat();
    assertThat(format.type()).isEqualTo(ResponseFormatType.JSON);
    assertThat(format.jsonSchema()).isNotNull();
    assertThat(format.jsonSchema().name()).isEqualTo("Response");
    assertThat(format.jsonSchema().rootElement()).isEqualTo(jsonObjectSchema);
  }

  @Test
  void responseCarriesUsageAndStopReasonFromAssistantMessage() {
    final var response = complete(textOptions()).join();

    assertThat(response.assistantMessage()).isNotNull();
    assertThat(response.usage())
        .isEqualTo(
            AgentMetrics.TokenUsage.builder().inputTokenCount(5).outputTokenCount(6).build());
    assertThat(response.errorMessage()).isNull();
  }

  private java.util.concurrent.CompletableFuture<
          io.camunda.connector.agenticai.aiagent.framework.api.ChatResponse>
      complete(ChatOptions options) {
    return api.complete(
        new io.camunda.connector.agenticai.aiagent.framework.api.ChatRequest(
            INPUT_MESSAGES, null, TOOL_DEFINITIONS),
        options,
        ChatStreamListener.NOOP);
  }

  private static ChatOptions textOptions() {
    return options(new ResponseFormat.Text());
  }

  private static ChatOptions options(ResponseFormat responseFormat) {
    return new ChatOptions(null, null, null, responseFormat, Map.of());
  }
}
