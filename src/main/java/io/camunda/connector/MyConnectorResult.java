package io.camunda.connector;

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
