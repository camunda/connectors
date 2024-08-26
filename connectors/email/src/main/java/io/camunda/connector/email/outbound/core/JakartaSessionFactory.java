/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.core;

import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.email.outbound.protocols.Imap;
import io.camunda.connector.email.outbound.protocols.Pop3;
import io.camunda.connector.email.outbound.protocols.Protocol;
import io.camunda.connector.email.outbound.protocols.Smtp;
import io.camunda.connector.email.outbound.protocols.config.CryptographicProtocol;
import jakarta.mail.Session;
import jakarta.validation.constraints.NotNull;
import java.util.Properties;

public class JakartaSessionFactory {
  public Session createSession(Protocol protocol, Authentication authentication) {
    return Session.getInstance(
        switch (protocol) {
          case Imap imap -> createProperties(imap, authentication.isSecuredAuth());
          case Pop3 pop3 -> createProperties(pop3, authentication.isSecuredAuth());
          case Smtp smtp -> createProperties(smtp, authentication.isSecuredAuth());
        });
  }

  private Properties createProperties(Smtp smtp, Boolean securedAuth) {
    Properties properties = new Properties();
    properties.put("mail.transport.protocol", "smtp");
    properties.put("mail.smtp.host", smtp.getSmtpConfig().getSmtpHost());
    properties.put("mail.smtp.port", smtp.getSmtpConfig().getSmtpPort().toString());
    properties.put("mail.smtp.auth", securedAuth);
    switch (smtp.getSmtpConfig().getSmtpCryptographicProtocol()) {
      case NONE -> {}
      case TLS -> properties.put("mail.smtp.starttls.enable", true);
      case SSL -> properties.put("mail.smtp.ssl.enable", true);
    }
    return properties;
  }

  private Properties createProperties(Pop3 pop3, Boolean securedAuth) {
    Properties properties = new Properties();

    switch (pop3.getPop3Config().getPop3CryptographicProtocol()) {
      case NONE -> {
        properties.put("mail.store.protocol", "pop3");
        properties.put("mail.pop3.host", pop3.getPop3Config().getPop3Host());
        properties.put("mail.pop3.port", pop3.getPop3Config().getPop3Port().toString());
        properties.put("mail.pop3.auth", securedAuth);
      }
      case TLS -> {
        properties.put("mail.store.protocol", "pop3s");
        properties.put("mail.pop3s.host", pop3.getPop3Config().getPop3Host());
        properties.put("mail.pop3s.port", pop3.getPop3Config().getPop3Port().toString());
        properties.put("mail.pop3s.auth", securedAuth);
        properties.put("mail.pop3s.starttls.enable", true);
      }
      case SSL -> {
        properties.put("mail.store.protocol", "pop3s");
        properties.put("mail.pop3s.host", pop3.getPop3Config().getPop3Host());
        properties.put("mail.pop3s.port", pop3.getPop3Config().getPop3Port().toString());
        properties.put("mail.pop3s.auth", securedAuth);
        properties.put("mail.pop3s.ssl.enable", true);
      }
    }
    return properties;
  }

  private void manageProtocolProperties(
      @NotNull CryptographicProtocol cryptographicProtocol, Properties properties) {}

  private Properties createProperties(Imap imap, Boolean securedAuth) {
    return new Properties();
  }
}
