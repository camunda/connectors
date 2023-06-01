/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.supplier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
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

public class GsonSheetsComponentSupplier {
  private static final Gson GSON =
      new GsonBuilder()
          .serializeNulls()
          .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
          .registerTypeAdapterFactory(
              RuntimeTypeAdapterFactory.of(Input.class, "type")
                  .registerSubtype(CreateSpreadsheet.class, "createSpreadsheet")
                  .registerSubtype(CreateWorksheet.class, "createWorksheet")
                  .registerSubtype(GetSpreadsheetDetails.class, "spreadsheetsDetails")
                  .registerSubtype(DeleteWorksheet.class, "deleteWorksheet")
                  .registerSubtype(AddValues.class, "addValues")
                  .registerSubtype(CreateRow.class, "createRow")
                  .registerSubtype(GetRowByIndex.class, "getRowByIndex")
                  .registerSubtype(GetWorksheetData.class, "getWorksheetData")
                  .registerSubtype(DeleteColumn.class, "deleteColumn")
                  .registerSubtype(CreateEmptyColumnOrRow.class, "createEmptyColumnOrRow"))
          .create();

  private GsonSheetsComponentSupplier() {}

  public static Gson gsonInstance() {
    return GSON;
  }
}
