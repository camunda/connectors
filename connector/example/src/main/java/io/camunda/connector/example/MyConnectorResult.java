/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.example;

import java.util.Objects;

public class MyConnectorResult {

  // TODO: define connector result properties, which are returned to the process engine
  private String myProperty;

  public String getMyProperty() {
    return myProperty;
  }

  public void setMyProperty(String myProperty) {
    this.myProperty = myProperty;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MyConnectorResult that = (MyConnectorResult) o;
    return Objects.equals(myProperty, that.myProperty);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myProperty);
  }

  @Override
  public String toString() {
    return "MyConnectorResult{" + "myProperty='" + myProperty + '\'' + '}';
  }
}
