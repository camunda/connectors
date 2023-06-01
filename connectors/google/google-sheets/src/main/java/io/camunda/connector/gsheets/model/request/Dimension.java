/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request;

public enum Dimension {
  ROWS("ROWS"),
  COLUMNS("COLUMNS");

  private final String value;

  Dimension(final String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
