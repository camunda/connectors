package io.camunda.connector.http.auth;

import com.google.api.client.http.HttpHeaders;
import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;

public class BearerAuthentication extends Authentication {

  private String token;

  @Override
  public void setHeaders(final HttpHeaders headers) {
    headers.setAuthorization("Bearer " + token);
  }

  @Override
  public void validate(final Validator validator) {
    validator.require(token, "Authentication - Bearer Token");
  }

  @Override
  public void replaceSecrets(final SecretStore secretStore) {
    token = secretStore.replaceSecret(token);
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }
}
