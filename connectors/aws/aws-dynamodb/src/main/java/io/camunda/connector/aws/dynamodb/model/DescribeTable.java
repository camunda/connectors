/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(id = OperationTypes.DESCRIBE_TABLE)
public record DescribeTable(
    @TemplateProperty(
            label = "Table name",
            id = "describeTable.tableName",
            group = "input",
            description = "Name of DynamoDB table")
        @NotBlank
        String tableName)
    implements TableInput {}
