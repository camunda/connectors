package io.camunda.connector.sendgrid;

import java.util.Objects;

public class SendGridEmail {

  private String name;
  private String email;

  public void replaceSecrets(final SecretStore secretStore) {
    name = secretStore.replaceSecret(name);
    email = secretStore.replaceSecret(email);
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(final String email) {
    this.email = email;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final SendGridEmail that = (SendGridEmail) o;
    return Objects.equals(name, that.name) && Objects.equals(email, that.email);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), name, email);
  }

  @Override
  public String toString() {
    return "SendGridEmail{" + "name='" + name + '\'' + ", email='" + email + '\'' + '}';
  }
}
