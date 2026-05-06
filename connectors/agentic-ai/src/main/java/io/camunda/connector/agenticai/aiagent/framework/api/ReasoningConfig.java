/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

/**
 * Two-tier reasoning configuration: high-level {@link Effort} or explicit token {@link
 * ReasoningBudget}, with {@link ReasoningDisabled} to opt out. Per-implementation translation maps
 * this onto provider-native fields (Anthropic adaptive effort / thinking budget, OpenAI Responses
 * reasoning effort, Gemini thinking budget, etc.).
 *
 * <p>Part of the ADR-004 Phase 1 SPI scaffolding. Not yet wired into the runtime.
 */
public sealed interface ReasoningConfig
    permits ReasoningConfig.ReasoningEffort,
        ReasoningConfig.ReasoningBudget,
        ReasoningConfig.ReasoningDisabled {

  enum Effort {
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH,
    X_HIGH
  }

  record ReasoningEffort(Effort level) implements ReasoningConfig {}

  record ReasoningBudget(int tokens) implements ReasoningConfig {}

  record ReasoningDisabled() implements ReasoningConfig {}
}
