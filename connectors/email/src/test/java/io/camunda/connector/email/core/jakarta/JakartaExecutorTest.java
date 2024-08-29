/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.core.jakarta;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.email.authentication.SimpleAuthentication;
import io.camunda.connector.email.outbound.model.EmailRequest;
import io.camunda.connector.email.outbound.protocols.Pop3;
import io.camunda.connector.email.outbound.protocols.Protocol;
import io.camunda.connector.email.outbound.protocols.Smtp;
import io.camunda.connector.email.outbound.protocols.actions.*;
import io.camunda.connector.email.response.ListEmailsResponse;
import io.camunda.connector.email.response.ReadEmailResponse;
import jakarta.mail.*;
import java.io.IOException;
import java.util.*;
import org.eclipse.angus.mail.pop3.POP3Folder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JakartaExecutorTest {

  @Test
  void executeSmtpSendEmail() throws MessagingException {

    JakartaUtils sessionFactory = mock(JakartaUtils.class);
    JakartaActionExecutor actionExecutor = JakartaActionExecutor.create(sessionFactory);

    EmailRequest emailRequest = mock(EmailRequest.class);
    SmtpSendEmail smtpSendEmail = mock(SmtpSendEmail.class);
    Authentication simpleAuthentication = mock(SimpleAuthentication.class);
    Protocol protocol = mock(Smtp.class);
    Session session = mock(Session.class);
    Transport transport = mock(Transport.class);

    // Authentication
    when(simpleAuthentication.isSecuredAuth()).thenReturn(true);
    when(simpleAuthentication.getUser()).thenReturn(Optional.of("user"));
    when(simpleAuthentication.getSecret()).thenReturn(Optional.of("secret"));
    doNothing().when(transport).connect(any(), any());

    when(emailRequest.getAuthentication()).thenReturn(simpleAuthentication);
    when(session.getProperties()).thenReturn(new Properties());
    when(emailRequest.getData()).thenReturn(protocol);
    when(protocol.getProtocolAction()).thenReturn(smtpSendEmail);
    when(sessionFactory.createSession(any(), any())).thenReturn(session);
    when(smtpSendEmail.getTo()).thenReturn(List.of("to"));
    when(smtpSendEmail.getCc()).thenReturn(List.of("cc"));
    when(smtpSendEmail.getCci()).thenReturn(List.of("bcc"));
    when(simpleAuthentication.getSender()).thenReturn("myself");
    when(smtpSendEmail.getBody()).thenReturn("body");
    when(session.getTransport()).thenReturn(transport);

    actionExecutor.execute(emailRequest);

    verify(transport, times(1))
        .sendMessage(
            argThat(
                argument -> {
                  try {
                    return Arrays.stream(argument.getFrom())
                            .allMatch(address -> address.toString().contains("myself"))
                        && argument.getContent().toString().contains("body");
                  } catch (MessagingException | IOException e) {
                    throw new RuntimeException(e);
                  }
                }),
            argThat(
                argument ->
                    Arrays.toString(argument).contains("to")
                        && Arrays.toString(argument).contains("cc")
                        && Arrays.toString(argument).contains("bcc")));
  }

  @Test
  void executePop3ListEmails() throws MessagingException {
    JakartaUtils sessionFactory = mock(JakartaUtils.class);
    JakartaActionExecutor actionExecutor = JakartaActionExecutor.create(sessionFactory);

    EmailRequest emailRequest = mock(EmailRequest.class);
    Pop3ListEmails pop3ListEmails = mock(Pop3ListEmails.class);
    Authentication simpleAuthentication = mock(SimpleAuthentication.class);
    Protocol protocol = mock(Pop3.class);
    Session session = mock(Session.class);
    Store store = mock(Store.class);
    POP3Folder pop3Folder = mock(POP3Folder.class);
    Message message = mock(Message.class);

    // Authentication
    when(simpleAuthentication.isSecuredAuth()).thenReturn(true);
    when(simpleAuthentication.getUser()).thenReturn(Optional.of("user"));
    when(simpleAuthentication.getSecret()).thenReturn(Optional.of("secret"));
    doNothing().when(store).connect(any(), any());

    when(store.getDefaultFolder()).thenReturn(pop3Folder);

    doNothing().when(pop3Folder).open(Folder.READ_ONLY);

    when(pop3ListEmails.getMaxToBeRead()).thenReturn(10);
    when(pop3Folder.getMessages(1, 10)).thenReturn(new Message[] {message});

    when(emailRequest.getAuthentication()).thenReturn(simpleAuthentication);
    when(sessionFactory.retrieveEmailComparator(any(), any())).thenReturn((o1, o2) -> 1);
    when(message.getHeader(any())).thenReturn(new String[] {"id"});
    when(session.getProperties()).thenReturn(new Properties());
    when(session.getStore()).thenReturn(store);
    when(emailRequest.getData()).thenReturn(protocol);
    when(protocol.getProtocolAction()).thenReturn(pop3ListEmails);
    when(sessionFactory.createSession(any(), any())).thenReturn(session);
    doNothing().when(store).connect(any(), any());

    Object object = actionExecutor.execute(emailRequest);

    Assertions.assertInstanceOf(List.class, object);
    Assertions.assertInstanceOf(ListEmailsResponse.class, ((List) object).getFirst());
  }

  @Test
  void executePop3ReadEmail() throws MessagingException, IOException {
    JakartaUtils sessionFactory = mock(JakartaUtils.class);
    JakartaActionExecutor actionExecutor = JakartaActionExecutor.create(sessionFactory);

    EmailRequest emailRequest = mock(EmailRequest.class);
    Pop3ReadEmail pop3ReadEmail = mock(Pop3ReadEmail.class);
    Authentication simpleAuthentication = mock(SimpleAuthentication.class);
    Protocol protocol = mock(Pop3.class);
    Session session = mock(Session.class);
    Store store = mock(Store.class);
    POP3Folder pop3Folder = mock(POP3Folder.class);
    Message message = mock(Message.class);

    when(sessionFactory.createSession(any(), any())).thenReturn(session);

    // Authentication
    when(simpleAuthentication.isSecuredAuth()).thenReturn(true);
    when(simpleAuthentication.getUser()).thenReturn(Optional.of("user"));
    when(simpleAuthentication.getSecret()).thenReturn(Optional.of("secret"));
    doNothing().when(store).connect(any(), any());

    when(store.getDefaultFolder()).thenReturn(pop3Folder);
    when(pop3Folder.search(any())).thenReturn(new Message[] {message});
    when(message.getHeader(any())).thenReturn(new String[] {"10"});
    when(pop3ReadEmail.getMessageId()).thenReturn("10");
    when(message.getContent()).thenReturn("string");
    when(message.isMimeType("text/plain")).thenReturn(true);
    when(emailRequest.getAuthentication()).thenReturn(simpleAuthentication);
    when(session.getProperties()).thenReturn(new Properties());
    when(session.getStore()).thenReturn(store);
    when(emailRequest.getData()).thenReturn(protocol);
    when(protocol.getProtocolAction()).thenReturn(pop3ReadEmail);
    doNothing().when(store).connect(any(), any());

    Object object = actionExecutor.execute(emailRequest);

    Assertions.assertInstanceOf(ReadEmailResponse.class, object);
  }

  @Test
  void executePop3DeleteEmail() throws MessagingException, IOException {
    JakartaUtils sessionFactory = mock(JakartaUtils.class);
    JakartaActionExecutor actionExecutor = JakartaActionExecutor.create(sessionFactory);

    EmailRequest emailRequest = mock(EmailRequest.class);
    Pop3DeleteEmail pop3DeleteEmail = mock(Pop3DeleteEmail.class);
    Authentication simpleAuthentication = mock(SimpleAuthentication.class);
    Protocol protocol = mock(Pop3.class);
    Session session = mock(Session.class);
    Store store = mock(Store.class);
    POP3Folder pop3Folder = mock(POP3Folder.class);
    Message message = mock(Message.class);

    when(sessionFactory.createSession(any(), any())).thenReturn(session);

    // Authentication
    when(simpleAuthentication.isSecuredAuth()).thenReturn(true);
    when(simpleAuthentication.getUser()).thenReturn(Optional.of("user"));
    when(simpleAuthentication.getSecret()).thenReturn(Optional.of("secret"));
    doNothing().when(store).connect(any(), any());

    when(store.getDefaultFolder()).thenReturn(pop3Folder);
    when(pop3Folder.search(any())).thenReturn(new Message[] {message});
    when(message.getHeader(any())).thenReturn(new String[] {"10"});
    when(pop3DeleteEmail.getMessageId()).thenReturn("10");
    when(message.getContent()).thenReturn("string");
    when(message.isMimeType("text/plain")).thenReturn(true);
    when(emailRequest.getAuthentication()).thenReturn(simpleAuthentication);
    when(session.getProperties()).thenReturn(new Properties());
    when(session.getStore()).thenReturn(store);
    when(emailRequest.getData()).thenReturn(protocol);
    when(protocol.getProtocolAction()).thenReturn(pop3DeleteEmail);
    doNothing().when(store).connect(any(), any());

    Object object = actionExecutor.execute(emailRequest);

    Assertions.assertTrue((Boolean) object);
  }
}
