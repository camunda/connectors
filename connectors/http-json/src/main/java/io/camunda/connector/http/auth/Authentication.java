package io.camunda.connector.http.auth;

import com.google.api.client.http.HttpHeaders;
import com.google.common.base.Objects;
import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;

public abstract class Authentication {

  private transient String type;

  public abstract void validate(Validator validator);

  public abstract void replaceSecrets(SecretStore secretStore);

  public abstract void setHeaders(HttpHeaders headers);

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Authentication that = (Authentication) o;
    return Objects.equal(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(type);
  }

  @Override
  public String toString() {
    return "Authentication{" + "type='" + type + '\'' + '}';
  }
}
