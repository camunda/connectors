/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email;

import com.microsoft.graph.models.Message;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.inbound.*;
import io.camunda.connector.microsoft.email.model.config.EmailProcessingOperation;
import io.camunda.connector.microsoft.email.model.config.MsInboundEmailProperties;
import io.camunda.connector.microsoft.email.model.output.EmailMessage;
import io.camunda.connector.microsoft.email.util.MailClient;
import java.util.List;

public class MessageProcessor {
  private final MsInboundEmailProperties properties;
  private final MailClient client;
  private final InboundConnectorContext context;

  public MessageProcessor(
      MsInboundEmailProperties properties, MailClient client, InboundConnectorContext context) {
    this.properties = properties;
    this.client = client;
    this.context = context;
  }

  private void postprocess(EmailMessage message) {

    switch (properties.operation()) {
      case EmailProcessingOperation.MoveOperation m -> {
        client.moveMessage(message, m.targetFolder());
      }
      case EmailProcessingOperation.MarkAsReadOperation markAsRead -> {
        client.markMessageRead(message);
      }
      case EmailProcessingOperation.DeleteOperation d -> {
        client.deleteMessage(message, d.force());
      }
    }
  }

  private ShouldPostprocess correlate(EmailMessage message) {
    List<Document> attachments = client.fetchAttachments(context, message);

    var correlationRequest =
        CorrelationRequest.builder()
            .variables(new EmailMessage(message, attachments))
            .messageId(message.id())
            .build();
    return switch (this.context.correlate(correlationRequest)) {
      case CorrelationResult.Success success -> ShouldPostprocess.YES;
      case CorrelationResult.Failure f -> {
        switch (f.handlingStrategy()) {
          case CorrelationFailureHandlingStrategy.ForwardErrorToUpstream forwardError -> {
            // Log error as we can't propagate up
            yield ShouldPostprocess.NO;
          }
          case CorrelationFailureHandlingStrategy.Ignore ignore -> {
            // Log info
            yield ShouldPostprocess.YES;
          }
        }
      }
    };
  }

  private List<Document> extractAttachments(Message message) {
    // FIXME: Get attachments
    return List.of();
  }

  private enum ShouldPostprocess {
    YES,
    NO
  }

  public void handleMessage(EmailMessage message) {
    var shouldPostprocess =
        switch (context.canActivate(message)) {
          case ActivationCheckResult.Success success -> correlate(message);
          case ActivationCheckResult.Failure.NoMatchingElement e -> {
            if (e.discardUnmatchedEvents()) {
              yield ShouldPostprocess.NO;
            }
            yield ShouldPostprocess.YES;
          }
          case ActivationCheckResult.Failure.TooManyMatchingElements failure -> {
            // TODO: log error
            yield ShouldPostprocess.NO;
          }
        };
    if (shouldPostprocess == ShouldPostprocess.YES) {
      postprocess(message);
    }
  }
}
