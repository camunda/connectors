package io.camunda.connector.http;

import com.google.api.client.http.HttpHeaders;
import java.util.Objects;

public class BearerAuthentication extends Authentication {
  private String token;

  public String getToken() {
    return token;
  }

  public void setToken(final String token) {
    this.token = token;
  }

  @Override
  void setHeaders(final HttpHeaders headers) {
    headers.setAuthorization("Bearer " + token);
  }

  @Override
  void validate(final Validator validator) {
    validator.require(token, "Authentication - Bearer Token");
  }

  @Override
  void replaceSecrets(final SecretStore secretStore) {
    token = secretStore.replaceSecret(token);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BearerAuthentication that = (BearerAuthentication) o;
    return Objects.equals(token, that.token);
  }

  @Override
  public int hashCode() {
    return Objects.hash(token);
  }

  @Override
  public String toString() {
    return "BearerAuthentication{" + "token='" + token + '\'' + '}';
  }
}
