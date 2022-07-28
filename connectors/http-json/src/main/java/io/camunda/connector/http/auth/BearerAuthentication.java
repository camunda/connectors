package io.camunda.connector.http.auth;

import com.google.api.client.http.HttpHeaders;
import com.google.common.base.Objects;
import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;

public class BearerAuthentication extends Authentication {

  private String token;

  @Override
  public void setHeaders(final HttpHeaders headers) {
    headers.setAuthorization("Bearer " + token);
  }

  @Override
  public void validateWith(final Validator validator) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    BearerAuthentication that = (BearerAuthentication) o;
    return Objects.equal(token, that.token);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), token);
  }

  @Override
  public String toString() {
    return "BearerAuthentication{" + "token='[REDACTED]'" + "}; Super: " + super.toString();
  }
}
