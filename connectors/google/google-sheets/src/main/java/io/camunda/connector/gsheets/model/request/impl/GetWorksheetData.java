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

public class GetWorksheetData extends Input {

  @NotBlank @Secret private String spreadsheetId;
  @NotBlank @Secret private String worksheetName;

  public GetWorksheetData() {}

  public GetWorksheetData(String spreadsheetId, String worksheetName) {
    this.spreadsheetId = spreadsheetId;
    this.worksheetName = worksheetName;
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
}
