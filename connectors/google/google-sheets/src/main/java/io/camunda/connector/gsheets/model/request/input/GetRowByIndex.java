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
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "getRowByIndex", label = "Get row by index")
public record GetRowByIndex(
    @TemplateProperty(
            id = "getRowByIndex.spreadsheetId",
            label = "Spreadsheet ID",
            description = "Enter the ID of the spreadsheet",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.spreadsheetId"))
        @NotBlank
        String spreadsheetId,
    @TemplateProperty(
            id = "getRowByIndex.worksheetName",
            label = "Worksheet name",
            description = "Enter name for the worksheet",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.worksheetName"))
        String worksheetName,
    @TemplateProperty(
            id = "getRowByIndex.rowIndex",
            label = "Row index",
            description =
                "Enter row index. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/google-sheets/#what-is-a-row-index\" target=\"_blank\">documentation</a>",
            group = "operationDetails",
            feel = FeelMode.optional,
            constraints = @PropertyConstraints(notEmpty = true),
            binding = @PropertyBinding(name = "operation.rowIndex"))
        @NotNull
        Integer rowIndex)
    implements Input {}
