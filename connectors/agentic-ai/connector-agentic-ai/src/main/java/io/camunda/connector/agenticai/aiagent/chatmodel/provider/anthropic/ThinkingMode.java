/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Anthropic extended-thinking mechanisms a model may support: {@code enabled} (manual token budget,
 * older models), {@code adaptive} (model-managed, newer models) or {@code disabled}.
 */
public enum ThinkingMode {
  @JsonProperty("enabled")
  ENABLED,
  @JsonProperty("adaptive")
  ADAPTIVE,
  @JsonProperty("disabled")
  DISABLED
}
