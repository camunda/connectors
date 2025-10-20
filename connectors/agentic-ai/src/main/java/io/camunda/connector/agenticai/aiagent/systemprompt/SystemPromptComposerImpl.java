/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.systemprompt;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link SystemPromptComposer} that composes system prompts by
 * concatenating the base prompt with contributions from registered {@link SystemPromptContributor}
 * instances.
 *
 * <p>Contributors are applied in order based on their {@link SystemPromptContributor#getOrder()}
 * value, with lower values being applied first.
 */
public class SystemPromptComposerImpl implements SystemPromptComposer {

  private static final Logger LOGGER = LoggerFactory.getLogger(SystemPromptComposerImpl.class);
  private static final String SEPARATOR = "\n\n";

  private final List<SystemPromptContributor> contributors;

  public SystemPromptComposerImpl(List<SystemPromptContributor> contributors) {
    this.contributors =
        contributors.stream()
            .sorted(Comparator.comparingInt(SystemPromptContributor::getOrder))
            .toList();
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Initialized SystemPromptComposer with {} contributors", contributors.size());
    }
  }

  @Override
  public String composeSystemPrompt(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      SystemPromptConfiguration baseSystemPrompt) {

    ArrayList<String> composed = new ArrayList<>();

    String basePrompt = baseSystemPrompt.prompt();
    if (StringUtils.isNotBlank(basePrompt)) {
      composed.add(basePrompt);
      LOGGER.trace("Added base system prompt");
    }

    contributors.forEach(
        contributor -> {
          String contribution = contributor.contributeSystemPrompt(executionContext, agentContext);
          if (StringUtils.isNotBlank(contribution)) {
            composed.add(contribution);
            LOGGER.debug(
                "Added system prompt contribution from {}", contributor.getClass().getSimpleName());
          }
        });

    return String.join(SEPARATOR, composed);
  }
}
