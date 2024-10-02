/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta.inbound;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.email.inbound.model.EmailListenerConfig;
import jakarta.mail.Flags;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.search.FlagTerm;
import java.util.Arrays;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.IMAPMessage;

public class InitialPolling {

  private final EventManager eventManager;
  private final InboundConnectorContext connectorContext;
  private final EmailListenerConfig emailListenerConfig;
  private final IMAPFolder imapFolder;

  public InitialPolling(
      EventManager eventManager,
      InboundConnectorContext connectorContext,
      EmailListenerConfig emailListenerConfig,
      IMAPFolder imapFolder) {
    this.eventManager = eventManager;
    this.connectorContext = connectorContext;
    this.emailListenerConfig = emailListenerConfig;
    this.imapFolder = imapFolder;
  }

  public static InitialPolling create(
      InboundConnectorContext connectorContext,
      EmailListenerConfig emailListenerConfig,
      IMAPFolder imapFolder) {
    return new InitialPolling(
        new EventManager(), connectorContext, emailListenerConfig, imapFolder);
  }

  public void poll() {
    switch (emailListenerConfig.initialPollingConfig()) {
      case UNSEEN -> pollUnseenAndProcess();
      case ALL -> pollAllAndProcess();
      case NONE -> {}
    }
  }

  private void pollAllAndProcess() {

    try {
      Message[] messages = this.imapFolder.getMessages();
      Arrays.stream(messages)
          .forEach(
              message ->
                  this.eventManager.processMail(
                      (IMAPMessage) message, this.connectorContext, this.emailListenerConfig));
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private void pollUnseenAndProcess() {
    try {
      FlagTerm unseenFlagTerm = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
      Message[] unseenMessages = this.imapFolder.search(unseenFlagTerm);
      Arrays.stream(unseenMessages)
          .forEach(
              message ->
                  this.eventManager.processMail(
                      (IMAPMessage) message, this.connectorContext, this.emailListenerConfig));
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }
}
