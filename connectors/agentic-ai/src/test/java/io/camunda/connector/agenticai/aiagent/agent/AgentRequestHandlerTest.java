/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALLS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_DEFINITIONS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentContextInitializationResult;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentResponseInitializationResult;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkChatResponse;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.InProcessMemoryStorageConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentRequestHandlerTest {

  private static final AgentContext INITIAL_AGENT_CONTEXT =
      AgentContext.builder().state(AgentState.READY).toolDefinitions(TOOL_DEFINITIONS).build();

  private static final SystemPromptConfiguration SYSTEM_PROMPT_CONFIGURATION =
      new SystemPromptConfiguration("You are a helpful assistant. Be nice.", Map.of());
  private static final UserPromptConfiguration USER_PROMPT_CONFIGURATION_WITHOUT_TOOLS =
      new UserPromptConfiguration("Write a haiku about the sea", Map.of(), List.of());
  private static final UserPromptConfiguration USER_PROMPT_CONFIGURATION_WITH_TOOLS =
      new UserPromptConfiguration("What is the weather in Munich?", Map.of(), List.of());

  @Mock private AgentInitializer agentInitializer;
  @Mock private ConversationStoreRegistry conversationStoreRegistry;
  @Mock private AgentLimitsValidator limitsValidator;
  @Mock private AgentMessagesHandler messagesHandler;
  @Mock private GatewayToolHandlerRegistry gatewayToolHandlers;
  @Mock private AiFrameworkAdapter<?> framework;
  @Mock private AgentResponseHandler responseHandler;

  @Mock private AgentExecutionContext agentExecutionContext;

  @Captor private ArgumentCaptor<RuntimeMemory> runtimeMemoryCaptor;

  @InjectMocks private AgentRequestHandlerImpl requestHandler;

  @BeforeEach
  void setUp() {
    doReturn(new InProcessConversationStore())
        .when(conversationStoreRegistry)
        .getConversationStore(eq(agentExecutionContext), any(AgentContext.class));
  }

  @Test
  void directlyReturnsAgentResponseWhenInitializationReturnsResponse() {
    reset(conversationStoreRegistry);

    final var agentResponse =
        AgentResponse.builder()
            .context(AgentContext.builder().state(AgentState.TOOL_DISCOVERY).build())
            .toolCalls(
                List.of(
                    ToolCallProcessVariable.from(
                        ToolCall.builder().id("tool_discovery").name("AGatewayTool").build())))
            .build();

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new AgentResponseInitializationResult(agentResponse));

    final var response = requestHandler.handleRequest(agentExecutionContext);
    assertThat(response).isEqualTo(agentResponse);

    verifyNoInteractions(
        limitsValidator, messagesHandler, gatewayToolHandlers, framework, responseHandler);
  }

  @Test
  void orchestratesRequestExecutionWithoutToolCalls() {
    mockSystemPrompt(SYSTEM_PROMPT_CONFIGURATION);
    mockUserPrompt(USER_PROMPT_CONFIGURATION_WITHOUT_TOOLS, List.of());

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new AgentContextInitializationResult(INITIAL_AGENT_CONTEXT, List.of()));

    final var assistantMessageText =
        "Endless waves whisper | moonlight dances on the tide | secrets drift below.";
    final var assistantMessage = assistantMessage(assistantMessageText);

    final var expectedMessages =
        List.of(
            systemMessage(SYSTEM_PROMPT_CONFIGURATION.prompt()),
            userMessage(USER_PROMPT_CONFIGURATION_WITHOUT_TOOLS.prompt()),
            assistantMessage);

    mockFrameworkExecution(assistantMessage);

    when(gatewayToolHandlers.transformToolCalls(any(AgentContext.class), anyList()))
        .thenAnswer(i -> i.getArgument(1));
    when(responseHandler.createResponse(
            eq(agentExecutionContext), any(AgentContext.class), eq(assistantMessage), anyList()))
        .thenAnswer(
            i ->
                AgentResponse.builder()
                    .context(i.getArgument(1, AgentContext.class))
                    .responseMessage(i.getArgument(2, AssistantMessage.class))
                    .responseText(assistantMessageText)
                    .toolCalls(i.getArgument(3))
                    .build());

    final var response = requestHandler.handleRequest(agentExecutionContext);

    assertThat(runtimeMemoryCaptor.getValue().allMessages())
        .containsExactlyElementsOf(expectedMessages);

    assertThat(response.context().state()).isEqualTo(AgentState.READY);
    assertThat(response.context().metrics()).isEqualTo(new AgentMetrics(1, new TokenUsage(10, 20)));
    assertThat(response.context().conversation())
        .isNotNull()
        .isInstanceOfSatisfying(
            InProcessConversationContext.class,
            c -> assertThat(c.messages()).containsExactlyElementsOf(expectedMessages));

    assertThat(response.responseMessage()).isEqualTo(assistantMessage);
    assertThat(response.responseText()).isEqualTo(assistantMessageText);
    assertThat(response.toolCalls()).isEmpty();

    verify(limitsValidator).validateConfiguredLimits(agentExecutionContext, INITIAL_AGENT_CONTEXT);
  }

  @Test
  void orchestratesRequestExecutionWithToolCalls() {
    mockSystemPrompt(SYSTEM_PROMPT_CONFIGURATION);
    mockUserPrompt(USER_PROMPT_CONFIGURATION_WITH_TOOLS, List.of());

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new AgentContextInitializationResult(INITIAL_AGENT_CONTEXT, List.of()));

    final var assistantMessage = AssistantMessage.builder().toolCalls(TOOL_CALLS).build();

    final var expectedMessages =
        List.of(
            systemMessage(SYSTEM_PROMPT_CONFIGURATION.prompt()),
            userMessage(USER_PROMPT_CONFIGURATION_WITH_TOOLS.prompt()),
            assistantMessage);

    mockFrameworkExecution(assistantMessage);

    final Function<ToolCall, ToolCall> toolCallTransformer =
        toolCall -> toolCall.withId(toolCall.id() + "_transformed");

    when(gatewayToolHandlers.transformToolCalls(any(AgentContext.class), anyList()))
        .thenAnswer(
            i -> {
              List<ToolCall> toolCalls = i.getArgument(1);
              return toolCalls.stream().map(toolCallTransformer).toList();
            });

    when(responseHandler.createResponse(
            eq(agentExecutionContext), any(AgentContext.class), eq(assistantMessage), anyList()))
        .thenAnswer(
            i ->
                AgentResponse.builder()
                    .context(i.getArgument(1, AgentContext.class))
                    .responseMessage(i.getArgument(2, AssistantMessage.class))
                    .toolCalls(i.getArgument(3))
                    .build());

    final var response = requestHandler.handleRequest(agentExecutionContext);

    assertThat(runtimeMemoryCaptor.getValue().allMessages())
        .containsExactlyElementsOf(expectedMessages);

    assertThat(response.context().state()).isEqualTo(AgentState.READY);
    assertThat(response.context().metrics()).isEqualTo(new AgentMetrics(1, new TokenUsage(10, 20)));
    assertThat(response.context().conversation())
        .isNotNull()
        .isInstanceOfSatisfying(
            InProcessConversationContext.class,
            c -> assertThat(c.messages()).containsExactlyElementsOf(expectedMessages));

    assertThat(response.responseMessage()).isEqualTo(assistantMessage);
    assertThat(response.responseText()).isNull();
    assertThat(response.toolCalls())
        .containsExactly(
            new ToolCallProcessVariable(
                new ToolCallProcessVariable.ToolCallMetadata("abcdef_transformed", "getWeather"),
                Map.of("location", "MUC")),
            new ToolCallProcessVariable(
                new ToolCallProcessVariable.ToolCallMetadata("fedcba_transformed", "getDateTime"),
                Map.of()));

    verify(limitsValidator).validateConfiguredLimits(agentExecutionContext, INITIAL_AGENT_CONTEXT);
  }

  @Test
  void usesConfiguredMaxMessagesWhenMessagesExceedContextWindow() {
    final var runtimeMemory =
        setupRuntimeMemorySizeTest(
            new MemoryConfiguration(new InProcessMemoryStorageConfiguration(), 11));

    assertThat(runtimeMemory.allMessages()).hasSize(31);
    assertThat(runtimeMemory.filteredMessages()).hasSize(11);

    assertThat(runtimeMemory.filteredMessages().getFirst())
        .isEqualTo(userMessage("User message 20"));
    assertThat(runtimeMemory.filteredMessages().getLast())
        .isEqualTo(assistantMessage("This is the assistant message"));
  }

  @ParameterizedTest
  @MethodSource("memoryConfigurationsWithoutMaxMessages")
  void fallsBackToDefaultContextWindowSizeWhenMemoryConfigurationIsMissing(
      MemoryConfiguration memoryConfiguration) {
    final var runtimeMemory = setupRuntimeMemorySizeTest(memoryConfiguration);

    assertThat(runtimeMemory.allMessages()).hasSize(31);
    assertThat(runtimeMemory.filteredMessages()).hasSize(20);

    assertThat(runtimeMemory.filteredMessages().getFirst())
        .isEqualTo(userMessage("User message 11"));
    assertThat(runtimeMemory.filteredMessages().getLast())
        .isEqualTo(assistantMessage("This is the assistant message"));
  }

  @Test
  void usesAllMessagesWhenMessagesWithinContextWindow() {
    final var runtimeMemory =
        setupRuntimeMemorySizeTest(
            new MemoryConfiguration(new InProcessMemoryStorageConfiguration(), 35));

    assertThat(runtimeMemory.allMessages()).hasSize(31);
    assertThat(runtimeMemory.filteredMessages()).hasSize(31);
    assertThat(runtimeMemory.filteredMessages())
        .containsExactlyElementsOf(runtimeMemory.allMessages());

    assertThat(runtimeMemory.filteredMessages().getFirst())
        .isEqualTo(userMessage("User message 0"));
    assertThat(runtimeMemory.filteredMessages().getLast())
        .isEqualTo(assistantMessage("This is the assistant message"));
  }

  @Test
  void throwsExceptionWhenNoUserMessageContent() {
    mockSystemPrompt(SYSTEM_PROMPT_CONFIGURATION);

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new AgentContextInitializationResult(INITIAL_AGENT_CONTEXT, List.of()));

    assertThatThrownBy(() -> requestHandler.handleRequest(agentExecutionContext))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("NO_USER_MESSAGE_CONTENT");
              assertThat(e.getMessage())
                  .isEqualTo(
                      "Agent cannot proceed as no user message content (user message, tool call results) is left to add.");
            });
  }

  private RuntimeMemory setupRuntimeMemorySizeTest(MemoryConfiguration memoryConfiguration) {
    mockUserPrompt(new UserPromptConfiguration("User message 30", Map.of(), List.of()), List.of());

    when(agentExecutionContext.memory()).thenReturn(memoryConfiguration);

    final List<Message> previousMessages =
        IntStream.range(0, 29)
            .mapToObj(i -> userMessage("User message " + i))
            .collect(Collectors.toUnmodifiableList());

    final var initialAgentContext =
        INITIAL_AGENT_CONTEXT.withConversation(
            InProcessConversationContext.builder("in-process").messages(previousMessages).build());

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new AgentContextInitializationResult(initialAgentContext, List.of()));

    final var assistantMessageText = "This is the assistant message";
    final var assistantMessage = assistantMessage(assistantMessageText);
    mockFrameworkExecution(assistantMessage);

    when(gatewayToolHandlers.transformToolCalls(any(AgentContext.class), anyList()))
        .thenAnswer(i -> i.getArgument(1));

    requestHandler.handleRequest(agentExecutionContext);

    return runtimeMemoryCaptor.getValue();
  }

  static Stream<MemoryConfiguration> memoryConfigurationsWithoutMaxMessages() {
    return Stream.of(
        null, new MemoryConfiguration(new InProcessMemoryStorageConfiguration(), null));
  }

  private void mockSystemPrompt(SystemPromptConfiguration systemPromptConfiguration) {
    when(agentExecutionContext.systemPrompt()).thenReturn(systemPromptConfiguration);
    doAnswer(
            i -> {
              final var runtimeMemory = i.getArgument(2, RuntimeMemory.class);
              runtimeMemory.addMessage(systemMessage(systemPromptConfiguration.prompt()));
              return null;
            })
        .when(messagesHandler)
        .addSystemMessage(
            eq(agentExecutionContext),
            any(AgentContext.class),
            any(RuntimeMemory.class),
            eq(systemPromptConfiguration));
  }

  private void mockUserPrompt(
      UserPromptConfiguration userPromptConfiguration, List<ToolCallResult> toolCallResults) {
    when(agentExecutionContext.userPrompt()).thenReturn(userPromptConfiguration);
    doAnswer(
            i -> {
              final var userMessage = userMessage(userPromptConfiguration.prompt());
              final var runtimeMemory = i.getArgument(2, RuntimeMemory.class);
              runtimeMemory.addMessage(userMessage);
              return List.of(userMessage);
            })
        .when(messagesHandler)
        .addUserMessages(
            eq(agentExecutionContext),
            any(AgentContext.class),
            any(RuntimeMemory.class),
            eq(userPromptConfiguration),
            eq(toolCallResults));
  }

  private void mockFrameworkExecution(AssistantMessage assistantMessage) {
    when(framework.executeChatRequest(
            eq(agentExecutionContext), any(AgentContext.class), runtimeMemoryCaptor.capture()))
        .thenAnswer(
            i -> {
              final var agentContext = i.getArgument(1, AgentContext.class);
              return new TestFrameworkChatResponse(
                  agentContext.withMetrics(
                      agentContext
                          .metrics()
                          .incrementModelCalls(1)
                          .incrementTokenUsage(new TokenUsage(10, 20))),
                  assistantMessage,
                  Map.of("message", assistantMessage.content()));
            });
  }

  private record TestFrameworkChatResponse(
      AgentContext agentContext,
      AssistantMessage assistantMessage,
      Map<String, Object> rawChatResponse)
      implements AiFrameworkChatResponse<Map<String, Object>> {}
}
