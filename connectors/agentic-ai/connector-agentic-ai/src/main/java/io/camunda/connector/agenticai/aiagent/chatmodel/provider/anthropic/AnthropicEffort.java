/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Anthropic effort levels. Carries lowercase {@link JsonProperty} values (matching {@link
 * ThinkingMode} and {@link
 * io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilities.Modality}) so it
 * round-trips the lowercase values used throughout the bundled YAML regardless of the consuming
 * {@link com.fasterxml.jackson.databind.ObjectMapper}'s case-sensitivity configuration.
 */
public enum AnthropicEffort {
  @JsonProperty("low")
  LOW,
  @JsonProperty("medium")
  MEDIUM,
  @JsonProperty("high")
  HIGH,
  @JsonProperty("xhigh")
  XHIGH,
  @JsonProperty("max")
  MAX
}
