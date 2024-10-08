/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta.inbound;

import static io.camunda.document.DocumentMetadata.CONTENT_TYPE;
import static io.camunda.document.DocumentMetadata.FILE_NAME;

import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.email.client.jakarta.utils.Email;
import io.camunda.connector.email.client.jakarta.utils.EmailAttachment;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import io.camunda.connector.email.inbound.model.EmailListenerConfig;
import io.camunda.connector.email.response.ReadEmailResponse;
import io.camunda.document.Document;
import io.camunda.document.store.DocumentCreationRequest;
import jakarta.mail.Flags;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.event.MessageChangedEvent;
import jakarta.mail.event.MessageCountEvent;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.eclipse.angus.mail.imap.IMAPMessage;

public class EventManager {

  JakartaUtils jakartaUtils = new JakartaUtils();

  public void processNewEvent(
      MessageCountEvent event,
      InboundConnectorContext connectorContext,
      EmailListenerConfig emailListenerConfig) {
    for (Message message : event.getMessages()) {
      processMail((IMAPMessage) message, connectorContext, emailListenerConfig);
    }
  }

  public void processMail(
      IMAPMessage message,
      InboundConnectorContext connectorContext,
      EmailListenerConfig emailListenerConfig) {
    message.setPeek(true);
    CorrelationResult correlationResult = this.correlateEmail(message, connectorContext);
    message.setPeek(false);
    switch (correlationResult) {
      case CorrelationResult.Failure failure -> {
        switch (failure.handlingStrategy()) {
          case CorrelationFailureHandlingStrategy.ForwardErrorToUpstream __ -> {}
          case CorrelationFailureHandlingStrategy.Ignore __ ->
              executePostProcess(message, emailListenerConfig);
        }
      }
      case CorrelationResult.Success __ -> executePostProcess(message, emailListenerConfig);
    }
  }

  public void processChangedEvent(
      MessageChangedEvent event,
      InboundConnectorContext connectorContext,
      EmailListenerConfig emailListenerConfig) {
    IMAPMessage message = (IMAPMessage) event.getMessage();
    try {
      if (!message.isSet(Flags.Flag.SEEN))
        processMail(message, connectorContext, emailListenerConfig);
    } catch (MessagingException ex) {
      throw new RuntimeException(ex);
    }
  }

  private void executePostProcess(Message message, EmailListenerConfig emailListenerConfig) {
    switch (emailListenerConfig.handlingStrategy()) {
      case READ -> this.jakartaUtils.markAsSeen(message);
      case DELETE -> this.jakartaUtils.markAsDeleted(message);
      case NO_HANDLING -> {}
      case MOVE ->
          this.jakartaUtils.moveMessage(
              message.getFolder().getStore(), message, emailListenerConfig.targetFolder());
    }
  }

  private CorrelationResult correlateEmail(
      Message message, InboundConnectorContext connectorContext) {
    Email email = this.jakartaUtils.createEmail(message);
    List<Document> documents =
        this.jakartaUtils.getDocumentList(email, this.createDocumentFunction(connectorContext));
    return connectorContext.correlateWithResult(
        new ReadEmailResponse(
            email.messageId(),
            email.from(),
            documents,
            email.headers(),
            email.subject(),
            email.size(),
            email.body().bodyAsPlainText(),
            email.body().bodyAsHtml(),
            email.receivedAt()));
  }

  private Function<EmailAttachment, Document> createDocumentFunction(
      InboundConnectorContext connectorContext) {
    return emailAttachment ->
        connectorContext.createDocument(
            DocumentCreationRequest.from(emailAttachment.inputStream())
                .metadata(
                    Map.of(
                        CONTENT_TYPE,
                        emailAttachment.contentType(),
                        FILE_NAME,
                        emailAttachment.name()))
                .build());
  }
}
