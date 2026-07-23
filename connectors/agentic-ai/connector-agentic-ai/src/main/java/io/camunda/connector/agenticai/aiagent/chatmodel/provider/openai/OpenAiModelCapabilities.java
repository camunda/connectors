/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import io.camunda.connector.agenticai.aiagent.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilities;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * OpenAI-owned {@link ModelCapabilities}: the neutral modality contract (delegated to {@link
 * #core()}) plus the typed OpenAI reasoning descriptor. {@link #supportsReasoning()} is derived
 * from the presence of {@link #reasoning()} rather than stored as its own flag.
 */
public record OpenAiModelCapabilities(
    CoreModelCapabilities core, @Nullable OpenAiReasoningCapabilities reasoning)
    implements ModelCapabilities {

  public boolean supportsReasoning() {
    return reasoning != null;
  }

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
