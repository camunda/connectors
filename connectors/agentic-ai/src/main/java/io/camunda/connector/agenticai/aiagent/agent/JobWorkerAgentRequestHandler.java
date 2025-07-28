/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.spring.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.spring.client.jobhandling.CommandWrapper;
import io.camunda.spring.client.metrics.MetricsRecorder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class JobWorkerAgentRequestHandler
    extends DefaultAgentRequestHandler<JobWorkerAgentExecutionContext> {

  private static final int MAX_ZEEBE_COMMAND_RETRIES = 3;

  private final CommandExceptionHandlingStrategy defaultExceptionHandlingStrategy;
  private final MetricsRecorder metricsRecorder;

  public JobWorkerAgentRequestHandler(
      AgentInitializer agentInitializer,
      ConversationStoreRegistry conversationStoreRegistry,
      AgentLimitsValidator limitsValidator,
      AgentMessagesHandler messagesHandler,
      GatewayToolHandlerRegistry gatewayToolHandlers,
      AiFrameworkAdapter<?> framework,
      AgentResponseHandler responseHandler,
      CommandExceptionHandlingStrategy defaultExceptionHandlingStrategy,
      MetricsRecorder metricsRecorder) {
    super(
        agentInitializer,
        conversationStoreRegistry,
        limitsValidator,
        messagesHandler,
        gatewayToolHandlers,
        framework,
        responseHandler);

    this.defaultExceptionHandlingStrategy = defaultExceptionHandlingStrategy;
    this.metricsRecorder = metricsRecorder;
  }

  @Override
  protected void handleMissingUserMessages(
      JobWorkerAgentExecutionContext executionContext, AgentContext agentContext) {
    // no-op
  }

  @Override
  public AgentResponse completeJob(
      JobWorkerAgentExecutionContext executionContext,
      AgentResponse agentResponse,
      ConversationStore conversationStore) {
    if (agentResponse == null) {
      completeAsyncWithoutResponse(executionContext);
    } else {
      completeAsyncWithResponse(executionContext, agentResponse, conversationStore);
    }

    return agentResponse;
  }

  private void completeAsyncWithoutResponse(JobWorkerAgentExecutionContext executionContext) {
    // no-op (do not activate elements, do not complete agent process) -> wait for next job to add
    // user messages
    executeCommandAsync(
        executionContext,
        prepareCompleteCommand(executionContext),
        defaultExceptionHandlingStrategy);
  }

  private void completeAsyncWithResponse(
      JobWorkerAgentExecutionContext executionContext,
      AgentResponse agentResponse,
      ConversationStore conversationStore) {
    boolean completionConditionFulfilled = agentResponse.toolCalls().isEmpty();
    boolean cancelRemainingInstances = false; // TODO JW check events for interruptions

    // TODO JW check variable names
    final var variables = new LinkedHashMap<String, Object>();
    variables.put("agentContext", agentResponse.context());
    Optional.ofNullable(agentResponse.responseText())
        .ifPresent(responseText -> variables.put("responseText", responseText));
    Optional.ofNullable(agentResponse.responseJson())
        .ifPresent(responseJson -> variables.put("responseJson", responseJson));
    Optional.ofNullable(agentResponse.responseMessage())
        .ifPresent(responseMessage -> variables.put("responseMessage", responseMessage));

    var completeCommand = prepareCompleteCommand(executionContext);
    completeCommand = completeCommand.variables(variables);
    completeCommand.withResult(
        result -> {
          var ahsp =
              result
                  .forAdHocSubProcess()
                  .completionConditionFulfilled(completionConditionFulfilled)
                  .cancelRemainingInstances(cancelRemainingInstances);

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

    executeCommandAsync(
        executionContext,
        completeCommand,
        exceptionHandlingStrategy(executionContext, agentResponse, conversationStore));
  }

  private void executeCommandAsync(
      JobWorkerAgentExecutionContext executionContext,
      FinalCommandStep<CompleteJobResponse> command,
      CommandExceptionHandlingStrategy exceptionHandlingStrategy) {
    new CommandWrapper(
            command,
            executionContext.job(),
            exceptionHandlingStrategy,
            metricsRecorder,
            MAX_ZEEBE_COMMAND_RETRIES)
        .executeAsync();
  }

  private CommandExceptionHandlingStrategy exceptionHandlingStrategy(
      JobWorkerAgentExecutionContext executionContext,
      AgentResponse agentResponse,
      ConversationStore conversationStore) {
    // conversationStore may be null if agent did not reach a state where it
    // interacted with the store (initialization only)
    if (conversationStore == null || agentResponse == null) {
      return defaultExceptionHandlingStrategy;
    } else {
      return (command, throwable) -> {
        // allow storage to compensate for failed job completion
        conversationStore.compensateFailedJobCompletion(
            executionContext, agentResponse.context(), throwable);
        defaultExceptionHandlingStrategy.handleCommandError(command, throwable);
      };
    }
  }

  private CompleteJobCommandStep1 prepareCompleteCommand(
      JobWorkerAgentExecutionContext executionContext) {
    return executionContext.jobClient().newCompleteCommand(executionContext.job());
  }
}
