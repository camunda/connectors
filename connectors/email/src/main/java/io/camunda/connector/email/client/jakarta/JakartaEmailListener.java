/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta;

import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy;
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
        IMAPFolder folder =
            (IMAPFolder) this.jakartaUtils.findImapFolder(store.getDefaultFolder(), inbox);
        EmailListenerConfig emailListenerConfig = emailInboundConnectorProperties.data();
        folder.open(Folder.READ_WRITE);
        folder.addMessageCountListener(
            new MessageCountListener() {
              @Override
              public void messagesAdded(MessageCountEvent event) {
                processNewEvent(event, context, emailListenerConfig);
              }

              @Override
              public void messagesRemoved(MessageCountEvent event) {}
            });
        switch (emailListenerConfig.initialPollingConfig()) {
          case UNSEEN -> pollUnseenAndProcess(folder, emailListenerConfig, context);
          case ALL -> pollAllAndProcess(folder, emailListenerConfig, context);
          case NONE -> {}
        }
        this.imapFolders.add(folder);
        idleManager.watch(folder);
      }
    } catch (MessagingException | IOException e) {
      LOGGER.error(e.getMessage(), e);
      throw new RuntimeException(e);
    }
  }

  private void pollAllAndProcess(
      IMAPFolder folder, EmailListenerConfig emailListenerConfig, InboundConnectorContext context) {
    try {
      Message[] messages = folder.getMessages();
      Arrays.stream(messages)
          .forEach(message -> processMail(folder, message, context, emailListenerConfig));
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private void pollUnseenAndProcess(
      IMAPFolder folder, EmailListenerConfig emailListenerConfig, InboundConnectorContext context) {
    try {
      FlagTerm unseenFlagTerm = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
      Message[] unseenMessages = folder.search(unseenFlagTerm);
      Arrays.stream(unseenMessages)
          .forEach(message -> processMail(folder, message, context, emailListenerConfig));
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private void processNewEvent(
      MessageCountEvent event,
      InboundConnectorContext connectorContext,
      EmailListenerConfig emailListenerConfig) {
    IMAPFolder imapFolder = (IMAPFolder) event.getSource();
    for (Message message : event.getMessages()) {
      processMail(imapFolder, message, connectorContext, emailListenerConfig);
      try {
        idleManager.watch(imapFolder);
      } catch (MessagingException ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  private void processMail(
      IMAPFolder imapFolder,
      Message message,
      InboundConnectorContext connectorContext,
      EmailListenerConfig emailListenerConfig) {
    CorrelationResult correlationResult = this.correlateEmail(message, connectorContext);

    Runnable postProcess =
        switch (emailListenerConfig.handlingStrategy()) {
          case READ -> () -> this.jakartaUtils.markAsSeen(message);
          case DELETE -> () -> this.jakartaUtils.markAsDeleted(message);
          case NO_HANDLING -> () -> {};
          case MOVE ->
              () ->
                  this.jakartaUtils.moveMessage(
                      this.store, message, imapFolder, emailListenerConfig.targetFolder());
        };

    switch (correlationResult) {
      case CorrelationResult.Failure failure -> {
        switch (failure.handlingStrategy()) {
          case CorrelationFailureHandlingStrategy.ForwardErrorToUpstream
                  forwardErrorToUpstream -> {}
          case CorrelationFailureHandlingStrategy.Ignore ignore -> postProcess.run();
        }
      }
      case CorrelationResult.Success success -> postProcess.run();
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
