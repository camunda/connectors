/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.email.config.CryptographicProtocol;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import org.junit.jupiter.api.Test;

class EmailAccountConfigurationTest {

  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();

  @Test
  void carriesOnlyTheProtocolBlocksThatHaveAHost() {
    var configuration =
        new EmailAccountConfiguration(
            "u",
            "p",
            "smtp.example.com",
            2525,
            CryptographicProtocol.SSL,
            null,
            null,
            null,
            null,
            null,
            null);

    assertThat(configuration.toSmtpConfig())
        .isNotNull()
        .satisfies(
            smtp -> {
              assertThat(smtp.smtpHost()).isEqualTo("smtp.example.com");
              assertThat(smtp.smtpPort()).isEqualTo(2525);
              assertThat(smtp.smtpCryptographicProtocol()).isEqualTo(CryptographicProtocol.SSL);
            });
    assertThat(configuration.toImapConfig()).isNull();
    assertThat(configuration.toPop3Config()).isNull();
  }

  @Test
  void carriesEveryProtocolBlockOfAFullAccount() {
    var configuration =
        new EmailAccountConfiguration(
            "u",
            "p",
            "smtp.example.com",
            587,
            CryptographicProtocol.TLS,
            "imap.example.com",
            993,
            CryptographicProtocol.TLS,
            "pop.example.com",
            995,
            CryptographicProtocol.TLS);

    assertThat(configuration.toSmtpConfig().smtpHost()).isEqualTo("smtp.example.com");
    assertThat(configuration.toImapConfig().imapHost()).isEqualTo("imap.example.com");
    assertThat(configuration.toPop3Config().pop3Host()).isEqualTo("pop.example.com");
  }

  /**
   * A credential editor writes an empty string for a protocol block the user left alone, which must
   * not read as "this account speaks SMTP".
   */
  @Test
  void treatsABlankHostAsNoServerAtAll() throws Exception {
    var configuration =
        objectMapper.readValue(
            """
            {
              "username": "u",
              "password": "p",
              "smtpHost": "",
              "smtpPort": 587,
              "smtpCryptographicProtocol": "TLS",
              "imapHost": "imap.example.com",
              "pop3Host": "  "
            }
            """,
            EmailAccountConfiguration.class);

    assertThat(configuration.smtpHost()).isNull();
    assertThat(configuration.toSmtpConfig()).isNull();
    assertThat(configuration.toPop3Config()).isNull();
    assertThat(configuration.toImapConfig()).isNotNull();
  }

  @Test
  void defaultsThePortAndEncryptionOfAConfiguredServer() throws Exception {
    var configuration =
        objectMapper.readValue(
            """
            {
              "username": "u",
              "password": "p",
              "smtpHost": "smtp.example.com",
              "imapHost": "imap.example.com",
              "pop3Host": "pop.example.com"
            }
            """,
            EmailAccountConfiguration.class);

    assertThat(configuration.toSmtpConfig().smtpPort()).isEqualTo(587);
    assertThat(configuration.toSmtpConfig().smtpCryptographicProtocol())
        .isEqualTo(CryptographicProtocol.TLS);
    assertThat(configuration.toImapConfig().imapPort()).isEqualTo(993);
    assertThat(configuration.toPop3Config().pop3Port()).isEqualTo(995);
  }

  @Test
  void leavesThePortOfAnUnconfiguredServerAlone() {
    var configuration =
        new EmailAccountConfiguration(
            "u", "p", null, null, null, "imap.example.com", null, null, null, null, null);

    assertThat(configuration.smtpPort()).isNull();
    assertThat(configuration.smtpCryptographicProtocol()).isNull();
  }

  @Test
  void reportsWhetherAnyServerIsConfigured() {
    assertThat(
            new EmailAccountConfiguration(
                    "u", "p", null, null, null, null, null, null, null, null, null)
                .isAtLeastOneServerConfigured())
        .isFalse();
    assertThat(
            new EmailAccountConfiguration(
                    "u", "p", null, null, null, "imap.example.com", null, null, null, null, null)
                .isAtLeastOneServerConfigured())
        .isTrue();
  }

  @Test
  void suppliesTheMailboxLoginAsSimpleAuthentication() {
    var authentication =
        new EmailAccountConfiguration(
                "the-login",
                "the-secret",
                "smtp.example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null)
            .toAuthentication();

    assertThat(authentication.username()).isEqualTo("the-login");
    assertThat(authentication.password()).isEqualTo("the-secret");
  }

  @Test
  void toStringRedactsTheLogin() {
    var configuration =
        new EmailAccountConfiguration(
            "the-login",
            "the-secret",
            "smtp.example.com",
            587,
            CryptographicProtocol.TLS,
            null,
            null,
            null,
            null,
            null,
            null);

    assertThat(configuration.toString())
        .doesNotContain("the-login")
        .doesNotContain("the-secret")
        .contains("[REDACTED]")
        .contains("smtp.example.com");
  }
}
