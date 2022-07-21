package io.camunda.connector.http.auth;

import com.google.api.client.http.HttpHeaders;
import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;

public abstract class Authentication {

  private transient String type;

  public abstract void validate(Validator validator);

  public abstract void replaceSecrets(final SecretStore secretStore);

  public abstract void setHeaders(HttpHeaders headers);
}
