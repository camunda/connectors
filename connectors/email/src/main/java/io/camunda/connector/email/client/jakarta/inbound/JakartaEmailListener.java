/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta.inbound;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.email.client.EmailListener;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import java.util.Objects;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JakartaEmailListener implements EmailListener {

  private static final Logger log = LoggerFactory.getLogger(JakartaEmailListener.class);
  private ScheduledExecutorService scheduledExecutorService;
  private PollingManager pollingManager;

  public JakartaEmailListener() {}

  public static JakartaEmailListener create() {
    return new JakartaEmailListener();
  }

  @Override
  public void startListener(InboundConnectorContext context) {
    scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    pollingManager = PollingManager.create(context, new JakartaUtils());
    scheduledExecutorService.scheduleWithFixedDelay(
        pollingManager::poll, 0, pollingManager.delay(), TimeUnit.SECONDS);
  }

  @Override
  public void stopListener() {
    try {
      if (!Objects.isNull(this.scheduledExecutorService)) {
        this.scheduledExecutorService.shutdown();
        if (!this.scheduledExecutorService.awaitTermination(10, TimeUnit.SECONDS))
          this.scheduledExecutorService.shutdownNow();
      }
      if (!Objects.isNull(pollingManager)) pollingManager.stop();
    } catch (InterruptedException e) {
      this.scheduledExecutorService.shutdownNow();
    }
  }
}
