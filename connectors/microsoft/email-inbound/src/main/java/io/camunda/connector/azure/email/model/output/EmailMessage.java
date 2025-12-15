/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.email.model.output;

import static io.camunda.connector.azure.email.model.output.EmailAddress.transformList;

import com.microsoft.graph.models.Message;
import io.camunda.connector.api.document.Document;
import java.time.OffsetDateTime;
import java.util.List;

public record EmailMessage(
    EmailAddress sender,
    List<EmailAddress> recipients,
    List<EmailAddress> cc,
    List<EmailAddress> bcc,
    String subject,
    String body,
    OffsetDateTime receivedDateTime,
    List<Document> attachments) {
  public EmailMessage(Message message) {
    this(message, List.of());
  }

  public EmailMessage(Message message, List<Document> documents) {
    var sender = new EmailAddress(message.getSender());
    var recipients = transformList(message.getToRecipients());
    var cc = transformList(message.getCcRecipients());
    var bcc = transformList(message.getBccRecipients());
    var subject = message.getSubject() != null ? message.getSubject() : "";
    String body = "";
    if (message.getBody() != null && message.getBody().getContent() != null) {
      body = message.getBody().getContent();
    }
    var receivedTime = message.getReceivedDateTime();
    this(sender, recipients, cc, bcc, subject, body, receivedTime, documents);
  }
}
