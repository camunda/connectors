/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.core.tasks.PageIterator;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.MessageCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.microsoft.email.model.MsInboundEmailProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class EmailPollingWorker implements Runnable {
  private final InboundConnectorContext context;
  private final Thread thread;
  private final AtomicBoolean shouldStop;

  public EmailPollingWorker(InboundConnectorContext context) {
    this.context = context;
    shouldStop = new AtomicBoolean(false);
    thread = Thread.startVirtualThread(this);
  }

  @Override
  public void run() {
    final var properties = context.bindProperties(MsInboundEmailProperties.class);
    // The client credentials flow requires that you request the
    // /.default scope, and pre-configure your permissions on the
    // app registration in Azure. An administrator must grant consent
    // to those permissions beforehand.
    final String[] scopes = new String[] {"https://graph.microsoft.com/.default"};

    final ClientSecretCredential credential =
        new ClientSecretCredentialBuilder()
            .clientId(properties.authentication().clientId())
            .tenantId(properties.authentication().tenantId())
            .clientSecret(properties.authentication().clientSecret())
            .build();
    final GraphServiceClient graphClient = new GraphServiceClient(credential, scopes);
    MessageCollectionResponse messageResponse =
        graphClient
            .users()
            .byUserId(properties.data().id())
            .messages()
            .get(
                requestConfiguration -> {
                  requestConfiguration.headers.add("Prefer", "outlook.body-content-type=\"text\"");
                  requestConfiguration.queryParameters.select = properties.data().selectAsArray();
                  requestConfiguration.queryParameters.top = 10;
                });
    while (!shouldStop.get()) {
      List<Message> messages = new ArrayList<>();
      final var pargeIterator =
          new PageIterator.Builder<Message, MessageCollectionResponse>()
              .client(graphClient)
              .collectionPage(Objects.requireNonNull(messageResponse))
              .collectionPageFactory(MessageCollectionResponse::createFromDiscriminatorValue)
              .requestConfigurator(
                  requestInfo -> {
                    // Re-add the header and query parameters to subsequent requests
                    requestInfo.headers.add("Prefer", "outlook.body-content-type=\"text\"");
                    requestInfo.addQueryParameter(
                        "%24select", new String[] {"sender, subject, body"});
                    requestInfo.addQueryParameter("%24top", 10);
                    return requestInfo;
                  })
              .processPageItemCallback(this::handleMessage);
      try {
        pargeIterator.build().iterate();
      } catch (ReflectiveOperationException e) {
        context.reportHealth(Health.down(e));
        throw new ConnectorException(e);
      }
      try {
        Thread.sleep(properties.data().pollingInterval());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  /**
   * @param message
   * @return - true if the iteration should continue
   */
  public boolean handleMessage(Message message) {

    return true;
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
