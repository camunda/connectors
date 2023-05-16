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

public class AddItem implements AwsInput {
  @NotBlank @Secret private String tableName;
  @NotNull @Secret private Object item;
  private transient String type;

  public String getTableName() {
    return tableName;
  }

  public void setTableName(final String tableName) {
    this.tableName = tableName;
  }

  public Object getItem() {
    return item;
  }

  public void setItem(final Object item) {
    this.item = item;
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
    final AddItem addItem = (AddItem) o;
    return Objects.equals(tableName, addItem.tableName)
        && Objects.equals(item, addItem.item)
        && Objects.equals(type, addItem.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tableName, item, type);
  }

  @Override
  public String toString() {
    return "AddItem{"
        + "tableName='"
        + tableName
        + "'"
        + ", item="
        + item
        + ", type='"
        + type
        + "'"
        + "}";
  }
}
