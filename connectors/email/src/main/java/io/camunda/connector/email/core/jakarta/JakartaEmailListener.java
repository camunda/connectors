/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.core.jakarta;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.email.inbound.model.EmailProperties;
import jakarta.mail.*;
import jakarta.mail.event.MessageCountEvent;
import jakarta.mail.event.MessageCountListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.IdleManager;

public class JakartaEmailListener {

  private final InboundConnectorContext connectorContext;
  private final EmailProperties emailProperties;
  private final JakartaSessionFactory sessionFactory;
  private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

  private IdleManager idleManager;
  private Store store;
  private List<IMAPFolder> imapFolders;

  public JakartaEmailListener(
      InboundConnectorContext context,
      EmailProperties emailProperties,
      JakartaSessionFactory sessionFactory) {
    this.connectorContext = context;
    this.emailProperties = emailProperties;
    this.sessionFactory = sessionFactory;
  }

  public static JakartaEmailListener create(
      InboundConnectorContext context, JakartaSessionFactory sessionFactory) {
    return new JakartaEmailListener(
        context, context.bindProperties(EmailProperties.class), sessionFactory);
  }

  public void startListener() {
    Authentication authentication = emailProperties.getAuthentication();
    Session session =
        this.sessionFactory.createSession(
            emailProperties.getData().getImapConfig(), emailProperties.getAuthentication());
    try {
      this.store = session.getStore();
      this.imapFolders = new ArrayList<>();
      this.idleManager = new IdleManager(session, this.executorService);

      this.sessionFactory.connectStore(this.store, authentication);
      List<String> inboxes = createInboxList(emailProperties.getData().getFolderToListen());
      for (String inbox : inboxes) {
        IMAPFolder folder = (IMAPFolder) store.getFolder(inbox);
        folder.open(Folder.READ_WRITE);
        folder.addMessageCountListener(
            new MessageCountListener() {
              @Override
              public void messagesAdded(MessageCountEvent e) {
                processNewEvent(e, emailProperties.getData().isTriggerAdded());
              }

              @Override
              public void messagesRemoved(MessageCountEvent e) {
                processNewEvent(e, emailProperties.getData().isTriggerRemoved());
              }
            });
        this.imapFolders.add(folder);
        idleManager.watch(folder);
      }
    } catch (MessagingException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void processNewEvent(MessageCountEvent e, boolean triggerAdded) {
    IMAPFolder imapFolder = (IMAPFolder) e.getSource();
    if (triggerAdded) {
      connectorContext.correlateWithResult(
          Arrays.stream(e.getMessages()).map(Email::createEmail).toList());
    }
    try {
      idleManager.watch(imapFolder);
    } catch (MessagingException ex) {
      throw new RuntimeException(ex);
    }
  }

  private List<String> createInboxList(Object folderToListen) {
    return switch (folderToListen) {
      case List<?> list -> list.stream().map(Object::toString).toList();
      case String string -> Arrays.stream(string.split(",")).toList();
      default ->
          throw new IllegalStateException(
              "Unexpected value: " + folderToListen + ". List or String was expected");
    };
  }
}
