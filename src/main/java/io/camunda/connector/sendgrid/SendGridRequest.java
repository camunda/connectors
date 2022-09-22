/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sendgrid;

import com.sendgrid.helpers.mail.objects.Email;
import io.camunda.connector.api.ConnectorInput;
import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;
import java.util.Objects;

public class SendGridRequest implements ConnectorInput {

  private String apiKey;
  private SendGridEmail from;
  private SendGridEmail to;
  private SendGridTemplate template;
  private SendGridContent content;

  @Override
  public void validateWith(final Validator validator) {
    validator.require(apiKey, "SendGrid API - SendGrid API Key");
    validator.require(from, "Sender");
    if (from != null) {
      from.validateWith(validator, "Sender");
    }
    validator.require(to, "Receiver");
    if (to != null) {
      to.validateWith(validator, "Receiver");
    }

    // at least one of them should be set
    if (!hasContent() && !hasTemplate()) {
      validator.require(null, "Email Content");
    }

    if (hasTemplate()) {
      template.validateWith(validator);
    }
    if (hasContent()) {
      content.validateWith(validator);
    }
  }

  @Override
  public void replaceSecrets(final SecretStore secretStore) {
    apiKey = secretStore.replaceSecret(apiKey);
    from.replaceSecrets(secretStore);
    to.replaceSecrets(secretStore);

    if (hasTemplate()) {
      template.replaceSecrets(secretStore);
    }
    if (hasContent()) {
      content.replaceSecrets(secretStore);
    }
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(final String apiKey) {
    this.apiKey = apiKey;
  }

  public Email getFrom() {
    return new Email(from.getEmail(), from.getName());
  }

  public void setFrom(final SendGridEmail from) {
    this.from = from;
  }

  public Email getTo() {
    return new Email(to.getEmail(), to.getName());
  }

  public void setTo(final SendGridEmail to) {
    this.to = to;
  }

  public SendGridTemplate getTemplate() {
    return template;
  }

  public void setTemplate(final SendGridTemplate template) {
    this.template = template;
  }

  public boolean hasTemplate() {
    return template != null;
  }

  public SendGridContent getContent() {
    return content;
  }

  public void setContent(final SendGridContent content) {
    this.content = content;
  }

  public boolean hasContent() {
    return content != null;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SendGridRequest that = (SendGridRequest) o;
    return Objects.equals(apiKey, that.apiKey)
        && Objects.equals(from, that.from)
        && Objects.equals(to, that.to)
        && Objects.equals(template, that.template)
        && Objects.equals(content, that.content);
  }

  @Override
  public int hashCode() {
    return Objects.hash(apiKey, from, to, template, content);
  }

  @Override
  public String toString() {
    return "SendGridRequest{"
        + "apiKey='"
        + apiKey
        + '\''
        + ", from="
        + from
        + ", to="
        + to
        + ", template="
        + template
        + ", content="
        + content
        + '}';
  }
}
