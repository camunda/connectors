/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sendgrid.model;

import static io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType.Dropdown;
import static io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType.Text;

import com.sendgrid.helpers.mail.objects.Email;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyBinding;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public class SendGridRequest {
  @TemplateProperty(group = "authentication", label = "SendGrid API key")
  @NotEmpty
  private String apiKey;

  public record Sender(
      @TemplateProperty(
              group = "sender",
              label = "Name",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String name,
      @TemplateProperty(
              group = "sender",
              label = "Email address",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String email) {}

  @Valid @NotNull private Sender from;

  public record Recipient(
      @TemplateProperty(
              group = "receiver",
              label = "Name",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String name,
      @TemplateProperty(
              group = "receiver",
              label = "Email address",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String email) {}

  @Valid @NotNull private Recipient to;

  enum MailType {
    mail,
    byTemplate
  }

  @TemplateProperty(
      id = "unMappedFieldNotUseInModel.mailType",
      binding = @PropertyBinding(name = "unMappedFieldNotUseInModel.mailType"),
      label = "Mail contents",
      group = "content",
      type = Dropdown,
      choices = {
        @DropdownPropertyChoice(label = "Simple (no dynamic template)", value = "mail"),
        @DropdownPropertyChoice(label = "Using dynamic template", value = "byTemplate")
      })
  private MailType mailType;

  public record Template(
      @TemplateProperty(label = "Template ID") @NotEmpty String id,
      @TemplateProperty(label = "Template data", feel = FeelMode.required) @NotEmpty
          Map<String, Object> data) {}

  @NestedProperties(
      group = "content",
      condition =
          @PropertyCondition(
              property = "unMappedFieldNotUseInModel.mailType",
              equals = "byTemplate"))
  @Valid
  private Template template;

  public record Content(
      @TemplateProperty(label = "Subject") @NotEmpty String subject,
      @TemplateProperty(label = "Content type", defaultValue = "text/plain") @NotEmpty String type,
      @TemplateProperty(label = "Body", type = Text) @NotEmpty String value) {}

  @NestedProperties(
      group = "content",
      condition =
          @PropertyCondition(property = "unMappedFieldNotUseInModel.mailType", equals = "mail"))
  @Valid
  private Content content;

  @TemplateProperty(
      id = "attachments",
      group = "content",
      label = "attachments",
      optional = true,
      feel = FeelMode.required,
      description =
          "List of <a href=\"https://docs.camunda.io/docs/apis-tools/camunda-api-rest/specifications/upload-document-alpha/\">Camunda Documents</a>")
  private List<Document> attachments;

  @AssertTrue(message = "must not be empty")
  private boolean isSenderName() {
    return from != null && isNotBlank(from.name());
  }

  @AssertTrue(message = "must not be empty")
  private boolean isSenderEmail() {
    return from != null && isNotBlank(from.email());
  }

  @AssertTrue(message = "must not be empty")
  private boolean isReceiverName() {
    return to != null && isNotBlank(to.name());
  }

  @AssertTrue(message = "must not be empty")
  private boolean isReceiverEmail() {
    return to != null && isNotBlank(to.email());
  }

  private boolean isNotBlank(String str) {
    return str != null && !str.isBlank();
  }

  @AssertTrue
  private boolean isHasContentOrTemplate() {
    return content != null || template != null;
  }

  @AssertTrue(message = "each attached document must contain a file name")
  private boolean isAttachmentsShouldContainsFileName() {
    if (this.attachments == null || this.attachments.isEmpty()) {
      return true;
    }
    return this.attachments.stream()
        .map(doc -> doc.metadata().getFileName())
        .noneMatch(StringUtils::isBlank);
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(final String apiKey) {
    this.apiKey = apiKey;
  }

  public Email getInnerSenGridEmailFrom() {
    return new Email(from.email(), from.name());
  }

  public Email getInnerSenGridEmailTo() {
    return new Email(to.email(), to.name());
  }

  public void setFrom(final Sender from) {
    this.from = from;
  }

  public Sender getFrom() {
    return from;
  }

  public Recipient getTo() {
    return to;
  }

  public void setTo(final Recipient to) {
    this.to = to;
  }

  public Template getTemplate() {
    return template;
  }

  public void setTemplate(final Template template) {
    this.template = template;
  }

  public boolean hasTemplate() {
    return template != null;
  }

  public Content getContent() {
    return content;
  }

  public void setContent(final Content content) {
    this.content = content;
  }

  public boolean hasContent() {
    return content != null;
  }

  public MailType getMailType() {
    return mailType;
  }

  public void setMailType(MailType mailType) {
    this.mailType = mailType;
  }

  public List<Document> getAttachments() {
    return attachments;
  }

  public void setAttachments(List<Document> attachments) {
    this.attachments = attachments;
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
        && Objects.equals(content, that.content)
        && Objects.equals(attachments, that.attachments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(apiKey, from, to, template, content, attachments);
  }

  @Override
  public String toString() {
    return "SendGridRequest{"
        + "apiKey=[REDACTED]"
        + ", from="
        + from
        + ", to="
        + to
        + ", template="
        + template
        + ", content="
        + content
        + ", attachments="
        + attachments
        + '}';
  }
}
