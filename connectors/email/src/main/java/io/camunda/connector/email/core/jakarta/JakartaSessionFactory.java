/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.core.jakarta;

import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.email.config.Configuration;
import io.camunda.connector.email.config.ImapConfig;
import io.camunda.connector.email.config.Pop3Config;
import io.camunda.connector.email.config.SmtpConfig;
import io.camunda.connector.email.core.SessionFactory;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import java.util.Properties;

public class JakartaSessionFactory implements SessionFactory<Session> {
  public Session createSession(Configuration configuration, Authentication authentication) {
    return Session.getInstance(
        switch (configuration) {
          case ImapConfig imap -> createProperties(imap, authentication.isSecuredAuth());
          case Pop3Config pop3 -> createProperties(pop3, authentication.isSecuredAuth());
          case SmtpConfig smtp -> createProperties(smtp, authentication.isSecuredAuth());
        });
  }

  public void connectStore(Store store, Authentication authentication) throws MessagingException {
    if (authentication.isSecuredAuth())
      store.connect(
          authentication.getUser().orElseThrow(() -> new RuntimeException("Unexpected Error")),
          authentication.getSecret().orElseThrow(() -> new RuntimeException("Unexpected Error")));
    else store.connect();
  }

  public void connectTransport(Transport transport, Authentication authentication)
      throws MessagingException {
    if (authentication.isSecuredAuth())
      transport.connect(
          authentication.getUser().orElseThrow(() -> new RuntimeException("Unexpected Error")),
          authentication.getSecret().orElseThrow(() -> new RuntimeException("Unexpected Error")));
    else transport.connect();
  }

  private Properties createProperties(SmtpConfig smtp, Boolean securedAuth) {
    Properties properties = new Properties();
    properties.put("mail.transport.protocol", "smtp");
    properties.put("mail.smtp.host", smtp.getSmtpHost());
    properties.put("mail.smtp.port", smtp.getSmtpPort().toString());
    properties.put("mail.smtp.auth", securedAuth);
    switch (smtp.getSmtpCryptographicProtocol()) {
      case NONE -> {}
      case TLS -> properties.put("mail.smtp.starttls.enable", true);
      case SSL -> properties.put("mail.smtp.ssl.enable", true);
    }
    return properties;
  }

  private Properties createProperties(Pop3Config pop3, Boolean securedAuth) {
    Properties properties = new Properties();

    switch (pop3.getPop3CryptographicProtocol()) {
      case NONE -> {
        properties.put("mail.store.protocol", "pop3");
        properties.put("mail.pop3.host", pop3.getPop3Host());
        properties.put("mail.pop3.port", pop3.getPop3Port().toString());
        properties.put("mail.pop3.auth", securedAuth);
      }
      case TLS -> {
        properties.put("mail.store.protocol", "pop3s");
        properties.put("mail.pop3s.host", pop3.getPop3Host());
        properties.put("mail.pop3s.port", pop3.getPop3Port().toString());
        properties.put("mail.pop3s.auth", securedAuth);
        properties.put("mail.pop3s.starttls.enable", true);
      }
      case SSL -> {
        properties.put("mail.store.protocol", "pop3s");
        properties.put("mail.pop3s.host", pop3.getPop3Host());
        properties.put("mail.pop3s.port", pop3.getPop3Port().toString());
        properties.put("mail.pop3s.auth", securedAuth);
        properties.put("mail.pop3s.ssl.enable", true);
      }
    }
    return properties;
  }

  private Properties createProperties(ImapConfig imap, Boolean securedAuth) {
    Properties properties = new Properties();

    switch (imap.getImapCryptographicProtocol()) {
      case NONE -> {
        properties.put("mail.store.protocol", "imap");
        properties.put("mail.imap.host", imap.getImapHost());
        properties.put("mail.imap.port", imap.getImapPort().toString());
        properties.put("mail.imap.auth", securedAuth);
      }
      case TLS -> {
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", imap.getImapHost());
        properties.put("mail.imaps.port", imap.getImapPort().toString());
        properties.put("mail.imaps.auth", securedAuth);
        properties.put("mail.imaps.starttls.enable", true);
        properties.put("mail.imaps.usesocketchannels", true);
      }
      case SSL -> {
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", imap.getImapHost());
        properties.put("mail.imaps.port", imap.getImapPort().toString());
        properties.put("mail.imaps.auth", securedAuth);
        properties.put("mail.imaps.ssl.enable", true);
        properties.put("mail.imaps.usesocketchannel", true);
      }
    }
    return properties;
  }
}
