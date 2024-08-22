/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.core;

import io.camunda.connector.email.protocols.Imap;
import io.camunda.connector.email.protocols.Pop3;
import io.camunda.connector.email.protocols.Protocol;
import io.camunda.connector.email.protocols.Smtp;
import jakarta.mail.Session;
import java.util.Properties;

public class JakartaSessionFactory {
  public Session createSession(Protocol protocol) {
    return Session.getDefaultInstance(
        switch (protocol) {
          case Imap imap -> createProperties(imap);
          case Pop3 pop3 -> createProperties(pop3);
          case Smtp smtp -> createProperties(smtp);
        });
  }

  private Properties createProperties(Smtp smtp) {
    Properties properties = new Properties();
    properties.put("mail.transport.protocol", "smtp");
    properties.put("mail.smtp.host", smtp.getSmtpConfig().getSmtpHost());
    properties.put("mail.smtp.port", smtp.getSmtpConfig().getSmtpPort().toString());
    properties.put("mail.smtp.auth", smtp.getSmtpConfig().getSmtpAuth());
    properties.put("mail.smtp.starttls.enable", smtp.getSmtpConfig().getSmtpTLS());
    return properties;
  }

  private Properties createProperties(Pop3 pop3) {
    return new Properties();
  }

  private Properties createProperties(Imap imap) {
    return new Properties();
  }
}
