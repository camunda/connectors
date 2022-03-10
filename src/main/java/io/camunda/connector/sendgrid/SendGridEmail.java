package io.camunda.connector.sendgrid;

import com.sendgrid.helpers.mail.objects.Email;

public class SendGridEmail extends Email {

  public void replaceSecrets(final SecretStore secretStore) {
    setName(secretStore.replaceSecret(getName()));
    setEmail(secretStore.replaceSecret(getEmail()));
  }
}
