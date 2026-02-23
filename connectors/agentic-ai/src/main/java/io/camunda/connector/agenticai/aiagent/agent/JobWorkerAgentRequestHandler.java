/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.AiAgentJobWorker;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentCompletion;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentResponse;
import java.util.LinkedHashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobWorkerAgentRequestHandler
    extends BaseAgentRequestHandler<JobWorkerAgentExecutionContext, JobWorkerAgentCompletion> {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobWorkerAgentRequestHandler.class);

  public JobWorkerAgentRequestHandler(
      AgentInitializer agentInitializer, AgentExecutor agentExecutor) {
    super(agentInitializer, agentExecutor);
  }

  @Override
  public JobWorkerAgentCompletion completeJob(
      JobWorkerAgentExecutionContext executionContext,
      AgentResponse agentResponse,
      ConversationSession session) {
    if (agentResponse == null) {
      LOGGER.debug(
          "No agent response provided, completing job {} without response",
          executionContext.job().getKey());

      // close session early — no response to process
      if (session != null) {
        session.close();
      }

      // no-op (do not activate elements, do not complete agent process) -> wait for next job to
      // proceed (e.g. by adding user messages or to complete tool call results)
      return JobWorkerAgentCompletion.builder()
          .completionConditionFulfilled(false)
          .cancelRemainingInstances(false)
          .build();
    } else {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(
            "Agent response provided, completing job {} with response and tool calls: {}",
            executionContext.job().getKey(),
            agentResponse.toolCalls().stream().map(tc -> tc.metadata().name()).toList());
      }

      return completeWithResponse(executionContext, agentResponse, session);
    }
  }

  private JobWorkerAgentCompletion completeWithResponse(
      JobWorkerAgentExecutionContext executionContext,
      AgentResponse agentResponse,
      ConversationSession session) {
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

    return JobWorkerAgentCompletion.builder()
        .agentResponse(agentResponse)
        .completionConditionFulfilled(completionConditionFulfilled)
        .cancelRemainingInstances(cancelRemainingInstances)
        .variables(variables)
        .onCompletionSuccess(
            () -> {
              if (session != null) {
                try {
                  session.onJobCompleted(agentResponse.context());
                } finally {
                  session.close();
                }
              }
            })
        .onCompletionError(
            throwable -> {
              if (session != null) {
                session.close();
              }
            })
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
}
