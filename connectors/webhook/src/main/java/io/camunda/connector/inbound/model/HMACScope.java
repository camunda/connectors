/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum HMACScope {
  @JsonProperty("url")
  @JsonAlias("URL")
  URL,
  @JsonProperty("body")
  @JsonAlias("BODY")
  BODY,
  @JsonProperty("parameters")
  @JsonAlias("PARAMETERS")
  PARAMETERS
}
