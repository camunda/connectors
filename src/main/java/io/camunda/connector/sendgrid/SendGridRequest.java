package io.camunda.connector.sendgrid;

import com.google.gson.annotations.Since;
import java.util.Objects;

public class SendGridRequest {
  @Since(0.1)
  private String clusterId;

  @Since(0.1)
  private String apiKey;

  @Since(0.1)
  private String fromName;

  @Since(0.1)
  private String fromEmail;

  @Since(0.1)
  private String toName;

  @Since(0.1)
  private String toEmail;

  @Since(0.1)
  private SendGridTemplate template;

  @Since(0.1)
  private SendGridContent content;

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

  public String getFromName() {
    return fromName;
  }

  public void setFromName(final String fromName) {
    this.fromName = fromName;
  }

  public String getFromEmail() {
    return fromEmail;
  }

  public void setFromEmail(final String fromEmail) {
    this.fromEmail = fromEmail;
  }

  public String getToName() {
    return toName;
  }

  public void setToName(final String toName) {
    this.toName = toName;
  }

  public String getToEmail() {
    return toEmail;
  }

  public void setToEmail(final String toEmail) {
    this.toEmail = toEmail;
  }

  public SendGridTemplate getTemplate() {
    return template;
  }

  public void setTemplate(final SendGridTemplate template) {
    this.template = template;
  }

  public SendGridContent getContent() {
    return content;
  }

  public void setContent(final SendGridContent content) {
    this.content = content;
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
        && Objects.equals(fromName, that.fromName)
        && Objects.equals(fromEmail, that.fromEmail)
        && Objects.equals(toName, that.toName)
        && Objects.equals(toEmail, that.toEmail)
        && Objects.equals(template, that.template)
        && Objects.equals(content, that.content);
  }

  @Override
  public int hashCode() {
    return Objects.hash(clusterId, apiKey, fromName, fromEmail, toName, toEmail, template, content);
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
        + ", fromName='"
        + fromName
        + '\''
        + ", fromEmail='"
        + fromEmail
        + '\''
        + ", toName='"
        + toName
        + '\''
        + ", toEmail='"
        + toEmail
        + '\''
        + ", template="
        + template
        + ", content="
        + content
        + '}';
  }
}
