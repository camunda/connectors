/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.operation.impl;

import com.google.api.services.sheets.v4.model.ValueRange;
import io.camunda.connector.gsheets.model.request.input.CreateRow;
import io.camunda.connector.gsheets.model.response.GoogleSheetsResult;
import io.camunda.connector.gsheets.operation.GoogleSheetOperation;
import io.camunda.google.model.Authentication;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class CreateRowOperation extends GoogleSheetOperation {

  private final CreateRow model;

  public CreateRowOperation(CreateRow model) {
    this.model = model;
  }

  @Override
  public Object execute(Authentication auth) {
    ValueRange valueRange = new ValueRange().setValues(List.of(model.values()));

    try {
      if (Objects.isNull(model.rowIndex())) {
        String range = buildRange(model.worksheetName(), "1:" + Integer.MAX_VALUE);
        this.append(auth, model.spreadsheetId(), model.worksheetName(), valueRange);
      } else {
        String range = buildRange(model.worksheetName(), buildRowRange(model.rowIndex()));
        this.update(auth, model.spreadsheetId(), range, valueRange);
      }
      return new GoogleSheetsResult("Create row", "OK");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
