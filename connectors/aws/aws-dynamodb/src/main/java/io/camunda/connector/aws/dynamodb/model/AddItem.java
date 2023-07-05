/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model;

import java.util.Objects;
import javax.validation.constraints.NotNull;

public final class AddItem extends TableOperation {
  @NotNull private Object item;

  public Object getItem() {
    return item;
  }

  public void setItem(final Object item) {
    this.item = item;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AddItem addItem = (AddItem) o;
    return Objects.equals(item, addItem.item);
  }

  @Override
  public String toString() {
    return "AddItem{" + "item=" + item + "} " + super.toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(item);
  }
}
