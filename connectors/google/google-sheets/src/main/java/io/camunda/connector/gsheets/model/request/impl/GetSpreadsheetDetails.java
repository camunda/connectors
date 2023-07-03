/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request.impl;

import io.camunda.connector.gsheets.model.request.SpreadsheetInput;

public class GetSpreadsheetDetails extends SpreadsheetInput {

  public GetSpreadsheetDetails() {}

  public GetSpreadsheetDetails(String spreadsheetId) {
    super(spreadsheetId);
  }

  @Override
  public String toString() {
    return "GetSpreadsheetDetails{} " + super.toString();
  }
}
