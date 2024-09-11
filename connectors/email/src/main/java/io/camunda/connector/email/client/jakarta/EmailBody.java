/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta;

import java.util.ArrayList;
import java.util.List;

public record EmailBody(
    String bodyAsPlainText, String bodyAsHtml, List<EmailAttachment> attachments) {

  public static EmailBodyBuilder createBuilder() {
    return new EmailBodyBuilder();
  }

  public static final class EmailBodyBuilder {
    private final List<EmailAttachment> attachments = new ArrayList<>();
    private String bodyAsPlainText;
    private String bodyAsHtml;

    public EmailBodyBuilder withBodyAsPlainText(String bodyAsPlainText) {
      this.bodyAsPlainText = bodyAsPlainText;
      return this;
    }

    public EmailBodyBuilder withBodyAsHtml(String bodyAsHtml) {
      this.bodyAsHtml = bodyAsHtml;
      return this;
    }

    public EmailBodyBuilder addAttachment(EmailAttachment attachment) {
      this.attachments.add(attachment);
      return this;
    }

    public EmailBodyBuilder withAttachments(List<EmailAttachment> attachments) {
      this.attachments.addAll(attachments);
      return this;
    }

    public EmailBody build() {
      return new EmailBody(bodyAsPlainText, bodyAsHtml, attachments);
    }
  }
}
