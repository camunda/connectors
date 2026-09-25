/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.model.output;

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
    String bodyContentType,
    OffsetDateTime receivedDateTime,
    Boolean hasAttachments,
    List<EmailAttachmentMetadata> attachmentMetadata,
    List<Document> attachments) {

  /** OData {@code $select} fields for the message list query. */
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
