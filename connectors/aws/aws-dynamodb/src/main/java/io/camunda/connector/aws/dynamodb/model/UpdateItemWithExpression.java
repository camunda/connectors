/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@TemplateSubType(id = OperationTypes.UPDATE_ITEM_WITH_EXPRESSION, label = "Update item")
public record UpdateItemWithExpression(
    @TemplateProperty(
            label = "Table name",
            id = "updateItem.tableName",
            group = "input",
            description = "Name of DynamoDB table")
        @NotBlank
        String tableName,
    @TemplateProperty(
            label = "Primary key components",
            id = "updateItemWithExpression.primaryKeyComponents",
            group = "input",
            feel = Property.FeelMode.required,
            description = "Simple or composite primary key")
        @NotNull
        Map<String, Object> primaryKeyComponents,
    @TemplateProperty(
            label = "Update Expression",
            group = "input",
            description =
                "String defining how item attributes are updated, specifying which attributes and their new values")
        @NotBlank
        String updateExpression,
    @TemplateProperty(
            id = "updateItemWithExpression.expressionAttributeNames",
            label = "Expression attribute values",
            group = "input",
            optional = true,
            feel = Property.FeelMode.required,
            description =
                "Optional map for aliasing attribute names in the update expression to avoid conflicts with DynamoDB reserved words. Use when attribute names overlap with reserved words or to enhance readability")
        Map<String, String> expressionAttributeNames,
    @TemplateProperty(
            id = "updateItemWithExpression.expressionAttributeValues",
            label = "Expression attribute values",
            group = "input",
            feel = Property.FeelMode.required,
            description =
                "Map of placeholders in the update expression to their actual values. Essential for dynamically setting attribute values at runtime. Keys start with ':'")
        @NotNull
        Map<String, Map<String, Object>> expressionAttributeValues,
    @TemplateProperty(
            label = "Attribute action",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "NONE", label = "NONE"),
              @TemplateProperty.DropdownPropertyChoice(value = "ALL_OLD", label = "ALL_OLD"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "UPDATED_OLD",
                  label = "UPDATED_OLD"),
              @TemplateProperty.DropdownPropertyChoice(value = "ALL_NEW", label = "ALL_NEW"),
              @TemplateProperty.DropdownPropertyChoice(value = "UPDATED_NEW", label = "UPDATED_NEW")
            },
            description =
                "Specifies returned data.  Details in the <a href=\"https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_UpdateItem.html\" target=\"_blank\">documentation</a>")
        @NotBlank
        String returnValues)
    implements ItemInput {}
