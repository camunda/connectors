/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.operation.impl;

import com.google.api.services.sheets.v4.model.AppendDimensionRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.InsertDimensionRequest;
import com.google.api.services.sheets.v4.model.Request;
import io.camunda.connector.gsheets.model.request.impl.CreateEmptyColumnOrRow;
import io.camunda.connector.gsheets.model.response.GoogleSheetsResult;
import io.camunda.connector.gsheets.operation.GoogleSheetOperation;
import io.camunda.google.model.Authentication;
import java.io.IOException;
import java.util.List;

public class CreateEmptyColumnOrRowOperation extends GoogleSheetOperation {

  private final CreateEmptyColumnOrRow model;

  public CreateEmptyColumnOrRowOperation(CreateEmptyColumnOrRow model) {
    this.model = model;
  }

  @Override
  public Object execute(Authentication auth) {
    Request request = new Request();

    if (isAppendRequest()) {
      request.setAppendDimension(
          new AppendDimensionRequest()
              .setSheetId(model.getWorksheetId())
              .setDimension(model.getDimension().getValue())
              .setLength(1));
    } else if (isOneOfTheIndexesEmpty()) {
      throw new IllegalArgumentException("Only both of the start and end indexes can be empty");
    } else {
      request.setInsertDimension(
          new InsertDimensionRequest()
              .setRange(
                  new DimensionRange()
                      .setSheetId(model.getWorksheetId())
                      .setDimension(model.getDimension().getValue())
                      .setStartIndex(model.getStartIndex())
                      .setEndIndex(model.getEndIndex())));
    }

    BatchUpdateSpreadsheetRequest updateRequest =
        new BatchUpdateSpreadsheetRequest().setRequests(List.of(request));

    try {
      this.batchUpdate(auth, model.getSpreadsheetId(), updateRequest);

      return new GoogleSheetsResult("Create empty column or row", "OK");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * There are two types of create empty column or row request: append insert. The append request
   * has empty both of start and end indexes. It appends empty column/row in the end of the sheet.
   * The insert request has both of start and end indexes. It inserts empty column/row in defined
   * places
   *
   * @return the boolean whether the request type is append or not
   */
  private boolean isAppendRequest() {
    return model.getStartIndex() == null && model.getEndIndex() == null;
  }

  private boolean isOneOfTheIndexesEmpty() {
    return (model.getStartIndex() == null && model.getEndIndex() != null)
        || (model.getStartIndex() != null && model.getEndIndex() == null);
  }
}
