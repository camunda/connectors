/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common;

import static io.camunda.connector.agenticai.a2a.client.common.A2aErrorCodes.ERROR_CODE_A2A_CLIENT_AGENT_CARD_RETRIEVAL_FAILED;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.a2a.A2A;
import io.a2a.spec.A2AClientError;
import io.a2a.spec.AgentCard;
import io.camunda.connector.agenticai.a2a.client.common.model.A2aConnectionConfiguration;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aAgentCard;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Collections;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class A2aAgentCardFetcherImpl implements A2aAgentCardFetcher {

  private static final Logger LOGGER = LoggerFactory.getLogger(A2aAgentCardFetcherImpl.class);
  private static final String DEFAULT_AGENT_CARD_PATH = ".well-known/agent-card.json";

  @Override
  public A2aAgentCard fetchAgentCard(A2aConnectionConfiguration connection) {
    LOGGER.debug("Fetching agent-card from URL: {}", connection.url());
    AgentCard agentCard = fetchAgentCardRaw(connection);
    return convertAgentCard(agentCard);
  }

  @Override
  public AgentCard fetchAgentCardRaw(A2aConnectionConfiguration connection) {
    final var relativeCardPath =
        isNotBlank(connection.agentCardLocation())
            ? connection.agentCardLocation()
            : DEFAULT_AGENT_CARD_PATH;
    try {
      return A2A.getAgentCard(connection.url(), relativeCardPath, Collections.emptyMap());
    } catch (A2AClientError e) {
      throw new ConnectorException(
          ERROR_CODE_A2A_CLIENT_AGENT_CARD_RETRIEVAL_FAILED,
          "Failed to load agent card from %s".formatted(relativeCardPath),
          e);
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
    return new A2aAgentCard(agentCard.name(), agentCard.description(), agentSkills);
  }
}
