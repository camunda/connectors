/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.email;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.inbound.*;
import io.camunda.connector.azure.email.model.config.EmailProcessingOperation;
import io.camunda.connector.azure.email.model.config.MsInboundEmailProperties;
import io.camunda.connector.azure.email.model.output.EmailMessage;
import io.camunda.connector.azure.email.util.MailClient;
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

  public void handleMessage(EmailMessage message) {
    var shouldPostprocess =
        switch (context.canActivate(message)) {
          case ActivationCheckResult.Success _ -> correlate(message);
          case ActivationCheckResult.Failure.NoMatchingElement e -> {
            if (e.discardUnmatchedEvents()) {
              yield ShouldPostproces.NO;
            }
            yield ShouldPostproces.YES;
          }
          case ActivationCheckResult.Failure.TooManyMatchingElements _ -> {
            // TODO: log error
            yield ShouldPostproces.NO;
          }
        };
    if (shouldPostprocess == ShouldPostproces.YES) {
      postprocess(message);
    }
  }

  private void postprocess(EmailMessage message) {

    switch (properties.operation()) {
      case EmailProcessingOperation.MoveOperation m -> {
        client.moveMessage(message, m.targetFolder());
      }
      case EmailProcessingOperation.MarkAsReadOperation _ -> {
        client.markMessageRead(message);
      }
      case EmailProcessingOperation.DeleteOperation d -> {
        client.deleteMessage(message, d.force());
      }
    }
  }

  private ShouldPostproces correlate(EmailMessage message) {
    List<Document> attachments = client.fetchAttachments(message);
    var correlationRequest =
        CorrelationRequest.builder()
            .variables(new EmailMessage(message, attachments))
            .messageId(message.id())
            .build();
    return switch (this.context.correlate(correlationRequest)) {
      case CorrelationResult.Success _ -> ShouldPostproces.YES;
      case CorrelationResult.Failure f -> {
        switch (f.handlingStrategy()) {
          case CorrelationFailureHandlingStrategy.ForwardErrorToUpstream _ -> {
            // Log error as we can't propagate up
            yield ShouldPostproces.NO;
          }
          case CorrelationFailureHandlingStrategy.Ignore _ -> {
            // Log info
            yield ShouldPostproces.YES;
          }
        }
      }
    };
  }

  private enum ShouldPostproces {
    YES,
    NO
  }
}
