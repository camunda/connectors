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

public class GetSpreadsheetDetails extends Input {

  @NotBlank @Secret private String spreadsheetId;

  public GetSpreadsheetDetails() {}

  public GetSpreadsheetDetails(String spreadsheetId) {
    this.spreadsheetId = spreadsheetId;
  }

  public String getSpreadsheetId() {
    return spreadsheetId;
  }

  public void setSpreadsheetId(String spreadsheetId) {
    this.spreadsheetId = spreadsheetId;
  }
}
