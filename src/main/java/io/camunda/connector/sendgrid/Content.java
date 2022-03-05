package io.camunda.connector.sendgrid;

import com.google.gson.annotations.Since;

public class Content {
  @Since(0.1)
  String subject;

  @Since(0.1)
  String type;

  @Since(0.1)
  String value;

  public void replaceSecrets(final SecretStore secretStore) {
    subject = secretStore.replaceSecret(subject);
    type = secretStore.replaceSecret(type);
    value = secretStore.replaceSecret(value);
  }
}
