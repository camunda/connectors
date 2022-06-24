package io.camunda.connector.http;

import com.google.api.client.http.HttpHeaders;
import io.camunda.connector.sdk.common.SecretStore;

import java.util.Objects;

public class BasicAuthentication extends Authentication {
  private String username;
  private String password;

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
  void validate(final Validator validator) {
    validator.require(username, "Authentication - Username");
    validator.require(password, "Authentication - Password");
  }

  @Override
  void replaceSecrets(final SecretStore secretStore) {
    username = secretStore.replaceSecret(username);
    password = secretStore.replaceSecret(password);
  }

  @Override
  void setHeaders(final HttpHeaders headers) {
    headers.setBasicAuthentication(username, password);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BasicAuthentication that = (BasicAuthentication) o;
    return Objects.equals(username, that.username) && Objects.equals(password, that.password);
  }

  @Override
  public int hashCode() {
    return Objects.hash(username, password);
  }

  @Override
  public String toString() {
    return "BasicAuthentication{"
        + "username='"
        + username
        + '\''
        + ", password='"
        + password
        + '\''
        + '}';
  }
}
