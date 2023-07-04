/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.google.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.api.client.util.Value;
import com.google.gson.annotations.SerializedName;

public enum AuthenticationType {
  @JsonProperty("bearer")
  @Value("bearer")
  @SerializedName("bearer")
  BEARER,

  @JsonProperty("refresh")
  @Value("refresh")
  @SerializedName("refresh")
  REFRESH
}
