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
 * An email as correlated into the process, after the activation condition matched and the
 * attachment content was downloaded. The {@link Document attachments} already expose name, content
 * type and size, so no separate metadata list is kept.
 */
public record MessageWithAttachments(
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
    List<Document> attachments)
    implements EmailMessage {}
