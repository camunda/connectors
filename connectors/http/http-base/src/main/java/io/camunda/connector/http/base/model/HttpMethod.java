/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.model;

public enum HttpMethod {
  POST(true),
  GET(false),
  DELETE(false),
  PATCH(true),
  PUT(true);

  public final boolean supportsBody;

  HttpMethod(boolean supportsBody) {
    this.supportsBody = supportsBody;
  }
}
