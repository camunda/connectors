/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.operation.impl;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import io.camunda.connector.gsheets.model.request.impl.GetSpreadsheetDetails;
import io.camunda.connector.gsheets.operation.GoogleSheetOperation;
import io.camunda.connector.gsheets.supplier.GoogleSheetsServiceSupplier;
import io.camunda.google.model.Authentication;
import java.io.IOException;

public class GetSpreadsheetDetailsOperation extends GoogleSheetOperation {

  private final GetSpreadsheetDetails model;

  public GetSpreadsheetDetailsOperation(GetSpreadsheetDetails model) {
    this.model = model;
  }

  @Override
  public Object execute(Authentication auth) {
    Sheets service = GoogleSheetsServiceSupplier.getGoogleSheetsService(auth);

    try {
      Spreadsheet spreadsheet = service.spreadsheets().get(model.getSpreadsheetId()).execute();

      return spreadsheet.getProperties();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
