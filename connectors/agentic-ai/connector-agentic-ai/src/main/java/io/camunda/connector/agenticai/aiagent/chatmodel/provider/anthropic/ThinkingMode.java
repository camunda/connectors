/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Anthropic extended-thinking mechanisms a model may support, as declared per-model in the
 * capability matrix's {@code provider.reasoning.thinking-modes} block (see {@link
 * AnthropicReasoningCapabilities}). Carries lowercase {@link JsonProperty} values (matching {@link
 * io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilities.Modality}) so it
 * round-trips the lowercase values used throughout the bundled YAML regardless of the consuming
 * {@link com.fasterxml.jackson.databind.ObjectMapper}'s case-sensitivity configuration.
 */
public enum ThinkingMode {
  @JsonProperty("enabled")
  ENABLED,
  @JsonProperty("adaptive")
  ADAPTIVE,
  @JsonProperty("disabled")
  DISABLED
}
