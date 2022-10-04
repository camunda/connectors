/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector;

import io.camunda.connector.api.annotation.Secret;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class MyConnectorRequest {

  @NotEmpty private String myProperty;

  @Valid @NotNull @Secret private Authentication authentication;

  // TODO: add request properties

  public String getMyProperty() {
    return myProperty;
  }

  public void setMyProperty(final String myProperty) {
    this.myProperty = myProperty;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MyConnectorRequest that = (MyConnectorRequest) o;
    return myProperty.equals(that.myProperty) && authentication.equals(that.authentication);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myProperty, authentication);
  }

  @Override
  public String toString() {
    return "MyConnectorRequest{"
        + "myProperty='"
        + myProperty
        + '\''
        + ", authentication="
        + authentication
        + '}';
  }
}
