/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.protocols.actions;

public enum ContentType {
  PLAIN("text/plain; charset=utf-8"),
  HTML("text/html; charset=utf-8"),
  MULTIPART("multipart/mixed; charset=utf-8");

  private final String value;

  ContentType(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
