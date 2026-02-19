/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.model.AgenticAiRecord;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.lang.Nullable;

@AgenticAiRecord
public record JobWorkerAgentCompletion(
    @Nullable AgentResponse agentResponse,
    boolean completionConditionFulfilled,
    boolean cancelRemainingInstances,
    Map<String, Object> variables,
    @Nullable Consumer<Throwable> onCompletionError)
    implements JobWorkerAgentCompletionBuilder.With {

  public static JobWorkerAgentCompletionBuilder builder() {
    return JobWorkerAgentCompletionBuilder.builder();
  }

  public void onCompletionError(Throwable throwable) {
    if (onCompletionError != null) {
      onCompletionError.accept(throwable);
    }
  }
}
