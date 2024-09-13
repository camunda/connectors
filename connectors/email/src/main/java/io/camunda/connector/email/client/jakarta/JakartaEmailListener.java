/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta;

import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.email.client.EmailListener;
import io.camunda.connector.email.inbound.model.*;
import io.camunda.connector.email.response.ReadEmailResponse;
import jakarta.mail.*;
import jakarta.mail.event.MessageCountEvent;
import jakarta.mail.event.MessageCountListener;
import jakarta.mail.search.FlagTerm;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.IdleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JakartaEmailListener implements EmailListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(JakartaEmailListener.class);

  private final JakartaUtils jakartaUtils;
  private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

  private IdleManager idleManager;
  private Store store;
  private List<IMAPFolder> imapFolders;

  public JakartaEmailListener(JakartaUtils jakartaUtils) {
    this.jakartaUtils = jakartaUtils;
  }

  public static JakartaEmailListener create(JakartaUtils sessionFactory) {
    return new JakartaEmailListener(sessionFactory);
  }

  public void startListener(InboundConnectorContext context) {
    EmailInboundConnectorProperties emailInboundConnectorProperties =
        context.bindProperties(EmailInboundConnectorProperties.class);
    Authentication authentication = emailInboundConnectorProperties.authentication();
    Session session =
        this.jakartaUtils.createSession(
            emailInboundConnectorProperties.data().imapConfig(),
            emailInboundConnectorProperties.authentication());
    try {
      this.store = session.getStore();
      this.imapFolders = new ArrayList<>();
      this.idleManager = new IdleManager(session, this.executorService);
      this.jakartaUtils.connectStore(this.store, authentication);
      List<String> inboxes =
          createInboxList(emailInboundConnectorProperties.data().folderToListen());
      for (String inbox : inboxes) {
        IMAPFolder folder = (IMAPFolder) store.getFolder(inbox);
        folder.open(Folder.READ_WRITE);
        EmailListenerConfig emailListenerConfig = emailInboundConnectorProperties.data();
        folder.addMessageCountListener(
            new MessageCountListener() {
              @Override
              public void messagesAdded(MessageCountEvent event) {
                processNewEvent(event, context, emailListenerConfig);
              }

              @Override
              public void messagesRemoved(MessageCountEvent event) {}
            });
        this.imapFolders.add(folder);
        this.idleManager.watch(folder);
        switch (emailInboundConnectorProperties.data().initialPollingConfig()) {
          // case UNSEEN -> this.processUnseen(folder, context, emailListenerConfig);
          // case ALL -> this.processAll(folder, context, emailListenerConfig);
          case NONE -> {}
        }
      }
    } catch (MessagingException | IOException e) {
      LOGGER.error(e.getMessage(), e);
      throw new RuntimeException(e);
    }
  }

  private void processNewEvent(
      MessageCountEvent event,
      InboundConnectorContext connectorContext,
      EmailListenerConfig emailListenerConfig) {
    IMAPFolder imapFolder = (IMAPFolder) event.getSource();
    for (Message message : event.getMessages()) {
      CorrelationResult correlationResult = this.correlateEmail(message, connectorContext);
      switch (emailListenerConfig.handlingStrategy()) {
        case DeleteHandlingStrategy deleteHandlingStrategy ->
            this.jakartaUtils.markAsDeleted(message);
        case MoveHandlingStrategy moveHandlingStrategy -> {}
        case NoHandlingStrategy noHandlingStrategy -> {}
        case ReadHandlingStrategy readHandlingStrategy -> {}
      }
    }
    try {
      idleManager.watch(imapFolder);
    } catch (MessagingException ex) {
      throw new RuntimeException(ex);
    }
  }

  private CorrelationResult correlateEmail(
      Message message, InboundConnectorContext connectorContext) {
    Email email = this.jakartaUtils.createEmail(message);
    return connectorContext.correlateWithResult(
        new ReadEmailResponse(
            email.messageId(),
            email.from(),
            email.subject(),
            email.size(),
            email.body().bodyAsPlainText(),
            email.body().bodyAsHtml()));
  }

  private List<String> createInboxList(Object folderToListen) {
    return switch (folderToListen) {
      case null -> List.of("");
      case List<?> list -> list.stream().map(Object::toString).toList();
      case String string -> List.of(string.split(","));
      default ->
          throw new IllegalStateException(
              "Unexpected value: " + folderToListen + ". List or String was expected");
    };
  }

  public void stopListener() {
    try {
      this.idleManager.stop();
      for (IMAPFolder folder : this.imapFolders) {
        folder.close();
      }
      this.store.close();
    } catch (MessagingException e) {
      LOGGER.error(e.getMessage(), e);
      throw new RuntimeException(e);
    }
  }
}
