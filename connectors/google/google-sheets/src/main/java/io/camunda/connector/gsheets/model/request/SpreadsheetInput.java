/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request;

import jakarta.validation.constraints.NotBlank;

public abstract class SpreadsheetInput extends Input {

  public SpreadsheetInput() {}

  public SpreadsheetInput(String spreadsheetId) {
    this.spreadsheetId = spreadsheetId;
  }

  @NotBlank private String spreadsheetId;

  public String getSpreadsheetId() {
    return spreadsheetId;
  }

  public void setSpreadsheetId(String spreadsheetId) {
    this.spreadsheetId = spreadsheetId;
  }

  @Override
  public String toString() {
    return "Input{" + "spreadsheetId='" + spreadsheetId + '\'' + '}';
  }
}
