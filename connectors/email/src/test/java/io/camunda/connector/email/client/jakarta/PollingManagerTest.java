/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.camunda.connector.api.inbound.ActivationCheckResult;
import io.camunda.connector.api.inbound.ActivityBuilder;
import io.camunda.connector.api.inbound.CorrelationRequest;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.email.authentication.InboundAuthentication;
import io.camunda.connector.email.authentication.SimpleAuthentication;
import io.camunda.connector.email.client.jakarta.inbound.PollingManager;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import io.camunda.connector.email.inbound.model.EmailInboundConnectorProperties;
import io.camunda.connector.email.inbound.model.EmailListenerConfig;
import io.camunda.connector.email.inbound.model.HandlingStrategy;
import io.camunda.connector.email.inbound.model.PollUnseen;
import io.camunda.connector.email.response.ReadEmailResponse;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.*;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

class PollingManagerTest {

  @Test
  void poll() throws MessagingException, IOException {
    InboundConnectorContext connectorContext = mock(InboundConnectorContext.class);
    EmailInboundConnectorProperties emailInboundConnectorProperties =
        mock(EmailInboundConnectorProperties.class);
    InboundAuthentication authentication = mock(SimpleAuthentication.class);
    Folder folder = mock(Folder.class);
    Session session = mock(Session.class);
    Store store = mock(Store.class);
    EmailListenerConfig emailListenerConfig = mock(EmailListenerConfig.class);
    PollUnseen pollUnseen = mock(PollUnseen.class);
    JakartaUtils jakartaUtils = mock(JakartaUtils.class);

    Multipart multipart = new MimeMultipart();
    MimeBodyPart textContent = new MimeBodyPart();
    textContent.setText("body");
    multipart.addBodyPart(textContent);
    BodyPart attachment = new MimeBodyPart();
    try (FileInputStream fileInputStream = new FileInputStream("src/test/resources/img/img.png")) {
      DataSource dataSource =
          new ByteArrayDataSource(fileInputStream, ContentType.IMAGE_PNG.getMimeType());
      attachment.setDataHandler(new DataHandler(dataSource));
      attachment.setFileName("any.svg");
      multipart.addBodyPart(attachment);
    }

    TestImapMessage message =
        TestImapMessage.builder()
            .setTo(List.of("recipient@example.com"))
            .setMessageId("messageId")
            .setFrom("sender")
            .setSubject("subject")
            .setBody(multipart)
            .createTestMessage();

    when(connectorContext.bindProperties(any())).thenReturn(emailInboundConnectorProperties);
    when(connectorContext.canActivate(any()))
        .thenReturn(new ActivationCheckResult.Success.CanActivate(null));
    when(connectorContext.correlate(any()))
        .thenReturn(new CorrelationResult.Success.ProcessInstanceCreated(null, null, null));
    when(emailInboundConnectorProperties.authentication()).thenReturn(authentication);
    when(emailInboundConnectorProperties.data()).thenReturn(emailListenerConfig);
    when(jakartaUtils.createSession(any(), any())).thenReturn(session);
    when(jakartaUtils.findImapFolder(any(), any())).thenReturn(folder);
    when(session.getStore()).thenReturn(store);
    when(emailListenerConfig.pollingConfig()).thenReturn(pollUnseen);
    when(pollUnseen.handlingStrategy()).thenReturn(HandlingStrategy.READ);
    PollingManager pollingManager = PollingManager.create(connectorContext, jakartaUtils);

    when(folder.getMessages()).thenReturn(new Message[] {message});
    when(folder.search(any(), any())).thenReturn(new Message[] {message});
    when(jakartaUtils.createEmail(any())).thenCallRealMethod();
    when(jakartaUtils.createBodylessEmail(any())).thenCallRealMethod();
    pollingManager.poll();

    verify(connectorContext, times(1)).correlate(argThat(Objects::nonNull));
  }

