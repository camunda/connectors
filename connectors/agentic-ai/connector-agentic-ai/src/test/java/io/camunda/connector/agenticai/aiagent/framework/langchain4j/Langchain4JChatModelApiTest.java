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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.UnresolvedModelServerException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelResult;
import io.camunda.connector.agenticai.aiagent.framework.api.ProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.request.OutboundConnectorResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;
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

  private static final ConversationSnapshot SNAPSHOT =
      new ConversationSnapshot(INPUT_MESSAGES, TOOL_DEFINITIONS);

  private static final AnthropicProviderConfiguration PROVIDER =
      new AnthropicProviderConfiguration(
          new AnthropicConnection(
              null,
              new AnthropicAuthentication("api-key"),
              null,
              new AnthropicModel("claude", null)));

  @Mock private ChatMessageConverter chatMessageConverter;
  @Mock private ToolSpecificationConverter toolSpecificationConverter;
  @Mock private JsonSchemaConverter jsonSchemaConverter;

  @Mock private CloseableChatModel chatModel;
  @Mock private ChatResponse chatResponse;

  @Captor private ArgumentCaptor<ChatRequest> chatRequestCaptor;

  private Langchain4JChatModelApi api;

  @BeforeEach
  void setUp() {
    // lenient: the close()/capabilities() tests below never call() the model, so these stubs are
    // unused on those paths.
    lenient().when(chatMessageConverter.map(INPUT_MESSAGES)).thenReturn(L4J_MESSAGES);

    lenient()
        .when(toolSpecificationConverter.asToolSpecifications(TOOL_DEFINITIONS))
        .thenReturn(L4J_TOOL_SPECIFICATIONS);

    lenient().when(chatModel.chat(chatRequestCaptor.capture())).thenReturn(chatResponse);
    lenient().when(chatResponse.tokenUsage()).thenReturn(new TokenUsage(5, 6));
    lenient()
        .when(chatMessageConverter.toAssistantMessage(chatResponse))
        .thenReturn(ASSISTANT_MESSAGE);

    api =
        new Langchain4JChatModelApi(
            chatModel,
            chatMessageConverter,
            toolSpecificationConverter,
            jsonSchemaConverter,
            Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
  }

  @Test
  void modelRequestContainsMessagesAndToolSpecifications() {
    api.call(new ChatModelRequest(createExecutionContext(), SNAPSHOT));

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

    assertThatThrownBy(() -> api.call(new ChatModelRequest(createExecutionContext(), SNAPSHOT)))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            ex -> {
              assertThat(ex.getErrorCode()).isEqualTo("FAILED_MODEL_CALL");
              assertThat(ex.getMessage())
                  .isEqualTo("Model call failed: Model 'dummy' was not found");
              assertThat(ex.getCause()).isEqualTo(cause);
            });
  }

  @Test
  void usesExceptionClassIfNoMessageIncludedInException() {
    reset(chatModel, chatResponse, chatMessageConverter);
    when(chatMessageConverter.map(INPUT_MESSAGES)).thenReturn(L4J_MESSAGES);

    final var cause = new UnresolvedModelServerException((String) null);
    doThrow(cause).when(chatModel).chat(any(ChatRequest.class));

    assertThatThrownBy(() -> api.call(new ChatModelRequest(createExecutionContext(), SNAPSHOT)))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            ex -> {
              assertThat(ex.getErrorCode()).isEqualTo("FAILED_MODEL_CALL");
              assertThat(ex.getMessage())
                  .isEqualTo("Model call failed: UnresolvedModelServerException");
              assertThat(ex.getCause()).isEqualTo(cause);
            });
  }

  @Test
  void doesNotExplicitelyConfigureResponseFormatWhenTextFormatIsConfigured() {
    api.call(new ChatModelRequest(createExecutionContext(), SNAPSHOT));

    final var chatRequest = chatRequestCaptor.getValue();
    assertThat(chatRequest.responseFormat()).isNull();
  }

  @Test
  void doesNotExplicitelyConfigureResponseFormatWhenResponseConfigurationIsMissing() {
    api.call(new ChatModelRequest(createExecutionContext(null), SNAPSHOT));

    final var chatRequest = chatRequestCaptor.getValue();
    assertThat(chatRequest.responseFormat()).isNull();
  }

  @Test
  void doesNotExplicitelyConfigureResponseFormatWhenResponseFormatConfigurationIsMissing() {
    api.call(
        new ChatModelRequest(
            createExecutionContext(new OutboundConnectorResponseConfiguration(null, false)),
            SNAPSHOT));

    final var chatRequest = chatRequestCaptor.getValue();
    assertThat(chatRequest.responseFormat()).isNull();
  }

  @Test
  void requestsJsonResponseWhenConfigured() {
    api.call(
        new ChatModelRequest(
            createExecutionContext(
                new OutboundConnectorResponseConfiguration(
                    new JsonResponseFormatConfiguration(null, null), false)),
            SNAPSHOT));

    final var chatRequest = chatRequestCaptor.getValue();
    assertThat(chatRequest.responseFormat().type()).isEqualTo(ResponseFormatType.JSON);
    assertThat(chatRequest.responseFormat().jsonSchema()).isNull();
  }

  @Test
  void requestsJsonResponseWithSchemaWhenConfigured() {
    final Map<String, Object> schema = Map.of("type", "object", "description", "My schema");
    final var schemaName = "Foo";

    final var jsonObjectSchema = JsonObjectSchema.builder().description("My schema").build();
    when(jsonSchemaConverter.mapToSchema(schema)).thenReturn(jsonObjectSchema);

    api.call(
        new ChatModelRequest(
            createExecutionContext(
                new OutboundConnectorResponseConfiguration(
                    new JsonResponseFormatConfiguration(schema, schemaName), false)),
            SNAPSHOT));

    final var chatRequest = chatRequestCaptor.getValue();
    assertThat(chatRequest.responseFormat().type()).isEqualTo(ResponseFormatType.JSON);
    assertThat(chatRequest.responseFormat().jsonSchema()).isNotNull();
    assertThat(chatRequest.responseFormat().jsonSchema().name()).isEqualTo(schemaName);
    assertThat(chatRequest.responseFormat().jsonSchema().rootElement()).isEqualTo(jsonObjectSchema);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void usesDefaultSchemaNameIfNullOrBlank(String schemaName) {
    final Map<String, Object> schema = Map.of("type", "object", "description", "My schema");

    final var jsonObjectSchema = JsonObjectSchema.builder().description("My schema").build();
    when(jsonSchemaConverter.mapToSchema(schema)).thenReturn(jsonObjectSchema);

    api.call(
        new ChatModelRequest(
            createExecutionContext(
                new OutboundConnectorResponseConfiguration(
                    new JsonResponseFormatConfiguration(schema, schemaName), false)),
            SNAPSHOT));

    final var chatRequest = chatRequestCaptor.getValue();
    assertThat(chatRequest.responseFormat().type()).isEqualTo(ResponseFormatType.JSON);
    assertThat(chatRequest.responseFormat().jsonSchema()).isNotNull();
    assertThat(chatRequest.responseFormat().jsonSchema().name()).isEqualTo("Response");
    assertThat(chatRequest.responseFormat().jsonSchema().rootElement()).isEqualTo(jsonObjectSchema);
  }

  @Test
  void returnsTokenUsageFromResponse() {
    final var result = api.call(new ChatModelRequest(createExecutionContext(), SNAPSHOT));

    assertThat(result.metrics().tokenUsage())
        .usingRecursiveComparison()
        .isEqualTo(AgentMetrics.TokenUsage.empty().withInputTokenCount(5).withOutputTokenCount(6));
    assertThat(result.metrics().modelCalls()).isEqualTo(1);
    assertThat(result.metrics().executionTime()).isNotNull();
  }

  @Test
  void returnsEmptyTokenUsageIfMissingInResponse() {
    when(chatResponse.tokenUsage()).thenReturn(null);

    final var result = api.call(new ChatModelRequest(createExecutionContext(), SNAPSHOT));

    assertThat(result.metrics().tokenUsage())
        .usingRecursiveComparison()
        .isEqualTo(AgentMetrics.TokenUsage.empty());
  }

  @Test
  void resultIsCompleted() {
    final var result = api.call(new ChatModelRequest(createExecutionContext(), SNAPSHOT));

    assertThat(result).isInstanceOf(ChatModelResult.Completed.class);
    assertThat(result.assistantMessage()).isEqualTo(ASSISTANT_MESSAGE);
  }

  @Test
  void capabilitiesReturnsDefaultCapabilitiesProfile() {
    assertThat(api.capabilities()).isSameAs(Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
    assertThat(api.capabilities().userMessageModalities())
        .containsExactly(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT);
    assertThat(api.capabilities().toolResultModalities()).isEmpty();
    assertThat(api.capabilities().assistantMessageModalities()).containsExactly(Modality.TEXT);
  }

  @Test
  void closesUnderlyingChatModel() {
    api.close();

    verify(chatModel).close();
  }

  @Test
  void closeLogsWarningInsteadOfThrowingWhenChatModelCloseFails() {
    doThrow(new RuntimeException("boom")).when(chatModel).close();

    api.close();

    verify(chatModel).close();
  }

  private AgentExecutionContext createExecutionContext() {
    return createExecutionContext(
        new OutboundConnectorResponseConfiguration(
            new TextResponseFormatConfiguration(false), false));
  }

  private AgentExecutionContext createExecutionContext(
      ResponseConfiguration responseConfiguration) {
    final var executionContext = mock(AgentExecutionContext.class);
    when(executionContext.configuration())
        .thenReturn(
            new AgentConfiguration(
                new ProviderChatModelApiConfiguration(PROVIDER),
                PROVIDER.model(),
                PROVIDER.providerType(),
                null,
                null,
                null,
                null,
                null,
                responseConfiguration));

    return executionContext;
  }
}
