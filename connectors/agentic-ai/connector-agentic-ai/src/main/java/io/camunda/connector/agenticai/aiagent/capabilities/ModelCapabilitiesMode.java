/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.capabilities;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Template-only selector deciding whether model capabilities are auto-resolved or overridden. */
public enum ModelCapabilitiesMode {
  @JsonProperty("auto")
  AUTO,
  @JsonProperty("custom")
  CUSTOM
}
