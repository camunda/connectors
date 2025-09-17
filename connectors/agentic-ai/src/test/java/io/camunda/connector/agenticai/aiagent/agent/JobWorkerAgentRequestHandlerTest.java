/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALLS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALL_RESULTS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_DEFINITIONS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.toolCallResultMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static io.camunda.connector.agenticai.util.WireMockUtils.assertJobCompletionRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.client.protocol.rest.JobResultActivateElement;
import io.camunda.client.protocol.rest.JobResultAdHocSubProcess;
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
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentJobContext;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
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
import org.mockito.junit.jupiter.MockitoExtension;

@WireMockTest
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

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private CamundaClient camundaClient;

  @Mock private AgentInitializer agentInitializer;
  @Mock private ConversationStoreRegistry conversationStoreRegistry;
  @Mock private AgentLimitsValidator limitsValidator;
  @Mock private AgentMessagesHandler messagesHandler;
  @Mock private GatewayToolHandlerRegistry gatewayToolHandlers;
  @Mock private AiFrameworkAdapter<?> framework;
  @Mock private AgentResponseHandler responseHandler;
  @Mock CommandExceptionHandlingStrategy defaultExceptionHandlingStrategy;
  @Mock MetricsRecorder metricsRecorder;

  private ConversationStore conversationStore;

  @Mock private ActivatedJob job;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private JobWorkerAgentJobContext agentJobContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private JobWorkerAgentExecutionContext agentExecutionContext;

  @Captor private ArgumentCaptor<RuntimeMemory> runtimeMemoryCaptor;

  @InjectMocks private JobWorkerAgentRequestHandler requestHandler;

  @BeforeEach
  void setUp(final WireMockRuntimeInfo wireMockRuntimeInfo) throws URISyntaxException {
    camundaClient =
        CamundaClient.newClientBuilder()
            .preferRestOverGrpc(true)
            .restAddress(new URI(wireMockRuntimeInfo.getHttpBaseUrl()))
            .build();

    when(job.getKey()).thenReturn(123456L);
    when(agentExecutionContext.job()).thenReturn(job);
    when(agentExecutionContext.jobClient()).thenReturn(camundaClient);

    conversationStore = spy(new InProcessConversationStore());
    doReturn(conversationStore)
        .when(conversationStoreRegistry)
        .getConversationStore(eq(agentExecutionContext), any(AgentContext.class));

    stubFor(post(urlPathEqualTo("/v2/jobs/123456/completion")).willReturn(jsonResponse("{}", 200)));
  }

  @AfterEach
  void afterEach() {
    if (camundaClient != null) {
      camundaClient.close();
    }
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

    assertJobCompletionRequest(
        request -> {
          assertThat(request.getVariables()).containsOnlyKeys("agentContext", "toolCallResults");
          assertThat(request.getResult())
              .isNotNull()
              .isInstanceOfSatisfying(
                  JobResultAdHocSubProcess.class,
                  adHoc -> {
                    assertThat(adHoc.getIsCompletionConditionFulfilled()).isFalse();
                    assertThat(adHoc.getIsCancelRemainingInstances()).isFalse();
                    assertThat(adHoc.getActivateElements())
                        .extracting(
                            JobResultActivateElement::getElementId,
                            JobResultActivateElement::getVariables)
                        .containsExactly(
                            tuple(
                                agentResponse.toolCalls().getFirst().metadata().name(),
                                Map.ofEntries(
                                    Map.entry(
                                        "toolCall", asMap(agentResponse.toolCalls().getFirst())),
                                    Map.entry("toolCallResult", ""))));
                  });
        });
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
    verify(agentExecutionContext, never()).setCancelRemainingInstances(anyBoolean());

    assertJobCompletionRequest(
        request -> {
          assertThat(request.getVariables()).containsOnlyKeys("agent");
          assertThat(request.getResult())
              .isNotNull()
              .isInstanceOfSatisfying(
                  JobResultAdHocSubProcess.class,
                  adHoc -> {
                    assertThat(adHoc.getIsCompletionConditionFulfilled()).isTrue();
                    assertThat(adHoc.getIsCancelRemainingInstances()).isFalse();
                    assertThat(adHoc.getActivateElements()).isEmpty();
                  });
        });
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
    verify(agentExecutionContext, never()).setCancelRemainingInstances(anyBoolean());

    assertJobCompletionRequest(
        request -> {
          assertThat(request.getVariables()).containsOnlyKeys("agentContext", "toolCallResults");
          assertThat(request.getResult())
              .isNotNull()
              .isInstanceOfSatisfying(
                  JobResultAdHocSubProcess.class,
                  adHoc -> {
                    assertThat(adHoc.getIsCompletionConditionFulfilled()).isFalse();
                    assertThat(adHoc.getIsCancelRemainingInstances()).isFalse();
                    assertThat(adHoc.getActivateElements())
                        .extracting(
                            JobResultActivateElement::getElementId,
                            JobResultActivateElement::getVariables)
                        .containsExactly(
                            tuple(
                                "getWeather",
                                Map.ofEntries(
                                    Map.entry("toolCall", asMap(response.toolCalls().getFirst())),
                                    Map.entry("toolCallResult", ""))),
                            tuple(
                                "getDateTime",
                                Map.ofEntries(
                                    Map.entry("toolCall", asMap(response.toolCalls().get(1))),
                                    Map.entry("toolCallResult", ""))));
                  });
        });
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
    assertThat(response.toolCalls()).isEmpty();

    verify(limitsValidator).validateConfiguredLimits(agentExecutionContext, INITIAL_AGENT_CONTEXT);
    verify(agentExecutionContext).setCancelRemainingInstances(true);

    assertJobCompletionRequest(
        request -> {
          assertThat(request.getVariables()).containsOnlyKeys("agent");
          assertThat(request.getResult())
              .isNotNull()
              .isInstanceOfSatisfying(
                  JobResultAdHocSubProcess.class,
                  adHoc -> {
                    assertThat(adHoc.getIsCompletionConditionFulfilled()).isTrue();
                    assertThat(adHoc.getIsCancelRemainingInstances()).isTrue();
                    assertThat(adHoc.getActivateElements()).isEmpty();
                  });
        });
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

    requestHandler.handleRequest(agentExecutionContext);

    verifyNoInteractions(framework);

    assertJobCompletionRequest(
        request -> {
          assertThat(request.getVariables()).isNull();
          assertThat(request.getResult()).isNull();
        });
  }

  @Test
  void triesToCompensateStorageOnCompletionError() {
    stubFor(
        post(urlPathEqualTo("/v2/jobs/123456/completion"))
            .willReturn(
                jsonResponse(
                    """
                    {
                      "type": "about:blank",
                      "title": "NOT_FOUND",
                      "status": 404,
                      "detail": "Job was not found",
                      "instance": "/v2/jobs/123456/completion"
                    }
                    """,
                    404)));

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

    requestHandler.handleRequest(agentExecutionContext);

    // make sure the verification is actually executed after the completion request was made
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                verify(conversationStore)
                    .compensateFailedJobCompletion(
                        eq(agentExecutionContext), any(AgentContext.class), any(Throwable.class)));
  }

  private Map<String, Object> asMap(final ToolCallProcessVariable toolCallProcessVariable) {
    return OBJECT_MAPPER.convertValue(toolCallProcessVariable, new TypeReference<>() {});
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
