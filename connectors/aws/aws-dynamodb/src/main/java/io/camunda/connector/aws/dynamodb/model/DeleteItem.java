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

@TemplateSubType(id = OperationTypes.DELETE_ITEM)
public record DeleteItem(
    @TemplateProperty(
            label = "Table name",
            id = "deleteItem.tableName",
            group = "input",
            description = "Name of DynamoDB table")
        @NotBlank
        String tableName,
    @TemplateProperty(
            label = "Primary key components",
            id = "deleteItem.primaryKeyComponents",
            group = "input",
            feel = Property.FeelMode.required,
            description = "Simple or composite primary key")
        @NotNull
        Object primaryKeyComponents)
    implements ItemInput {}
