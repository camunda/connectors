/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.api.client.util.Value;

public enum Type {
  @Value("folder")
  @JsonProperty("folder")
  FOLDER,
  @JsonProperty("file")
  @Value("file")
  FILE
}
