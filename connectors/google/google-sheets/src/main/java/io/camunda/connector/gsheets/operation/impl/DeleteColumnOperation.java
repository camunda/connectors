/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.operation.impl;

import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.DeleteDimensionRequest;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.Request;
import io.camunda.connector.gsheets.model.request.ColumnIndexType;
import io.camunda.connector.gsheets.model.request.Dimension;
import io.camunda.connector.gsheets.model.request.impl.DeleteColumn;
import io.camunda.connector.gsheets.model.response.GoogleSheetsResult;
import io.camunda.connector.gsheets.operation.GoogleSheetOperation;
import io.camunda.connector.gsheets.util.LetterNumericSystemConverter;
import io.camunda.google.model.Authentication;
import java.io.IOException;
import java.util.List;

public class DeleteColumnOperation extends GoogleSheetOperation {

  private final DeleteColumn model;

  public DeleteColumnOperation(DeleteColumn model) {
    this.model = model;
  }

  @Override
  public Object execute(Authentication auth) {
    int index = getIndex();

    DimensionRange dimensionRange =
        new DimensionRange()
            .setDimension(Dimension.COLUMNS.getValue())
            .setSheetId(model.getWorksheetId())
            .setStartIndex(index)
            .setEndIndex(index + 1);
    Request request =
        new Request().setDeleteDimension(new DeleteDimensionRequest().setRange(dimensionRange));
    BatchUpdateSpreadsheetRequest updateRequest =
        new BatchUpdateSpreadsheetRequest().setRequests(List.of(request));

    try {
      this.batchUpdate(auth, model.getSpreadsheetId(), updateRequest);

      return new GoogleSheetsResult("Delete column", "OK");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private int getIndex() {
    if (ColumnIndexType.LETTERS.equals(model.getColumnIndexType())) {
      return LetterNumericSystemConverter.spreadsheetLetterToNumericIndex(
          model.getColumnLetterIndex());
    }

    return model.getColumnNumberIndex();
  }
}
