/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.operation.impl;

import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.DeleteSheetRequest;
import com.google.api.services.sheets.v4.model.Request;
import io.camunda.connector.gsheets.model.request.impl.DeleteWorksheet;
import io.camunda.connector.gsheets.model.response.GoogleSheetsResult;
import io.camunda.connector.gsheets.operation.GoogleSheetOperation;
import io.camunda.google.model.Authentication;
import java.io.IOException;
import java.util.List;

public class DeleteWorksheetOperation extends GoogleSheetOperation {

  private final DeleteWorksheet model;

  public DeleteWorksheetOperation(DeleteWorksheet model) {
    this.model = model;
  }

  @Override
  public Object execute(Authentication auth) {
    DeleteSheetRequest deleteSheetRequest =
        new DeleteSheetRequest().setSheetId(model.getWorksheetId());
    Request request = new Request().setDeleteSheet(deleteSheetRequest);
    BatchUpdateSpreadsheetRequest updateRequest =
        new BatchUpdateSpreadsheetRequest().setRequests(List.of(request));

    try {
      this.batchUpdate(auth, model.getSpreadsheetId(), updateRequest);

      return new GoogleSheetsResult("Delete worksheet", "OK");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
