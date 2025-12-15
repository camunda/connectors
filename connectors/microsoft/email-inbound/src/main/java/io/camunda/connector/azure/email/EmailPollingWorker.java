/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.azure.email;

import io.camunda.connector.api.inbound.*;
import io.camunda.connector.azure.email.model.config.MsInboundEmailProperties;
import io.camunda.connector.azure.email.util.MailClient;
import io.camunda.connector.azure.email.util.MicrosoftMailClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EmailPollingWorker implements Runnable {
  private final InboundConnectorContext context;
  private final ScheduledExecutorService scheduler;
  private final MsInboundEmailProperties properties;
  private final MailClient.OpaqueMessageFetcher fetcher;
  private static final Logger LOGGER = LoggerFactory.getLogger(EmailPollingWorker.class);

  public EmailPollingWorker(InboundConnectorContext context) {
    this.context = context;
    this.properties = context.bindProperties(MsInboundEmailProperties.class);
    MicrosoftMailClient client = new MicrosoftMailClient(properties);
    var messageProcessor = new MessageProcessor(properties, client, context);
    //TODO: Don't do this here. This is a first call
    this.fetcher =
        client.constructMessageFetcher(
            properties.pollingConfig().folder(),
            properties.pollingConfig().getFilter(),
            messageProcessor::handleMessage);
    this.scheduler = Executors.newSingleThreadScheduledExecutor();
    scheduler.scheduleWithFixedDelay(
        this, 0, properties.pollingConfig().pollingInterval().toMillis(), TimeUnit.MILLISECONDS);
  }

  @Override
  public void run() {
    try {
      fetcher.poll();
    } catch (RuntimeException e) {
      LOGGER.error("Uncaught exception in Microsoft Inbound Mail connector",e);
    }
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
