/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import org.junit.jupiter.api.Test;

class EmailAccountConfigurationTest {

  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();

  @Test
  void deserializesAnSmtpAccountThroughTheProtocolDiscriminator() throws Exception {
    var configuration =
        objectMapper.readValue(
            """
            {
              "protocol": "smtp",
              "username": "u",
              "password": "p",
              "smtpHost": "smtp.example.com",
              "smtpPort": 2525,
              "smtpCryptographicProtocol": "SSL"
            }
            """,
            EmailAccountConfiguration.class);

    assertThat(configuration).isInstanceOf(SmtpAccountConfiguration.class);
    assertThat(configuration.getConfiguration())
        .isEqualTo(new SmtpConfig("smtp.example.com", 2525, CryptographicProtocol.SSL));
  }

  @Test
  void deserializesAnImapAccountThroughTheProtocolDiscriminator() throws Exception {
    var configuration =
        objectMapper.readValue(
            """
            {
              "protocol": "imap",
              "username": "u",
              "password": "p",
              "imapHost": "imap.example.com",
              "imapPort": 1993,
              "imapCryptographicProtocol": "SSL"
            }
            """,
            EmailAccountConfiguration.class);

    assertThat(configuration).isInstanceOf(ImapAccountConfiguration.class);
    assertThat(configuration.getConfiguration())
        .isEqualTo(new ImapConfig("imap.example.com", 1993, CryptographicProtocol.SSL));
  }

  @Test
  void deserializesAPop3AccountThroughTheProtocolDiscriminator() throws Exception {
    var configuration =
        objectMapper.readValue(
            """
            {
              "protocol": "pop3",
              "username": "u",
              "password": "p",
              "pop3Host": "pop.example.com",
              "pop3Port": 1995,
              "pop3CryptographicProtocol": "SSL"
            }
            """,
            EmailAccountConfiguration.class);

    assertThat(configuration).isInstanceOf(Pop3AccountConfiguration.class);
    assertThat(configuration.getConfiguration())
        .isEqualTo(new Pop3Config("pop.example.com", 1995, CryptographicProtocol.SSL));
  }

  @Test
  void suppliesTheMailboxLoginAsSimpleAuthentication() {
    var authentication =
        new SmtpAccountConfiguration(
                "the-login", "the-secret", "smtp.example.com", 587, CryptographicProtocol.TLS)
            .toAuthentication();

    assertThat(authentication.username()).isEqualTo("the-login");
    assertThat(authentication.password()).isEqualTo("the-secret");
  }

  @Test
  void toStringRedactsTheLoginForEveryProtocol() {
    assertThat(
            new SmtpAccountConfiguration(
                    "the-login", "the-secret", "smtp.example.com", 587, CryptographicProtocol.TLS)
                .toString())
        .doesNotContain("the-login")
        .doesNotContain("the-secret")
        .contains("[REDACTED]")
        .contains("smtp.example.com");
    assertThat(
            new ImapAccountConfiguration(
                    "the-login", "the-secret", "imap.example.com", 993, CryptographicProtocol.TLS)
                .toString())
        .doesNotContain("the-login")
        .doesNotContain("the-secret")
        .contains("[REDACTED]")
        .contains("imap.example.com");
    assertThat(
            new Pop3AccountConfiguration(
                    "the-login", "the-secret", "pop.example.com", 995, CryptographicProtocol.TLS)
                .toString())
        .doesNotContain("the-login")
        .doesNotContain("the-secret")
        .contains("[REDACTED]")
        .contains("pop.example.com");
  }
}
