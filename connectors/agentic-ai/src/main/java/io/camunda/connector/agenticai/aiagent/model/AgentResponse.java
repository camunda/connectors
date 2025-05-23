/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import java.util.List;
import org.springframework.lang.Nullable;

@AgenticAiRecord
@JsonDeserialize(builder = AgentResponse.AgentResponseJacksonProxyBuilder.class)
public record AgentResponse(
    AgentContext context,
    List<ToolCallProcessVariable> toolCalls,
    @Nullable AssistantMessage responseMessage,
    @Nullable String responseText)
    implements AgentResponseBuilder.With {

  public static AgentResponseBuilder builder() {
    return AgentResponseBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class AgentResponseJacksonProxyBuilder extends AgentResponseBuilder {}
}
