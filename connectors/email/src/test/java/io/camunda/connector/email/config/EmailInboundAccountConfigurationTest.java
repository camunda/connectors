/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailInboundAccountConfigurationTest {

  @Test
  void suppliesTheMailboxLoginAsSimpleAuthentication() {
    var authentication =
        new EmailInboundAccountConfiguration(
                "the-login", "the-secret", "imap.example.com", 993, CryptographicProtocol.TLS)
            .toAuthentication();

    assertThat(authentication.username()).isEqualTo("the-login");
    assertThat(authentication.password()).isEqualTo("the-secret");
  }

  @Test
  void suppliesItsImapCoordinates() {
    var configuration =
        new EmailInboundAccountConfiguration(
            "u", "p", "imap.example.com", 1993, CryptographicProtocol.SSL);

    assertThat(configuration.toImapConfig())
        .isEqualTo(new ImapConfig("imap.example.com", 1993, CryptographicProtocol.SSL));
  }

  @Test
  void toStringRedactsTheLogin() {
    var configuration =
        new EmailInboundAccountConfiguration(
            "the-login", "the-secret", "imap.example.com", 993, CryptographicProtocol.TLS);

    assertThat(configuration.toString())
        .doesNotContain("the-login")
        .doesNotContain("the-secret")
        .contains("[REDACTED]")
        .contains("imap.example.com");
  }
}
