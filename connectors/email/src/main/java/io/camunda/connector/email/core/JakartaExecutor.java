/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.core;

import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.email.model.EmailRequest;
import io.camunda.connector.email.protocols.Protocol;
import io.camunda.connector.email.protocols.actions.*;
import io.camunda.connector.email.response.Pop3ListEmailsResponse;
import io.camunda.connector.email.response.Pop3ReadEmailResponse;
import jakarta.mail.*;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.*;
import org.eclipse.angus.mail.pop3.POP3Folder;

public class JakartaExecutor {

  private JakartaExecutor() {}

  public static JakartaExecutor create() {
    return new JakartaExecutor();
  }

  public Object execute(EmailRequest emailRequest) {
    JakartaSessionFactory jakartaSessionFactory = new JakartaSessionFactory();
    Authentication authentication = emailRequest.getAuthentication();
    Protocol protocol = emailRequest.getData();
    Action action = protocol.getProtocolAction();
    Session session = jakartaSessionFactory.createSession(protocol, authentication);
    return switch (action) {
      case SmtpSendEmail smtpSendEmail -> smtpSendEmail(smtpSendEmail, authentication, session);
      case ImapMoveEmails imapMoveEmails -> null;
      case ImapListEmails imapListEmails -> null;
      case ImapDeleteEmail imapDeleteEmail -> null;
      case ImapReadEmail imapReadEmail -> null;
      case Pop3DeleteEmail pop3DeleteEmail ->
          pop3DeleteEmail(pop3DeleteEmail, authentication, session);
      case Pop3ListEmails pop3ListEmails -> pop3ListEmails(pop3ListEmails, authentication, session);
      case Pop3ReadEmail pop3ReadEmail -> pop3ReadEmail(pop3ReadEmail, authentication, session);
    };
  }

  private Object pop3DeleteEmail(
      Pop3DeleteEmail pop3DeleteEmail, Authentication authentication, Session session) {
    try {
      try (Store store = session.getStore()) {
        connectStore(store, authentication);
        try (POP3Folder folder = (POP3Folder) store.getFolder("INBOX")) {
          folder.open(Folder.READ_WRITE);
          Message[] messages = folder.getMessages();
          for (Message message : messages) {
            String uid = folder.getUID(message);
            if (uid.equals(pop3DeleteEmail.getUidlDelete())) {
              message.setFlag(Flags.Flag.DELETED, true);
            }
          }
        }
      }
      throw new RuntimeException(
          "No corresponding POP3 email found for uidl %s"
              .formatted(pop3DeleteEmail.getUidlDelete()));
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private Object pop3ReadEmail(
      Pop3ReadEmail pop3ReadEmail, Authentication authentication, Session session) {
    try {
      try (Store store = session.getStore()) {
        connectStore(store, authentication);
        try (POP3Folder folder = (POP3Folder) store.getFolder("INBOX")) {
          folder.open(Folder.READ_WRITE);
          Message[] messages = folder.getMessages();
          for (Message message : messages) {
            String uid = folder.getUID(message);
            if (uid.equals(pop3ReadEmail.getUidlRead())) {
              Email email = Email.createEmail(message);
              if (pop3ReadEmail.isDeleteOnRead()) message.setFlag(Flags.Flag.DELETED, true);
              return new Pop3ReadEmailResponse(
                  folder.getUID(message),
                  email.getFrom(),
                  email.getSubject(),
                  email.getSize(),
                  email.getBody().getBodyAsPlainText(),
                  email.getBody().getBodyAsHtml());
            }
          }
        }
      }
      throw new RuntimeException(
          "No corresponding POP3 email found for uidl %s".formatted(pop3ReadEmail.getUidlRead()));
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private Object pop3ListEmails(
      Pop3ListEmails pop3ListEmails, Authentication authentication, Session session) {
    try {
      try (Store store = session.getStore()) {
        connectStore(store, authentication);
        try (POP3Folder folder = (POP3Folder) store.getFolder("INBOX")) {
          folder.open(Folder.READ_ONLY);
          Message[] messages = folder.getMessages();
          List<Pop3ListEmailsResponse> response = new ArrayList<>();
          for (Message message : messages) {
            Email email = Email.createBodylessEmail(message);
            Pop3ListEmailsResponse pop3ListEmailsResponse =
                new Pop3ListEmailsResponse(
                    folder.getUID(message), email.getFrom(), email.getSubject(), email.getSize());
            response.add(pop3ListEmailsResponse);
          }
          return response;
        }
      }
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean smtpSendEmail(
      SmtpSendEmail smtpSendEmail, Authentication authentication, Session session) {
    try {
      Optional<InternetAddress[]> to = createParsedInternetAddresses(smtpSendEmail.getTo());
      Optional<InternetAddress[]> cc = createParsedInternetAddresses(smtpSendEmail.getCc());
      Optional<InternetAddress[]> cci = createParsedInternetAddresses(smtpSendEmail.getCci());
      Message message = new MimeMessage(session);
      message.setFrom(new InternetAddress(authentication.getSender()));
      if (to.isPresent()) message.setRecipients(Message.RecipientType.TO, to.get());
      if (cc.isPresent()) message.setRecipients(Message.RecipientType.CC, cc.get());
      if (cci.isPresent()) message.setRecipients(Message.RecipientType.BCC, cci.get());
      message.setSubject(smtpSendEmail.getSubject());
      message.setText(smtpSendEmail.getBody());
      try (Transport transport = session.getTransport()) {
        connectTransport(transport, authentication);
        transport.sendMessage(message, message.getAllRecipients());
      }
    } catch (MessagingException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  private void connectStore(Store store, Authentication authentication) throws MessagingException {
    if (authentication.isSecuredAuth())
      store.connect(
          authentication.getUser().orElseThrow(() -> new RuntimeException("Unexpected Error")),
          authentication.getSecret().orElseThrow(() -> new RuntimeException("Unexpected Error")));
    else store.connect();
  }

  private void connectTransport(Transport transport, Authentication authentication)
      throws MessagingException {
    if (authentication.isSecuredAuth())
      transport.connect(
          authentication.getUser().orElseThrow(() -> new RuntimeException("Unexpected Error")),
          authentication.getSecret().orElseThrow(() -> new RuntimeException("Unexpected Error")));
    else transport.connect();
  }

  private Optional<InternetAddress[]> createParsedInternetAddresses(Object object)
      throws AddressException {
    if (Objects.isNull(object)) {
      return Optional.empty();
    }
    return Optional.of(
        switch (object) {
          case List<?> list ->
              InternetAddress.parse(String.join(",", list.stream().map(Object::toString).toList()));
          case String string -> InternetAddress.parse(string);
          default ->
              throw new IllegalStateException(
                  "Unexpected value: " + object + ". List or String was expected");
        });
  }
}
