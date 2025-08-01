/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta.inbound;

import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.api.inbound.*;
import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.email.client.jakarta.models.Email;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import io.camunda.connector.email.exception.EmailConnectorException;
import io.camunda.connector.email.inbound.model.*;
import io.camunda.connector.email.response.ReadEmailResponse;
import io.camunda.document.Document;
import io.camunda.document.store.DocumentCreationRequest;
import jakarta.mail.*;
import jakarta.mail.search.FlagTerm;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.eclipse.angus.mail.imap.IMAPMessage;
import org.eclipse.angus.mail.util.MailConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PollingManager {

  private static final Logger log = LoggerFactory.getLogger(PollingManager.class);
  private final InboundConnectorContext connectorContext;
  private final EmailListenerConfig emailListenerConfig;
  private final JakartaUtils jakartaUtils;
  private final Folder folder;
  private final Store store;
  private final Authentication authentication;

  public PollingManager(
      InboundConnectorContext connectorContext,
      EmailListenerConfig emailListenerConfig,
      Authentication authentication,
      JakartaUtils jakartaUtils,
      Folder folder,
      Store store) {
    this.connectorContext = connectorContext;
    this.emailListenerConfig = emailListenerConfig;
    this.authentication = authentication;
    this.jakartaUtils = jakartaUtils;
    this.folder = folder;
    this.store = store;
  }

  private static ReadEmailResponse createResponse(Email email, List<Document> documents) {
    return new ReadEmailResponse(
        email.messageId(),
        email.from(),
        email.headers(),
        email.subject(),
        email.size(),
        email.body().bodyAsPlainText(),
        email.body().bodyAsHtml(),
        documents,
        email.receivedAt());
  }

  public static PollingManager create(
      InboundConnectorContext connectorContext, JakartaUtils jakartaUtils) {
    Store store = null;
    Folder folder = null;
    try {
      EmailInboundConnectorProperties emailInboundConnectorProperties =
          connectorContext.bindProperties(EmailInboundConnectorProperties.class);
      Authentication authentication = emailInboundConnectorProperties.authentication();
      EmailListenerConfig emailListenerConfig = emailInboundConnectorProperties.data();
      Session session =
          jakartaUtils.createSession(emailInboundConnectorProperties.data().imapConfig());
      store = session.getStore();
      jakartaUtils.connectStore(store, authentication);
      folder = jakartaUtils.findImapFolder(store, emailListenerConfig.folderToListen());
      folder.open(Folder.READ_WRITE);
      if (emailListenerConfig.pollingConfig().handlingStrategy().equals(HandlingStrategy.MOVE)
          && (Objects.isNull(emailListenerConfig.pollingConfig().targetFolder())
              || emailListenerConfig.pollingConfig().targetFolder().isBlank()))
        throw new RuntimeException(
            "If the post process action is `MOVE`, a target folder must be specified");
      return new PollingManager(
          connectorContext, emailListenerConfig, authentication, jakartaUtils, folder, store);
    } catch (AuthenticationFailedException exception) {
      connectorContext.log(
          Activity.level(Severity.ERROR)
              .tag("Authentication error")
              .message("Authentication failed"));
      throw new RuntimeException(exception);
    } catch (MailConnectException exception) {
      connectorContext.log(
          Activity.level(Severity.ERROR).tag("Connection error").message(exception.getMessage()));
      throw new EmailConnectorException(exception);
    } catch (MessagingException e) {
      try {
        if (folder != null && folder.isOpen()) {
          folder.close();
        }
        if (store != null && store.isConnected()) {
          store.close();
        }
      } catch (MessagingException ex) {
        throw new EmailConnectorException(ex);
      }
      throw new EmailConnectorException(e);
    }
  }

  private List<Document> createDocumentList(Email email) {
    return email.body().attachments().stream()
        .map(
            document ->
                this.connectorContext.create(
                    DocumentCreationRequest.from(document.inputStream())
                        .contentType(document.contentType())
                        .fileName(document.name())
                        .build()))
        .toList();
  }

  private boolean correlate(Email email) {
    List<Document> documents = createDocumentList(email);
    CorrelationRequest correlationRequest =
        CorrelationRequest.builder()
            .variables(createResponse(email, documents))
            .messageId(email.messageId())
            .build();
    return switch (this.connectorContext.correlate(correlationRequest)) {
      case CorrelationResult.Failure failure ->
          switch (failure.handlingStrategy()) {
            case CorrelationFailureHandlingStrategy.ForwardErrorToUpstream ignored -> {
              this.connectorContext.log(
                  Activity.level(Severity.ERROR)
                      .tag("ForwardErrorToUpstream")
                      .message(
                          "Error processing mail: %s, message %s"
                              .formatted(email.messageId(), failure.message())));
              yield false;
            }
            case CorrelationFailureHandlingStrategy.Ignore ignored -> {
              this.connectorContext.log(
                  Activity.level(Severity.INFO)
                      .tag("Ignore")
                      .message(
                          "No activation condition was met for email: %s. `Consume unmatched event` was selected. Continuing.."
                              .formatted(email.messageId())));
              yield true;
            }
          };
      case CorrelationResult.Success ignored -> {
        this.connectorContext.log(
            Activity.level(Severity.INFO)
                .tag("Success")
                .message("Correlated email: %s".formatted(email.messageId())));
        yield true;
      }
    };
  }

  public void poll() {
    this.prepareForPolling();
    switch (this.emailListenerConfig.pollingConfig()) {
      case PollAll pollAll -> pollAllAndProcess(pollAll);
      case PollUnseen pollUnseen -> pollUnseenAndProcess(pollUnseen);
    }
  }

  private void prepareForPolling() {
    if (!this.store.isConnected()) {
      try {
        this.jakartaUtils.connectStore(store, authentication);
      } catch (MessagingException e) {
        log.error("Could not reconnect to store", e);
        throw new RuntimeException("Could not reconnect to store");
      }
    }
    if (!this.folder.isOpen()) {
      try {
        this.folder.open(Folder.READ_WRITE);
      } catch (MessagingException e) {
        log.error("Could not reopen folder", e);
        throw new RuntimeException("Could not reopen folder");
      }
    }
  }

  private void pollAllAndProcess(PollAll pollAll) {
    try {
      Message[] messages = this.folder.getMessages();
      Arrays.stream(messages).forEach(message -> this.processMail((IMAPMessage) message, pollAll));
    } catch (Exception e) {
      this.connectorContext.log(
          Activity.level(Severity.ERROR).tag("mail-polling").message(e.getMessage()));
      this.connectorContext.cancel(
          ConnectorRetryException.builder()
              .cause(e)
              .message(e.getMessage())
              .retries(1)
              .backoffDuration(Duration.of(5, ChronoUnit.SECONDS))
              .build());
    }
  }

  private void pollUnseenAndProcess(PollUnseen pollUnseen) {
    try {
      FlagTerm unseenFlagTerm = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
      Message[] unseenMessages = this.folder.search(unseenFlagTerm, this.folder.getMessages());
      Arrays.stream(unseenMessages)
          .forEach(message -> this.processMail((IMAPMessage) message, pollUnseen));
    } catch (Exception e) {
      this.connectorContext.log(
          Activity.level(Severity.ERROR).tag("mail-polling").message(e.getMessage()));
      this.connectorContext.cancel(
          ConnectorRetryException.builder()
              .cause(e)
              .message(e.getMessage())
              .retries(1)
              .backoffDuration(Duration.of(5, ChronoUnit.SECONDS))
              .build());
    }
  }

  private void processMail(IMAPMessage message, PollingConfig pollingConfig) {
    // Setting `peek` to true prevents the library to trigger any side effects when reading the
    // message, such as marking it as read
    message.setPeek(true);
    Email email = this.jakartaUtils.createEmail(message);
    boolean markAsProcessed = process(email);
    message.setPeek(false);
    if (markAsProcessed) {
      switch (pollingConfig.handlingStrategy()) {
        case READ -> this.jakartaUtils.markAsSeen(message);
        case DELETE -> this.jakartaUtils.markAsDeleted(message);
        case MOVE -> {
          this.jakartaUtils.markAsSeen(message);
          this.jakartaUtils.moveMessage(this.store, message, pollingConfig.targetFolder());
        }
      }
    }
  }

  private boolean process(Email email) {
    this.connectorContext.log(
        Activity.level(Severity.INFO)
            .tag("new-email")
            .message("Processing email: %s".formatted(email.messageId())));
    ActivationCheckResult activationCheckResult =
        this.connectorContext.canActivate(createResponse(email, List.of()));
    return switch (activationCheckResult) {
      case ActivationCheckResult.Failure failure ->
          switch (failure) {
            case ActivationCheckResult.Failure.NoMatchingElement noMatchingElement -> {
              if (noMatchingElement.discardUnmatchedEvents()) {
                this.connectorContext.log(
                    Activity.level(Severity.INFO)
                        .tag("NoMatchingElement")
                        .message(
                            "No matching activation condition. Discarding unmatched email: %s"
                                .formatted(email.messageId())));
                yield true;
              } else {
                this.connectorContext.log(
                    Activity.level(Severity.INFO)
                        .tag("NoMatchingElement")
                        .message(
                            "No matching activation condition. Not discarding unmatched email: %s"
                                .formatted(email.messageId())));
                yield false;
              }
            }
            case ActivationCheckResult.Failure.TooManyMatchingElements ignored -> {
              this.connectorContext.log(
                  Activity.level(Severity.ERROR)
                      .tag("TooManyMatchingElements")
                      .message(
                          "Too many matching activation conditions. Email: %s"
                              .formatted(email.messageId())));
              yield false;
            }
          };
      case ActivationCheckResult.Success ignored -> correlate(email);
    };
  }

  public long delay() {
    return this.emailListenerConfig.pollingWaitTime().getSeconds();
  }

  public void stop() {
    try {
      this.folder.close();
      this.store.close();
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }
}
