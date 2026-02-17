/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.microsoft.email;

import static io.camunda.connector.microsoft.email.MsEmailInboundConstants.SHUTDOWN_TIMEOUT;

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
  private final MessageProcessor messageProcessor;
  private static final Logger LOGGER = LoggerFactory.getLogger(EmailPollingWorker.class);

  public EmailPollingWorker(InboundConnectorContext context) {
    this.context = context;
    MsInboundEmailProperties properties = context.bindProperties(MsInboundEmailProperties.class);
    var mailClient =
        new MicrosoftMailClient(properties.authentication(), properties.pollingConfig().userId());
    messageProcessor = new MessageProcessor(properties.operation(), mailClient, context);
    // Doing this here to establish connection/access rights
    this.fetcher =
        mailClient.constructMessageFetcher(
            properties.pollingConfig().folder(), properties.pollingConfig().getFilter());
    this.scheduler = Executors.newSingleThreadScheduledExecutor();
    var pollingInterval = properties.pollingConfig().pollingInterval();
    scheduler.scheduleWithFixedDelay(this, 0, pollingInterval.toMillis(), TimeUnit.MILLISECONDS);
  }

  @Override
  public void run() {
    try {
      fetcher.poll(messageProcessor::handleMessage);
      context.reportHealth(Health.up());
    } catch (Exception e) {
      LOGGER.error("Uncaught exception in Microsoft Inbound Mail connector", e);
      context.log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withTag("polling-error")
                  .withMessage("Error polling emails", e));
      context.reportHealth(Health.down(e));
    }
  }

  public void close() {
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
        LOGGER.debug("Worker did not terminate gracefully, forcing shutdown");
        scheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      LOGGER.debug("Interrupted while waiting for worker to terminate, forcing shutdown");
      Thread.currentThread().interrupt();
      scheduler.shutdownNow();
    }
  }
}
