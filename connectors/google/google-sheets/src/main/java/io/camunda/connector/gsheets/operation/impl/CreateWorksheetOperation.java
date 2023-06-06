/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.operation.impl;

import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.SheetProperties;
import io.camunda.connector.gsheets.model.request.impl.CreateWorksheet;
import io.camunda.connector.gsheets.model.response.GoogleSheetsResult;
import io.camunda.connector.gsheets.operation.GoogleSheetOperation;
import io.camunda.google.model.Authentication;
import java.io.IOException;
import java.util.List;

public class CreateWorksheetOperation extends GoogleSheetOperation {

  private final CreateWorksheet model;

  public CreateWorksheetOperation(CreateWorksheet model) {
    this.model = model;
  }

  @Override
  public Object execute(Authentication auth) {
    SheetProperties sheetProperties = new SheetProperties().setTitle(model.getWorksheetName());

    if (model.getWorksheetIndex() != null) {
      sheetProperties.setIndex(model.getWorksheetIndex());
    }

    Request request =
        new Request().setAddSheet(new AddSheetRequest().setProperties(sheetProperties));
    BatchUpdateSpreadsheetRequest updateRequest =
        new BatchUpdateSpreadsheetRequest().setRequests(List.of(request));

    try {
      this.batchUpdate(auth, model.getSpreadsheetId(), updateRequest);

      return new GoogleSheetsResult("Create worksheet", "OK");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
