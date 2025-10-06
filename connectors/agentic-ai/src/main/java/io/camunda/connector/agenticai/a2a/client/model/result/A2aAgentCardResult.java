/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.model.result;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import java.util.List;

@AgenticAiRecord
@JsonDeserialize(builder = A2aAgentCardResult.A2aAgentCardResultJacksonProxyBuilder.class)
public record A2aAgentCardResult(String name, String description, List<AgentSkill> skills)
    implements A2aClientResult {

  public record AgentSkill(
      String id,
      String name,
      String description,
      List<String> tags,
      List<String> examples,
      List<String> inputModes,
      List<String> outputModes) {}

  public static A2aAgentCardResultBuilder builder() {
    return A2aAgentCardResultBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class A2aAgentCardResultJacksonProxyBuilder extends A2aAgentCardResultBuilder {}
}
