/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.jobworker;

import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.connector.agenticai.aiagent.AiAgentJobWorker;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentCompletion;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.runtime.core.outbound.ConnectorJobCompletion;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiAgentJobCompletion implements ConnectorJobCompletion {

  private static final Logger LOGGER = LoggerFactory.getLogger(AiAgentJobCompletion.class);

  private final JobWorkerAgentCompletion completion;

  public AiAgentJobCompletion(JobWorkerAgentCompletion completion) {
    this.completion = completion;
  }

  @Override
  public Object responseValue() {
    return completion.agentResponse();
  }

  @Override
  public boolean rejectIgnoreError() {
    return true;
  }

  @Override
  public void onError(Throwable throwable) {
    completion.onCompletionError(throwable);
  }

  @Override
  public FinalCommandStep<?> prepareCompleteCommand(
      JobClient client, ActivatedJob job, Map<String, Object> variables) {
    return prepareCompleteCommand(client, job);
  }

  JobWorkerAgentCompletion completion() {
    return completion;
  }

  private CompleteJobCommandStep1 prepareCompleteCommand(JobClient client, ActivatedJob job) {
    return client
        .newCompleteCommand(job)
        .variables(completion.variables())
        .withResult(
            result -> {
              var adHocSubProcess =
                  result
                      .forAdHocSubProcess()
                      .completionConditionFulfilled(completion.completionConditionFulfilled())
                      .cancelRemainingInstances(completion.cancelRemainingInstances());

              if (completion.agentResponse() != null) {
                final var agentResponse = completion.agentResponse();
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