  @Test
  @SuppressWarnings("unchecked")
  void poll_skipsFailedAttachmentAndStillCorrelates_whenDocumentUploadThrows()
      throws MessagingException, IOException {
    InboundConnectorContext connectorContext = mock(InboundConnectorContext.class);
    EmailInboundConnectorProperties emailInboundConnectorProperties =
        mock(EmailInboundConnectorProperties.class);
    InboundAuthentication authentication = mock(SimpleAuthentication.class);
    Folder folder = mock(Folder.class);
    Session session = mock(Session.class);
    Store store = mock(Store.class);
    EmailListenerConfig emailListenerConfig = mock(EmailListenerConfig.class);
    PollUnseen pollUnseen = mock(PollUnseen.class);
    JakartaUtils jakartaUtils = mock(JakartaUtils.class);

    // Capture logged severities by intercepting log() calls before the code runs
    List<Severity> loggedSeverities = new ArrayList<>();
    doAnswer(
            invocation -> {
              java.util.function.Consumer<ActivityBuilder> consumer = invocation.getArgument(0);
              ActivityBuilder builder = mock(ActivityBuilder.class);
              doAnswer(
                      sev -> {
                        loggedSeverities.add(sev.getArgument(0));
                        return builder;
                      })
                  .when(builder)
                  .withSeverity(any());
              when(builder.withTag(any())).thenReturn(builder);
              when(builder.withMessage(any(String.class))).thenReturn(builder);
              when(builder.withMessage(any(Throwable.class))).thenReturn(builder);
              consumer.accept(builder);
              return null;
            })
        .when(connectorContext)
        .log(ArgumentMatchers.<java.util.function.Consumer<ActivityBuilder>>any());

    Multipart multipart = new MimeMultipart();
    MimeBodyPart textContent = new MimeBodyPart();
    textContent.setText("body");
    multipart.addBodyPart(textContent);
    BodyPart attachment = new MimeBodyPart();
    try (FileInputStream fileInputStream = new FileInputStream("src/test/resources/img/img.png")) {
      DataSource dataSource =
          new ByteArrayDataSource(fileInputStream, ContentType.IMAGE_PNG.getMimeType());
      attachment.setDataHandler(new DataHandler(dataSource));
      attachment.setFileName("large-file.pdf");
      multipart.addBodyPart(attachment);
    }

    TestImapMessage message =
        TestImapMessage.builder()
            .setTo(List.of("recipient@example.com"))
            .setMessageId("messageId")
            .setFrom("sender")
            .setSubject("subject")
            .setBody(multipart)
            .createTestMessage();

    when(connectorContext.bindProperties(any())).thenReturn(emailInboundConnectorProperties);
    when(connectorContext.canActivate(any()))
        .thenReturn(new ActivationCheckResult.Success.CanActivate(null));
    when(connectorContext.correlate(any()))
        .thenReturn(new CorrelationResult.Success.ProcessInstanceCreated(null, null, null));
    when(connectorContext.create(any()))
        .thenThrow(new RuntimeException("Document exceeds size limit"));
    when(emailInboundConnectorProperties.authentication()).thenReturn(authentication);
    when(emailInboundConnectorProperties.data()).thenReturn(emailListenerConfig);
    when(jakartaUtils.createSession(any(), any())).thenReturn(session);
    when(jakartaUtils.findImapFolder(any(), any())).thenReturn(folder);
    when(session.getStore()).thenReturn(store);
    when(emailListenerConfig.pollingConfig()).thenReturn(pollUnseen);
    when(pollUnseen.handlingStrategy()).thenReturn(HandlingStrategy.READ);
    PollingManager pollingManager = PollingManager.create(connectorContext, jakartaUtils);

    when(folder.getMessages()).thenReturn(new Message[] {message});
    when(folder.search(any(), any())).thenReturn(new Message[] {message});
    when(jakartaUtils.createEmail(any())).thenCallRealMethod();
    when(jakartaUtils.createBodylessEmail(any())).thenCallRealMethod();
    pollingManager.poll();

    // correlation must still happen — upload failure must not block processing
    ArgumentCaptor<CorrelationRequest> correlationCaptor =
        ArgumentCaptor.forClass(CorrelationRequest.class);
    verify(connectorContext, times(1)).correlate(correlationCaptor.capture());
    // health must be UP — the exception was handled, not propagated to the top-level catch-all
    verify(connectorContext).reportHealth(argThat(h -> h.equals(Health.up())));
    // the upload failure must be surfaced in the activity log with WARNING severity
    Assertions.assertTrue(
        loggedSeverities.contains(Severity.WARNING),
        "Expected an ERROR activity log entry for the failed document upload");
    // the error must be present in the correlation payload so process modelers can handle it
    ReadEmailResponse response = (ReadEmailResponse) correlationCaptor.getValue().getVariables();
    Assertions.assertNotNull(response.errors());
    Assertions.assertFalse(
        response.errors().isEmpty(), "Expected errors to be non-empty in the correlation payload");
    Assertions.assertTrue(
        response.errors().stream().anyMatch(e -> e.contains("large-file.pdf")),
        "Expected an error message referencing 'large-file.pdf' in the payload");
  }
}
