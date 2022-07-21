package io.camunda.connector.http.auth;

import com.google.api.client.http.HttpHeaders;
import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;

public class BasicAuthentication extends Authentication {
  private String username;
  private String password;

  @Override
  public void validate(final Validator validator) {
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

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
