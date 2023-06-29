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

public class DeleteWorksheet extends Input {

  @NotBlank @Secret private String spreadsheetId;
  @NotNull private Integer worksheetId;

  public DeleteWorksheet() {}

  public DeleteWorksheet(String spreadsheetId, Integer worksheetId) {
    this.spreadsheetId = spreadsheetId;
    this.worksheetId = worksheetId;
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
}
