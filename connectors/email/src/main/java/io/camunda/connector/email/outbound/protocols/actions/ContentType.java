/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.protocols.actions;

public enum ContentType {
  PLAIN,
  HTML;

  public static class Constants {
    public static final String PLAIN_VALUE = "plain";
    public static final String HTML_VALUE = "html";
  }
}
