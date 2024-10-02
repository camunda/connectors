/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta.inbound;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.email.client.EmailListener;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import io.camunda.connector.email.inbound.model.EmailInboundConnectorProperties;
import io.camunda.connector.email.inbound.model.EmailListenerConfig;
import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import java.util.Objects;
import java.util.concurrent.*;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.IMAPStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JakartaEmailListener implements EmailListener {

  private static final Logger log = LoggerFactory.getLogger(JakartaEmailListener.class);
  private final JakartaUtils jakartaUtils;
  private ExecutorService executorService;
  private ScheduledExecutorService scheduledExecutorService;
  private IMAPFolder folder;
  private IMAPStore store;

  public JakartaEmailListener(JakartaUtils jakartaUtils) {
    this.jakartaUtils = jakartaUtils;
  }

  public static JakartaEmailListener create(JakartaUtils jakartaUtils) {
    return new JakartaEmailListener(jakartaUtils);
  }

  @Override
  public void startListener(InboundConnectorContext context) {
    this.executorService = Executors.newSingleThreadExecutor();
    this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    EmailInboundConnectorProperties emailInboundConnectorProperties =
        context.bindProperties(EmailInboundConnectorProperties.class);
    Authentication authentication = emailInboundConnectorProperties.authentication();
    EmailListenerConfig emailListenerConfig = emailInboundConnectorProperties.data();
    Session session =
        this.jakartaUtils.createSession(emailInboundConnectorProperties.data().imapConfig());
    try {
      this.store = (IMAPStore) session.getStore();
      this.jakartaUtils.connectStore(store, authentication);
      this.folder =
          (IMAPFolder)
              this.jakartaUtils.findImapFolder(
                  store.getDefaultFolder(), emailListenerConfig.folderToListen());
      CustomMessageCountListener customMessageCountListener =
          CustomMessageCountListener.create(context, emailListenerConfig);
      CustomConnectionListener customConnectionListener =
          CustomConnectionListener.create(
              this.folder, () -> executorService.submit(this::startIdle));
      CustomChangedListener customMessageChangedListener =
          CustomChangedListener.create(context, emailListenerConfig);
      this.folder.addConnectionListener(customConnectionListener);
      this.folder.addMessageCountListener(customMessageCountListener);
      this.folder.addMessageChangedListener(customMessageChangedListener);
      this.folder.open(Folder.READ_WRITE);
      scheduledExecutorService.scheduleWithFixedDelay(this::keepAlive, 1, 5, TimeUnit.SECONDS);
      InitialPolling.create(context, emailListenerConfig, folder).poll();
    } catch (MessagingException e) {
      this.stopListener();
      log.error("Error starting email listener", e);
      throw new RuntimeException(e);
    }
  }

  private void keepAlive() {
    try {
      if (!this.folder.isOpen()) folder.open(Folder.READ_WRITE);
      folder.doCommand(
          protocol -> {
            protocol.simpleCommand("NOOP", null);
            return null;
          });
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private synchronized void startIdle() {
    try {
      while (this.folder.isOpen()) {
        this.folder.idle();
      }
    } catch (MessagingException e) {
      log.error(e.getMessage(), e);
    }
  }

  @Override
  public void stopListener() {
    try {
      if (!Objects.isNull(this.folder)) this.folder.close();
      if (!Objects.isNull(this.store)) this.store.close();
      if (!Objects.isNull(this.executorService)) this.executorService.shutdown();
      if (!Objects.isNull(this.scheduledExecutorService)) this.scheduledExecutorService.shutdown();
      if (!this.executorService.awaitTermination(1, TimeUnit.SECONDS))
        this.executorService.shutdownNow();
      if (!this.scheduledExecutorService.awaitTermination(1, TimeUnit.SECONDS))
        this.scheduledExecutorService.shutdownNow();
    } catch (MessagingException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
