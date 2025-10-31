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
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.email.authentication.SimpleAuthentication;
import io.camunda.connector.email.client.jakarta.inbound.PollingManager;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import io.camunda.connector.email.inbound.model.EmailInboundConnectorProperties;
import io.camunda.connector.email.inbound.model.EmailListenerConfig;
import io.camunda.connector.email.inbound.model.HandlingStrategy;
import io.camunda.connector.email.inbound.model.PollUnseen;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.*;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Test;

class PollingManagerTest {

  @Test
  void poll() throws MessagingException, IOException {
    InboundConnectorContext connectorContext = mock(InboundConnectorContext.class);
    EmailInboundConnectorProperties emailInboundConnectorProperties =
        mock(EmailInboundConnectorProperties.class);
    Authentication authentication = mock(SimpleAuthentication.class);
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
}
