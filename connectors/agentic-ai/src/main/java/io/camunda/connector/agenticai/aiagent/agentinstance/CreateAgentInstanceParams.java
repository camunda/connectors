/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;

public record CreateAgentInstanceParams(
    long elementInstanceKey, String model, String provider, String systemPrompt, Limits limits) {

  public record Limits(Integer maxModelCalls) {}

  public static CreateAgentInstanceParams from(AgentExecutionContext executionContext) {
    final var limitsConfig = executionContext.limits();
    return new CreateAgentInstanceParams(
        executionContext.jobContext().getElementInstanceKey(),
        ProviderModelExtractor.extract(executionContext.provider()),
        executionContext.provider().providerType(),
        executionContext.systemPrompt() != null ? executionContext.systemPrompt().prompt() : null,
        limitsConfig != null ? new Limits(limitsConfig.maxModelCalls()) : null);
  }
}
