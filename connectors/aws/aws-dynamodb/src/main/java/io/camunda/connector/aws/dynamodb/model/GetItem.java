/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model;

import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public final class GetItem extends TableOperation {
  @NotNull private Object primaryKeyComponents;

  public Object getPrimaryKeyComponents() {
    return primaryKeyComponents;
  }

  public void setPrimaryKeyComponents(final Object primaryKeyComponents) {
    this.primaryKeyComponents = primaryKeyComponents;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GetItem getItem = (GetItem) o;
    return Objects.equals(primaryKeyComponents, getItem.primaryKeyComponents);
  }

  @Override
  public int hashCode() {
    return Objects.hash(primaryKeyComponents);
  }

  @Override
  public String toString() {
    return "GetItem{" + "primaryKeyComponents=" + primaryKeyComponents + "} " + super.toString();
  }
}
