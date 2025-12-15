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
import com.microsoft.graph.users.item.messages.item.move.MovePostRequestBody;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.inbound.*;
import io.camunda.connector.microsoft.email.model.config.EmailProcessingOperation;
import io.camunda.connector.microsoft.email.model.config.MsInboundEmailProperties;
import io.camunda.connector.microsoft.email.model.output.EmailMessage;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class EmailPollingWorker implements Runnable {
  private final InboundConnectorContext context;
  private final Thread thread;
  private final AtomicBoolean shouldStop;
  private final MsInboundEmailProperties properties;

  private enum ShouldPostproces {
    YES,
    NO
  }

  public EmailPollingWorker(InboundConnectorContext context) {
    this.context = context;
    this.properties = context.bindProperties(MsInboundEmailProperties.class);
    this.shouldStop = new AtomicBoolean(false);
    this.thread = Thread.startVirtualThread(this);
  }

  @Override
  public void run() {
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
            .byUserId(properties.data().userId())
            .messages()
            .get(
                requestConfiguration -> {
                  requestConfiguration.headers.add("Prefer", "outlook.body-content-type=\"text\"");
                  requestConfiguration.queryParameters.select = properties.data().selectAsArray();
                  requestConfiguration.queryParameters.top = 10;
                });
    while (!shouldStop.get()) {
      final var pageIterator =
          new PageIterator.Builder<Message, MessageCollectionResponse>()
              .client(graphClient)
              .collectionPage(Objects.requireNonNull(messageResponse))
              .collectionPageFactory(MessageCollectionResponse::createFromDiscriminatorValue)
              .requestConfigurator(
                  requestInfo -> {
                    // Re-add the header and query parameters to subsequent requests
                    requestInfo.headers.add("Prefer", "outlook.body-content-type=\"text\"");
                    requestInfo.addQueryParameter("%24select", properties.data().selectAsArray());
                    requestInfo.addQueryParameter("%24top", 10);
                    return requestInfo;
                  })
              .processPageItemCallback(
                  msg -> {
                    this.handleMessage(graphClient, msg);
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
        Thread.sleep(properties.data().pollingInterval());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
      if (shouldStop.get()) {
        return;
      }
    }
  }

  private void handleMessage(GraphServiceClient client, Message message) {
    var shouldPostprocess =
        switch (context.canActivate(new EmailMessage(message))) {
          case ActivationCheckResult.Success success -> correlate(message);
          case ActivationCheckResult.Failure.NoMatchingElement e -> {
            if (e.discardUnmatchedEvents()) {
              yield ShouldPostproces.NO;
            }
            yield ShouldPostproces.YES;
          }
          case ActivationCheckResult.Failure.TooManyMatchingElements failure -> {
            // TODO: log error
            yield ShouldPostproces.NO;
          }
        };
    if (shouldPostprocess == ShouldPostproces.YES) {
      postprocess(client, message);
    }
  }

  private void postprocess(GraphServiceClient client, Message message) {
    var partialMessageRequest =
        client.users().byUserId(properties.data().userId()).messages().byMessageId(message.getId());
    switch (properties.operation()) {
      case EmailProcessingOperation.MoveOperation m -> {
        var moveRequest = new MovePostRequestBody();
        // FIXME: We should consider mapping from targetFolder name
        // to ID, because AFAICT folder IDs are not exposed in the Outlook UI
        moveRequest.setDestinationId(m.targetFolder());
        partialMessageRequest.move().post(moveRequest);
      }
      case EmailProcessingOperation.MarkAsReadOperation markAsRead -> {
        message.setIsRead(true);
        partialMessageRequest.patch(message);
      }
      case EmailProcessingOperation.DeleteOperation d -> {
        if (d.force()) {
          partialMessageRequest.delete();
        } else {
          var moveRequest = new MovePostRequestBody();
          // See well-known folder names here:
          // https://learn.microsoft.com/en-us/graph/api/resources/mailfolder?view=graph-rest-1.0
          moveRequest.setDestinationId("deletedItems");
          partialMessageRequest.move().post(moveRequest);
        }
      }
    }
  }

  private ShouldPostproces correlate(Message message) {
    List<Document> attachements = extractAttachments(message);
    var correlationRequest =
        CorrelationRequest.builder()
            .variables(new EmailMessage(message, attachements))
            .messageId(message.getInternetMessageId())
            .build();
    return switch (this.context.correlate(correlationRequest)) {
      case CorrelationResult.Success success -> ShouldPostproces.YES;
      case CorrelationResult.Failure f -> {
        switch (f.handlingStrategy()) {
          case CorrelationFailureHandlingStrategy.ForwardErrorToUpstream forwardError -> {
            // Log error as we can't propagate up
            yield ShouldPostproces.NO;
          }
          case CorrelationFailureHandlingStrategy.Ignore ignore -> {
            // Log info
            yield ShouldPostproces.YES;
          }
        }
      }
    };
  }

  private List<Document> extractAttachments(Message message) {
    return List.of();
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
