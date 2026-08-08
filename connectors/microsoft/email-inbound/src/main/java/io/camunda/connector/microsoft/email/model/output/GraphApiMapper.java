/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.model.output;

import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import io.camunda.connector.api.document.Document;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Maps Microsoft Graph API types to connector output models. */
public final class GraphApiMapper {

  private GraphApiMapper() {}

  public static EmailAddress toEmailAddress(Recipient recipient) {
    if (recipient == null || recipient.getEmailAddress() == null) {
      return new EmailAddress(null, null);
    }
    return new EmailAddress(
        recipient.getEmailAddress().getName(), recipient.getEmailAddress().getAddress());
  }

  public static List<EmailAddress> toEmailAddressList(List<Recipient> recipients) {
    return Optional.ofNullable(recipients).stream()
        .flatMap(list -> list.stream().map(GraphApiMapper::toEmailAddress))
        .toList();
  }

  /**
   * Maps a Graph message and its resolved attachment metadata to a {@link MessageWithMetadata}, the
   * shape evaluated by the activation condition.
   */
  public static MessageWithMetadata toMessageWithMetadata(
      Message message, List<EmailAttachmentMetadata> attachmentMetadata) {
    String body = null;
    String bodyContentType = null;
    if (message.getBody() != null) {
      body = message.getBody().getContent();
      var contentType = message.getBody().getContentType();
      if (contentType != null) {
        bodyContentType = contentType.getValue();
      }
    }
    OffsetDateTime receivedTime = message.getReceivedDateTime();
    return new MessageWithMetadata(
        message.getId(),
        message.getConversationId(),
        toEmailAddress(message.getSender()),
        toEmailAddressList(message.getToRecipients()),
        toEmailAddressList(message.getCcRecipients()),
        toEmailAddressList(message.getBccRecipients()),
        message.getSubject(),
        body,
        bodyContentType,
        receivedTime,
        attachmentMetadata);
  }

  /**
   * Builds a {@link MessageWithAttachments} by copying the common fields of an existing message and
   * attaching the downloaded attachment documents.
   */
  public static MessageWithAttachments toMessageWithAttachments(
      EmailMessage source, List<Document> documents) {
    return new MessageWithAttachments(
        source.id(),
        source.conversationId(),
        source.sender(),
        source.recipients(),
        source.cc(),
        source.bcc(),
        source.subject(),
        source.body(),
        source.bodyContentType(),
        source.receivedDateTime(),
        documents);
  }
}
