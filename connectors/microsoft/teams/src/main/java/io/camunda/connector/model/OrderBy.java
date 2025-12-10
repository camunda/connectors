/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

public enum OrderBy {
  withoutOrdering("withoutOrdering"),
  lastModifiedDateTime("lastModifiedDateTime desc"),
  createdDateTime("createdDateTime desc");

  private String value;

  OrderBy(final String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public void setValue(final String value) {
    this.value = value;
  }
}
