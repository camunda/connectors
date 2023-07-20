/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model;

import jakarta.validation.constraints.NotBlank;

public sealed class TableOperation implements AwsInput
    permits AddItem,
        CreateTable,
        DeleteItem,
        DeleteTable,
        DescribeTable,
        GetItem,
        UpdateItem,
        ScanTable {

  @NotBlank private String tableName;

  public String getTableName() {
    return tableName;
  }

  public void setTableName(final String tableName) {
    this.tableName = tableName;
  }
}
