/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import io.camunda.connector.email.config.CryptographicProtocol;
import io.camunda.connector.email.config.SmtpConfig;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoAuthenticationTest {

  private JakartaUtils jakartaUtils;

  @BeforeEach
  void setUp() {
    jakartaUtils = new JakartaUtils();
  }

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldSerializeToJson() throws Exception {
    NoAuthentication noAuth = new NoAuthentication();
    assertNotNull(noAuth);
    assertEquals(NoAuthentication.TYPE, "noAuth");

    String json = objectMapper.writeValueAsString(noAuth);

    assertTrue(json.contains("\"type\":\"noAuth\""));
  }

  @Test
  void shouldDeserializeFromJson() throws Exception {
    String json = "{\"type\":\"noAuth\"}";
    OutboundAuthentication auth = objectMapper.readValue(json, OutboundAuthentication.class);
    assertInstanceOf(NoAuthentication.class, auth);
  }

  @Test
  void shouldCreateSessionWithNoAuthForSmtp() {
    // Given
    NoAuthentication noAuth = new NoAuthentication();
    SmtpConfig smtpConfig = new SmtpConfig("localhost", 25, CryptographicProtocol.NONE);

    // When
    Session session = jakartaUtils.createSession(smtpConfig, noAuth);

    // Then
    assertNotNull(session);
    assertEquals("smtp", session.getProperties().get("mail.transport.protocol"));
    assertEquals("localhost", session.getProperties().get("mail.smtp.host"));
    assertEquals("25", session.getProperties().get("mail.smtp.port"));
    // Auth should be disabled for NoAuthentication
    assertEquals("false", session.getProperties().get("mail.smtp.auth").toString());
  }

  @Test
  void shouldCreateSessionWithNoAuthForSmtpWithTLS() {
    // Given
    NoAuthentication noAuth = new NoAuthentication();
    SmtpConfig smtpConfig = new SmtpConfig("localhost", 587, CryptographicProtocol.TLS);

    // When
    Session session = jakartaUtils.createSession(smtpConfig, noAuth);

    // Then
    assertNotNull(session);
    assertEquals("smtp", session.getProperties().get("mail.transport.protocol"));
    assertEquals("localhost", session.getProperties().get("mail.smtp.host"));
    assertEquals("587", session.getProperties().get("mail.smtp.port"));
    assertEquals("true", session.getProperties().get("mail.smtp.starttls.enable").toString());
  }

  @Test
  void shouldCreateSessionWithNoAuthForSmtpWithSSL() {
    // Given
    NoAuthentication noAuth = new NoAuthentication();
    SmtpConfig smtpConfig = new SmtpConfig("localhost", 465, CryptographicProtocol.SSL);

    // When
    Session session = jakartaUtils.createSession(smtpConfig, noAuth);

    // Then
    assertNotNull(session);
    assertEquals("smtp", session.getProperties().get("mail.transport.protocol"));
    assertEquals("localhost", session.getProperties().get("mail.smtp.host"));
    assertEquals("465", session.getProperties().get("mail.smtp.port"));
    assertEquals("true", session.getProperties().get("mail.smtp.ssl.enable").toString());
  }

  @Test
  void shouldConnectTransportWithoutCredentials() throws MessagingException {
    // Given
    NoAuthentication noAuth = new NoAuthentication();
    Transport transport = mock(Transport.class);

    // When
    jakartaUtils.connectTransport(transport, noAuth);

    // Then - Verify connect was called without username/password
    verify(transport, times(1)).connect();
    verify(transport, never()).connect(anyString(), anyString());
  }
}
