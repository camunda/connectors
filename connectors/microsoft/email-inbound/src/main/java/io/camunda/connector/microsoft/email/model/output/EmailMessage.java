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
 * Common contract for an inbound email as it flows through the connector.
 *
 * <p>An email is represented by one of two lifecycle-specific shapes:
 *
 * <ul>
 *   <li>{@link MessageWithMetadata} — produced at poll time and used for the activation condition.
 *       It carries lightweight {@link EmailAttachmentMetadata} (name, content type, size, ...) so
 *       conditions can filter on attachment properties such as file type, without downloading the
 *       attachment content.
 *   <li>{@link MessageWithAttachments} — produced after the activation condition matches and the
 *       attachment content has been downloaded. It carries the actual {@link Document attachments};
 *       those already expose name, content type and size, so no separate metadata list is kept.
 * </ul>
 */
public sealed interface EmailMessage permits MessageWithMetadata, MessageWithAttachments {

  String id();

  String conversationId();

  EmailAddress sender();

  List<EmailAddress> recipients();

  List<EmailAddress> cc();

  List<EmailAddress> bcc();

  String subject();

  String body();

  String bodyContentType();

  OffsetDateTime receivedDateTime();

  /** OData {@code $select} fields for the message list query. */
  static String[] getSelect() {
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
