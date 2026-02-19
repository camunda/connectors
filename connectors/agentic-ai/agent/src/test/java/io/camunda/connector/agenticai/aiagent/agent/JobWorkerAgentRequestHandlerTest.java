/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALLS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALL_RESULTS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_DEFINITIONS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.toolCallResultMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentContextInitializationResult;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentResponseInitializationResult;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkChatResponse;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentCompletion;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.InProcessMemoryStorageConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
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
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobWorkerAgentRequestHandlerTest {

  private static final AgentContext INITIAL_AGENT_CONTEXT =
      AgentContext.builder().state(AgentState.READY).toolDefinitions(TOOL_DEFINITIONS).build();

  private static final PromptConfiguration.SystemPromptConfiguration SYSTEM_PROMPT_CONFIGURATION =
      new PromptConfiguration.SystemPromptConfiguration("You are a helpful assistant. Be nice.");
  private static final PromptConfiguration.UserPromptConfiguration
      USER_PROMPT_CONFIGURATION_WITHOUT_TOOLS =
          new PromptConfiguration.UserPromptConfiguration("Write a haiku about the sea", List.of());
  private static final PromptConfiguration.UserPromptConfiguration
      USER_PROMPT_CONFIGURATION_WITH_TOOLS =
          new PromptConfiguration.UserPromptConfiguration(
              "What is the weather in Munich?", List.of());

  @Mock private AgentInitializer agentInitializer;
  @Mock private ConversationStoreRegistry conversationStoreRegistry;
  @Mock private AgentLimitsValidator limitsValidator;
  @Mock private AgentMessagesHandler messagesHandler;
  @Mock private GatewayToolHandlerRegistry gatewayToolHandlers;
  @Mock private AiFrameworkAdapter<?> framework;
  @Mock private AgentResponseHandler responseHandler;

  private ConversationStore conversationStore;

  @Mock private ActivatedJob job;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private JobWorkerAgentExecutionContext agentExecutionContext;

  @Captor private ArgumentCaptor<RuntimeMemory> runtimeMemoryCaptor;

  @InjectMocks private JobWorkerAgentRequestHandler requestHandler;

  @BeforeEach
  void setUp() {
    lenient().when(job.getKey()).thenReturn(123456L);
    when(agentExecutionContext.job()).thenReturn(job);

    conversationStore = spy(new InProcessConversationStore());
    doReturn(conversationStore)
        .when(conversationStoreRegistry)
        .getConversationStore(eq(agentExecutionContext), any(AgentContext.class));
  }

  @Test
  void directlyReturnsAgentResponseWhenInitializationReturnsResponse() {
    Mockito.reset(conversationStoreRegistry);

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

    final var completion = requestHandler.handleRequest(agentExecutionContext);
    assertThat(completion.variables()).containsOnlyKeys("agentContext", "toolCallResults");
    assertThat(completion.completionConditionFulfilled()).isFalse();
    assertThat(completion.cancelRemainingInstances()).isFalse();
    assertThat(completion.agentResponse()).isNotNull().isEqualTo(agentResponse);

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

    final var completion = requestHandler.handleRequest(agentExecutionContext);
    assertThat(completion.variables()).containsOnlyKeys("agentContext", "agent");
    assertThat(completion.completionConditionFulfilled()).isTrue();
    assertThat(completion.cancelRemainingInstances()).isFalse();

    final var agentResponse = completion.agentResponse();
    assertThat(agentResponse.context()).isEqualTo(completion.variables().get("agentContext"));
    assertThat(agentResponse.context().state()).isEqualTo(AgentState.READY);
    assertThat(agentResponse.context().metrics())
        .isEqualTo(new AgentMetrics(1, new TokenUsage(10, 20)));
    assertThat(agentResponse.context().conversation())
        .isNotNull()
        .isInstanceOfSatisfying(
            InProcessConversationContext.class,
            c -> assertThat(c.messages()).containsExactlyElementsOf(expectedMessages));

    assertThat(agentResponse.responseMessage()).isEqualTo(assistantMessage);
    assertThat(agentResponse.responseText()).isEqualTo(assistantMessageText);
    assertThat(agentResponse.toolCalls()).isEmpty();

    assertThat(runtimeMemoryCaptor.getValue().allMessages())
        .containsExactlyElementsOf(expectedMessages);

    verify(limitsValidator).validateConfiguredLimits(agentExecutionContext, INITIAL_AGENT_CONTEXT);
    verify(agentExecutionContext, never()).setCancelRemainingInstances(anyBoolean());
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

    final var completion = requestHandler.handleRequest(agentExecutionContext);
    assertThat(completion.variables()).containsOnlyKeys("agentContext", "toolCallResults");
    assertThat(completion.completionConditionFulfilled()).isFalse();
    assertThat(completion.cancelRemainingInstances()).isFalse();

    final var agentResponse = completion.agentResponse();
    assertThat(agentResponse.context().state()).isEqualTo(AgentState.READY);
    assertThat(agentResponse.context().metrics())
        .isEqualTo(new AgentMetrics(1, new TokenUsage(10, 20)));
    assertThat(agentResponse.context().conversation())
        .isNotNull()
        .isInstanceOfSatisfying(
            InProcessConversationContext.class,
            c -> assertThat(c.messages()).containsExactlyElementsOf(expectedMessages));

    assertThat(agentResponse.responseMessage()).isEqualTo(assistantMessage);
    assertThat(agentResponse.responseText()).isNull();
    assertThat(agentResponse.toolCalls())
        .containsExactly(
            new ToolCallProcessVariable(
                new ToolCallProcessVariable.ToolCallMetadata("abcdef_transformed", "getWeather"),
                Map.of("location", "MUC")),
            new ToolCallProcessVariable(
                new ToolCallProcessVariable.ToolCallMetadata("fedcba_transformed", "getDateTime"),
                Map.of()));

    assertThat(runtimeMemoryCaptor.getValue().allMessages())
        .containsExactlyElementsOf(expectedMessages);

    verify(limitsValidator).validateConfiguredLimits(agentExecutionContext, INITIAL_AGENT_CONTEXT);
    verify(agentExecutionContext, never()).setCancelRemainingInstances(anyBoolean());
  }

  @Test
  void orchestratesRequestExecutionWithInterruptedToolCall() {
    List<ToolCallResult> toolCallResults = List.of(TOOL_CALL_RESULTS.get(0));
    mockSystemPrompt(SYSTEM_PROMPT_CONFIGURATION);
    mockInterruptedToolCall(toolCallResults);

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new AgentContextInitializationResult(INITIAL_AGENT_CONTEXT, List.of()));

    when(agentExecutionContext.cancelRemainingInstances()).thenReturn(true);

    final var assistantMessage = AssistantMessage.builder().build();

    final var expectedMessages =
        List.of(
            systemMessage(SYSTEM_PROMPT_CONFIGURATION.prompt()),
            toolCallResultMessage(
                toolCallResults.stream()
                    .map(tc -> ToolCallResult.forCancelledToolCall(tc.id(), tc.name()))
                    .toList()),
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

    final var completion = requestHandler.handleRequest(agentExecutionContext);
    assertThat(completion.variables()).containsOnlyKeys("agentContext", "agent");
    assertThat(completion.completionConditionFulfilled()).isTrue();
    assertThat(completion.cancelRemainingInstances()).isTrue();

    final var agentResponse = completion.agentResponse();
    assertThat(agentResponse.context()).isEqualTo(completion.variables().get("agentContext"));
    assertThat(agentResponse.context().state()).isEqualTo(AgentState.READY);
    assertThat(agentResponse.context().metrics())
        .isEqualTo(new AgentMetrics(1, new TokenUsage(10, 20)));
    assertThat(agentResponse.context().conversation())
        .isNotNull()
        .isInstanceOfSatisfying(
            InProcessConversationContext.class,
            c -> assertThat(c.messages()).containsExactlyElementsOf(expectedMessages));

    assertThat(agentResponse.responseMessage()).isEqualTo(assistantMessage);
    assertThat(agentResponse.responseText()).isNull();
    assertThat(agentResponse.toolCalls()).isEmpty();

    assertThat(runtimeMemoryCaptor.getValue().allMessages())
        .containsExactlyElementsOf(expectedMessages);

    verify(limitsValidator).validateConfiguredLimits(agentExecutionContext, INITIAL_AGENT_CONTEXT);
    verify(agentExecutionContext).setCancelRemainingInstances(true);
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
  void silentlyCompletesJobWhenNoUserMessageContent() {
    mockSystemPrompt(SYSTEM_PROMPT_CONFIGURATION);

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new AgentContextInitializationResult(INITIAL_AGENT_CONTEXT, List.of()));

    JobWorkerAgentCompletion completion = requestHandler.handleRequest(agentExecutionContext);
    assertThat(completion.variables()).isEmpty();
    assertThat(completion.completionConditionFulfilled()).isFalse();
    assertThat(completion.cancelRemainingInstances()).isFalse();

    verifyNoInteractions(framework);
  }

  @Test
  void completionErrorHandlerCompensatesStorageOnCompletionError() {
    mockSystemPrompt(SYSTEM_PROMPT_CONFIGURATION);
    mockUserPrompt(USER_PROMPT_CONFIGURATION_WITHOUT_TOOLS, List.of());

    when(agentInitializer.initializeAgent(agentExecutionContext))
        .thenReturn(new AgentContextInitializationResult(INITIAL_AGENT_CONTEXT, List.of()));

    final var assistantMessageText =
        "Endless waves whisper | moonlight dances on the tide | secrets drift below.";
    final var assistantMessage = assistantMessage(assistantMessageText);

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

    final var completion = requestHandler.handleRequest(agentExecutionContext);

    final var exception = new RuntimeException("This is a test");
    completion.onCompletionError(exception);

    verify(conversationStore)
        .compensateFailedJobCompletion(
            agentExecutionContext, completion.agentResponse().context(), exception);
  }

  private RuntimeMemory setupRuntimeMemorySizeTest(MemoryConfiguration memoryConfiguration) {
    mockUserPrompt(new UserPromptConfiguration("User message 30", List.of()), List.of());

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

  private void mockInterruptedToolCall(List<ToolCallResult> toolCallResults) {
    doAnswer(
            i -> {
              final var toolCallMessage =
                  toolCallResultMessage(
                      toolCallResults.stream()
                          .map(tc -> ToolCallResult.forCancelledToolCall(tc.id(), tc.name()))
                          .toList());
              final var runtimeMemory = i.getArgument(2, RuntimeMemory.class);
              runtimeMemory.addMessage(toolCallMessage);
              return List.of(toolCallMessage);
            })
        .when(messagesHandler)
        .addUserMessages(
            eq(agentExecutionContext),
            any(AgentContext.class),
            any(RuntimeMemory.class),
            any(UserPromptConfiguration.class),
            anyList());
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
