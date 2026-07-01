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
    id = "createWorksheet",
    label = "Create worksheet",
    description = "Add a new worksheet to an existing Google Sheets spreadsheet",
    keywords = {"create worksheet", "add sheet", "new worksheet", "new tab", "insert sheet"})
public record CreateWorksheet(
    @TemplateProperty(
            id = "createWorksheet.spreadsheetId",
            label = "Spreadsheet ID",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.spreadsheetId"))
        @NotBlank
        String spreadsheetId,
    @TemplateProperty(
            id = "createWorksheet.worksheetName",
            label = "Worksheet name",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.worksheetName"))
        @NotBlank
        String worksheetName,
    @TemplateProperty(
            label = "Worksheet index",
            tooltip =
                "Leave empty to add to the end of the sheet list. See the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/google-sheets/#what-is-a-worksheet-index\" target=\"_blank\">worksheet index documentation</a>",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.worksheetIndex"))
        Integer worksheetIndex)
    implements Input {}
