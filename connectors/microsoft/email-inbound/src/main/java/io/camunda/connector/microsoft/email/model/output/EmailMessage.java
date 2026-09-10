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

/**
 * An inbound email as it flows through the connector.
 *
 * <p>{@code attachmentMetadata} is resolved at poll time, before the activation condition is
 * evaluated, so conditions can filter on attachment properties such as file type without
 * downloading the attachment content. {@code attachments} is empty at that point and only populated
 * with the downloaded {@link Document documents} once the condition has matched, mirroring how the
 * runtime's internal re-check of the same condition against the correlation variables expects
 * {@code attachmentMetadata} to still be present.
 *
 * <p>Inline attachments (e.g. signature images embedded in the email body) are included in both
 * lists like any other file attachment. A condition that only cares about attachments a user
 * intentionally added should filter on {@code isInline = false}.
 */
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
