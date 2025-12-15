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
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.messages.item.move.MovePostRequestBody;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.inbound.*;
import io.camunda.connector.microsoft.email.model.config.EmailProcessingOperation;
import io.camunda.connector.microsoft.email.model.config.MsInboundEmailProperties;
import io.camunda.connector.microsoft.email.model.output.EmailMessage;
import io.camunda.connector.microsoft.email.util.MicrosoftMailClient;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

// TODO: Use ScheduledExecutor
// TODO: Use Delta polling
public class EmailPollingWorker implements Runnable {
  private final InboundConnectorContext context;
  private final Thread thread;
  private final AtomicBoolean shouldStop;
  private final MsInboundEmailProperties properties;
  private MicrosoftMailClient mailClient;

  public EmailPollingWorker(InboundConnectorContext context) {
    this.context = context;
    this.properties = context.bindProperties(MsInboundEmailProperties.class);
    this.shouldStop = new AtomicBoolean(false);
    this.thread = Thread.startVirtualThread(this);
  }

  @Override
  public void run() {
    this.mailClient = new MicrosoftMailClient(properties);
    String folderId = mailClient.getFolderId(properties.pollingConfig().folder());
    // FIXME: How do I get the @odata.deltaLink out of the iterator
    while (!shouldStop.get()) {
      MessageCollectionResponse messageResponse =
          mailClient
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
              .client(mailClient.getGraphclient())
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
                    this.handleMessage(mailClient.getGraphclient(), msg);
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

  private enum ShouldPostproces {
    YES,
    NO
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

  // FIXME: Introduce interface for MSEmail Service
  private void postprocess(GraphServiceClient client, Message message) {
    var partialMessageRequest =
        client
            .users()
            .byUserId(properties.pollingConfig().userId())
            .messages()
            .byMessageId(message.getId());
    switch (properties.operation()) {
      case EmailProcessingOperation.MoveOperation m -> {
        var moveRequest = new MovePostRequestBody();
        moveRequest.setDestinationId(mailClient.getFolderId(m.targetFolder()));
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
    List<Document> attachments = extractAttachments(message);
    var correlationRequest =
        CorrelationRequest.builder()
            .variables(new EmailMessage(message, attachments))
            .messageId(message.getId())
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
    // FIXME: Get attachments
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
