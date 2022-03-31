package io.camunda.connector.sendgrid;

import com.sendgrid.helpers.mail.objects.Email;
import java.util.Objects;

public class SendGridRequest {
  private String clusterId;
  private String apiKey;
  private SendGridEmail from;
  private SendGridEmail to;
  private SendGridTemplate template;
  private SendGridContent content;

  public void validate(final Validator validator) {
    validator.require(clusterId, "Cluster ID");
    validator.require(apiKey, "SendGrid API - SendGrid API Key");
    validator.require(from, "Sender");
    if (from != null) {
      from.validate(validator, "Sender");
    }
    validator.require(to, "Receiver");
    if (to != null) {
      to.validate(validator, "Receiver");
    }

    // at least one of them should be set
    if (!hasContent() && !hasTemplate()) {
      validator.require(null, "Email Content");
    }

    if (hasTemplate()) {
      template.validate(validator);
    }
    if (hasContent()) {
      content.validate(validator);
    }
  }

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

  public String getClusterId() {
    return clusterId;
  }

  public void setClusterId(final String clusterId) {
    this.clusterId = clusterId;
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
    return Objects.equals(clusterId, that.clusterId)
        && Objects.equals(apiKey, that.apiKey)
        && Objects.equals(from, that.from)
        && Objects.equals(to, that.to)
        && Objects.equals(template, that.template)
        && Objects.equals(content, that.content);
  }

  @Override
  public int hashCode() {
    return Objects.hash(clusterId, apiKey, from, to, template, content);
  }

  @Override
  public String toString() {
    return "SendGridRequest{"
        + "clusterId='"
        + clusterId
        + '\''
        + ", apiKey='"
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
