/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@TemplateSubType(id = OperationTypes.UPDATE_ITEM)
public record UpdateItem(
    @TemplateProperty(
            label = "Table name",
            id = "updateTable.tableName",
            group = "input",
            description = "Name of DynamoDB table")
        @NotBlank
        String tableName,
    @TemplateProperty(
            label = "Primary key components",
            id = "updateItem.primaryKeyComponents",
            group = "input",
            feel = FeelMode.required,
            description = "Simple or composite primary key")
        @NotNull
        Map<String, Object> primaryKeyComponents,
    @TemplateProperty(
            label = "Key attributes",
            group = "input",
            feel = FeelMode.required,
            description =
                "DynamoDB key attributes. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-dynamodb/\" target=\"_blank\">documentation</a>")
        @NotNull
        Map<String, Object> keyAttributes,
    @TemplateProperty(
            label = "Attribute action",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "put", label = "PUT"),
              @TemplateProperty.DropdownPropertyChoice(value = "delete", label = "DELETE")
            },
            description = "Specifies how to perform the update")
        @NotBlank
        String attributeAction)
    implements ItemInput {}
