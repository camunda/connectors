/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.model.result;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import java.util.List;

@AgenticAiRecord
@JsonDeserialize(builder = A2aAgentCard.A2aAgentCardJacksonProxyBuilder.class)
public record A2aAgentCard(String name, String description, List<AgentSkill> skills)
    implements A2aResult {

  public static final String AGENT_CARD = "agentCard";

  @Override
  @JsonGetter
  public String kind() {
    return AGENT_CARD;
  }

  @AgenticAiRecord
  @JsonDeserialize(builder = AgentSkill.AgentSkillJacksonProxyBuilder.class)
  public record AgentSkill(
      String id,
      String name,
      String description,
      List<String> tags,
      List<String> examples,
      List<String> inputModes,
      List<String> outputModes) {

    public static A2aAgentCardAgentSkillBuilder builder() {
      return A2aAgentCardAgentSkillBuilder.builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class AgentSkillJacksonProxyBuilder extends A2aAgentCardAgentSkillBuilder {}
  }

  public static A2aAgentCardBuilder builder() {
    return A2aAgentCardBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class A2aAgentCardJacksonProxyBuilder extends A2aAgentCardBuilder {}
}
