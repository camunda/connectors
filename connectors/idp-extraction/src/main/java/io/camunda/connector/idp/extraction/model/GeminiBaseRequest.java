/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model;

import io.camunda.google.model.GoogleBaseRequest;

public class GeminiBaseRequest extends GoogleBaseRequest {

  private GeminiRequestConfiguration configuration;

  public GeminiRequestConfiguration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(GeminiRequestConfiguration configuration) {
    this.configuration = configuration;
  }
}
