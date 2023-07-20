/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request.impl;

import io.camunda.connector.gsheets.model.request.Input;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;

public class CreateSpreadsheet extends Input {

  @NotBlank private String spreadsheetName;
  private String parent;

  public CreateSpreadsheet() {}

  public CreateSpreadsheet(String spreadsheetName, String parent) {
    this.spreadsheetName = spreadsheetName;
    this.parent = parent;
  }

  public String getSpreadsheetName() {
    return spreadsheetName;
  }

  public void setSpreadsheetName(String spreadsheetName) {
    this.spreadsheetName = spreadsheetName;
  }

  public String getParent() {
    return parent;
  }

  public void setParent(String parent) {
    this.parent = parent;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CreateSpreadsheet that = (CreateSpreadsheet) o;
    return Objects.equals(spreadsheetName, that.spreadsheetName)
        && Objects.equals(parent, that.parent);
  }

  @Override
  public int hashCode() {
    return Objects.hash(spreadsheetName, parent);
  }

  @Override
  public String toString() {
    return "CreateSpreadsheet{"
        + "spreadsheetName='"
        + spreadsheetName
        + '\''
        + ", parent='"
        + parent
        + '\''
        + "} "
        + super.toString();
  }
}
