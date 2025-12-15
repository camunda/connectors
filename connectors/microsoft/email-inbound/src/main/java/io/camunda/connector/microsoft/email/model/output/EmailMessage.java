/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.model.output;

import static io.camunda.connector.microsoft.email.model.output.EmailAddress.transformList;

import com.microsoft.graph.models.Message;
import io.camunda.connector.api.document.Document;
import java.time.OffsetDateTime;
import java.util.List;

public record EmailMessage(
    String id,
    String conversationId,
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
    this(
        message.getId(),
        message.getConversationId(),
        new EmailAddress(message.getSender()),
        transformList(message.getToRecipients()),
        transformList(message.getCcRecipients()),
        transformList(message.getBccRecipients()),
        message.getSubject() != null ? message.getSubject() : "",
        message.getBody() != null && message.getBody().getContent() != null
            ? message.getBody().getContent()
            : "",
        message.getReceivedDateTime(),
        documents);
  }

  public EmailMessage(EmailMessage message, List<Document> documents) {
    this(
        message.id,
        message.conversationId,
        message.sender,
        message.recipients,
        message.cc,
        message.bcc,
        message.subject,
        message.body,
        message.receivedDateTime,
        documents);
  }

  public static String[] getSelect() {
    return new String[] {
      "id",
      "conversationId",
      "sender",
      "toRecipients",
      "ccRecipients",
      "bccRecipients",
      "subject",
      "body",
      "receivedDateTime",
      "hasAttachments"
    };
  }
}
