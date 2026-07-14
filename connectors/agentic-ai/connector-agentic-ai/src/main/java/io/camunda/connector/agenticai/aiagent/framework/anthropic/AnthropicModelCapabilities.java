/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import io.camunda.connector.agenticai.aiagent.framework.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities;
import java.util.List;

/**
 * Anthropic-owned {@link ModelCapabilities}: the neutral modality contract (delegated to {@link
 * #core()}) plus the flags only the Anthropic path consumes. R1 will add a typed {@code
 * AnthropicReasoningCapabilities reasoning} component.
 */
public record AnthropicModelCapabilities(
    CoreModelCapabilities core,
    boolean supportsReasoning,
    boolean supportsReasoningSignatureRoundtrip,
    boolean supportsPromptCaching,
    boolean supportsParallelToolCalls)
    implements ModelCapabilities {

  @Override
  public List<Modality> userMessageModalities() {
    return core.userMessageModalities();
  }

  @Override
  public List<Modality> toolResultModalities() {
    return core.toolResultModalities();
  }

  @Override
  public List<Modality> assistantMessageModalities() {
    return core.assistantMessageModalities();
  }
}
