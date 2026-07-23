/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Typed Anthropic reasoning descriptor, materialised from a model's {@code provider.reasoning}
 * matrix block. {@code thinking-modes} lists the thinking mechanisms the model accepts (a manual
 * {@code enabled} budget 400s on adaptive-only models; {@code disabled} 400s on always-on models);
 * {@code effort-levels} lists supported effort values (empty ⇒ effort unsupported).
 */
public record AnthropicReasoningCapabilities(
    @JsonProperty("thinking-modes") List<ThinkingMode> thinkingModes,
    @JsonProperty("effort-levels") List<AnthropicEffort> effortLevels) {

  public AnthropicReasoningCapabilities {
    thinkingModes = thinkingModes == null ? List.of() : List.copyOf(thinkingModes);
    effortLevels = effortLevels == null ? List.of() : List.copyOf(effortLevels);
  }
}
