/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.connector.agenticai.aiagent.AiAgentJobWorker;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.runtime.core.ConnectorHelper;
import io.camunda.connector.runtime.core.Keywords;
import io.camunda.spring.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.spring.client.jobhandling.CommandWrapper;
import io.camunda.spring.client.metrics.MetricsRecorder;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

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

    final var variables = new LinkedHashMap<String, Object>();
    if (completionConditionFulfilled) {
      variables.putAll(createCompletionVariables(executionContext, agentResponse));
    } else {
      variables.put(AiAgentJobWorker.AGENT_CONTEXT_VARIABLE, agentResponse.context());
    }

    final var completeCommand =
        prepareCompleteCommand(executionContext)
            .variables(variables)
            .withResult(
                result -> {
                  var adHocSubProcess =
                      result
                          .forAdHocSubProcess()
                          .completionConditionFulfilled(completionConditionFulfilled)
                          .cancelRemainingInstances(cancelRemainingInstances);

                  for (ToolCallProcessVariable toolCall : agentResponse.toolCalls()) {
                    // TODO JW check if we want to expose variables without "toolCall.*" prefix as
                    // we're in direct control of variables
                    adHocSubProcess =
                        adHocSubProcess
                            .activateElement(toolCall.metadata().id())
                            .variables(
                                Map.of(
                                    "_meta", toolCall.metadata(),
                                    "toolCall", toolCall.arguments()));
                  }

                  return adHocSubProcess;
                });

    executeCommandAsync(
        executionContext,
        completeCommand,
        exceptionHandlingStrategy(executionContext, agentResponse, conversationStore));
  }

  private Map<String, Object> createCompletionVariables(
      JobWorkerAgentExecutionContext executionContext, AgentResponse agentResponse) {
    final var resultExpression =
        executionContext.job().getCustomHeaders().get(Keywords.RESULT_EXPRESSION_KEYWORD);

    // no result expression -> return whole agent response
    if (StringUtils.isBlank(resultExpression)) {
      return Map.of(AiAgentJobWorker.AGENT_RESPONSE_VARIABLE, agentResponse);
    }

    // evaluate result expression and set result as agent response variable
    final var outputVariables =
        ConnectorHelper.createOutputVariables(
            new JobWorkerAgentResponse(
                agentResponse.context(),
                agentResponse.responseMessage(),
                agentResponse.responseText(),
                agentResponse.responseJson()),
            null,
            resultExpression);

    return Map.of(AiAgentJobWorker.AGENT_RESPONSE_VARIABLE, outputVariables);
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

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private record JobWorkerAgentResponse(
      AgentContext context,
      AssistantMessage responseMessage,
      String responseText,
      Object responseJson) {}
}
