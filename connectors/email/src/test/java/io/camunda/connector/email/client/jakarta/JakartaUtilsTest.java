/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import io.camunda.connector.email.authentication.Authentication;
import io.camunda.connector.email.authentication.SimpleAuthentication;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
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
    SmtpConfig smtpConfig = new SmtpConfig("smtp.example.com", 587, CryptographicProtocol.TLS);
    Smtp smtp = new Smtp(null, smtpConfig);
    Authentication auth = mock(SimpleAuthentication.class);

    JakartaUtils factory = new JakartaUtils();

    // When
    Session session = factory.createSession(smtp.smtpConfig(), auth);

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
    SmtpConfig smtpConfig = new SmtpConfig("smtp.example.com", 25, CryptographicProtocol.NONE);
    Smtp smtp = new Smtp(null, smtpConfig);
    Authentication auth = mock(SimpleAuthentication.class);

    JakartaUtils factory = new JakartaUtils();

    // When
    Session session = factory.createSession(smtp.smtpConfig(), auth);

    // Then
    assertEquals("smtp", session.getProperties().get("mail.transport.protocol"));
    assertEquals("smtp.example.com", session.getProperties().get("mail.smtp.host"));
    assertEquals("25", session.getProperties().get("mail.smtp.port"));
    assertEquals("true", session.getProperties().get("mail.smtp.auth").toString());
    assertNull(session.getProperties().get("mail.smtp.starttls.enable"));
    assertNull(session.getProperties().get("mail.smtp.ssl.enable"));
  }

  @Test
  void testCreateSessionWithSmtpAndSSL() {
    // Given
    SmtpConfig smtpConfig = new SmtpConfig("smtp.ssl-example.com", 465, CryptographicProtocol.SSL);
    Smtp smtp = new Smtp(null, smtpConfig);
    Authentication auth = mock(SimpleAuthentication.class);

    JakartaUtils factory = new JakartaUtils();

    // When
    Session session = factory.createSession(smtp.smtpConfig(), auth);

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
    Pop3Config pop3Config = new Pop3Config("pop3.example.com", 110, CryptographicProtocol.NONE);
    Authentication auth = mock(SimpleAuthentication.class);

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
    Pop3Config pop3Config = new Pop3Config("pop3.example.com", 995, CryptographicProtocol.TLS);

    Authentication auth = mock(SimpleAuthentication.class);

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
    ImapConfig imapConfig = new ImapConfig("imap.example.com", 143, CryptographicProtocol.NONE);

    Authentication auth = mock(SimpleAuthentication.class);

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
    ImapConfig imapConfig = new ImapConfig("imap.tls-example.com", 993, CryptographicProtocol.TLS);
    // When
    Authentication auth = mock(SimpleAuthentication.class);

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
    ImapConfig imapConfig = new ImapConfig("imap.ssl-example.com", 993, CryptographicProtocol.SSL);

    // When
    Authentication auth = mock(SimpleAuthentication.class);

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
