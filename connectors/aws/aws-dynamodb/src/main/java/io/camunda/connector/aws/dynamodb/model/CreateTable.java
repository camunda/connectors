/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
@TemplateSubType(id = OperationTypes.CREATE_TABLE)
public record CreateTable(
    @TemplateProperty(
            label = "Table name",
            id = "createTable.tableName",
            group = "input",
            description = "Name of DynamoDB table")
        @NotBlank
        String tableName,
    @TemplateProperty(
            group = "input",
            description =
                "Partition key role. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/aws-dynamodb/\" target=\"_blank\">documentation</a>")
        @NotBlank
        String partitionKey,
    @TemplateProperty(
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "HASH", label = "HASH"),
              @TemplateProperty.DropdownPropertyChoice(value = "RANGE", label = "RANGE")
            },
            description =
                "The role that this key attribute will assume. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/aws-dynamodb/\" target=\"_blank\">documentation</a>")
        @NotBlank
        String partitionKeyRole,
    @TemplateProperty(
            label = "Partition key attribute data type",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "B", label = "Binary"),
              @TemplateProperty.DropdownPropertyChoice(value = "N", label = "Number"),
              @TemplateProperty.DropdownPropertyChoice(value = "S", label = "String")
            },
            description = "Represents the data for an attribute")
        @NotBlank
        String partitionKeyType,
    @TemplateProperty(
            label = "Sort key",
            group = "input",
            optional = true,
            description =
                "Sort key. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/aws-dynamodb/\" target=\"_blank\">documentation</a>")
        String sortKey,
    @TemplateProperty(
            label = "Sort key role",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            optional = true,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "HASH", label = "HASH"),
              @TemplateProperty.DropdownPropertyChoice(value = "RANGE", label = "RANGE")
            },
            description = "The role that this key attribute will assume")
        String sortKeyRole,
    @TemplateProperty(
            label = "Sort key attribute data type",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            optional = true,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "B", label = "Binary"),
              @TemplateProperty.DropdownPropertyChoice(value = "N", label = "Number"),
              @TemplateProperty.DropdownPropertyChoice(value = "S", label = "String")
            },
            description = "Represents the data for an attribute")
        String sortKeyType,
    @TemplateProperty(
            label = "Read capacity units",
            group = "input",
            description =
                "Total number of read capacity units. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/aws-dynamodb/\" target=\"_blank\">documentation</a>")
        @NotNull
        Long readCapacityUnits,
    @TemplateProperty(
            label = "Write capacity units",
            group = "input",
            description =
                "Total number of write capacity units. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/aws-dynamodb/\" target=\"_blank\">documentation</a>")
        @NotNull
        Long writeCapacityUnits,
    @TemplateProperty(
            label = "Billing mode",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  value = "PROVISIONED",
                  label = "PROVISIONED"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "PAY_PER_REQUEST",
                  label = "PAY_PER_REQUEST")
            },
            description =
                "Controls how you are charged for read and write throughput. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/aws-dynamodb/\" target=\"_blank\">documentation</a>")
        @NotBlank
        String billingModeStr,
    @TemplateProperty(
            label = "Deletion protection",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "true", label = "True"),
              @TemplateProperty.DropdownPropertyChoice(value = "false", label = "False")
            },
            defaultValue = "false",
            description = "Prevents accidental table deletion")
        @NotNull
        boolean deletionProtection)
    implements TableInput {}
