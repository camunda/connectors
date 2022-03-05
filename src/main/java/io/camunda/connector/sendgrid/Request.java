package io.camunda.connector.sendgrid;

import com.google.gson.annotations.Since;

public class Request {
  @Since(0.1)
  String clusterId;

  @Since(0.1)
  String apiKey;

  @Since(0.1)
  String fromName;

  @Since(0.1)
  String fromEmail;

  @Since(0.1)
  String toName;

  @Since(0.1)
  String toEmail;

  @Since(0.1)
  Template template;

  @Since(0.1)
  Content content;

  public void replaceSecrets(final SecretStore secretStore) {
    apiKey = secretStore.replaceSecret(apiKey);
    fromName = secretStore.replaceSecret(fromName);
    fromEmail = secretStore.replaceSecret(fromEmail);
    toName = secretStore.replaceSecret(toName);
    toEmail = secretStore.replaceSecret(toEmail);
    if (hasTemplate()) {
      template.replaceSecrets(secretStore);
    }
    if (hasContent()) {
      content.replaceSecrets(secretStore);
    }
  }

  public boolean hasTemplate() {
    return template != null;
  }

  public boolean hasContent() {
    return content != null;
  }
}
