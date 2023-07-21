/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request.impl;

import io.camunda.connector.gsheets.model.request.ColumnIndexType;
import io.camunda.connector.gsheets.model.request.SpreadsheetInput;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class DeleteColumn extends SpreadsheetInput {

  @NotNull private Integer worksheetId;
  @NotNull private ColumnIndexType columnIndexType;
  private Integer columnNumberIndex;
  private String columnLetterIndex;

  public DeleteColumn() {}

  public DeleteColumn(
      String spreadsheetId,
      Integer worksheetId,
      ColumnIndexType columnIndexType,
      Integer columnNumberIndex,
      String columnLetterIndex) {
    super(spreadsheetId);
    this.worksheetId = worksheetId;
    this.columnIndexType = columnIndexType;
    this.columnNumberIndex = columnNumberIndex;
    this.columnLetterIndex = columnLetterIndex;
  }

  @AssertTrue(message = "Column index cannot be blank")
  private boolean isColumnIndexValid() {
    if (ColumnIndexType.LETTERS.equals(this.columnIndexType)) {
      String index = this.columnLetterIndex;

      return !(index == null || index.trim().length() == 0);
    }

    return null != this.columnNumberIndex;
  }

  public Integer getWorksheetId() {
    return worksheetId;
  }

  public void setWorksheetId(Integer worksheetId) {
    this.worksheetId = worksheetId;
  }

  public ColumnIndexType getColumnIndexType() {
    return columnIndexType;
  }

  public void setColumnIndexType(ColumnIndexType columnIndexType) {
    this.columnIndexType = columnIndexType;
  }

  public Integer getColumnNumberIndex() {
    return columnNumberIndex;
  }

  public void setColumnNumberIndex(Integer columnNumberIndex) {
    this.columnNumberIndex = columnNumberIndex;
  }

  public String getColumnLetterIndex() {
    return columnLetterIndex;
  }

  public void setColumnLetterIndex(String columnLetterIndex) {
    this.columnLetterIndex = columnLetterIndex;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DeleteColumn that = (DeleteColumn) o;
    return Objects.equals(worksheetId, that.worksheetId)
        && columnIndexType == that.columnIndexType
        && Objects.equals(columnNumberIndex, that.columnNumberIndex)
        && Objects.equals(columnLetterIndex, that.columnLetterIndex);
  }

  @Override
  public int hashCode() {
    return Objects.hash(worksheetId, columnIndexType, columnNumberIndex, columnLetterIndex);
  }

  @Override
  public String toString() {
    return "DeleteColumn{"
        + "worksheetId="
        + worksheetId
        + ", columnIndexType="
        + columnIndexType
        + ", columnNumberIndex="
        + columnNumberIndex
        + ", columnLetterIndex='"
        + columnLetterIndex
        + '\''
        + "} "
        + super.toString();
  }
}
