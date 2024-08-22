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
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JakartaExecutor {

  public JakartaExecutor() {}

  public static Object execute(EmailRequest emailRequest) {
    JakartaSessionFactory jakartaSessionFactory = new JakartaSessionFactory();
    Authentication authentication = emailRequest.getAuthentication();
    Protocol protocol = emailRequest.getData();
    Action action = protocol.getProtocolAction();
    Session session = jakartaSessionFactory.createSession(protocol);
    return switch (action) {
      case SmtpSendEmail smtpSendEmail -> smtpSendEmail(smtpSendEmail, authentication, session);
      case ImapMoveEmails imapMoveEmails -> null;
      case ImapListEmails imapListEmails -> null;
      case ImapDeleteEmail imapDeleteEmail -> null;
      case ImapReadEmail imapReadEmail -> null;
      case Pop3DeleteEmail pop3DeleteEmail -> null;
      case Pop3ListEmails pop3ListEmails -> null;
      case Pop3ReadEmail pop3ReadEmail -> null;
    };
  }

  private static boolean smtpSendEmail(
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
        transport.connect(authentication.getSender(), authentication.getSecret());
        transport.sendMessage(message, message.getAllRecipients());
      }
    } catch (MessagingException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  private static Optional<InternetAddress[]> createParsedInternetAddresses(Object object)
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
