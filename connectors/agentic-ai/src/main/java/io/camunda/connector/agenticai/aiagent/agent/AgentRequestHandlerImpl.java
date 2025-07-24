/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_NO_USER_MESSAGE_CONTENT;

import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentContextInitializationResult;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentResponseInitializationResult;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.runtime.MessageWindowRuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.OutboundConnectorAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.spring.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.spring.client.jobhandling.CommandWrapper;
import io.camunda.spring.client.metrics.MetricsRecorder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AgentRequestHandlerImpl implements AgentRequestHandler {

  private static final int DEFAULT_CONTEXT_WINDOW_SIZE = 20;

  private final AgentInitializer agentInitializer;
  private final ConversationStoreRegistry conversationStoreRegistry;
  private final AgentLimitsValidator limitsValidator;
  private final AgentMessagesHandler messagesHandler;
  private final GatewayToolHandlerRegistry gatewayToolHandlers;
  private final AiFrameworkAdapter<?> framework;
  private final AgentResponseHandler responseHandler;
  private final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy;
  private final MetricsRecorder metricsRecorder;

  public AgentRequestHandlerImpl(
      AgentInitializer agentInitializer,
      ConversationStoreRegistry conversationStoreRegistry,
      AgentLimitsValidator limitsValidator,
      AgentMessagesHandler messagesHandler,
      GatewayToolHandlerRegistry gatewayToolHandlers,
      AiFrameworkAdapter<?> framework,
      AgentResponseHandler responseHandler,
      CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      MetricsRecorder metricsRecorder) {
    this.agentInitializer = agentInitializer;
    this.conversationStoreRegistry = conversationStoreRegistry;
    this.limitsValidator = limitsValidator;
    this.messagesHandler = messagesHandler;
    this.gatewayToolHandlers = gatewayToolHandlers;
    this.framework = framework;
    this.responseHandler = responseHandler;
    this.commandExceptionHandlingStrategy = commandExceptionHandlingStrategy;
    this.metricsRecorder = metricsRecorder;
  }

  @Override
  public AgentResponse handleRequest(AgentExecutionContext executionContext) {
    final var agentInitializationResult = agentInitializer.initializeAgent(executionContext);
    return switch (agentInitializationResult) {
      // directly return agent response if needed (e.g. tool discovery tool calls before calling the
      // LLM)
      case AgentResponseInitializationResult(AgentResponse agentResponse) -> agentResponse;

      case AgentContextInitializationResult(
              AgentContext agentContext,
              List<ToolCallResult> toolCallResults) ->
          handleRequest(executionContext, agentContext, toolCallResults);
    };
  }

  private AgentResponse handleRequest(
      final AgentExecutionContext executionContext,
      final AgentContext agentContext,
      final List<ToolCallResult> toolCallResults) {
    final var conversationStore =
        conversationStoreRegistry.getConversationStore(executionContext, agentContext);
    final var agentResponse =
        conversationStore.executeInSession(
            executionContext,
            agentContext,
            session -> handleRequest(executionContext, agentContext, toolCallResults, session));

    if (executionContext instanceof JobWorkerAgentExecutionContext jwCtx) {
      var completeCommand = jwCtx.jobClient().newCompleteCommand(jwCtx.job());

      if (agentResponse == null) {
        // no-op, wait for next job to add user messages
        new CommandWrapper(
                completeCommand, jwCtx.job(), commandExceptionHandlingStrategy, metricsRecorder, 3)
            .executeAsync();
      } else {
        boolean isCompletionConditionFulfilled = agentResponse.toolCalls().isEmpty();
        boolean isCancelRemainingInstances = false; // TODO JW check events for interruptions

        final var variables = new LinkedHashMap<String, Object>();
        variables.put("agentContext", agentResponse.context());
        Optional.ofNullable(agentResponse.responseText())
            .ifPresent(responseText -> variables.put("responseText", responseText));
        Optional.ofNullable(agentResponse.responseJson())
            .ifPresent(responseJson -> variables.put("responseJson", responseJson));
        Optional.ofNullable(agentResponse.responseMessage())
            .ifPresent(responseMessage -> variables.put("responseMessage", responseMessage));

        completeCommand = completeCommand.variables(variables);
        completeCommand.withResult(
            result -> {
              var ahsp = result.forAdHocSubProcess();
              // TODO JW set completion condition
              // TODO JW set cancel remaining instances

              for (ToolCallProcessVariable toolCall : agentResponse.toolCalls()) {
                // TODO JW check if we want to expose variables without "toolCall.*" prefix as we're
                // in direct control of variables
                ahsp =
                    ahsp.activateElement(toolCall.metadata().id())
                        .variables(
                            Map.of(
                                "_meta", toolCall.metadata(),
                                "toolCall", toolCall.arguments()));
              }

              return ahsp;
            });

        new CommandWrapper(
                completeCommand,
                jwCtx.job(),
                ((command, throwable) -> {
                  conversationStore.compensateFailedJobCompletion(
                      executionContext, agentResponse.context(), throwable);
                  commandExceptionHandlingStrategy.handleCommandError(command, throwable);
                }),
                metricsRecorder,
                3)
            .executeAsync();
      }
    }

    return agentResponse;
  }

  private AgentResponse handleRequest(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      List<ToolCallResult> toolCallResults,
      ConversationSession session) {
    // set up memory and load from context if available
    final var runtimeMemory =
        new MessageWindowRuntimeMemory(
            Optional.ofNullable(executionContext.memory())
                .map(MemoryConfiguration::contextWindowSize)
                .orElse(DEFAULT_CONTEXT_WINDOW_SIZE));

    session.loadIntoRuntimeMemory(agentContext, runtimeMemory);

    // validate configured limits
    limitsValidator.validateConfiguredLimits(executionContext, agentContext);

    // update memory with system message
    messagesHandler.addSystemMessage(
        executionContext, agentContext, runtimeMemory, executionContext.systemPrompt());

    // update memory with user messages/tool call responses
    final var userMessages =
        messagesHandler.addUserMessages(
            executionContext,
            agentContext,
            runtimeMemory,
            executionContext.userPrompt(),
            toolCallResults);
    if (userMessages.isEmpty()) {
      // TODO JW abstract this into execution context specific handlers
      if (executionContext instanceof OutboundConnectorAgentExecutionContext) {
        throw new ConnectorException(
            ERROR_CODE_NO_USER_MESSAGE_CONTENT,
            "Agent cannot proceed as no user message content (user message, tool call results) is left to add.");
      } else if (executionContext instanceof JobWorkerAgentExecutionContext) {
        return null;
      } else {
        throw new IllegalStateException(
            "Unsupported AgentExecutionContext type: %s".formatted(executionContext.getClass()));
      }
    }

    // call framework with memory
    final var frameworkChatResponse =
        framework.executeChatRequest(executionContext, agentContext, runtimeMemory);
    agentContext = frameworkChatResponse.agentContext();

    final var assistantMessage = frameworkChatResponse.assistantMessage();
    runtimeMemory.addMessage(assistantMessage);

    // apply potential gateway tool call transformations & map tool call to process
    // variable format
    final var toolCalls =
        gatewayToolHandlers.transformToolCalls(agentContext, assistantMessage.toolCalls());
    final var processVariableToolCalls =
        toolCalls.stream().map(ToolCallProcessVariable::from).toList();

    // store memory reference to context
    agentContext = session.storeFromRuntimeMemory(agentContext, runtimeMemory);

    return responseHandler.createResponse(
        executionContext, agentContext, assistantMessage, processVariableToolCalls);
  }
}
