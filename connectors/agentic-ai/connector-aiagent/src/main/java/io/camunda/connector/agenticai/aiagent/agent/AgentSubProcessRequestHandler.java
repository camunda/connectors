/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.AgentProcessVariables;
import io.camunda.connector.agenticai.aiagent.AgentSubProcessConnectorResponse;
import io.camunda.connector.agenticai.aiagent.AgentSubProcessConnectorResponse.ToolCallElementActivation;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentSubProcessExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentSubProcessResponse;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.api.outbound.ConnectorResponse.AdHocSubProcessConnectorResponse.ElementActivation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentSubProcessRequestHandler
    extends BaseAgentRequestHandler<
        AgentSubProcessExecutionContext, AgentSubProcessConnectorResponse> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentSubProcessRequestHandler.class);

  public AgentSubProcessRequestHandler(
      AgentInitializer agentInitializer,
      ConversationStoreRegistry conversationStoreRegistry,
      AgentConversationTurnInputComposer agentInputComposer,
      ChatModelRegistry chatModelRegistry,
      SystemPromptComposer systemPromptComposer,
      AgentResponseHandler responseHandler,
      AgentInstanceClient agentInstanceClient) {
    super(
        agentInitializer,
        conversationStoreRegistry,
        agentInputComposer,
        chatModelRegistry,
        systemPromptComposer,
        responseHandler,
        agentInstanceClient);
  }

  @Override
  protected AgentSubProcessConnectorResponse handleNoInput(
      AgentSubProcessExecutionContext executionContext) {
    LOGGER.warn(
        "No input to process; completing job {} without response.",
        executionContext.jobContext().getJobKey());
    return buildConnectorResponse(executionContext, null, null, null);
  }

  @Override
  public AgentSubProcessConnectorResponse buildConnectorResponse(
      AgentSubProcessExecutionContext executionContext,
      @Nullable AgentConversation conversation,
      @Nullable AgentResponse agentResponse,
      @Nullable AgentJobCompletionListener completionListener) {
    if (agentResponse == null) {
      LOGGER.debug(
          "No agent response provided, completing job {} without response",
          executionContext.jobContext().getJobKey());

      // no-op (do not activate elements, do not complete agent process) -> wait for next job to
      // proceed (e.g. by adding user messages or to complete tool call results)
      return AgentSubProcessConnectorResponse.builder()
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

      return buildResponse(executionContext, conversation, agentResponse, completionListener);
    }
  }

  private AgentSubProcessConnectorResponse buildResponse(
      AgentSubProcessExecutionContext executionContext,
      @Nullable AgentConversation conversation,
      AgentResponse agentResponse,
      @Nullable AgentJobCompletionListener completionListener) {
    boolean completionConditionFulfilled = agentResponse.toolCalls().isEmpty();
    // cancel remaining instances if any tool call in this turn's input was interrupted
    boolean cancelRemainingInstances =
        conversation != null && conversation.currentTurn().hasInterruptedToolCallResults();

    LOGGER.debug(
        "completionConditionFulfilled: {}, cancelRemainingInstances: {}",
        completionConditionFulfilled,
        cancelRemainingInstances);

    final var variables = new LinkedHashMap<String, Object>();
    variables.put(AgentProcessVariables.AGENT_CONTEXT, agentResponse.context());

    if (completionConditionFulfilled) {
      LOGGER.debug("Completion condition fulfilled, creating agent response variable");
      variables.put(
          AgentProcessVariables.AGENT_RESPONSE,
          createAgentResponseVariable(executionContext, agentResponse));
    } else {
      LOGGER.debug(
          "Completion condition not fulfilled, clearing tool call results for next tool call iteration");
      variables.put(AgentProcessVariables.TOOL_CALL_RESULTS, List.of());
    }

    return AgentSubProcessConnectorResponse.builder()
        .responseValue(agentResponse)
        .variables(variables)
        .elementActivations(buildElementActivations(agentResponse))
        .completionConditionFulfilled(completionConditionFulfilled)
        .cancelRemainingInstances(cancelRemainingInstances)
        .completionListener(completionListener)
        .build();
  }

  private AgentSubProcessResponse createAgentResponseVariable(
      AgentSubProcessExecutionContext executionContext, AgentResponse agentResponse) {
    var builder =
        AgentSubProcessResponse.builder()
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
                          Map.entry(AgentProcessVariables.TOOL_CALL, toolCall),
                          // Creating empty toolCallResult variable to avoid variable
                          // to bubble up in the upper scopes while merging variables on
                          // ad-hoc sub-process inner instance completion.
                          Map.entry(AgentProcessVariables.TOOL_CALL_RESULT, "")));
            })
        .toList();
  }
}
