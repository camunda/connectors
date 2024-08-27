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
import io.camunda.connector.email.config.SmtpConfig;
import io.camunda.connector.email.outbound.protocols.Smtp;
import jakarta.mail.Session;
import org.junit.jupiter.api.Test;

class JakartaSessionFactoryTest {

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

    JakartaSessionFactory factory = new JakartaSessionFactory();

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

    JakartaSessionFactory factory = new JakartaSessionFactory();

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

    JakartaSessionFactory factory = new JakartaSessionFactory();

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

    JakartaSessionFactory factory = new JakartaSessionFactory();

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
}
