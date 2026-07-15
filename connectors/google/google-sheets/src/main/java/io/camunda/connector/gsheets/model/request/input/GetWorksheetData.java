/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request.input;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyBinding;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(
    id = "getWorksheetData",
    label = "Get worksheet data",
    description = "Retrieve all data from a Google Sheets worksheet",
    keywords = {
      "get worksheet data",
      "read sheet",
      "export data",
      "fetch sheet data",
      "retrieve sheet rows"
    })
public record GetWorksheetData(
    @TemplateProperty(
            id = "getWorksheetData.spreadsheetId",
            label = "Spreadsheet ID",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.spreadsheetId"))
        @NotBlank
        String spreadsheetId,
    @TemplateProperty(
            id = "getWorksheetData.worksheetName",
            label = "Worksheet name",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.worksheetName"))
        @NotBlank
        String worksheetName)
    implements Input {}
