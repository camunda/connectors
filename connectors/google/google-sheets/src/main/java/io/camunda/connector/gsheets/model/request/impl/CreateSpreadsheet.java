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

public class CreateSpreadsheet extends Input {

  @NotBlank @Secret private String spreadsheetName;
  @Secret private String parent;

  public CreateSpreadsheet() {}

  public CreateSpreadsheet(String spreadsheetName, String parent) {
    this.spreadsheetName = spreadsheetName;
    this.parent = parent;
  }

  public String getSpreadsheetName() {
    return spreadsheetName;
  }

  public void setSpreadsheetName(String spreadsheetName) {
    this.spreadsheetName = spreadsheetName;
  }

  public String getParent() {
    return parent;
  }

  public void setParent(String parent) {
    this.parent = parent;
  }
}
