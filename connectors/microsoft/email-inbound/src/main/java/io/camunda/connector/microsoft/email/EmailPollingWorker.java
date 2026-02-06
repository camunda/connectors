/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.microsoft.email;

import io.camunda.connector.api.inbound.*;
import io.camunda.connector.microsoft.email.model.config.MsInboundEmailProperties;
import io.camunda.connector.microsoft.email.util.MailClient;
import io.camunda.connector.microsoft.email.util.MicrosoftMailClient;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailPollingWorker implements Runnable {
  private final InboundConnectorContext context;
  private final ScheduledExecutorService scheduler;
  private final MailClient.OpaqueMessageFetcher fetcher;
  private static final Logger LOGGER = LoggerFactory.getLogger(EmailPollingWorker.class);

  public EmailPollingWorker(InboundConnectorContext context, MailClient mailClient) {
    this.context = context;
    MsInboundEmailProperties properties = context.bindProperties(MsInboundEmailProperties.class);
    var messageProcessor = new MessageProcessor(properties.operation(), mailClient, context);
    // Doing this here to establish connection/access rights
    this.fetcher =
        mailClient.constructMessageFetcher(
            properties.pollingConfig().folder(),
            properties.pollingConfig().getFilter(),
            messageProcessor::handleMessage);
    this.scheduler = Executors.newSingleThreadScheduledExecutor();
    scheduler.scheduleWithFixedDelay(
        this, 0, properties.pollingConfig().pollingInterval().toMillis(), TimeUnit.MILLISECONDS);
  }

  public EmailPollingWorker(InboundConnectorContext context) {
    this(context, createDefaultMailClient(context));
  }

  private static MailClient createDefaultMailClient(InboundConnectorContext context) {
    MsInboundEmailProperties properties = context.bindProperties(MsInboundEmailProperties.class);
    return new MicrosoftMailClient(
        properties.authentication(), properties.pollingConfig().userId());
  }

  @Override
  public void run() {
    try {
      fetcher.poll();
      context.reportHealth(Health.up());
    } catch (RuntimeException e) {
      LOGGER.error("Uncaught exception in Microsoft Inbound Mail connector", e);
      context.log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withTag("polling-error")
                  .withMessage("Error polling emails: " + e.getMessage()));
      context.reportHealth(Health.down(e));
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
