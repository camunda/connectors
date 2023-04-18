/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model.table;

import io.camunda.connector.aws.model.AwsInput;
import java.util.Objects;
import javax.validation.constraints.NotBlank;

public class DescribeTable implements AwsInput {
  @NotBlank private String tableName;
  private transient String type;

  public String getTableName() {
    return tableName;
  }

  public void setTableName(final String tableName) {
    this.tableName = tableName;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public void setType(final String type) {
    this.type = type;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DescribeTable that = (DescribeTable) o;
    return Objects.equals(tableName, that.tableName) && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tableName, type);
  }

  @Override
  public String toString() {
    return "DescribeTable{" + "tableName='" + tableName + "'" + ", type='" + type + "'" + "}";
  }
}
