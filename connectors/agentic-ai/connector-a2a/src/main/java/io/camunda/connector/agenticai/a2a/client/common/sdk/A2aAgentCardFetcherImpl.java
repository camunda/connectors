/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk;

import static io.camunda.connector.agenticai.a2a.client.common.A2aErrorCodes.ERROR_CODE_A2A_CLIENT_AGENT_CARD_RETRIEVAL_FAILED;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.camunda.connector.agenticai.a2a.client.common.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.client.common.model.A2aConnectionConfiguration;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aAgentCard;
import io.camunda.connector.agenticai.a2a.client.common.sdk.http.A2aHttpClientFactory;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Map;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.spec.A2AClientError;
import org.a2aproject.sdk.spec.A2AClientJSONError;
import org.a2aproject.sdk.spec.AgentCard;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class A2aAgentCardFetcherImpl implements A2aAgentCardFetcher {

  private static final Logger LOGGER = LoggerFactory.getLogger(A2aAgentCardFetcherImpl.class);
  private static final String DEFAULT_AGENT_CARD_PATH = ".well-known/agent-card.json";

  private static final String A2A_VERSION_HEADER = "A2A-Version";
  private static final String A2A_VERSION = "1.0";
  private static final String UNSUPPORTED_CARD_HINT =
      "The A2A client supports only A2A 1.0 servers. A2A 0.3 agent cards are not supported.";

  private final A2AHttpClient httpClient;

  public A2aAgentCardFetcherImpl(A2aHttpClientFactory httpClientFactory) {
    this.httpClient = httpClientFactory.createHttpClient();
  }

  @Override
  public A2aAgentCard fetchAgentCard(A2aConnectionConfiguration connection) {
    LOGGER.debug("Fetching agent-card from URL: {}", connection.url());
    AgentCard agentCard = fetchSdkAgentCard(connection);
    return convertAgentCard(agentCard);
  }

  @Override
  public A2aSdkAgentCard fetchAgentCardRaw(A2aConnectionConfiguration connection) {
    return new A2aSdkAgentCard(fetchSdkAgentCard(connection));
  }

  private AgentCard fetchSdkAgentCard(A2aConnectionConfiguration connection) {
    final var relativeCardPath =
        isNotBlank(connection.agentCardLocation())
            ? connection.agentCardLocation()
            : DEFAULT_AGENT_CARD_PATH;
    final AgentCard agentCard;
    try {
      agentCard =
          A2A.getAgentCard(
              httpClient,
              connection.url(),
              relativeCardPath,
              Map.of(A2A_VERSION_HEADER, A2A_VERSION));
    } catch (A2AClientJSONError e) {
      throw new ConnectorException(
          ERROR_CODE_A2A_CLIENT_AGENT_CARD_RETRIEVAL_FAILED,
          "Failed to parse agent card from %s. %s"
              .formatted(relativeCardPath, UNSUPPORTED_CARD_HINT),
          e);
    } catch (A2AClientError e) {
      throw new ConnectorException(
          ERROR_CODE_A2A_CLIENT_AGENT_CARD_RETRIEVAL_FAILED,
          "Failed to load agent card from %s: %s".formatted(relativeCardPath, rootCauseMessage(e)),
          e);
    }
    if (agentCard.supportedInterfaces() == null || agentCard.supportedInterfaces().isEmpty()) {
      throw new ConnectorException(
          ERROR_CODE_A2A_CLIENT_AGENT_CARD_RETRIEVAL_FAILED,
          "Agent card from %s has no supportedInterfaces. %s"
              .formatted(relativeCardPath, UNSUPPORTED_CARD_HINT));
    }
    return agentCard;
  }

  private A2aAgentCard convertAgentCard(AgentCard agentCard) {
    final var agentSkills =
        agentCard.skills().stream()
            .map(skill ->
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

  private static String rootCauseMessage(Throwable e) {
    Throwable root = e;
    Throwable next;
    while ((next = root.getCause()) != null && !next.equals(root)) {
      root = next;
    }
    return root.getClass().getSimpleName() + ": " + root.getMessage();
  }
}
