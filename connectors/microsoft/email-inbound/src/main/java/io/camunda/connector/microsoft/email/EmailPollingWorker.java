/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.microsoft.email;

import com.microsoft.graph.core.tasks.PageIterator;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.MessageCollectionResponse;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.inbound.*;
import io.camunda.connector.microsoft.email.model.config.MsInboundEmailProperties;
import io.camunda.connector.microsoft.email.model.output.EmailMessage;
import io.camunda.connector.microsoft.email.util.MicrosoftMailClient;
import java.util.Objects;
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
    while (!shouldStop.get()) {
      MessageCollectionResponse messageResponse =
          client
              .getClient()
              .mailFolders()
              .byMailFolderId(folderId)
              .messages()
              // .delta() // TODO: Check if this works
              .get(
                  requestConfiguration -> {
                    requestConfiguration.headers.add(
                        "Prefer", "outlook.body-content-type=\"text\"");
                    requestConfiguration.queryParameters.filter =
                        properties.pollingConfig().getFilter();
                    requestConfiguration.queryParameters.select = EmailMessage.getSelect();
                    requestConfiguration.queryParameters.top = 10;
                  });
      final var pageIterator =
          new PageIterator.Builder<Message, MessageCollectionResponse>()
              .client(client.getGraphclient())
              .collectionPage(Objects.requireNonNull(messageResponse))
              .collectionPageFactory(MessageCollectionResponse::createFromDiscriminatorValue)
              .requestConfigurator(
                  requestInfo -> {
                    // Re-add the header and query parameters to subsequent requests
                    requestInfo.headers.add("Prefer", "outlook.body-content-type=\"text\"");
                    requestInfo.addQueryParameter(
                        "%24select", properties.pollingConfig().getFilter());
                    requestInfo.addQueryParameter("%24top", 10);
                    return requestInfo;
                  })
              .processPageItemCallback(
                  msg -> {
                    new MessageProcessor(properties, client, context)
                        .handleMessage(new EmailMessage(msg));
                    return shouldStop.get();
                  });
      try {
        pageIterator.build().iterate();
      } catch (ReflectiveOperationException e) {
        context.reportHealth(Health.down(e));
        throw new ConnectorException(e);
      }
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
