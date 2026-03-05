/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.jobworker;

import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.connector.agenticai.aiagent.AiAgentJobWorker;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.runtime.core.outbound.ConnectorJobCompletion;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

@AgenticAiRecord
public record AiAgentJobCompletion(
    @Nullable AgentResponse agentResponse,
    boolean completionConditionFulfilled,
    boolean cancelRemainingInstances,
    Map<String, Object> variables,
    @Nullable Consumer<Throwable> completionErrorHandler)
    implements ConnectorJobCompletion, AiAgentJobCompletionBuilder.With {

  private static final Logger LOGGER = LoggerFactory.getLogger(AiAgentJobCompletion.class);

  public static AiAgentJobCompletionBuilder builder() {
    return AiAgentJobCompletionBuilder.builder();
  }

  @Override
  public Object responseValue() {
    return agentResponse;
  }

  @Override
  public boolean rejectIgnoreError() {
    return true;
  }

  @Override
  public void onCompletionError(Throwable throwable) {
    if (completionErrorHandler != null) {
      completionErrorHandler.accept(throwable);
    }
  }

  @Override
  public FinalCommandStep<?> prepareCompleteCommand(
      JobClient client, ActivatedJob job, Map<String, Object> ignoredVariables) {
    return client
        .newCompleteCommand(job)
        .variables(variables)
        .withResult(
            result -> {
              var adHocSubProcess =
                  result
                      .forAdHocSubProcess()
                      .completionConditionFulfilled(completionConditionFulfilled)
                      .cancelRemainingInstances(cancelRemainingInstances);

              if (agentResponse != null) {
                for (ToolCallProcessVariable toolCall : agentResponse.toolCalls()) {
                  if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Activating tool {}: {}", toolCall.metadata().name(), toolCall);
                  } else {
                    LOGGER.debug("Activating tool {}", toolCall.metadata().name());
                  }

                  adHocSubProcess =
                      adHocSubProcess
                          .activateElement(toolCall.metadata().name())
                          .variables(
                              Map.ofEntries(
                                  Map.entry(AiAgentJobWorker.TOOL_CALL_VARIABLE, toolCall),
                                  Map.entry(AiAgentJobWorker.TOOL_CALL_RESULT_VARIABLE, "")));
                }
              }

              return adHocSubProcess;
            });
  }
}
