/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sendgrid;

import com.sendgrid.helpers.mail.objects.Email;
import io.camunda.connector.api.annotation.Secret;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class SendGridRequest {
  @NotEmpty @Secret private String apiKey;
  @Valid @NotNull @Secret private SendGridEmail from;
  @Valid @NotNull @Secret private SendGridEmail to;
  @Valid @Secret private SendGridTemplate template;
  @Valid @Secret private SendGridContent content;

  @AssertTrue
  private boolean isHasContentOrTemplate() {
    return content != null || template != null;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(final String apiKey) {
    this.apiKey = apiKey;
  }

  public Email getInnerSenGridEmailFrom() {
    return new Email(from.getEmail(), from.getName());
  }

  public Email getInnerSenGridEmailTo() {
    return new Email(to.getEmail(), to.getName());
  }

  public void setFrom(final SendGridEmail from) {
    this.from = from;
  }

  public SendGridEmail getFrom() {
    return from;
  }

  public SendGridEmail getTo() {
    return to;
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
