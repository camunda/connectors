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

public class CreateWorksheet extends Input {

  @NotBlank @Secret private String spreadsheetId;
  @NotBlank @Secret private String worksheetName;
  private Integer worksheetIndex;

  public CreateWorksheet() {}

  public CreateWorksheet(String spreadsheetId, String worksheetName, Integer worksheetIndex) {
    this.spreadsheetId = spreadsheetId;
    this.worksheetName = worksheetName;
    this.worksheetIndex = worksheetIndex;
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

  public Integer getWorksheetIndex() {
    return worksheetIndex;
  }

  public void setWorksheetIndex(Integer worksheetIndex) {
    this.worksheetIndex = worksheetIndex;
  }
}
