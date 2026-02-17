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

@TemplateSubType(id = "createWorksheet", label = "Create worksheet")
public record CreateWorksheet(
    @TemplateProperty(
            id = "createWorksheet.spreadsheetId",
            label = "Spreadsheet ID",
            description = "Enter the ID of the spreadsheet",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.spreadsheetId"))
        @NotBlank
        String spreadsheetId,
    @TemplateProperty(
            id = "createWorksheet.worksheetName",
            label = "Worksheet name",
            description = "Enter name for the worksheet",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.worksheetName"))
        @NotBlank
        String worksheetName,
    @TemplateProperty(
            label = "Worksheet index",
            description =
                "Enter index of the place where to add worksheet (leave empty if add to the end of sheet list) Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/google-sheets/#what-is-a-worksheet-index\" target=\"_blank\">documentation</a>",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.worksheetIndex"))
        Integer worksheetIndex)
    implements Input {}
