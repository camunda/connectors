/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.protocols.actions;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@TemplateSubType(id = "sendEmailSmtp", label = "Send Email")
public record SmtpSendEmail(
    @TemplateProperty(
            label = "From",
            group = "sendEmailSmtp",
            id = "smtpFrom",
            tooltip = "Address the email will be sent from",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.from"))
        @Valid
        @NotNull
        String from,
    @TemplateProperty(
            label = "To",
            group = "sendEmailSmtp",
            id = "smtpTo",
            tooltip =
                "Comma-separated list of email, e.g., 'email1@domain.com,email2@domain.com' or '=[ \"email1@domain.com\", \"email2@domain.com\"]'",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.to"))
        @Valid
        @NotNull
        Object to,
    @TemplateProperty(
            label = "Cc",
            group = "sendEmailSmtp",
            id = "smtpCc",
            tooltip =
                "Comma-separated list of email, e.g., 'email1@domain.com,email2@domain.com' or '=[ \"email1@domain.com\", \"email2@domain.com\"]'",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.cc"),
            optional = true)
        @Valid
        Object cc,
    @TemplateProperty(
            label = "Bcc",
            group = "sendEmailSmtp",
            id = "smtpBcc",
            tooltip =
                "Comma-separated list of email, e.g., 'email1@domain.com,email2@domain.com' or '=[ \"email1@domain.com\", \"email2@domain.com\"]'",
            feel = FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.bcc"),
            optional = true)
        @Valid
        Object bcc,
    @TemplateProperty(
            label = "Headers",
            group = "sendEmailSmtp",
            id = "smtpHeaders",
            tooltip = "Additional email headers",
            feel = FeelMode.required,
            binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.headers"),
            optional = true)
        @Valid
        Map<String, String> headers,
    @TemplateProperty(
            label = "Subject",
            group = "sendEmailSmtp",
            id = "smtpSubject",
            type = TemplateProperty.PropertyType.String,
            tooltip = "Email's subject",
            binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.subject"),
            feel = FeelMode.optional)
        @Valid
        @NotNull
        String subject,
    @TemplateProperty(
            label = "ContentType",
            group = "sendEmailSmtp",
            id = "contentType",
            defaultValue = "PLAIN",
            type = TemplateProperty.PropertyType.Dropdown,
            tooltip = "Email's contentType",
            binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.contentType"))
        @Valid
        @NotNull
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        ContentType contentType,
    @TemplateProperty(
            label = "Email Text Content",
            group = "sendEmailSmtp",
            id = "smtpBody",
            type = TemplateProperty.PropertyType.Text,
            tooltip = "Email's content",
            binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.body"),
            feel = FeelMode.optional,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "contentType",
                    oneOf = {"PLAIN", "MULTIPART"}))
        @Valid
        String body,
    @TemplateProperty(
            label = "Email Html Content",
            group = "sendEmailSmtp",
            id = "smtpHtmlBody",
            type = TemplateProperty.PropertyType.Text,
            tooltip = "Email's Html content",
            binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.htmlBody"),
            feel = FeelMode.optional,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "contentType",
                    oneOf = {"HTML", "MULTIPART"}))
        @Valid
        String htmlBody,
    @TemplateProperty(
            label = "Attachment",
            group = "sendEmailSmtp",
            id = "attachmentsSmtp",
            tooltip = "Email's attachments, should be set as a list ",
            type = TemplateProperty.PropertyType.String,
            feel = FeelMode.required,
            optional = true,
            description = "Email's attachment. e.g., =[ document1, document2]",
            binding = @TemplateProperty.PropertyBinding(name = "data.smtpAction.attachments"))
        List<Document> attachments)
    implements SmtpAction {
  @AssertTrue(message = "Please provide a proper message body")
  public boolean isEmailMessageValid() {
    return switch (contentType) {
      case PLAIN -> body != null;
      case HTML -> htmlBody != null;
      case MULTIPART -> body != null && htmlBody != null;
    };
  }
}
