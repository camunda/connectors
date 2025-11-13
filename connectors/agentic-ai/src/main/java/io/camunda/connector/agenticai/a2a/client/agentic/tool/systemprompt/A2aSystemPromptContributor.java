/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.agentic.tool.systemprompt;

import static io.camunda.connector.agenticai.a2a.client.common.A2aConstants.PROPERTY_A2A_CLIENTS;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptContributor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

/**
 * Contributes A2A (Agent-to-Agent) protocol instructions to the system prompt when A2A tools are
 * detected in the agent's execution context.
 *
 * <p>This contributor automatically adds protocol-specific guidance for interacting with remote A2A
 * agents, including proper message construction, task lifecycle management, and context handling.
 */
public class A2aSystemPromptContributor implements SystemPromptContributor {

  private static final Logger LOGGER = LoggerFactory.getLogger(A2aSystemPromptContributor.class);
  public static final int ORDER = 100;

  private final String a2aInstructions;

  public A2aSystemPromptContributor(Resource systemPromptResource) {
    this.a2aInstructions = loadA2aInstructions(systemPromptResource);
  }

  @Override
  public String contributeSystemPrompt(
      AgentExecutionContext executionContext, AgentContext agentContext) {

    // Only contribute if A2A tools are present in the execution context
    boolean hasA2aTools =
        agentContext.properties().getOrDefault(PROPERTY_A2A_CLIENTS, List.of())
                instanceof List<?> a2aClients
            && !a2aClients.isEmpty();

    if (hasA2aTools) {
      LOGGER.debug("A2A tools detected, contributing A2A protocol instructions to system prompt");
      return a2aInstructions;
    }

    LOGGER.debug("No A2A tools detected, skipping A2A protocol instructions");
    return null;
  }

  @Override
  public int getOrder() {
    return ORDER;
  }

  private String loadA2aInstructions(Resource systemPromptResource) {
    try {
      String content =
          StreamUtils.copyToString(systemPromptResource.getInputStream(), StandardCharsets.UTF_8);
      LOGGER.debug("Loaded A2A protocol instructions from {}", systemPromptResource);
      return content;
    } catch (IOException e) {
      LOGGER.error(
          "Failed to load A2A protocol instructions from {}: {}",
          systemPromptResource,
          e.getMessage());
      throw new IllegalStateException(e);
    }
  }
}
