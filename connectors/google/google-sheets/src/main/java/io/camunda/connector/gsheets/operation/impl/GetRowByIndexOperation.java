/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.operation.impl;

import io.camunda.connector.gsheets.model.request.impl.GetRowByIndex;
import io.camunda.connector.gsheets.model.response.GoogleSheetsResult;
import io.camunda.connector.gsheets.operation.GoogleSheetOperation;
import io.camunda.google.model.Authentication;
import java.io.IOException;
import java.util.List;

public class GetRowByIndexOperation extends GoogleSheetOperation {

  private final GetRowByIndex model;

  public GetRowByIndexOperation(GetRowByIndex model) {
    this.model = model;
  }

  @Override
  public Object execute(Authentication auth) {
    String range = buildRange(model.getWorksheetName(), buildRowRange(model.getRowIndex()));

    try {
      List<List<Object>> values = this.get(auth, model.getSpreadsheetId(), range);

      if (values == null || values.isEmpty()) {
        return new GoogleSheetsResult("Get row by index", "OK", null);
      }

      return new GoogleSheetsResult("Get row by index", "OK", values.get(0));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
