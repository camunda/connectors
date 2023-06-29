/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request.impl;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.gsheets.model.request.Input;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class AddValues extends Input {

  @NotBlank @Secret private String spreadsheetId;
  @Secret private String worksheetName;
  @NotBlank @Secret private String cellId;
  @NotNull @Secret private Object value;

  public AddValues() {}

  public AddValues(String spreadsheetId, String worksheetName, String cellId, Object value) {
    this.spreadsheetId = spreadsheetId;
    this.worksheetName = worksheetName;
    this.cellId = cellId;
    this.value = value;
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
}
