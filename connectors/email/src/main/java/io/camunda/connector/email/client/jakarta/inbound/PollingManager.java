/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta.inbound;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.email.client.jakarta.models.Email;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import io.camunda.connector.email.inbound.model.*;
import io.camunda.connector.email.response.ReadEmailResponse;
import jakarta.mail.*;
import jakarta.mail.search.FlagTerm;
import java.util.Arrays;
import java.util.Objects;
import org.eclipse.angus.mail.imap.IMAPMessage;

public class PollingManager {

  private final InboundConnectorContext connectorContext;
  private final EmailListenerConfig emailListenerConfig;
  private final JakartaUtils jakartaUtils;
  private final Folder folder;
  private final Store store;

  public PollingManager(
      InboundConnectorContext connectorContext,
      EmailListenerConfig emailListenerConfig,
      JakartaUtils jakartaUtils,
      Folder folder,
      Store store) {
    this.connectorContext = connectorContext;
    this.emailListenerConfig = emailListenerConfig;
    this.jakartaUtils = jakartaUtils;
    this.folder = folder;
    this.store = store;
  }

  public static PollingManager create(
      InboundConnectorContext connectorContext, JakartaUtils jakartaUtils) {
    Store store = null;
    Folder folder = null;
    try {
      EmailInboundConnectorProperties emailInboundConnectorProperties =
          connectorContext.bindProperties(EmailInboundConnectorProperties.class);
      Authentication authentication = emailInboundConnectorProperties.authentication();
      EmailListenerConfig emailListenerConfig = emailInboundConnectorProperties.data();
      Session session =
          jakartaUtils.createSession(emailInboundConnectorProperties.data().imapConfig());
      store = session.getStore();
      jakartaUtils.connectStore(store, authentication);
      folder =
          jakartaUtils.findImapFolder(
              store.getDefaultFolder(), emailListenerConfig.folderToListen());
      folder.open(Folder.READ_WRITE);
      if (emailListenerConfig.pollingConfig().handlingStrategy().equals(HandlingStrategy.MOVE)
          && (Objects.isNull(emailListenerConfig.pollingConfig().targetFolder())
              || emailListenerConfig.pollingConfig().targetFolder().isBlank()))
        throw new RuntimeException(
            "If the post process action is `MOVE`, a target folder must be specified");
      return new PollingManager(connectorContext, emailListenerConfig, jakartaUtils, folder, store);
    } catch (MessagingException e) {
      try {
        if (folder != null && folder.isOpen()) {
          folder.close();
        }
        if (store != null && store.isConnected()) {
          store.close();
        }
      } catch (MessagingException ex) {
        throw new RuntimeException(ex);
      }
      throw new RuntimeException(e);
    }
  }

  public void poll() {
    switch (this.emailListenerConfig.pollingConfig()) {
      case AllPollingConfig allPollingConfig -> pollAllAndProcess(allPollingConfig);
      case UnseenPollingConfig unseenPollingConfig -> pollUnseenAndProcess(unseenPollingConfig);
    }
  }

  private void pollAllAndProcess(AllPollingConfig allPollingConfig) {
    try {
      Message[] messages = this.folder.getMessages();
      Arrays.stream(messages)
          .forEach(message -> this.processMail((IMAPMessage) message, allPollingConfig));
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private void pollUnseenAndProcess(UnseenPollingConfig unseenPollingConfig) {
    try {
      FlagTerm unseenFlagTerm = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
      Message[] unseenMessages = this.folder.search(unseenFlagTerm, this.folder.getMessages());
      Arrays.stream(unseenMessages)
          .forEach(message -> this.processMail((IMAPMessage) message, unseenPollingConfig));
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private void processMail(IMAPMessage message, PollingConfig pollingConfig) {
    // Setting `peek` to true prevents the library to trigger any side effects when reading the
    // message, such as marking it as read
    message.setPeek(true);
    this.correlateEmail(message, connectorContext);
    message.setPeek(false);
    switch (pollingConfig.handlingStrategy()) {
      case READ -> this.jakartaUtils.markAsSeen(message);
      case DELETE -> this.jakartaUtils.markAsDeleted(message);
      case MOVE -> this.jakartaUtils.moveMessage(this.store, message, pollingConfig.targetFolder());
    }
  }

  private void correlateEmail(Message message, InboundConnectorContext connectorContext) {
    Email email = this.jakartaUtils.createEmail(message);
    connectorContext.correlateWithResult(
        new ReadEmailResponse(
            email.messageId(),
            email.from(),
            email.headers(),
            email.subject(),
            email.size(),
            email.body().bodyAsPlainText(),
            email.body().bodyAsHtml(),
            email.receivedAt()));
  }

  public long delay() {
    return this.emailListenerConfig.pollingWaitTime().getSeconds();
  }

  public void stop() {
    try {
      this.folder.close();
      this.store.close();
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }
}
