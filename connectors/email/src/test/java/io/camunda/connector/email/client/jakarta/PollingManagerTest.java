/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.email.authentication.SimpleAuthentication;
import io.camunda.connector.email.client.jakarta.inbound.PollingManager;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import io.camunda.connector.email.inbound.model.EmailInboundConnectorProperties;
import io.camunda.connector.email.inbound.model.EmailListenerConfig;
import io.camunda.connector.email.inbound.model.HandlingStrategy;
import io.camunda.connector.email.inbound.model.PollUnseen;
import jakarta.mail.*;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class PollingManagerTest {

  @Test
  void poll() throws MessagingException {
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

    TestImapMessage message =
        TestImapMessage.builder()
            .setTo(List.of("recipient@example.com"))
            .setMessageId("messageId")
            .setFrom("sender")
            .setSubject("subject")
            .setBody("body")
            .createTestMessage();

    when(connectorContext.bindProperties(any())).thenReturn(emailInboundConnectorProperties);
    when(emailInboundConnectorProperties.authentication()).thenReturn(authentication);
    when(emailInboundConnectorProperties.data()).thenReturn(emailListenerConfig);
    when(jakartaUtils.createSession(any())).thenReturn(session);
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

    verify(connectorContext, times(1)).correlateWithResult(argThat(Objects::nonNull));
  }
}
