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
import java.util.Map;

@TemplateSubType(id = OperationTypes.SCAN_TABLE)
public record ScanTable(
    @TemplateProperty(
            label = "Table name",
            id = "scanTable.tableName",
            group = "input",
            description = "Name of DynamoDB table")
        @NotBlank
        String tableName,
    @TemplateProperty(
            label = "Filter expression",
            group = "input",
            optional = true,
            description =
                "Filter expressions for scan. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-dynamodb/\" target=\"_blank\">documentation</a>")
        String filterExpression,
    @TemplateProperty(
            label = "Projection expression",
            group = "input",
            optional = true,
            description =
                "Is a string that identifies the attributes that you want. For multiple attributes, the names must be comma-separated")
        String projectionExpression,
    @TemplateProperty(
            label = "Expression attribute names",
            group = "input",
            feel = FeelMode.required,
            optional = true,
            description =
                "Is a placeholder that you use as an alternative to an actual attribute name. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-dynamodb/\" target=\"_blank\">documentation</a>")
        Map<String, String> expressionAttributeNames,
    @TemplateProperty(
            label = "Expression attribute values",
            group = "input",
            feel = FeelMode.required,
            optional = true,
            description =
                "Expression attribute values. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-dynamodb/\" target=\"_blank\">documentation</a>")
        Map<String, Object> expressionAttributeValues)
    implements TableInput {}
