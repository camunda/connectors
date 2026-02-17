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
import io.camunda.connector.gsheets.model.request.Dimension;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "createEmptyColumnOrRow", label = "Create empty column or row")
public record CreateEmptyColumnOrRow(
    @TemplateProperty(
            id = "createEmptyColumnOrRow.spreadsheetId",
            label = "Spreadsheet ID",
            description = "Enter the ID of the spreadsheet",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.spreadsheetId"))
        @NotBlank
        String spreadsheetId,
    @TemplateProperty(
            id = "createEmptyColumnOrRow.worksheetId",
            label = "Worksheet ID",
            description = "Enter the ID of the worksheet",
            group = "operationDetails",
            feel = FeelMode.optional,
            constraints = @PropertyConstraints(notEmpty = true),
            binding = @PropertyBinding(name = "operation.worksheetId"))
        @NotNull
        Integer worksheetId,
    @TemplateProperty(
            label = "Dimension",
            description = "Choose what to add: column or row",
            group = "operationDetails",
            feel = FeelMode.optional,
            constraints = @PropertyConstraints(notEmpty = true),
            binding = @PropertyBinding(name = "operation.dimension"))
        @NotNull
        Dimension dimension,
    @TemplateProperty(
            label = "Start index",
            description =
                "Enter start index (leave empty if add to the end of the sheet). Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/google-sheets/#create-empty-column-or-row\" target=\"_blank\">documentation</a>",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.startIndex"))
        Integer startIndex,
    @TemplateProperty(
            label = "End index",
            description =
                "Enter end index (leave empty if add to the end of the sheet). Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/google-sheets/#create-empty-column-or-row\" target=\"_blank\">documentation</a>",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.endIndex"))
        Integer endIndex)
    implements Input {}
