/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.microsoft.email;

import io.camunda.connector.api.inbound.*;
import io.camunda.connector.microsoft.email.model.config.MsInboundEmailProperties;
import io.camunda.connector.microsoft.email.util.MicrosoftMailClient;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// TODO: Use ScheduledExecutor
// TODO: Use Delta polling
public class EmailPollingWorker implements Runnable {
  private final InboundConnectorContext context;
  private final ScheduledExecutorService scheduler;
  private final MsInboundEmailProperties properties;
  private String deltaToken = null;

  public EmailPollingWorker(InboundConnectorContext context) {
    this.context = context;
    this.properties = context.bindProperties(MsInboundEmailProperties.class);
    this.scheduler = Executors.newSingleThreadScheduledExecutor();
    scheduler.scheduleWithFixedDelay(
        this, 0, properties.pollingConfig().pollingInterval().toMillis(), TimeUnit.MILLISECONDS);
  }

  @Override
  public void run() {
    MicrosoftMailClient client = new MicrosoftMailClient(properties);
    var messageProcessor = new MessageProcessor(properties, client, context);
    deltaToken =
        client.getMessages(
            deltaToken,
            properties.pollingConfig().folder(),
            properties.pollingConfig().getFilter(),
            messageProcessor::handleMessage);
  }

  public void forceShutdown() {
    scheduler.shutdownNow();
  }

  public void shutdown() {
    scheduler.shutdown();
  }

  public boolean isShutdown() {
    return scheduler.isShutdown();
  }
}
