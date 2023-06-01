/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.operation.impl;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import io.camunda.connector.gsheets.model.request.impl.CreateSpreadsheet;
import io.camunda.connector.gsheets.model.response.CreateSpreadSheetResponse;
import io.camunda.connector.gsheets.operation.GoogleSheetOperation;
import io.camunda.connector.gsheets.supplier.GoogleSheetsServiceSupplier;
import io.camunda.google.DriveUtil;
import io.camunda.google.model.Authentication;
import java.io.IOException;

public class CreateSpreadSheetOperation extends GoogleSheetOperation {

  private final CreateSpreadsheet model;

  public CreateSpreadSheetOperation(CreateSpreadsheet model) {
    this.model = model;
  }

  @Override
  public Object execute(final Authentication auth) {
    Sheets service = GoogleSheetsServiceSupplier.getGoogleSheetsService(auth);
    Spreadsheet spreadsheet =
        new Spreadsheet()
            .setProperties(new SpreadsheetProperties().setTitle(model.getSpreadsheetName()));

    try {
      spreadsheet =
          service
              .spreadsheets()
              .create(spreadsheet)
              .setFields(buildFields(SPREADSHEET_ID_FIELD, SPREADSHEET_URL_FIELD))
              .execute();

      if (model.getParent() != null) {
        DriveUtil.moveFile(auth, model.getParent(), spreadsheet.getSpreadsheetId());
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return new CreateSpreadSheetResponse(
        spreadsheet.getSpreadsheetId(), spreadsheet.getSpreadsheetUrl());
  }
}
