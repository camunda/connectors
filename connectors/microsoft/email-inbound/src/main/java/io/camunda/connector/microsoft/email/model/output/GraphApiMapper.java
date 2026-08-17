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

  public static EmailMessage toEmailMessage(
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
    return new EmailMessage(
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
        message.getHasAttachments(),
        attachmentMetadata,
        List.of());
  }

  /**
   * Returns a copy of {@code source} with the downloaded attachment documents attached. {@code
   * attachmentMetadata} is carried over unchanged, so an activation condition referencing it still
   * resolves the same way when the runtime re-evaluates it against these correlation variables.
   */
  public static EmailMessage withAttachments(EmailMessage source, List<Document> documents) {
    return new EmailMessage(
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
        source.hasAttachments(),
        source.attachmentMetadata(),
        documents);
  }
}
