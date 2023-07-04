/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
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

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  // channel
  @JsonSubTypes.Type(value = AddValues.class, name = "addValues"),
  @JsonSubTypes.Type(value = CreateEmptyColumnOrRow.class, name = "createEmptyColumnOrRow"),
  @JsonSubTypes.Type(value = CreateRow.class, name = "createRow"),
  @JsonSubTypes.Type(value = CreateSpreadsheet.class, name = "createSpreadsheet"),
  @JsonSubTypes.Type(value = CreateWorksheet.class, name = "createWorksheet"),
  @JsonSubTypes.Type(value = DeleteColumn.class, name = "deleteColumn"),
  @JsonSubTypes.Type(value = DeleteWorksheet.class, name = "deleteWorksheet"),
  @JsonSubTypes.Type(value = GetRowByIndex.class, name = "getRowByIndex"),
  @JsonSubTypes.Type(value = GetSpreadsheetDetails.class, name = "spreadsheetsDetails"),
  @JsonSubTypes.Type(value = GetWorksheetData.class, name = "getWorksheetData")
})
public abstract class Input {}
