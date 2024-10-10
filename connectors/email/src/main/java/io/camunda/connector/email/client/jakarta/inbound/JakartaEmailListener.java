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
import io.camunda.connector.email.inbound.model.HandlingStrategy;
import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JakartaEmailListener implements EmailListener {

  private static final Logger log = LoggerFactory.getLogger(JakartaEmailListener.class);
  private final JakartaUtils jakartaUtils;
  private ScheduledExecutorService scheduledExecutorService;

  public JakartaEmailListener(JakartaUtils jakartaUtils) {
    this.jakartaUtils = jakartaUtils;
  }

  public static JakartaEmailListener create(JakartaUtils jakartaUtils) {
    return new JakartaEmailListener(jakartaUtils);
  }

  @Override
  public void startListener(InboundConnectorContext context) {
    this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    EmailInboundConnectorProperties emailInboundConnectorProperties =
        context.bindProperties(EmailInboundConnectorProperties.class);
    Authentication authentication = emailInboundConnectorProperties.authentication();
    EmailListenerConfig emailListenerConfig = emailInboundConnectorProperties.data();
    Session session =
        this.jakartaUtils.createSession(emailInboundConnectorProperties.data().imapConfig());
    try {
      Store store = session.getStore();
      this.jakartaUtils.connectStore(store, authentication);
      Folder folder =
          this.jakartaUtils.findImapFolder(
              store.getDefaultFolder(), emailListenerConfig.folderToListen());
      folder.open(Folder.READ_WRITE);
      PollingManager pollingManager = PollingManager.create(context, folder, store);
      if (emailListenerConfig.pollingConfig().handlingStrategy().equals(HandlingStrategy.MOVE)
          && (Objects.isNull(emailListenerConfig.pollingConfig().targetFolder())
              || emailListenerConfig.pollingConfig().targetFolder().isBlank()))
        throw new RuntimeException(
            "If the post process action is `MOVE`, a target folder must be specified");
      scheduledExecutorService.scheduleWithFixedDelay(
          () -> pollingManager.poll(emailListenerConfig),
          0,
          Optional.of(emailListenerConfig.pollingWaitTime()).map(Long::parseLong).orElse(20L),
          TimeUnit.SECONDS);
    } catch (MessagingException e) {
      this.stopListener();
      log.error("Error starting email listener", e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void stopListener() {
    try {
      if (!Objects.isNull(this.scheduledExecutorService)) {
        this.scheduledExecutorService.shutdown();
        if (!this.scheduledExecutorService.awaitTermination(1, TimeUnit.SECONDS))
          this.scheduledExecutorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
