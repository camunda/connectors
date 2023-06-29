/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request.impl;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.gsheets.model.request.ColumnIndexType;
import io.camunda.connector.gsheets.model.request.Input;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class DeleteColumn extends Input {

  @NotBlank @Secret private String spreadsheetId;
  @NotNull private Integer worksheetId;
  @NotNull private ColumnIndexType columnIndexType;
  private Integer columnNumberIndex;
  @Secret private String columnLetterIndex;

  public DeleteColumn() {}

  public DeleteColumn(
      String spreadsheetId,
      Integer worksheetId,
      ColumnIndexType columnIndexType,
      Integer columnNumberIndex,
      String columnLetterIndex) {
    this.spreadsheetId = spreadsheetId;
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

  public String getSpreadsheetId() {
    return spreadsheetId;
  }

  public void setSpreadsheetId(String spreadsheetId) {
    this.spreadsheetId = spreadsheetId;
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
}
