package io.camunda.connector;

import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;
import java.util.Objects;

public class MyConnectorRequest {

  private String myProperty;

  // TODO: add request properties

  public void validate(final Validator validator) {
    validator.require(myProperty, "my property");
    // TODO: validate request properties
  }

  public void replaceSecrets(final SecretStore secretStore) {
    // TODO: replace secrets in request properties
    myProperty = secretStore.replaceSecret(myProperty);
  }

  public String getMyProperty() {
    return myProperty;
  }

  public void setMyProperty(final String myProperty) {
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
    final MyConnectorRequest that = (MyConnectorRequest) o;
    return Objects.equals(myProperty, that.myProperty);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myProperty);
  }

  @Override
  public String toString() {
    return "MyConnectorRequest{" + "myProperty='" + myProperty + '\'' + '}';
  }
}
