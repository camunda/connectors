package io.camunda.connector.sendgrid;

import com.google.gson.annotations.Since;
import java.util.Map;

public class Template {
  @Since(0.1)
  String id;

  @Since(0.1)
  Map<String, String> data;

  public void replaceSecrets(final SecretStore secretStore) {
    id = secretStore.replaceSecret(id);
    data.replaceAll((k, v) -> secretStore.replaceSecret(v));
  }
}
