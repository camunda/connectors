/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.impl;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.a2a.A2A;
import io.a2a.spec.A2AClientError;
import io.a2a.spec.AgentCard;
import io.camunda.connector.agenticai.a2a.client.common.api.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.client.common.model.A2aConnectionConfiguration;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aAgentCard;
import java.util.Collections;
import java.util.UUID;
import org.apache.commons.collections4.CollectionUtils;

public class A2aAgentCardFetcherImpl implements A2aAgentCardFetcher {

  // TODO: add caching?
  @Override
  public A2aAgentCard fetchAgentCard(A2aConnectionConfiguration connection) {
    AgentCard agentCard = fetchAgentCardRaw(connection);
    return convertAgentCard(agentCard);
  }

  @Override
  public AgentCard fetchAgentCardRaw(A2aConnectionConfiguration connection) {
    final var relativeCardPath =
        isNotBlank(connection.agentCardLocation()) ? connection.agentCardLocation() : null;
    try {
      return A2A.getAgentCard(connection.url(), relativeCardPath, Collections.emptyMap());
    } catch (A2AClientError e) {
      throw new RuntimeException(e);
    }
  }

  private A2aAgentCard convertAgentCard(AgentCard agentCard) {
    final var agentSkills =
        agentCard.skills().stream()
            .map(
                skill ->
                    A2aAgentCard.AgentSkill.builder()
                        .id(skill.id())
                        .name(skill.name())
                        .description(skill.description())
                        .tags(skill.tags())
                        .examples(skill.examples())
                        .inputModes(
                            CollectionUtils.isEmpty(skill.inputModes())
                                ? agentCard.defaultInputModes()
                                : skill.inputModes())
                        .outputModes(
                            CollectionUtils.isEmpty(skill.outputModes())
                                ? agentCard.defaultOutputModes()
                                : skill.outputModes())
                        .build())
            .toList();
    return new A2aAgentCard(
        UUID.randomUUID().toString(), agentCard.name(), agentCard.description(), agentSkills);
  }
}
