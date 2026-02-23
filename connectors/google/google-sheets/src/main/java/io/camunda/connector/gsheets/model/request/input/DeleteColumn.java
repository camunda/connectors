/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request.input;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyBinding;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.gsheets.model.request.ColumnIndexType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "deleteColumn", label = "Delete column")
public record DeleteColumn(
    @TemplateProperty(
            id = "deleteColumn.spreadsheetId",
            label = "Spreadsheet ID",
            description = "Enter the ID of the spreadsheet",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.spreadsheetId"))
        @NotBlank
        String spreadsheetId,
    @TemplateProperty(
            id = "deleteColumn.worksheetId",
            label = "Worksheet ID",
            description = "Enter the ID of the worksheet",
            group = "operationDetails",
            feel = FeelMode.optional,
            constraints = @PropertyConstraints(notEmpty = true),
            binding = @PropertyBinding(name = "operation.worksheetId"))
        @NotNull
        Integer worksheetId,
    @TemplateProperty(
            label = "Index format",
            description =
                "Choose the type of the index. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/google-sheets/#how-can-i-define-which-column-will-be-deleted\" target=\"_blank\">documentation</a>",
            group = "operationDetails",
            feel = FeelMode.optional,
            constraints = @PropertyConstraints(notEmpty = true),
            defaultValue = "NUMBERS",
            choices = {
              @DropdownPropertyChoice(label = "Numbers", value = "NUMBERS"),
              @DropdownPropertyChoice(label = "Letters", value = "LETTERS")
            },
            binding = @PropertyBinding(name = "operation.columnIndexType"))
        @NotNull
        ColumnIndexType columnIndexType,
    @TemplateProperty(
            label = "Column numeric index",
            description =
                "Enter the index of the column. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/google-sheets/#how-can-i-define-which-column-will-be-deleted\" target=\"_blank\">documentation</a>",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.columnNumberIndex"),
            condition =
                @PropertyCondition(property = "operation.columnIndexType", equals = "NUMBERS"))
        Integer columnNumberIndex,
    @TemplateProperty(
            label = "Column letter index",
            description =
                "Enter the index of the column. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/google-sheets/#how-can-i-define-which-column-will-be-deleted\" target=\"_blank\">documentation</a>",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.columnLetterIndex"),
            condition =
                @PropertyCondition(property = "operation.columnIndexType", equals = "LETTERS"))
        String columnLetterIndex)
    implements Input {

  @AssertTrue(message = "Column index cannot be blank")
  private boolean isColumnIndexValid() {
    if (ColumnIndexType.LETTERS.equals(this.columnIndexType)) {
      String index = this.columnLetterIndex;

      return !(index == null || index.trim().isEmpty());
    }

    return null != this.columnNumberIndex;
  }
}
