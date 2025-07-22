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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.memory.runtime.DefaultRuntimeMemory;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentJobContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
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
class Langchain4JAiFrameworkAdapterTest {

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

  private static final AgentContext AGENT_CONTEXT =
      AgentContext.empty()
          .withState(AgentState.WAITING_FOR_TOOL_INPUT)
          .withToolDefinitions(TOOL_DEFINITIONS)
          .withMetrics(
              AgentMetrics.empty()
                  .withModelCalls(5)
                  .withTokenUsage(
                      AgentMetrics.TokenUsage.empty()
                          .withInputTokenCount(10)
                          .withOutputTokenCount(20)));

  @Mock private ChatModelFactory chatModelFactory;
  @Mock private ChatMessageConverter chatMessageConverter;
  @Mock private ToolSpecificationConverter toolSpecificationConverter;
  @Mock private JsonSchemaConverter jsonSchemaConverter;

  @Mock private ChatModel chatModel;
  @Mock private ChatResponse chatResponse;

  @Captor private ArgumentCaptor<ChatRequest> chatRequestCaptor;

  @Mock private AgentJobContext agentJobContext;

  private RuntimeMemory runtimeMemory;
  private Langchain4JAiFrameworkAdapter adapter;

  @BeforeEach
  void setUp() {
    runtimeMemory = new DefaultRuntimeMemory();
    runtimeMemory.addMessages(INPUT_MESSAGES);
    when(chatMessageConverter.map(runtimeMemory.filteredMessages())).thenReturn(L4J_MESSAGES);

    when(toolSpecificationConverter.asToolSpecifications(TOOL_DEFINITIONS))
        .thenReturn(L4J_TOOL_SPECIFICATIONS);

    when(chatModelFactory.createChatModel(any())).thenReturn(chatModel);
    when(chatModel.chat(chatRequestCaptor.capture())).thenReturn(chatResponse);
    when(chatResponse.tokenUsage()).thenReturn(new TokenUsage(5, 6));
    when(chatMessageConverter.toAssistantMessage(chatResponse)).thenReturn(ASSISTANT_MESSAGE);

    adapter =
        new Langchain4JAiFrameworkAdapter(
            chatModelFactory,
            chatMessageConverter,
            toolSpecificationConverter,
            jsonSchemaConverter);
  }

  @Test
  void modelRequestContainsMessagesAndToolSpecifications() {
    adapter.executeChatRequest(createExecutionContext(), AGENT_CONTEXT, runtimeMemory);

    final var chatRequest = chatRequestCaptor.getValue();
    assertThat(chatRequest.messages()).containsExactlyElementsOf(L4J_MESSAGES);
    assertThat(chatRequest.toolSpecifications()).containsExactlyElementsOf(L4J_TOOL_SPECIFICATIONS);
  }

  @Test
  void requestsTextResponseWhenConfigured() {
    adapter.executeChatRequest(createExecutionContext(), AGENT_CONTEXT, runtimeMemory);

    final var chatRequest = chatRequestCaptor.getValue();
    assertThat(chatRequest.responseFormat().type()).isEqualTo(ResponseFormatType.TEXT);
    assertThat(chatRequest.responseFormat().jsonSchema()).isNull();
  }

  @Test
  void requestsTextResponseIfResponseConfigurationIsMissing() {
    adapter.executeChatRequest(createExecutionContext(null), AGENT_CONTEXT, runtimeMemory);

    final var chatRequest = chatRequestCaptor.getValue();
    assertThat(chatRequest.responseFormat().type()).isEqualTo(ResponseFormatType.TEXT);
    assertThat(chatRequest.responseFormat().jsonSchema()).isNull();
  }

  @Test
  void requestsTextResponseIfResponseFormatConfigurationIsMissing() {
    adapter.executeChatRequest(
        createExecutionContext(new ResponseConfiguration(null, false)),
        AGENT_CONTEXT,
        runtimeMemory);

    final var chatRequest = chatRequestCaptor.getValue();
    assertThat(chatRequest.responseFormat().type()).isEqualTo(ResponseFormatType.TEXT);
    assertThat(chatRequest.responseFormat().jsonSchema()).isNull();
  }

  @Test
  void requestsJsonResponseWhenConfigured() {
    adapter.executeChatRequest(
        createExecutionContext(
            new ResponseConfiguration(new JsonResponseFormatConfiguration(null, null), false)),
        AGENT_CONTEXT,
        runtimeMemory);

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

    adapter.executeChatRequest(
        createExecutionContext(
            new ResponseConfiguration(
                new JsonResponseFormatConfiguration(schema, schemaName), false)),
        AGENT_CONTEXT,
        runtimeMemory);

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

    adapter.executeChatRequest(
        createExecutionContext(
            new ResponseConfiguration(
                new JsonResponseFormatConfiguration(schema, schemaName), false)),
        AGENT_CONTEXT,
        runtimeMemory);

    final var chatRequest = chatRequestCaptor.getValue();
    assertThat(chatRequest.responseFormat().type()).isEqualTo(ResponseFormatType.JSON);
    assertThat(chatRequest.responseFormat().jsonSchema()).isNotNull();
    assertThat(chatRequest.responseFormat().jsonSchema().name()).isEqualTo("Response");
    assertThat(chatRequest.responseFormat().jsonSchema().rootElement()).isEqualTo(jsonObjectSchema);
  }

  @Test
  void incrementsMetricsFromResponse() {
    final var adapterResponse =
        adapter.executeChatRequest(createExecutionContext(), AGENT_CONTEXT, runtimeMemory);
    final var expectedMetrics =
        AGENT_CONTEXT
            .metrics()
            .withModelCalls(6)
            .withTokenUsage(
                AgentMetrics.TokenUsage.empty()
                    .withInputTokenCount(15) // 10 from context + 5 from response
                    .withOutputTokenCount(26)); // 20 from context + 6 from response

    assertThat(adapterResponse.agentContext())
        .usingRecursiveComparison()
        .isEqualTo(AGENT_CONTEXT.withMetrics(expectedMetrics));
  }

  @Test
  void tokenUsageIsUnchangedIfMissingInResponse() {
    when(chatResponse.tokenUsage()).thenReturn(null);

    final var adapterResponse =
        adapter.executeChatRequest(createExecutionContext(), AGENT_CONTEXT, runtimeMemory);

    assertThat(adapterResponse.agentContext())
        .usingRecursiveComparison()
        .isEqualTo(AGENT_CONTEXT.withMetrics(AGENT_CONTEXT.metrics().withModelCalls(6)));
  }

  private AgentExecutionContext createExecutionContext() {
    return createExecutionContext(
        new ResponseConfiguration(new TextResponseFormatConfiguration(false), false));
  }

  private AgentExecutionContext createExecutionContext(
      ResponseConfiguration responseConfiguration) {
    final var agentRequest =
        new AgentRequest(
            new OpenAiProviderConfiguration(null),
            new AgentRequestData(
                AGENT_CONTEXT, null, null, null, null, null, responseConfiguration));

    return new AgentExecutionContext(agentJobContext, agentRequest);
  }
}
