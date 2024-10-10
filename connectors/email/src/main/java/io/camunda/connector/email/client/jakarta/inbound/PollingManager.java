/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta.inbound;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.email.client.jakarta.utils.Email;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import io.camunda.connector.email.inbound.model.AllPollingConfig;
import io.camunda.connector.email.inbound.model.EmailListenerConfig;
import io.camunda.connector.email.inbound.model.PollingConfig;
import io.camunda.connector.email.inbound.model.UnseenPollingConfig;
import io.camunda.connector.email.response.ReadEmailResponse;
import jakarta.mail.*;
import jakarta.mail.search.FlagTerm;
import java.util.Arrays;
import org.eclipse.angus.mail.imap.IMAPMessage;

public class PollingManager {

  private final InboundConnectorContext connectorContext;
  private final JakartaUtils jakartaUtils;
  private final Folder folder;
  private final Store store;

  public PollingManager(
      InboundConnectorContext connectorContext,
      JakartaUtils jakartaUtils,
      Folder folder,
      Store store) {
    this.connectorContext = connectorContext;
    this.jakartaUtils = jakartaUtils;
    this.folder = folder;
    this.store = store;
  }

  public static PollingManager create(
      InboundConnectorContext connectorContext, Folder folder, Store store) {
    return new PollingManager(connectorContext, new JakartaUtils(), folder, store);
  }

  public void poll(EmailListenerConfig config) {
    switch (config.pollingConfig()) {
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

  public void processMail(IMAPMessage message, PollingConfig pollingConfig) {
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
}
