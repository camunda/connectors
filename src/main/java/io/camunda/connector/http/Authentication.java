package io.camunda.connector.http;

import com.google.api.client.http.HttpHeaders;
import io.camunda.connector.sdk.common.SecretStore;

public abstract class Authentication {
  private transient String type;

  abstract void validate(Validator validator);

  abstract void replaceSecrets(final SecretStore secretStore);

  abstract void setHeaders(HttpHeaders headers);
}
