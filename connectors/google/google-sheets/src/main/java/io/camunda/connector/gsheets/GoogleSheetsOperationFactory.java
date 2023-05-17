/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets;

import io.camunda.connector.gsheets.model.request.Input;
import io.camunda.connector.gsheets.model.request.impl.AddValues;
import io.camunda.connector.gsheets.model.request.impl.CreateEmptyColumnOrRow;
import io.camunda.connector.gsheets.model.request.impl.CreateRow;
import io.camunda.connector.gsheets.model.request.impl.CreateSpreadsheet;
import io.camunda.connector.gsheets.model.request.impl.CreateWorksheet;
import io.camunda.connector.gsheets.model.request.impl.DeleteColumn;
import io.camunda.connector.gsheets.model.request.impl.DeleteWorksheet;
import io.camunda.connector.gsheets.model.request.impl.GetRowByIndex;
import io.camunda.connector.gsheets.model.request.impl.GetSpreadsheetDetails;
import io.camunda.connector.gsheets.model.request.impl.GetWorksheetData;
import io.camunda.connector.gsheets.operation.GoogleSheetOperation;
import io.camunda.connector.gsheets.operation.impl.AddValuesOperation;
import io.camunda.connector.gsheets.operation.impl.CreateEmptyColumnOrRowOperation;
import io.camunda.connector.gsheets.operation.impl.CreateRowOperation;
import io.camunda.connector.gsheets.operation.impl.CreateSpreadSheetOperation;
import io.camunda.connector.gsheets.operation.impl.CreateWorksheetOperation;
import io.camunda.connector.gsheets.operation.impl.DeleteColumnOperation;
import io.camunda.connector.gsheets.operation.impl.DeleteWorksheetOperation;
import io.camunda.connector.gsheets.operation.impl.GetRowByIndexOperation;
import io.camunda.connector.gsheets.operation.impl.GetSpreadsheetDetailsOperation;
import io.camunda.connector.gsheets.operation.impl.GetWorksheetDataOperation;

public class GoogleSheetsOperationFactory {

  private static final GoogleSheetsOperationFactory instance = new GoogleSheetsOperationFactory();

  private GoogleSheetsOperationFactory() {}

  public static GoogleSheetsOperationFactory getInstance() {
    return instance;
  }

  public GoogleSheetOperation createOperation(Input input) {
    GoogleSheetOperation operation;

    if (input instanceof CreateSpreadsheet in) {
      operation = new CreateSpreadSheetOperation(in);
    } else if (input instanceof CreateWorksheet in) {
      operation = new CreateWorksheetOperation(in);
    } else if (input instanceof GetSpreadsheetDetails in) {
      operation = new GetSpreadsheetDetailsOperation(in);
    } else if (input instanceof DeleteWorksheet in) {
      operation = new DeleteWorksheetOperation(in);
    } else if (input instanceof AddValues in) {
      operation = new AddValuesOperation(in);
    } else if (input instanceof CreateRow in) {
      operation = new CreateRowOperation(in);
    } else if (input instanceof GetRowByIndex in) {
      operation = new GetRowByIndexOperation(in);
    } else if (input instanceof GetWorksheetData in) {
      operation = new GetWorksheetDataOperation(in);
    } else if (input instanceof DeleteColumn in) {
      operation = new DeleteColumnOperation(in);
    } else if (input instanceof CreateEmptyColumnOrRow in) {
      operation = new CreateEmptyColumnOrRowOperation(in);
    } else {
      throw new UnsupportedOperationException("Unsupported operation : [" + input.getClass() + "]");
    }

    return operation;
  }
}
