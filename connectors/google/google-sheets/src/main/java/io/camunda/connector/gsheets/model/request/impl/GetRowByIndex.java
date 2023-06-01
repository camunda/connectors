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

public class GetRowByIndex implements Input {

  @NotBlank @Secret private String spreadsheetId;
  @Secret private String worksheetName;
  @NotNull private Integer rowIndex;
  private String type;

  public GetRowByIndex(String spreadsheetId, String worksheetName, Integer rowIndex, String type) {
    this.spreadsheetId = spreadsheetId;
    this.worksheetName = worksheetName;
    this.rowIndex = rowIndex;
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
    GetRowByIndex that = (GetRowByIndex) o;
    return Objects.equals(spreadsheetId, that.spreadsheetId)
        && Objects.equals(worksheetName, that.worksheetName)
        && Objects.equals(rowIndex, that.rowIndex)
        && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(spreadsheetId, worksheetName, rowIndex, type);
  }

  @Override
  public String toString() {
    return "GetRowByIndex{"
        + "spreadsheetId='"
        + spreadsheetId
        + '\''
        + ", worksheetName='"
        + worksheetName
        + '\''
        + ", rowIndex="
        + rowIndex
        + ", type='"
        + type
        + '\''
        + '}';
  }
}
