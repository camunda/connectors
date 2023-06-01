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
import javax.validation.constraints.NotNull;

public class AddValues implements Input {

  @NotBlank @Secret private String spreadsheetId;
  @Secret private String worksheetName;
  @NotBlank @Secret private String cellId;
  @NotNull @Secret private Object value;
  private String type;

  public AddValues(
      String spreadsheetId, String worksheetName, String cellId, Object value, String type) {
    this.spreadsheetId = spreadsheetId;
    this.worksheetName = worksheetName;
    this.cellId = cellId;
    this.value = value;
    this.type = type;
  }

  public String getSpreadsheetId() {
    return spreadsheetId;
  }

  public void setSpreadsheetId(String spreadsheetId) {
    this.spreadsheetId = spreadsheetId;
  }

  public String getWorksheetName() {
    return worksheetName;
  }

  public void setWorksheetName(String worksheetName) {
    this.worksheetName = worksheetName;
  }

  public String getCellId() {
    return cellId;
  }

  public void setCellId(String cellId) {
    this.cellId = cellId;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = value;
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
    AddValues addValues = (AddValues) o;
    return Objects.equals(spreadsheetId, addValues.spreadsheetId)
        && Objects.equals(worksheetName, addValues.worksheetName)
        && Objects.equals(cellId, addValues.cellId)
        && Objects.equals(value, addValues.value)
        && Objects.equals(type, addValues.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(spreadsheetId, worksheetName, cellId, value, type);
  }

  @Override
  public String toString() {
    return "AddValues{"
        + "spreadsheetId='"
        + spreadsheetId
        + '\''
        + ", worksheetName='"
        + worksheetName
        + '\''
        + ", cellId='"
        + cellId
        + '\''
        + ", value=[REDACTED]"
        + ", type='"
        + type
        + '\''
        + '}';
  }
}
