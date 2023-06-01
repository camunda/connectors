/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.operation.impl;

import com.google.api.services.sheets.v4.model.ValueRange;
import io.camunda.connector.gsheets.model.request.impl.AddValues;
import io.camunda.connector.gsheets.model.response.GoogleSheetsResult;
import io.camunda.connector.gsheets.operation.GoogleSheetOperation;
import io.camunda.google.model.Authentication;
import java.io.IOException;
import java.util.List;

public class AddValuesOperation extends GoogleSheetOperation {

  private final AddValues model;

  public AddValuesOperation(AddValues model) {
    this.model = model;
  }

  @Override
  public Object execute(Authentication auth) {
    ValueRange valueRange = new ValueRange().setValues(List.of(List.of(model.getValue())));
    String range = buildRange(model.getWorksheetName(), model.getCellId());

    try {
      this.update(auth, model.getSpreadsheetId(), range, valueRange);

      return new GoogleSheetsResult("Add values to spreadsheet", "OK");

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
