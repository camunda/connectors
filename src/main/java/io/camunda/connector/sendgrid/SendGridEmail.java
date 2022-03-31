package io.camunda.connector.sendgrid;

import java.util.Objects;

public class SendGridEmail {

  private String name;
  private String email;

  public void replaceSecrets(final SecretStore secretStore, final String objectName) {
    email =
        secretStore.replaceSecret(
            Objects.requireNonNull(email, "Field '" + objectName + ".email' required in request"));
    name =
        secretStore.replaceSecret(
            Objects.requireNonNull(name, "Field '" + objectName + ".name' required in request"));
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
    final SendGridEmail that = (SendGridEmail) o;
    return Objects.equals(name, that.name) && Objects.equals(email, that.email);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, email);
  }

  @Override
  public String toString() {
    return "SendGridEmail{" + "name='" + name + '\'' + ", email='" + email + '\'' + '}';
  }
}
