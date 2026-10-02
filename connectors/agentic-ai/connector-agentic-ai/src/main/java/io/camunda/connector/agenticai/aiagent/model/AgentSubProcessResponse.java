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
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.common.AgenticAiRecord;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@AgenticAiRecord
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonDeserialize(builder = AgentSubProcessResponse.AgentSubProcessResponseJacksonProxyBuilder.class)
public record AgentSubProcessResponse(
    @Nullable AgentContext context,
    @Nullable AssistantMessage responseMessage,
    @Nullable String responseText,
    @Nullable Object responseJson,
    List<Map<String, Object>> documents)
    implements AgentSubProcessResponseBuilder.With {

  public static AgentSubProcessResponseBuilder builder() {
    return AgentSubProcessResponseBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class AgentSubProcessResponseJacksonProxyBuilder
      extends AgentSubProcessResponseBuilder {}
}
