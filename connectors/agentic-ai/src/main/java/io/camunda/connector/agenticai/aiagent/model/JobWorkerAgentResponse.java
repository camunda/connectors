/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import org.springframework.lang.Nullable;

@AgenticAiRecord
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonDeserialize(builder = JobWorkerAgentResponse.JobWorkerAgentResponseJacksonProxyBuilder.class)
public record JobWorkerAgentResponse(
    @Nullable AgentContext context,
    @Nullable AssistantMessage responseMessage,
    @Nullable String responseText,
    @Nullable Object responseJson)
    implements JobWorkerAgentResponseBuilder.With {

  public static JobWorkerAgentResponseBuilder builder() {
    return JobWorkerAgentResponseBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class JobWorkerAgentResponseJacksonProxyBuilder
      extends JobWorkerAgentResponseBuilder {}
}
