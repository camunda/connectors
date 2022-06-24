package io.camunda.connector.sendgrid;

import io.camunda.connector.sdk.common.SecretStore;
import io.camunda.connector.sdk.common.Validator;

import java.util.Map;
import java.util.Objects;

public class SendGridTemplate {
  private String id;
  private Map<String, String> data;

  public void validate(final Validator validator) {
    validator.require(id, "Dynamic Email Template - Template ID");
    validator.require(data, "Dynamic Email Template - Template Data");
  }

  public void replaceSecrets(final SecretStore secretStore) {
    id =
        secretStore.replaceSecret(
            Objects.requireNonNull(id, "Field 'template.id' required in request"));
    Objects.requireNonNull(data, "Field 'template.data' required in request")
        .replaceAll((k, v) -> secretStore.replaceSecret(v));
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public Map<String, String> getData() {
    return data;
  }

  public void setData(final Map<String, String> data) {
    this.data = data;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SendGridTemplate that = (SendGridTemplate) o;
    return Objects.equals(id, that.id) && Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, data);
  }

  @Override
  public String toString() {
    return "SendGridTemplate{" + "id='" + id + '\'' + ", data=" + data + '}';
  }
}
