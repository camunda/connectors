/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model.item;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.aws.dynamodb.model.AwsInput;
import java.util.Objects;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class GetItem implements AwsInput {
  @NotBlank @Secret private String tableName;
  @NotNull @Secret private Object primaryKeyComponents;
  private transient String type;

  public String getTableName() {
    return tableName;
  }

  public void setTableName(final String tableName) {
    this.tableName = tableName;
  }

  public Object getPrimaryKeyComponents() {
    return primaryKeyComponents;
  }

  public void setPrimaryKeyComponents(final Object primaryKeyComponents) {
    this.primaryKeyComponents = primaryKeyComponents;
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
    final GetItem getItem = (GetItem) o;
    return Objects.equals(tableName, getItem.tableName)
        && Objects.equals(primaryKeyComponents, getItem.primaryKeyComponents)
        && Objects.equals(type, getItem.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tableName, primaryKeyComponents, type);
  }

  @Override
  public String toString() {
    return "GetItem{"
        + "tableName='"
        + tableName
        + "'"
        + ", primaryKeyComponents="
        + primaryKeyComponents
        + ", type='"
        + type
        + "'"
        + "}";
  }
}
