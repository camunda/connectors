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
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class CreateRow extends Input {

  @NotBlank @Secret private String spreadsheetId;
  @Secret private String worksheetName;
  @NotNull private Integer rowIndex;
  @NotEmpty @Secret private List<Object> values;

  public CreateRow() {}

  public CreateRow(
      String spreadsheetId, String worksheetName, Integer rowIndex, List<Object> values) {
    this.spreadsheetId = spreadsheetId;
    this.worksheetName = worksheetName;
    this.rowIndex = rowIndex;
    this.values = values;
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
}
