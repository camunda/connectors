/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta;

import static org.apache.hc.core5.http.ContentType.IMAGE_PNG;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.email.authentication.SimpleAuthentication;
import io.camunda.connector.email.client.jakarta.models.Email;
import io.camunda.connector.email.client.jakarta.models.EmailBody;
import io.camunda.connector.email.client.jakarta.outbound.JakartaEmailActionExecutor;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import io.camunda.connector.email.outbound.model.EmailRequest;
import io.camunda.connector.email.outbound.protocols.Imap;
import io.camunda.connector.email.outbound.protocols.Pop3;
import io.camunda.connector.email.outbound.protocols.Protocol;
import io.camunda.connector.email.outbound.protocols.Smtp;
import io.camunda.connector.email.outbound.protocols.actions.*;
import io.camunda.connector.email.response.*;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.document.DocumentFactoryImpl;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.document.store.InMemoryDocumentStore;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMultipart;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;
import org.eclipse.angus.mail.pop3.POP3Folder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JakartaExecutorTest {

  DocumentFactory documentFactory = new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  private static boolean messageHasContentType(Message message, String... messageContentType) {
    List<String> values = Arrays.asList(messageContentType);
    try {
      boolean contains = false;
      if (message.getContent() instanceof Multipart multipart) {
        for (int i = 0; i < multipart.getCount(); i++) {
          contains =
              contains
                  || values.contains(multipart.getBodyPart(i).getDataHandler().getContentType());
        }
      }
      return contains;
    } catch (MessagingException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean bodyContains(Object element, String toBeFound)
      throws MessagingException, IOException {
    return switch (element) {
      case String str -> str.contains(toBeFound);
      case MimeMultipart multipart -> {
        try {
          int max = multipart.getCount();
          boolean found = false;
          for (int i = 0; i < max; i++) {
            found =
                found
                    || bodyContains(multipart.getBodyPart(i).getContent(), toBeFound)
                    || toBeFound.equals(multipart.getBodyPart(i).getFileName());
          }
          yield found;
        } catch (MessagingException | IOException e) {
          throw new RuntimeException(e);
        }
      }
      default -> false;
    };
  }

  @Test
  void executeSmtpSendEmail() throws MessagingException {
    JakartaUtils sessionFactory = mock(JakartaUtils.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    JakartaEmailActionExecutor actionExecutor =
        JakartaEmailActionExecutor.create(sessionFactory, objectMapper);

    OutboundConnectorContext outboundConnectorContext = mock(OutboundConnectorContext.class);
    EmailRequest emailRequest = mock(EmailRequest.class);
    SmtpSendEmail smtpSendEmail = mock(SmtpSendEmail.class);
    SimpleAuthentication simpleAuthentication = mock(SimpleAuthentication.class);
    Protocol protocol = mock(Smtp.class);
    Session session = mock(Session.class);
    Transport transport = mock(Transport.class);

    // Authentication
    when(simpleAuthentication.username()).thenReturn("user");
    when(simpleAuthentication.password()).thenReturn("secret");
    doNothing().when(transport).connect(any(), any());

    when(outboundConnectorContext.bindVariables(any())).thenReturn(emailRequest);
    when(emailRequest.authentication()).thenReturn(simpleAuthentication);
    when(session.getProperties()).thenReturn(new Properties());
    when(emailRequest.data()).thenReturn(protocol);
    when(protocol.getProtocolAction()).thenReturn(smtpSendEmail);
    when(sessionFactory.createSession(any())).thenReturn(session);
    when(smtpSendEmail.to()).thenReturn(List.of("to"));
    when(smtpSendEmail.cc()).thenReturn(List.of("cc"));
    when(smtpSendEmail.bcc()).thenReturn(List.of("bcc"));
    when(smtpSendEmail.from()).thenReturn("myself");
    when(smtpSendEmail.contentType()).thenReturn(ContentType.PLAIN);
    when(smtpSendEmail.body()).thenReturn("body");
    when(session.getTransport()).thenReturn(transport);

    actionExecutor.execute(outboundConnectorContext);

    verify(transport, times(1))
        .sendMessage(
            argThat(
                argument -> {
                  try {
                    return Arrays.stream(argument.getFrom())
                            .allMatch(address -> address.toString().contains("myself"))
                        && bodyContains(argument.getContent(), "body")
                        && messageHasContentType(argument, "text/plain; charset=UTF-8");
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
  void executeSmtpSendEmailAsHtml() throws MessagingException {
    JakartaUtils sessionFactory = mock(JakartaUtils.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    JakartaEmailActionExecutor actionExecutor =
        JakartaEmailActionExecutor.create(sessionFactory, objectMapper);

    OutboundConnectorContext outboundConnectorContext = mock(OutboundConnectorContext.class);
    EmailRequest emailRequest = mock(EmailRequest.class);
    SmtpSendEmail smtpSendEmail = mock(SmtpSendEmail.class);
    SimpleAuthentication simpleAuthentication = mock(SimpleAuthentication.class);
    Protocol protocol = mock(Smtp.class);
    Session session = mock(Session.class);
    Transport transport = mock(Transport.class);

    // Authentication
    when(simpleAuthentication.username()).thenReturn("user");
    when(simpleAuthentication.password()).thenReturn("secret");
    doNothing().when(transport).connect(any(), any());

    when(outboundConnectorContext.bindVariables(any())).thenReturn(emailRequest);
    when(emailRequest.authentication()).thenReturn(simpleAuthentication);
    when(session.getProperties()).thenReturn(new Properties());
    when(emailRequest.data()).thenReturn(protocol);
    when(protocol.getProtocolAction()).thenReturn(smtpSendEmail);
    when(sessionFactory.createSession(any())).thenReturn(session);
    when(smtpSendEmail.to()).thenReturn(List.of("to"));
    when(smtpSendEmail.cc()).thenReturn(List.of("cc"));
    when(smtpSendEmail.bcc()).thenReturn(List.of("bcc"));
    when(smtpSendEmail.from()).thenReturn("myself");
    when(smtpSendEmail.contentType()).thenReturn(ContentType.HTML);
    when(smtpSendEmail.htmlBody()).thenReturn("<html><body>body</body></html>");
    when(session.getTransport()).thenReturn(transport);

    actionExecutor.execute(outboundConnectorContext);

    verify(transport, times(1))
        .sendMessage(
            argThat(
                argument -> {
                  try {
                    return Arrays.stream(argument.getFrom())
                            .allMatch(address -> address.toString().contains("myself"))
                        && bodyContains(argument.getContent(), "body")
                        && messageHasContentType(argument, "text/html; charset=utf-8");
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
  void executeSmtpSendEmailAsMultiPart() throws MessagingException {
    JakartaUtils sessionFactory = mock(JakartaUtils.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    JakartaEmailActionExecutor actionExecutor =
        JakartaEmailActionExecutor.create(sessionFactory, objectMapper);

    OutboundConnectorContext outboundConnectorContext = mock(OutboundConnectorContext.class);
    EmailRequest emailRequest = mock(EmailRequest.class);
    SmtpSendEmail smtpSendEmail = mock(SmtpSendEmail.class);
    SimpleAuthentication simpleAuthentication = mock(SimpleAuthentication.class);
    Protocol protocol = mock(Smtp.class);
    Session session = mock(Session.class);
    Transport transport = mock(Transport.class);

    // Authentication
    when(simpleAuthentication.username()).thenReturn("user");
    when(simpleAuthentication.password()).thenReturn("secret");
    doNothing().when(transport).connect(any(), any());

    when(outboundConnectorContext.bindVariables(any())).thenReturn(emailRequest);
    when(emailRequest.authentication()).thenReturn(simpleAuthentication);
    when(session.getProperties()).thenReturn(new Properties());
    when(emailRequest.data()).thenReturn(protocol);
    when(protocol.getProtocolAction()).thenReturn(smtpSendEmail);
    when(sessionFactory.createSession(any())).thenReturn(session);
    when(smtpSendEmail.to()).thenReturn(List.of("to"));
    when(smtpSendEmail.cc()).thenReturn(List.of("cc"));
    when(smtpSendEmail.bcc()).thenReturn(List.of("bcc"));
    when(smtpSendEmail.from()).thenReturn("myself");
    when(smtpSendEmail.contentType()).thenReturn(ContentType.MULTIPART);
    when(smtpSendEmail.body()).thenReturn("Hello");
    when(smtpSendEmail.htmlBody()).thenReturn("<html><body>body</body></html>");
    when(session.getTransport()).thenReturn(transport);

    actionExecutor.execute(outboundConnectorContext);

    verify(transport, times(1))
        .sendMessage(
            argThat(
                argument -> {
                  try {
                    return Arrays.stream(argument.getFrom())
                            .allMatch(address -> address.toString().contains("myself"))
                        && bodyContains(argument.getContent(), "body")
                        && messageHasContentType(
                            argument, "text/plain; charset=UTF-8", "text/html; charset=utf-8");
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
  void executeSmtpSendEmailWithAttachment() throws MessagingException, IOException {

    JakartaUtils sessionFactory = mock(JakartaUtils.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    JakartaEmailActionExecutor actionExecutor =
        JakartaEmailActionExecutor.create(sessionFactory, objectMapper);

    OutboundConnectorContext outboundConnectorContext = mock(OutboundConnectorContext.class);
    EmailRequest emailRequest = mock(EmailRequest.class);
    SmtpSendEmail smtpSendEmail = mock(SmtpSendEmail.class);
    SimpleAuthentication simpleAuthentication = mock(SimpleAuthentication.class);
    Protocol protocol = mock(Smtp.class);
    Session session = mock(Session.class);
    Transport transport = mock(Transport.class);

    // Authentication
    when(simpleAuthentication.username()).thenReturn("user");
    when(simpleAuthentication.password()).thenReturn("secret");
    doNothing().when(transport).connect(any(), any());

    when(outboundConnectorContext.bindVariables(any())).thenReturn(emailRequest);
    when(emailRequest.authentication()).thenReturn(simpleAuthentication);
    when(session.getProperties()).thenReturn(new Properties());
    when(emailRequest.data()).thenReturn(protocol);
    when(protocol.getProtocolAction()).thenReturn(smtpSendEmail);
    when(sessionFactory.createSession(any())).thenReturn(session);
    when(smtpSendEmail.to()).thenReturn(List.of("to"));
    when(smtpSendEmail.cc()).thenReturn(List.of("cc"));
    when(smtpSendEmail.bcc()).thenReturn(List.of("bcc"));
    when(smtpSendEmail.from()).thenReturn("myself");
    when(smtpSendEmail.body()).thenReturn("body");
    when(smtpSendEmail.contentType()).thenReturn(ContentType.PLAIN);

    try (FileInputStream fileInputStream = new FileInputStream("src/test/resources/img/img.png")) {
      when(smtpSendEmail.attachments())
          .thenReturn(
              List.of(
                  this.documentFactory.create(
                      DocumentCreationRequest.from(fileInputStream)
                          .contentType(IMAGE_PNG.getMimeType())
                          .fileName("testFile")
                          .build())));
    }
    when(session.getTransport()).thenReturn(transport);

    actionExecutor.execute(outboundConnectorContext);

    verify(transport, times(1))
        .sendMessage(
            argThat(
                argument -> {
                  try {
                    return Arrays.stream(argument.getFrom())
                            .allMatch(address -> address.toString().contains("myself"))
                        && bodyContains(argument.getContent(), "testFile")
                        && messageHasContentType(argument, "text/plain; charset=UTF-8")
                        && bodyContains(argument.getContent(), "body");
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
  void executeSmtpSendEmailWithHeaders() throws MessagingException {

    JakartaUtils sessionFactory = mock(JakartaUtils.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    JakartaEmailActionExecutor actionExecutor =
        JakartaEmailActionExecutor.create(sessionFactory, objectMapper);

    OutboundConnectorContext outboundConnectorContext = mock(OutboundConnectorContext.class);
    EmailRequest emailRequest = mock(EmailRequest.class);
    SmtpSendEmail smtpSendEmail = mock(SmtpSendEmail.class);
    SimpleAuthentication simpleAuthentication = mock(SimpleAuthentication.class);
    Protocol protocol = mock(Smtp.class);
    Session session = mock(Session.class);
    Transport transport = mock(Transport.class);

    // Authentication
    when(simpleAuthentication.username()).thenReturn("user");
    when(simpleAuthentication.password()).thenReturn("secret");
    doNothing().when(transport).connect(any(), any());

    when(outboundConnectorContext.bindVariables(any())).thenReturn(emailRequest);
    when(emailRequest.authentication()).thenReturn(simpleAuthentication);
    when(session.getProperties()).thenReturn(new Properties());
    when(emailRequest.data()).thenReturn(protocol);
    when(protocol.getProtocolAction()).thenReturn(smtpSendEmail);
    when(sessionFactory.createSession(any())).thenReturn(session);
    when(smtpSendEmail.to()).thenReturn(List.of("to"));
    when(smtpSendEmail.headers()).thenReturn(Map.of("test", "header1"));
    when(smtpSendEmail.from()).thenReturn("myself");
    when(smtpSendEmail.body()).thenReturn("body");
    when(smtpSendEmail.contentType()).thenReturn(ContentType.PLAIN);
    when(session.getTransport()).thenReturn(transport);

    actionExecutor.execute(outboundConnectorContext);

    verify(transport, times(1))
        .sendMessage(
            argThat(
                argument -> {
                  try {
                    return Arrays.stream(argument.getFrom())
                            .allMatch(address -> address.toString().contains("myself"))
                        && bodyContains(argument.getContent(), "body")
                        && Arrays.stream(argument.getHeader("test")).toList().contains("header1");
                  } catch (MessagingException | IOException e) {
                    throw new RuntimeException(e);
                  }
                }),
            argThat(argument -> Arrays.toString(argument).contains("to")));
  }

  @Test
  void executePop3ListEmails() throws MessagingException {
    JakartaUtils sessionFactory = mock(JakartaUtils.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);

    JakartaEmailActionExecutor actionExecutor =
        JakartaEmailActionExecutor.create(sessionFactory, objectMapper);

    OutboundConnectorContext outboundConnectorContext = mock(OutboundConnectorContext.class);
    EmailRequest emailRequest = mock(EmailRequest.class);
    Pop3ListEmails pop3ListEmails = mock(Pop3ListEmails.class);
    SimpleAuthentication simpleAuthentication = mock(SimpleAuthentication.class);
    Protocol protocol = mock(Pop3.class);
    Session session = mock(Session.class);
    Store store = mock(Store.class);
    POP3Folder pop3Folder = mock(POP3Folder.class);
    Message message = mock(Message.class);

    // Authentication
    when(simpleAuthentication.username()).thenReturn("user");
    when(simpleAuthentication.password()).thenReturn("secret");
    doNothing().when(store).connect(any(), any());
    when(outboundConnectorContext.bindVariables(any())).thenReturn(emailRequest);

    when(store.getFolder(anyString())).thenReturn(pop3Folder);

    doNothing().when(pop3Folder).open(Folder.READ_ONLY);

    when(pop3ListEmails.maxToBeRead()).thenReturn(10);
    when(pop3Folder.getMessages()).thenReturn(new Message[] {message});

    when(emailRequest.authentication()).thenReturn(simpleAuthentication);
    when(sessionFactory.retrieveEmailComparator((SortFieldPop3) any(), any()))
        .thenReturn((o1, o2) -> 1);
    when(message.getHeader(any())).thenReturn(new String[] {"id"});
    when(session.getProperties()).thenReturn(new Properties());
    when(session.getStore()).thenReturn(store);
    when(emailRequest.data()).thenReturn(protocol);
    when(protocol.getProtocolAction()).thenReturn(pop3ListEmails);
    when(sessionFactory.createSession(any())).thenReturn(session);
    when(sessionFactory.createBodylessEmail(any()))
        .thenReturn(
            new Email(
                null,
                "1",
                "",
                List.of(),
                "",
                List.of(""),
                List.of(""),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                1));
    doNothing().when(store).connect(any(), any());

    Object object = actionExecutor.execute(outboundConnectorContext);

    Assertions.assertInstanceOf(List.class, object);
    Assertions.assertInstanceOf(ListEmailsResponse.class, ((List) object).getFirst());
  }

  @Test
  void executeImapListEmails() throws MessagingException {
    JakartaUtils sessionFactory = mock(JakartaUtils.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);

    JakartaEmailActionExecutor actionExecutor =
        JakartaEmailActionExecutor.create(sessionFactory, objectMapper);

    OutboundConnectorContext outboundConnectorContext = mock(OutboundConnectorContext.class);
    EmailRequest emailRequest = mock(EmailRequest.class);
    ImapListEmails imapListEmails = mock(ImapListEmails.class);
    SimpleAuthentication simpleAuthentication = mock(SimpleAuthentication.class);
    Protocol protocol = mock(Imap.class);
    Session session = mock(Session.class);
    Store store = mock(Store.class);
    Folder folder = mock(Folder.class);
    Message message = mock(Message.class);

    // Authentication
    when(simpleAuthentication.username()).thenReturn("user");
    when(simpleAuthentication.password()).thenReturn("secret");
    doNothing().when(store).connect(any(), any());
    when(outboundConnectorContext.bindVariables(any())).thenReturn(emailRequest);

    doNothing().when(folder).open(Folder.READ_ONLY);

    when(imapListEmails.maxToBeRead()).thenReturn(10);
    when(folder.getMessages()).thenReturn(new Message[] {message});

    when(emailRequest.authentication()).thenReturn(simpleAuthentication);
    when(sessionFactory.findImapFolder(any(), any())).thenReturn(folder);
    when(sessionFactory.retrieveEmailComparator((SortFieldImap) any(), any()))
        .thenReturn((o1, o2) -> 1);
    when(message.getHeader(any())).thenReturn(new String[] {"id"});
    when(session.getProperties()).thenReturn(new Properties());
    when(session.getStore()).thenReturn(store);
    when(emailRequest.data()).thenReturn(protocol);
    when(protocol.getProtocolAction()).thenReturn(imapListEmails);
    when(sessionFactory.createSession(any())).thenReturn(session);
    when(sessionFactory.createBodylessEmail(any()))
        .thenReturn(
            new Email(
                null,
                "1",
                "",
                List.of(),
                "",
                List.of(""),
                List.of(""),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                1));
    doNothing().when(store).connect(any(), any());

    Object object = actionExecutor.execute(outboundConnectorContext);

    Assertions.assertInstanceOf(List.class, object);
    Assertions.assertInstanceOf(ListEmailsResponse.class, ((List) object).getFirst());
  }

  @Test
  void executePop3ReadEmail() throws MessagingException, IOException {
    JakartaUtils sessionFactory = mock(JakartaUtils.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);

    JakartaEmailActionExecutor actionExecutor =
        JakartaEmailActionExecutor.create(sessionFactory, objectMapper);

    OutboundConnectorContext outboundConnectorContext = mock(OutboundConnectorContext.class);
    EmailRequest emailRequest = mock(EmailRequest.class);
    Pop3ReadEmail pop3ReadEmail = mock(Pop3ReadEmail.class);
    SimpleAuthentication simpleAuthentication = mock(SimpleAuthentication.class);
    Protocol protocol = mock(Pop3.class);
    Session session = mock(Session.class);
    Store store = mock(Store.class);
    POP3Folder pop3Folder = mock(POP3Folder.class);
    Message message = mock(Message.class);

    when(sessionFactory.createSession(any())).thenReturn(session);

    // Authentication
    when(simpleAuthentication.username()).thenReturn("user");
    when(simpleAuthentication.password()).thenReturn("secret");
    doNothing().when(store).connect(any(), any());

    when(outboundConnectorContext.bindVariables(any())).thenReturn(emailRequest);
    when(store.getFolder(anyString())).thenReturn(pop3Folder);
    when(pop3Folder.search(any())).thenReturn(new Message[] {message});
    when(message.getHeader(any())).thenReturn(new String[] {"10"});
    when(pop3ReadEmail.messageId()).thenReturn("10");
    when(message.getContent()).thenReturn("string");
    when(message.isMimeType("text/plain")).thenReturn(true);
    when(emailRequest.authentication()).thenReturn(simpleAuthentication);
    when(session.getProperties()).thenReturn(new Properties());
    when(session.getStore()).thenReturn(store);
    when(emailRequest.data()).thenReturn(protocol);
    when(protocol.getProtocolAction()).thenReturn(pop3ReadEmail);
    when(sessionFactory.createEmail(any()))
        .thenReturn(
            new Email(
                new EmailBody("", "", List.of()),
                "1",
                "",
                List.of(),
                "",
                List.of(""),
                List.of(""),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                1));
    doNothing().when(store).connect(any(), any());

    Object object = actionExecutor.execute(outboundConnectorContext);

    Assertions.assertInstanceOf(ReadEmailResponse.class, object);
  }

  @Test
  void executeImapReadEmail() throws MessagingException, IOException {
    JakartaUtils sessionFactory = mock(JakartaUtils.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);

    JakartaEmailActionExecutor actionExecutor =
        JakartaEmailActionExecutor.create(sessionFactory, objectMapper);

    OutboundConnectorContext outboundConnectorContext = mock(OutboundConnectorContext.class);
    EmailRequest emailRequest = mock(EmailRequest.class);
    ImapReadEmail imapReadEmail = mock(ImapReadEmail.class);
    SimpleAuthentication simpleAuthentication = mock(SimpleAuthentication.class);
    Protocol protocol = mock(Imap.class);
    Session session = mock(Session.class);
    Store store = mock(Store.class);
    Folder folder = mock(Folder.class);
    Message message = mock(Message.class);

    when(sessionFactory.createSession(any())).thenReturn(session);

    // Authentication
    when(simpleAuthentication.username()).thenReturn("user");
    when(simpleAuthentication.password()).thenReturn("secret");
    doNothing().when(store).connect(any(), any());

    when(outboundConnectorContext.bindVariables(any())).thenReturn(emailRequest);
    when(sessionFactory.findImapFolder(any(), any())).thenReturn(folder);
    when(folder.search(any())).thenReturn(new Message[] {message});
    when(message.getHeader(any())).thenReturn(new String[] {"10"});
    when(imapReadEmail.messageId()).thenReturn("10");
    when(message.getContent()).thenReturn("string");
    when(message.isMimeType("text/plain")).thenReturn(true);
    when(emailRequest.authentication()).thenReturn(simpleAuthentication);
    when(session.getProperties()).thenReturn(new Properties());
    when(session.getStore()).thenReturn(store);
    when(emailRequest.data()).thenReturn(protocol);
    when(protocol.getProtocolAction()).thenReturn(imapReadEmail);
    when(sessionFactory.createEmail(any()))
        .thenReturn(
            new Email(
                EmailBody.createBuilder().build(),
                "1",
                "",
                List.of(),
                "",
                List.of(""),
                List.of(""),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                1));
    doNothing().when(store).connect(any(), any());

    Object object = actionExecutor.execute(outboundConnectorContext);

    Assertions.assertInstanceOf(ReadEmailResponse.class, object);
  }

  @Test
  void executePop3DeleteEmail() throws MessagingException, IOException {
    JakartaUtils sessionFactory = mock(JakartaUtils.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);

    JakartaEmailActionExecutor actionExecutor =
        JakartaEmailActionExecutor.create(sessionFactory, objectMapper);

    OutboundConnectorContext outboundConnectorContext = mock(OutboundConnectorContext.class);
    EmailRequest emailRequest = mock(EmailRequest.class);
    Pop3DeleteEmail pop3DeleteEmail = mock(Pop3DeleteEmail.class);
    SimpleAuthentication simpleAuthentication = mock(SimpleAuthentication.class);
    Protocol protocol = mock(Pop3.class);
    Session session = mock(Session.class);
    Store store = mock(Store.class);
    POP3Folder pop3Folder = mock(POP3Folder.class);
    Message message = mock(Message.class);

    when(sessionFactory.createSession(any())).thenReturn(session);

    // Authentication
    when(simpleAuthentication.username()).thenReturn("user");
    when(simpleAuthentication.password()).thenReturn("secret");
    doNothing().when(store).connect(any(), any());

    when(outboundConnectorContext.bindVariables(any())).thenReturn(emailRequest);
    when(store.getFolder(anyString())).thenReturn(pop3Folder);
    when(pop3Folder.search(any())).thenReturn(new Message[] {message});
    when(message.getHeader(any())).thenReturn(new String[] {"10"});
    when(pop3DeleteEmail.messageId()).thenReturn("10");
    when(message.getContent()).thenReturn("string");
    when(message.isMimeType("text/plain")).thenReturn(true);
    when(emailRequest.authentication()).thenReturn(simpleAuthentication);
    when(session.getProperties()).thenReturn(new Properties());
    when(session.getStore()).thenReturn(store);
    when(emailRequest.data()).thenReturn(protocol);
    when(protocol.getProtocolAction()).thenReturn(pop3DeleteEmail);
    doNothing().when(store).connect(any(), any());

    Object object = actionExecutor.execute(outboundConnectorContext);

    Assertions.assertInstanceOf(DeleteEmailResponse.class, object);
  }

  @Test
  void executeImapDeleteEmail() throws MessagingException, IOException {
    JakartaUtils sessionFactory = mock(JakartaUtils.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);

    JakartaEmailActionExecutor actionExecutor =
        JakartaEmailActionExecutor.create(sessionFactory, objectMapper);

    OutboundConnectorContext outboundConnectorContext = mock(OutboundConnectorContext.class);
    EmailRequest emailRequest = mock(EmailRequest.class);
    ImapDeleteEmail imapDeleteEmail = mock(ImapDeleteEmail.class);
    SimpleAuthentication simpleAuthentication = mock(SimpleAuthentication.class);
    Protocol protocol = mock(Imap.class);
    Session session = mock(Session.class);
    Store store = mock(Store.class);
    Folder folder = mock(Folder.class);
    Message message = mock(Message.class);

    when(sessionFactory.createSession(any())).thenReturn(session);

    // Authentication
    when(simpleAuthentication.username()).thenReturn("user");
    when(simpleAuthentication.password()).thenReturn("secret");
    doNothing().when(store).connect(any(), any());

    when(outboundConnectorContext.bindVariables(any())).thenReturn(emailRequest);
    when(sessionFactory.findImapFolder(any(), any())).thenReturn(folder);
    when(folder.search(any())).thenReturn(new Message[] {message});
    when(message.getHeader(any())).thenReturn(new String[] {"10"});
    when(imapDeleteEmail.messageId()).thenReturn("10");
    when(message.getContent()).thenReturn("string");
    when(message.isMimeType("text/plain")).thenReturn(true);
    when(emailRequest.authentication()).thenReturn(simpleAuthentication);
    when(session.getProperties()).thenReturn(new Properties());
    when(session.getStore()).thenReturn(store);
    when(emailRequest.data()).thenReturn(protocol);
    when(protocol.getProtocolAction()).thenReturn(imapDeleteEmail);
    doNothing().when(store).connect(any(), any());

    Object object = actionExecutor.execute(outboundConnectorContext);

    Assertions.assertInstanceOf(DeleteEmailResponse.class, object);
  }

  @Test
  void executePop3SearchEmails() throws MessagingException, IOException {
    JakartaUtils sessionFactory = mock(JakartaUtils.class);
    ObjectMapper objectMapper = new ObjectMapper();

    JakartaEmailActionExecutor actionExecutor =
        JakartaEmailActionExecutor.create(sessionFactory, objectMapper);

    OutboundConnectorContext outboundConnectorContext = mock(OutboundConnectorContext.class);
    EmailRequest emailRequest = mock(EmailRequest.class);
    Pop3SearchEmails pop3SearchEmails = mock(Pop3SearchEmails.class);
    SimpleAuthentication simpleAuthentication = mock(SimpleAuthentication.class);
    Protocol protocol = mock(Pop3.class);
    Session session = mock(Session.class);
    Store store = mock(Store.class);
    POP3Folder pop3Folder = mock(POP3Folder.class);
    Message message = mock(Message.class);

    when(sessionFactory.createSession(any())).thenReturn(session);

    // Authentication
    when(simpleAuthentication.username()).thenReturn("user");
    when(simpleAuthentication.password()).thenReturn("secret");
    doNothing().when(store).connect(any(), any());

    when(outboundConnectorContext.bindVariables(any())).thenReturn(emailRequest);
    when(store.getFolder(anyString())).thenReturn(pop3Folder);
    when(pop3Folder.search(any(), any())).thenReturn(new Message[] {message});
    when(pop3SearchEmails.criteria())
        .thenReturn(loadCriteria("src/test/resources/criterias/simple-criteria.json"));
    when(message.getContent()).thenReturn("string");
    when(message.isMimeType("text/plain")).thenReturn(true);
    when(message.getHeader(any())).thenReturn(new String[] {"1"});
    when(emailRequest.authentication()).thenReturn(simpleAuthentication);
    when(session.getProperties()).thenReturn(new Properties());
    when(session.getStore()).thenReturn(store);
    when(emailRequest.data()).thenReturn(protocol);
    when(protocol.getProtocolAction()).thenReturn(pop3SearchEmails);
    when(sessionFactory.createBodylessEmail(any()))
        .thenReturn(
            new Email(
                null,
                "1",
                "",
                List.of(),
                "",
                List.of(""),
                List.of(""),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                1));
    doNothing().when(store).connect(any(), any());

    Object object = actionExecutor.execute(outboundConnectorContext);

    Assertions.assertInstanceOf(List.class, object);
  }

  @Test
  void executeImapSearchEmails() throws MessagingException, IOException {
    JakartaUtils sessionFactory = mock(JakartaUtils.class);
    ObjectMapper objectMapper = new ObjectMapper();

    JakartaEmailActionExecutor actionExecutor =
        JakartaEmailActionExecutor.create(sessionFactory, objectMapper);

    OutboundConnectorContext outboundConnectorContext = mock(OutboundConnectorContext.class);
    EmailRequest emailRequest = mock(EmailRequest.class);
    ImapSearchEmails imapSearchEmails = mock(ImapSearchEmails.class);
    SimpleAuthentication simpleAuthentication = mock(SimpleAuthentication.class);
    Protocol protocol = mock(Imap.class);
    Session session = mock(Session.class);
    Store store = mock(Store.class);
    Folder folder = mock(Folder.class);
    Message message = mock(Message.class);

    when(sessionFactory.createSession(any())).thenReturn(session);

    // Authentication
    when(simpleAuthentication.username()).thenReturn("user");
    when(simpleAuthentication.password()).thenReturn("secret");
    doNothing().when(store).connect(any(), any());

    when(outboundConnectorContext.bindVariables(any())).thenReturn(emailRequest);
    when(sessionFactory.findImapFolder(any(), any())).thenReturn(folder);
    when(folder.search(any(), any())).thenReturn(new Message[] {message});
    when(imapSearchEmails.criteria())
        .thenReturn(loadCriteria("src/test/resources/criterias/simple-criteria.json"));
    when(message.getContent()).thenReturn("string");
    when(message.isMimeType("text/plain")).thenReturn(true);
    when(message.getHeader(any())).thenReturn(new String[] {"1"});
    when(emailRequest.authentication()).thenReturn(simpleAuthentication);
    when(session.getProperties()).thenReturn(new Properties());
    when(session.getStore()).thenReturn(store);
    when(emailRequest.data()).thenReturn(protocol);
    when(protocol.getProtocolAction()).thenReturn(imapSearchEmails);
    when(sessionFactory.createBodylessEmail(any()))
        .thenReturn(
            new Email(
                null,
                "1",
                "",
                List.of(),
                "",
                List.of(""),
                List.of(""),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                1));
    doNothing().when(store).connect(any(), any());

    Object object = actionExecutor.execute(outboundConnectorContext);

    Assertions.assertInstanceOf(List.class, object);
  }

  @Test
  void executeImapMoveEmail() throws MessagingException, IOException {
    JakartaUtils sessionFactory = mock(JakartaUtils.class);
    ObjectMapper objectMapper = new ObjectMapper();

    JakartaEmailActionExecutor actionExecutor =
        JakartaEmailActionExecutor.create(sessionFactory, objectMapper);

    OutboundConnectorContext outboundConnectorContext = mock(OutboundConnectorContext.class);
    EmailRequest emailRequest = mock(EmailRequest.class);
    ImapMoveEmail imapMoveEmail = mock(ImapMoveEmail.class);
    SimpleAuthentication simpleAuthentication = mock(SimpleAuthentication.class);
    Protocol protocol = mock(Imap.class);
    Session session = mock(Session.class);
    Store store = mock(Store.class);
    Folder folder = mock(Folder.class);
    Folder defaultFolder = mock(Folder.class);
    Folder targetFolder = mock(Folder.class);
    Message message = mock(Message.class);

    when(sessionFactory.createSession(any())).thenReturn(session);

    // Authentication
    when(simpleAuthentication.username()).thenReturn("user");
    when(simpleAuthentication.password()).thenReturn("secret");
    doNothing().when(store).connect(any(), any());

    when(outboundConnectorContext.bindVariables(any())).thenReturn(emailRequest);
    when(sessionFactory.findImapFolder(any(), any())).thenReturn(folder);
    when(folder.search(any())).thenReturn(new Message[] {message});
    when(store.getDefaultFolder()).thenReturn(defaultFolder);
    when(defaultFolder.getSeparator()).thenReturn('|');
    when(folder.exists()).thenReturn(Boolean.TRUE);
    when(message.getContent()).thenReturn("string");
    when(message.isMimeType("text/plain")).thenReturn(true);
    when(message.getHeader(any())).thenReturn(new String[] {"1"});
    when(emailRequest.authentication()).thenReturn(simpleAuthentication);
    when(session.getProperties()).thenReturn(new Properties());
    when(session.getStore()).thenReturn(store);
    when(emailRequest.data()).thenReturn(protocol);
    when(protocol.getProtocolAction()).thenReturn(imapMoveEmail);
    when(imapMoveEmail.fromFolder()).thenReturn("");
    when(imapMoveEmail.toFolder()).thenReturn("test.to/folder");
    when(store.getFolder("test|to|folder")).thenReturn(targetFolder);
    when(sessionFactory.createBodylessEmail(any()))
        .thenReturn(
            new Email(
                null,
                "1",
                "",
                List.of(),
                "",
                List.of(""),
                List.of(""),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                1));
    doNothing().when(store).connect(any(), any());

    Object object = actionExecutor.execute(outboundConnectorContext);

    Assertions.assertInstanceOf(MoveEmailResponse.class, object);
  }

  @Test
  void executeImapSearchEmailsCriteriaSpecification() throws MessagingException {
    JakartaUtils sessionFactory = mock(JakartaUtils.class);
    ObjectMapper objectMapper = new ObjectMapper();

    JakartaEmailActionExecutor actionExecutor =
        JakartaEmailActionExecutor.create(sessionFactory, objectMapper);

    OutboundConnectorContext outboundConnectorContext = mock(OutboundConnectorContext.class);
    EmailRequest emailRequest = mock(EmailRequest.class);
    ImapSearchEmails imapSearchEmails = mock(ImapSearchEmails.class);
    SimpleAuthentication simpleAuthentication = mock(SimpleAuthentication.class);
    Protocol protocol = mock(Imap.class);
    Session session = mock(Session.class);
    when(sessionFactory.createSession(any())).thenReturn(session);

    // Authentication
    when(simpleAuthentication.username()).thenReturn("user");
    when(simpleAuthentication.password()).thenReturn("secret");
    when(sessionFactory.createBodylessEmail(any())).thenCallRealMethod();

    Message message =
        TestMessage.builder()
            .setMessageId("10")
            .setSubject("important")
            .setFrom(List.of("camundi@camunda.com"))
            .createTestMessage();
    Message message2 =
        TestMessage.builder()
            .setMessageId("12")
            .setSubject("test")
            .setFrom(List.of("camundi@camunda.com"))
            .createTestMessage();
    Message message3 =
        TestMessage.builder()
            .setMessageId("11")
            .setSubject("urgent")
            .setFrom(List.of("test@camundal.com"))
            .createTestMessage();

    Store store = new TestStore(Session.getInstance(new Properties()), new URLName(""));
    Folder folder = new TestFolder(store, message, message2, message3);

    when(outboundConnectorContext.bindVariables(any())).thenReturn(emailRequest);
    when(session.getStore()).thenReturn(store);
    when(sessionFactory.findImapFolder(any(), any())).thenReturn(folder);
    when(imapSearchEmails.criteria())
        .thenReturn(loadCriteria("src/test/resources/criterias/simple-criteria.json"));
    when(emailRequest.authentication()).thenReturn(simpleAuthentication);
    when(emailRequest.data()).thenReturn(protocol);
    when(protocol.getProtocolAction()).thenReturn(imapSearchEmails);

    List<SearchEmailsResponse> searchEmailsResponses =
        (List<SearchEmailsResponse>) actionExecutor.execute(outboundConnectorContext);

    Assertions.assertEquals(1, searchEmailsResponses.size());
    Assertions.assertEquals("important", searchEmailsResponses.getFirst().subject());
  }

  @Test
  void executeImapSearchEmailsBodyCriteriaSpecification() throws MessagingException, IOException {
    JakartaUtils sessionFactory = mock(JakartaUtils.class);
    ObjectMapper objectMapper = new ObjectMapper();

    JakartaEmailActionExecutor actionExecutor =
        JakartaEmailActionExecutor.create(sessionFactory, objectMapper);

    OutboundConnectorContext outboundConnectorContext = mock(OutboundConnectorContext.class);
    EmailRequest emailRequest = mock(EmailRequest.class);
    ImapSearchEmails imapSearchEmails = mock(ImapSearchEmails.class);
    SimpleAuthentication simpleAuthentication = mock(SimpleAuthentication.class);
    Protocol protocol = mock(Imap.class);
    Session session = mock(Session.class);
    when(sessionFactory.createSession(any())).thenReturn(session);

    // Authentication
    when(simpleAuthentication.username()).thenReturn("user");
    when(simpleAuthentication.password()).thenReturn("secret");

    Message message =
        TestMessage.builder()
            .setMessageId("10")
            .setSubject("important")
            .setBody("crazy")
            .setFrom(List.of("camundi@camunda.com"))
            .createTestMessage();
    Message message2 =
        TestMessage.builder()
            .setMessageId("12")
            .setBody("crazy")
            .setSubject("test")
            .setFrom(List.of("camundi@camunda.com"))
            .createTestMessage();
    Message message3 =
        TestMessage.builder()
            .setMessageId("11")
            .setBody("crazy")
            .setSubject("urgent")
            .setFrom(List.of("test@camundal.com"))
            .createTestMessage();

    Store store = new TestStore(Session.getInstance(new Properties()), new URLName(""));
    Folder folder = new TestFolder(store, message, message2, message3);

    when(outboundConnectorContext.bindVariables(any())).thenReturn(emailRequest);
    when(session.getStore()).thenReturn(store);
    when(sessionFactory.findImapFolder(any(), any())).thenReturn(folder);
    when(imapSearchEmails.criteria())
        .thenReturn(loadCriteria("src/test/resources/criterias/body-criteria.json"));
    when(emailRequest.authentication()).thenReturn(simpleAuthentication);
    when(emailRequest.data()).thenReturn(protocol);
    when(protocol.getProtocolAction()).thenReturn(imapSearchEmails);
    when(sessionFactory.createBodylessEmail(any())).thenCallRealMethod();

    List<SearchEmailsResponse> searchEmailsResponses =
        (List<SearchEmailsResponse>) actionExecutor.execute(outboundConnectorContext);

    System.out.println(searchEmailsResponses);
    Assertions.assertEquals(2, searchEmailsResponses.size());
    Assertions.assertEquals("10", searchEmailsResponses.get(0).messageId());
    Assertions.assertEquals("important", searchEmailsResponses.get(0).subject());
    Assertions.assertEquals("11", searchEmailsResponses.get(1).messageId());
    Assertions.assertEquals("urgent", searchEmailsResponses.get(1).subject());
  }

  private Object loadCriteria(String path) {
    try {
      return new ObjectMapper().readValue(Files.readString(Path.of(path)), Object.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
