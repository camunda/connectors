/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.core.jakarta;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.email.authentication.NoAuthentication;
import io.camunda.connector.email.authentication.SimpleAuthentication;
import io.camunda.connector.email.config.CryptographicProtocol;
import io.camunda.connector.email.config.ImapConfig;
import io.camunda.connector.email.config.Pop3Config;
import io.camunda.connector.email.config.SmtpConfig;
import io.camunda.connector.email.outbound.protocols.Smtp;
import jakarta.mail.Session;
import org.junit.jupiter.api.Test;

class JakartaUtilsTest {

  @Test
  void testCreateSessionWithSmtpAndTLS() {
    // Given
    SmtpConfig smtpConfig = new SmtpConfig();
    smtpConfig.setSmtpHost("smtp.example.com");
    smtpConfig.setSmtpPort(587);
    smtpConfig.setSmtpCryptographicProtocol(CryptographicProtocol.TLS);
    Smtp smtp = new Smtp();
    smtp.setSmtpConfig(smtpConfig);
    Authentication auth = mock(SimpleAuthentication.class);
    when(auth.isSecuredAuth()).thenReturn(true);

    JakartaUtils factory = new JakartaUtils();

    // When
    Session session = factory.createSession(smtp.getSmtpConfig(), auth);

    // Then
    assertEquals("smtp", session.getProperties().get("mail.transport.protocol"));
    assertEquals("smtp.example.com", session.getProperties().get("mail.smtp.host"));
    assertEquals("587", session.getProperties().get("mail.smtp.port"));
    assertEquals("true", session.getProperties().get("mail.smtp.auth").toString());
    assertEquals("true", session.getProperties().get("mail.smtp.starttls.enable").toString());
  }

  @Test
  void testCreateSessionWithSmtpAndNoSecurity() {
    // Given
    SmtpConfig smtpConfig = new SmtpConfig();
    smtpConfig.setSmtpHost("smtp.example.com");
    smtpConfig.setSmtpPort(25);
    smtpConfig.setSmtpCryptographicProtocol(CryptographicProtocol.NONE);
    Smtp smtp = new Smtp();
    smtp.setSmtpConfig(smtpConfig);
    Authentication auth = mock(SimpleAuthentication.class);
    when(auth.isSecuredAuth()).thenReturn(true);

    JakartaUtils factory = new JakartaUtils();

    // When
    Session session = factory.createSession(smtp.getSmtpConfig(), auth);

    // Then
    assertEquals("smtp", session.getProperties().get("mail.transport.protocol"));
    assertEquals("smtp.example.com", session.getProperties().get("mail.smtp.host"));
    assertEquals("25", session.getProperties().get("mail.smtp.port"));
    assertEquals("true", session.getProperties().get("mail.smtp.auth").toString());
    assertNull(session.getProperties().get("mail.smtp.starttls.enable"));
    assertNull(session.getProperties().get("mail.smtp.ssl.enable"));
  }

  @Test
  void testCreateSessionWithSmtpWithoutAuthentication() {
    // Given
    SmtpConfig smtpConfig = new SmtpConfig();
    smtpConfig.setSmtpHost("smtp.example.com");
    smtpConfig.setSmtpPort(25);
    smtpConfig.setSmtpCryptographicProtocol(CryptographicProtocol.NONE);
    Smtp smtp = new Smtp();
    smtp.setSmtpConfig(smtpConfig);
    Authentication auth = mock(NoAuthentication.class);
    when(auth.isSecuredAuth()).thenReturn(false);

    JakartaUtils factory = new JakartaUtils();

    // When
    Session session = factory.createSession(smtp.getSmtpConfig(), auth);

    // Then
    assertEquals("smtp", session.getProperties().get("mail.transport.protocol"));
    assertEquals("smtp.example.com", session.getProperties().get("mail.smtp.host"));
    assertEquals("25", session.getProperties().get("mail.smtp.port"));
    assertEquals("false", session.getProperties().get("mail.smtp.auth").toString());
    assertNull(session.getProperties().get("mail.smtp.starttls.enable"));
    assertNull(session.getProperties().get("mail.smtp.ssl.enable"));
  }

  @Test
  void testCreateSessionWithSmtpAndSSL() {
    // Given
    SmtpConfig smtpConfig = new SmtpConfig();
    smtpConfig.setSmtpHost("smtp.ssl-example.com");
    smtpConfig.setSmtpPort(465);
    smtpConfig.setSmtpCryptographicProtocol(CryptographicProtocol.SSL);
    Smtp smtp = new Smtp();
    smtp.setSmtpConfig(smtpConfig);
    Authentication auth = mock(SimpleAuthentication.class);
    when(auth.isSecuredAuth()).thenReturn(true);

    JakartaUtils factory = new JakartaUtils();

    // When
    Session session = factory.createSession(smtp.getSmtpConfig(), auth);

    // Then
    assertEquals("smtp", session.getProperties().get("mail.transport.protocol"));
    assertEquals("smtp.ssl-example.com", session.getProperties().get("mail.smtp.host"));
    assertEquals("465", session.getProperties().get("mail.smtp.port"));
    assertEquals("true", session.getProperties().get("mail.smtp.auth").toString());
    assertEquals("true", session.getProperties().get("mail.smtp.ssl.enable").toString());
    assertNull(session.getProperties().get("mail.smtp.starttls.enable"));
  }

