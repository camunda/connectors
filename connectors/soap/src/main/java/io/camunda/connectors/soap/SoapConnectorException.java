/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap;

import com.fasterxml.jackson.databind.JsonNode;

public class SoapConnectorException extends Exception {
  private final JsonNode response;

  public SoapConnectorException(Throwable cause, JsonNode response) {
    super(cause);
    this.response = response;
  }

  public JsonNode getResponse() {
    return response;
  }
}
