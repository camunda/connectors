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

@TemplateSubType(
    id = "deleteColumn",
    label = "Delete column",
    description = "Delete a column from a Google Sheets worksheet",
    keywords = {"delete column", "remove column", "drop column", "erase column"})
public record DeleteColumn(
    @TemplateProperty(
            id = "deleteColumn.spreadsheetId",
            label = "Spreadsheet ID",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.spreadsheetId"))
        @NotBlank
        String spreadsheetId,
    @TemplateProperty(
            id = "deleteColumn.worksheetId",
            label = "Worksheet ID",
            group = "operationDetails",
            feel = FeelMode.optional,
            constraints = @PropertyConstraints(notEmpty = true),
            binding = @PropertyBinding(name = "operation.worksheetId"))
        @NotNull
        Integer worksheetId,
    @TemplateProperty(
            label = "Index format",
            tooltip =
                "How the column to delete is identified: Numbers (numeric index at the top of the column, starting from 0) or Letters (the column letter). See <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/google-sheets/#how-can-i-define-which-column-will-be-deleted\" target=\"_blank\">defining which column will be deleted</a>.",
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
            tooltip =
                "Numeric position of the column to delete; count starts from 0 (column A is 0, B is 1). See <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/google-sheets/#how-can-i-define-which-column-will-be-deleted\" target=\"_blank\">defining which column will be deleted</a>.",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.columnNumberIndex"),
            condition = @PropertyCondition(property = "columnIndexType", equals = "NUMBERS"))
        Integer columnNumberIndex,
    @TemplateProperty(
            label = "Column letter index",
            tooltip =
                "Letter of the column to delete, as shown at the top of the column. See <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/google-sheets/#how-can-i-define-which-column-will-be-deleted\" target=\"_blank\">defining which column will be deleted</a>.",
            placeholder = "A",
            group = "operationDetails",
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "operation.columnLetterIndex"),
            condition = @PropertyCondition(property = "columnIndexType", equals = "LETTERS"))
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
