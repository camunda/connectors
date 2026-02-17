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
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@TemplateSubType(id = "createRow", label = "Create row")
public record CreateRow(
    @TemplateProperty(
            id = "createRow.spreadsheetId",
            label = "Spreadsheet ID",
            description = "Enter the ID of the spreadsheet",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.spreadsheetId"))
        @NotBlank
        String spreadsheetId,
    @TemplateProperty(
            id = "createRow.worksheetName",
            label = "Worksheet name",
            description = "Enter name for the worksheet",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.worksheetName"))
        @NotBlank
        String worksheetName,
    @TemplateProperty(
            id = "createRow.rowIndex",
            label = "Row index",
            description =
                "Enter row index. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/google-sheets/#what-is-a-row-index\" target=\"_blank\">documentation</a>",
            group = "operationDetails",
            feel = FeelMode.optional,
            constraints =
                @PropertyConstraints(
                    pattern =
                        @TemplateProperty.Pattern(
                            value = "^(=.*|[0-9]+|)$",
                            message = "Must be a number")),
            optional = true,
            binding = @PropertyBinding(name = "operation.rowIndex"))
        Integer rowIndex,
    @TemplateProperty(
            label = "Enter values",
            description =
                "Enter the array of values. <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/google-sheets/#create-row\" target=\"_blank\">Learn more about the required format</a>",
            group = "operationDetails",
            feel = FeelMode.required,
            constraints = @PropertyConstraints(notEmpty = true),
            binding = @PropertyBinding(name = "operation.values"))
        @NotEmpty
        List<Object> values)
    implements Input {}
