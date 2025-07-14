package io.camunda.connector.runtime.core.secret;

import io.camunda.connector.api.secret.SecretContext;

public interface SecretReplacer {
  String replaceSecrets(String name, SecretContext secretContext);
}
