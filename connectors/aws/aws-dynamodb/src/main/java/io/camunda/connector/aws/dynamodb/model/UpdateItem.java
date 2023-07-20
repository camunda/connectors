/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public final class UpdateItem extends TableOperation {
  @NotNull private Object primaryKeyComponents;

  @NotNull private Object keyAttributes;

  @NotBlank private String attributeAction;

  public Object getPrimaryKeyComponents() {
    return primaryKeyComponents;
  }

  public void setPrimaryKeyComponents(Object primaryKeyComponents) {
    this.primaryKeyComponents = primaryKeyComponents;
  }

  public Object getKeyAttributes() {
    return keyAttributes;
  }

  public void setKeyAttributes(final Object keyAttributes) {
    this.keyAttributes = keyAttributes;
  }

  public String getAttributeAction() {
    return attributeAction;
  }

  public void setAttributeAction(final String attributeAction) {
    this.attributeAction = attributeAction;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    UpdateItem that = (UpdateItem) o;
    return Objects.equals(primaryKeyComponents, that.primaryKeyComponents)
        && Objects.equals(keyAttributes, that.keyAttributes)
        && Objects.equals(attributeAction, that.attributeAction);
  }

  @Override
  public int hashCode() {
    return Objects.hash(primaryKeyComponents, keyAttributes, attributeAction);
  }

  @Override
  public String toString() {
    return "UpdateItem{"
        + "primaryKeyComponents="
        + primaryKeyComponents
        + ", keyAttributes="
        + keyAttributes
        + ", attributeAction='"
        + attributeAction
        + '\''
        + "} "
        + super.toString();
  }
}
