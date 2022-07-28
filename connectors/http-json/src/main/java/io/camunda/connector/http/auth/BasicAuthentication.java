package io.camunda.connector.http.auth;

import com.google.api.client.http.HttpHeaders;
import com.google.common.base.Objects;
import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;

public class BasicAuthentication extends Authentication {
  private String username;
  private String password;

  @Override
  public void validateWith(final Validator validator) {
    validator.require(username, "Authentication - Username");
    validator.require(password, "Authentication - Password");
  }

  @Override
  public void replaceSecrets(final SecretStore secretStore) {
    username = secretStore.replaceSecret(username);
    password = secretStore.replaceSecret(password);
  }

  @Override
  public void setHeaders(final HttpHeaders headers) {
    headers.setBasicAuthentication(username, password);
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    BasicAuthentication that = (BasicAuthentication) o;
    return Objects.equal(username, that.username) && Objects.equal(password, that.password);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), username, password);
  }

  @Override
  public String toString() {
    return "BasicAuthentication {"
        + "username='[REDACTED]'"
        + ", password='[REDACTED]'"
        + "}; Super: "
        + super.toString();
  }
}
