/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request.impl;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.gsheets.model.request.Input;
import java.util.Objects;
import javax.validation.constraints.NotBlank;

public class CreateSpreadsheet implements Input {

  @NotBlank @Secret private String spreadsheetName;
  @Secret private String parent;
  private String type;

  public CreateSpreadsheet(String spreadsheetName, String parent, String type) {
    this.spreadsheetName = spreadsheetName;
    this.parent = parent;
    this.type = type;
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
  public String getType() {
    return type;
  }

  @Override
  public void setType(String type) {
    this.type = type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CreateSpreadsheet that = (CreateSpreadsheet) o;
    return Objects.equals(spreadsheetName, that.spreadsheetName)
        && Objects.equals(parent, that.parent)
        && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(spreadsheetName, parent, type);
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
        + ", type='"
        + type
        + '\''
        + '}';
  }
}
