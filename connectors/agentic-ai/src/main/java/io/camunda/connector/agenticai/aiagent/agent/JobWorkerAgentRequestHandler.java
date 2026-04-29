/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.AiAgentJobWorker;
import io.camunda.connector.agenticai.aiagent.AiAgentSubProcessConnectorResponse;
import io.camunda.connector.agenticai.aiagent.AiAgentSubProcessConnectorResponse.ToolCallElementActivation;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentResponse;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.outbound.ConnectorResponse.AdHocSubProcessConnectorResponse.ElementActivation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

public class JobWorkerAgentRequestHandler
    extends BaseAgentRequestHandler<
        JobWorkerAgentExecutionContext, AiAgentSubProcessConnectorResponse> {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobWorkerAgentRequestHandler.class);

  public JobWorkerAgentRequestHandler(
      AgentInitializer agentInitializer,
      ConversationStoreRegistry conversationStoreRegistry,
      AgentLimitsValidator limitsValidator,
      AgentMessagesHandler messagesHandler,
      GatewayToolHandlerRegistry gatewayToolHandlers,
      AiFrameworkAdapter<?> framework,
      AgentResponseHandler responseHandler) {
    super(
        agentInitializer,
        conversationStoreRegistry,
        limitsValidator,
        messagesHandler,
        gatewayToolHandlers,
        framework,
        responseHandler);
  }

  @Override
  protected boolean modelCallPrerequisitesFulfilled(
      JobWorkerAgentExecutionContext executionContext,
      AgentContext agentContext,
      List<Message> addedUserMessages) {
    return !CollectionUtils.isEmpty(addedUserMessages);
  }

  @Override
  protected void handleAddedUserMessages(
      JobWorkerAgentExecutionContext executionContext,
      AgentContext agentContext,
      List<Message> addedUserMessages) {
    final boolean hasInterruptedToolCalls =
        addedUserMessages.stream()
            .filter(ToolCallResultMessage.class::isInstance)
            .map(ToolCallResultMessage.class::cast)
            .flatMap(msg -> msg.results().stream())
            .anyMatch(
                result ->
                    Boolean.TRUE.equals(
                        result
                            .properties()
                            .getOrDefault(ToolCallResult.PROPERTY_INTERRUPTED, false)));

    // cancel remaining instances if any tool call was interrupted
    if (hasInterruptedToolCalls) {
      executionContext.setCancelRemainingInstances(true);
    }
  }

  @Override
  public AiAgentSubProcessConnectorResponse buildConnectorResponse(
      JobWorkerAgentExecutionContext executionContext,
      AgentResponse agentResponse,
      AgentJobCompletionListener completionListener) {
    if (agentResponse == null) {
      LOGGER.debug(
          "No agent response provided, completing job {} without response",
          executionContext.jobContext().getJobKey());

      // no-op (do not activate elements, do not complete agent process) -> wait for next job to
      // proceed (e.g. by adding user messages or to complete tool call results)
      return AiAgentSubProcessConnectorResponse.builder()
          .completionConditionFulfilled(false)
          .cancelRemainingInstances(false)
          .build();
    } else {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(
            "Agent response provided, completing job {} with response and tool calls: {}",
            executionContext.jobContext().getJobKey(),
            agentResponse.toolCalls().stream().map(tc -> tc.metadata().name()).toList());
      }

      return buildResponse(executionContext, agentResponse, completionListener);
    }
  }

  private AiAgentSubProcessConnectorResponse buildResponse(
      JobWorkerAgentExecutionContext executionContext,
      AgentResponse agentResponse,
      AgentJobCompletionListener completionListener) {
    boolean completionConditionFulfilled = agentResponse.toolCalls().isEmpty();
    boolean cancelRemainingInstances = executionContext.cancelRemainingInstances();

    LOGGER.debug(
        "completionConditionFulfilled: {}, cancelRemainingInstances: {}",
        completionConditionFulfilled,
        cancelRemainingInstances);

    final var variables = new LinkedHashMap<String, Object>();
    variables.put(AiAgentJobWorker.AGENT_CONTEXT_VARIABLE, agentResponse.context());

    if (completionConditionFulfilled) {
      LOGGER.debug("Completion condition fulfilled, creating agent response variable");
      variables.put(
          AiAgentJobWorker.AGENT_RESPONSE_VARIABLE,
          createAgentResponseVariable(executionContext, agentResponse));
    } else {
      LOGGER.debug(
          "Completion condition not fulfilled, clearing tool call results for next tool call iteration");
      variables.put(AiAgentJobWorker.TOOL_CALL_RESULTS_VARIABLE, List.of());
    }

    return AiAgentSubProcessConnectorResponse.builder()
        .responseValue(agentResponse)
        .variables(variables)
        .elementActivations(buildElementActivations(agentResponse))
        .completionConditionFulfilled(completionConditionFulfilled)
        .cancelRemainingInstances(cancelRemainingInstances)
        .completionListener(completionListener)
        .build();
  }

  private JobWorkerAgentResponse createAgentResponseVariable(
      JobWorkerAgentExecutionContext executionContext, AgentResponse agentResponse) {
    var builder =
        JobWorkerAgentResponse.builder()
            .responseText(agentResponse.responseText())
            .responseJson(agentResponse.responseJson())
            .responseMessage(agentResponse.responseMessage());

    if (executionContext.response() != null
        && Boolean.TRUE.equals(executionContext.response().includeAgentContext())) {
      LOGGER.debug("Including agent context in response variable");
      builder = builder.context(agentResponse.context());
    }

    return builder.build();
  }

  private List<ElementActivation> buildElementActivations(AgentResponse agentResponse) {
    return agentResponse.toolCalls().stream()
        .map(
            toolCall -> {
              if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Activating tool {}: {}", toolCall.metadata().name(), toolCall);
              } else {
                LOGGER.debug("Activating tool {}", toolCall.metadata().name());
              }

              return (ElementActivation)
                  new ToolCallElementActivation(
                      toolCall.metadata().name(),
                      Map.ofEntries(
                          Map.entry(AiAgentJobWorker.TOOL_CALL_VARIABLE, toolCall),
                          // Creating empty toolCallResult variable to avoid variable
                          // to bubble up in the upper scopes while merging variables on
                          // ad-hoc sub-process inner instance completion.
                          Map.entry(AiAgentJobWorker.TOOL_CALL_RESULT_VARIABLE, "")));
            })
        .toList();
  }
}
