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
    EmailProperties emailProperties = context.bindProperties(EmailProperties.class);
    Authentication authentication = emailProperties.getAuthentication();
    Session session =
        this.jakartaUtils.createSession(
            emailProperties.getData().getImapConfig(), emailProperties.getAuthentication());
    try {
      this.store = session.getStore();
      this.imapFolders = new ArrayList<>();
      this.idleManager = new IdleManager(session, this.executorService);

      this.jakartaUtils.connectStore(this.store, authentication);
      List<String> inboxes = createInboxList(emailProperties.getData().getFolderToListen());
      for (String inbox : inboxes) {
        IMAPFolder folder = (IMAPFolder) store.getFolder(inbox);
        folder.open(Folder.READ_WRITE);
        folder.addMessageCountListener(
            new MessageCountListener() {
              @Override
              public void messagesAdded(MessageCountEvent e) {
                processNewEvent(
                    e,
                    context,
                    emailProperties.getData().isTriggerAdded(),
                    emailProperties.getData().isMarkAsRead());
              }

              @Override
              public void messagesRemoved(MessageCountEvent e) {
                processNewEvent(
                    e,
                    context,
                    emailProperties.getData().isTriggerRemoved(),
                    emailProperties.getData().isMarkAsRead());
              }
            });
        this.imapFolders.add(folder);
        idleManager.watch(folder);
      }
    } catch (MessagingException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void processNewEvent(
      MessageCountEvent e,
      InboundConnectorContext connectorContext,
      boolean triggerAdded,
      boolean markAsRead) {
    IMAPFolder imapFolder = (IMAPFolder) e.getSource();
    if (triggerAdded) {
      connectorContext.correlateWithResult(
          Arrays.stream(e.getMessages())
              .peek(
                  message -> {
                    if (markAsRead) this.jakartaUtils.markAsSeen(message);
                  })
              .map(Email::createEmail)
              .toList());
    }
    try {
      idleManager.watch(imapFolder);
    } catch (MessagingException ex) {
      throw new RuntimeException(ex);
    }
  }

  private List<String> createInboxList(Object folderToListen) {
    return switch (folderToListen) {
      case null -> List.of("");
      case List<?> list -> list.stream().map(Object::toString).toList();
      case String string -> Arrays.stream(string.split(",")).toList();
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
      throw new RuntimeException(e);
    }
  }
}
