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
import io.camunda.connector.microsoft.email.model.output.EmailMessage;
import io.camunda.connector.microsoft.email.util.MailClient;
import java.util.List;

public class MessageProcessor {
  private final EmailProcessingOperation operation;
  private final MailClient client;
  private final InboundConnectorContext context;

  public MessageProcessor(
      EmailProcessingOperation operation, MailClient client, InboundConnectorContext context) {
    this.operation = operation;
    this.client = client;
    this.context = context;
  }

  public void handleMessage(EmailMessage message) {
    context.log(
        activity ->
            activity
                .withSeverity(Severity.INFO)
                .withTag("new-email")
                .withMessage("Processing email: " + message.id()));
    var shouldPostprocess =
        switch (context.canActivate(message)) {
          case ActivationCheckResult.Success success -> correlate(message);
          case ActivationCheckResult.Failure.NoMatchingElement e -> {
            if (e.discardUnmatchedEvents()) {
              context.log(
                  activity ->
                      activity
                          .withSeverity(Severity.INFO)
                          .withTag("NoMatchingElement")
                          .withMessage(
                              "No matching activation condition. Discarding unmatched email: "
                                  + message.id()));
              yield ShouldPostprocess.NO;
            }
            context.log(
                activity ->
                    activity
                        .withSeverity(Severity.INFO)
                        .withTag("NoMatchingElement")
                        .withMessage(
                            "No matching activation condition. Not discarding unmatched email: "
                                + message.id()));
            yield ShouldPostprocess.YES;
          }
          case ActivationCheckResult.Failure.TooManyMatchingElements failure -> {
            context.log(
                activity ->
                    activity
                        .withSeverity(Severity.ERROR)
                        .withTag("TooManyMatchingElements")
                        .withMessage(
                            "Too many matching activation conditions. Email: " + message.id()));
            yield ShouldPostprocess.NO;
          }
        };
    if (shouldPostprocess == ShouldPostprocess.YES) {
      postprocess(message);
    }
  }

  private void postprocess(EmailMessage message) {

    switch (operation) {
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
            context.log(
                activity ->
                    activity
                        .withSeverity(Severity.ERROR)
                        .withTag(ActivityLogTag.MESSAGE)
                        .withMessage(
                            "Error processing email: "
                                + message.id()
                                + ", message: "
                                + f.message()));
            yield ShouldPostprocess.NO;
          }
          case CorrelationFailureHandlingStrategy.Ignore ignore -> {
            context.log(
                activity ->
                    activity
                        .withSeverity(Severity.INFO)
                        .withTag(ActivityLogTag.MESSAGE)
                        .withMessage(
                            "No correlation condition was met for email: "
                                + message.id()
                                + ". `Ignore unmatched event` was selected. Continuing.."));
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
}
