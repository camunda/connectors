/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.azure.email;

import io.camunda.connector.api.inbound.*;
import io.camunda.connector.azure.email.model.config.MsInboundEmailProperties;
import io.camunda.connector.azure.email.util.MicrosoftMailClient;
import java.util.concurrent.atomic.AtomicBoolean;

// TODO: Use ScheduledExecutor
// TODO: Use Delta polling
public class EmailPollingWorker implements Runnable {
  private final InboundConnectorContext context;
  private final Thread thread;
  private final AtomicBoolean shouldStop;
  private final MsInboundEmailProperties properties;

  public EmailPollingWorker(InboundConnectorContext context) {
    this.context = context;
    this.properties = context.bindProperties(MsInboundEmailProperties.class);
    this.shouldStop = new AtomicBoolean(false);
    this.thread = Thread.startVirtualThread(this);
  }

  @Override
  public void run() {
    MicrosoftMailClient client = new MicrosoftMailClient(properties);
    String folderId = client.getFolderId(properties.pollingConfig().folder());
    // FIXME: How do I get the @odata.deltaLink out of the iterator
    String token = null;
    var messageProcessor = new MessageProcessor(properties, client, context);
    while (!shouldStop.get()) {
      token =
          client.getMessages(
              token,
              properties.pollingConfig().folder(),
              properties.pollingConfig().getFilter(),
              messageProcessor::handleMessage);
      if (shouldStop.get()) {
        return;
      }
      try {
        Thread.sleep(properties.pollingConfig().pollingInterval());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
      if (shouldStop.get()) {
        return;
      }
    }
  }

  public void forceShutdown() {
    thread.interrupt();
  }

  public void shutdown() {
    shouldStop.set(true);
  }

  public boolean isShutdown() {
    return thread.getState().equals(Thread.State.TERMINATED);
  }
}
