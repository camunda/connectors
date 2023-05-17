/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request.impl;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.gsheets.model.request.Input;
import java.util.List;
import java.util.Objects;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class CreateRow implements Input {

  @NotBlank @Secret private String spreadsheetId;
  @Secret private String worksheetName;
  @NotNull private Integer rowIndex;
  @NotEmpty @Secret private List<Object> values;
  private String type;

  public CreateRow(
      String spreadsheetId,
      String worksheetName,
      Integer rowIndex,
      List<Object> values,
      String type) {
    this.spreadsheetId = spreadsheetId;
    this.worksheetName = worksheetName;
    this.rowIndex = rowIndex;
    this.values = values;
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

  public Integer getRowIndex() {
    return rowIndex;
  }

  public void setRowIndex(Integer rowIndex) {
    this.rowIndex = rowIndex;
  }

  public List<Object> getValues() {
    return values;
  }

  public void setValues(List<Object> values) {
    this.values = values;
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
    CreateRow createRow = (CreateRow) o;
    return Objects.equals(spreadsheetId, createRow.spreadsheetId)
        && Objects.equals(worksheetName, createRow.worksheetName)
        && Objects.equals(rowIndex, createRow.rowIndex)
        && Objects.equals(values, createRow.values)
        && Objects.equals(type, createRow.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(spreadsheetId, worksheetName, rowIndex, values, type);
  }

  @Override
  public String toString() {
    return "CreateRow{"
        + "spreadsheetId='"
        + spreadsheetId
        + '\''
        + ", worksheetName='"
        + worksheetName
        + '\''
        + ", rowIndex="
        + rowIndex
        + ", values=[REDACTED]"
        + ", type='"
        + type
        + '\''
        + '}';
  }
}
