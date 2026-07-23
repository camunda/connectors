/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Anthropic effort levels, trading thoroughness against speed and cost. Affects all output (text,
 * tool calls and extended thinking); not supported on all models.
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
