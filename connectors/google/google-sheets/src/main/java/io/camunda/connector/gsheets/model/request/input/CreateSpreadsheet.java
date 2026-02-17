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

@TemplateSubType(id = "createSpreadsheet", label = "Create spreadsheet")
public record CreateSpreadsheet(
    @TemplateProperty(
            id = "createSpreadsheet.spreadsheetName",
            label = "Spreadsheet name",
            description = "Enter name for the new spreadsheet",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.spreadsheetName"))
        @NotBlank
        String spreadsheetName,
    @TemplateProperty(
            label = "Parent folder ID",
            description = "Enter ID of the parent folder where new spreadsheet will be created",
            group = "operationDetails",
            optional = true,
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.parent"))
        String parent)
    implements Input {}
