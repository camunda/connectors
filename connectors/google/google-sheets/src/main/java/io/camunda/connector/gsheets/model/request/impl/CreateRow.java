/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request.impl;

import io.camunda.connector.gsheets.model.request.SpreadsheetInput;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;

public class CreateRow extends SpreadsheetInput {

  private String worksheetName;
  @NotNull private Integer rowIndex;
  @NotEmpty private List<Object> values;

  public CreateRow() {}

  public CreateRow(
      String spreadsheetId, String worksheetName, Integer rowIndex, List<Object> values) {
    super(spreadsheetId);
    this.worksheetName = worksheetName;
    this.rowIndex = rowIndex;
    this.values = values;
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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CreateRow createRow = (CreateRow) o;
    return Objects.equals(worksheetName, createRow.worksheetName)
        && Objects.equals(rowIndex, createRow.rowIndex)
        && Objects.equals(values, createRow.values);
  }

  @Override
  public int hashCode() {
    return Objects.hash(worksheetName, rowIndex, values);
  }

  @Override
  public String toString() {
    return "CreateRow{"
        + "worksheetName='"
        + worksheetName
        + '\''
        + ", rowIndex="
        + rowIndex
        + ", values="
        + values
        + "} "
        + super.toString();
  }
}