  @Test
  void testCreatePropertiesWithPop3AndNoSecurity() {
    // Given
    Pop3Config pop3Config = new Pop3Config();
    pop3Config.setPop3Host("pop3.example.com");
    pop3Config.setPop3Port(110);
    pop3Config.setPop3CryptographicProtocol(CryptographicProtocol.NONE);
    Authentication auth = mock(SimpleAuthentication.class);
    when(auth.isSecuredAuth()).thenReturn(true);

    JakartaUtils factory = new JakartaUtils();
    // When
    Session session = factory.createSession(pop3Config, auth);

    // Then
    assertEquals("pop3", session.getProperties().get("mail.store.protocol"));
    assertEquals("pop3.example.com", session.getProperties().get("mail.pop3.host"));
    assertEquals("110", session.getProperties().get("mail.pop3.port"));
    assertEquals("true", session.getProperties().get("mail.pop3.auth").toString());
    assertNull(session.getProperties().get("mail.pop3s.starttls.enable"));
    assertNull(session.getProperties().get("mail.pop3s.ssl.enable"));
  }

  @Test
  void testCreatePropertiesWithPop3AndTLS() {
    // Given
    Pop3Config pop3Config = new Pop3Config();
    pop3Config.setPop3Host("pop3.example.com");
    pop3Config.setPop3Port(995);
    pop3Config.setPop3CryptographicProtocol(CryptographicProtocol.TLS);

    Authentication auth = mock(SimpleAuthentication.class);
    when(auth.isSecuredAuth()).thenReturn(true);

    JakartaUtils factory = new JakartaUtils();
    // When
    Session session = factory.createSession(pop3Config, auth);

    // Then
    assertEquals("pop3s", session.getProperties().get("mail.store.protocol"));
    assertEquals("pop3.example.com", session.getProperties().get("mail.pop3s.host"));
    assertEquals("995", session.getProperties().get("mail.pop3s.port"));
    assertEquals("true", session.getProperties().get("mail.pop3s.auth").toString());
    assertEquals("true", session.getProperties().get("mail.pop3s.starttls.enable").toString());
  }

  @Test
  void testCreatePropertiesWithImapAndNoSecurity() {
    // Given
    ImapConfig imapConfig = new ImapConfig();
    imapConfig.setImapHost("imap.example.com");
    imapConfig.setImapPort(143);
    imapConfig.setImapCryptographicProtocol(CryptographicProtocol.NONE);

    Authentication auth = mock(SimpleAuthentication.class);
    when(auth.isSecuredAuth()).thenReturn(true);

    JakartaUtils factory = new JakartaUtils();

    Session session = factory.createSession(imapConfig, auth);

    // Then
    assertEquals("imap", session.getProperties().get("mail.store.protocol"));
    assertEquals("imap.example.com", session.getProperties().get("mail.imap.host"));
    assertEquals("143", session.getProperties().get("mail.imap.port"));
    assertEquals("true", session.getProperties().get("mail.imap.auth").toString());
    assertNull(session.getProperties().get("mail.imaps.starttls.enable"));
    assertNull(session.getProperties().get("mail.imaps.ssl.enable"));
  }

  @Test
  void testCreatePropertiesWithImapAndTLS() {
    // Given
    ImapConfig imapConfig = new ImapConfig();
    imapConfig.setImapHost("imap.tls-example.com");
    imapConfig.setImapPort(993);
    imapConfig.setImapCryptographicProtocol(CryptographicProtocol.TLS);

    // When
    Authentication auth = mock(SimpleAuthentication.class);
    when(auth.isSecuredAuth()).thenReturn(true);

    JakartaUtils factory = new JakartaUtils();

    Session session = factory.createSession(imapConfig, auth);

    // Then
    assertEquals("imaps", session.getProperties().get("mail.store.protocol"));
    assertEquals("imap.tls-example.com", session.getProperties().get("mail.imaps.host"));
    assertEquals("993", session.getProperties().get("mail.imaps.port"));
    assertEquals("true", session.getProperties().get("mail.imaps.auth").toString());
    assertEquals("true", session.getProperties().get("mail.imaps.starttls.enable").toString());
    assertNull(session.getProperties().get("mail.imaps.ssl.enable"));
  }

  @Test
  void testCreatePropertiesWithImapAndSSL() {
    // Given
    ImapConfig imapConfig = new ImapConfig();
    imapConfig.setImapHost("imap.ssl-example.com");
    imapConfig.setImapPort(993);
    imapConfig.setImapCryptographicProtocol(CryptographicProtocol.SSL);

    // When
    Authentication auth = mock(SimpleAuthentication.class);
    when(auth.isSecuredAuth()).thenReturn(true);

    JakartaUtils factory = new JakartaUtils();

    Session session = factory.createSession(imapConfig, auth);

    // Then
    assertEquals("imaps", session.getProperties().get("mail.store.protocol"));
    assertEquals("imap.ssl-example.com", session.getProperties().get("mail.imaps.host"));
    assertEquals("993", session.getProperties().get("mail.imaps.port"));
    assertEquals("true", session.getProperties().get("mail.imaps.auth").toString());
    assertEquals("true", session.getProperties().get("mail.imaps.ssl.enable").toString());
    assertNull(session.getProperties().get("mail.imaps.starttls.enable"));
  }
}
