/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.email.authentication.NoAuthentication;
import io.camunda.connector.email.authentication.SimpleAuthentication;
import io.camunda.connector.email.config.CryptographicProtocol;
import io.camunda.connector.email.config.ImapConfig;
import io.camunda.connector.email.config.SmtpConfig;
import io.camunda.connector.email.outbound.protocols.Smtp;
import io.camunda.connector.email.outbound.protocols.actions.ContentType;
import io.camunda.connector.email.outbound.protocols.actions.SmtpSendEmail;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Covers both sources of the authentication and server settings - a bound email account credential
 * and the inline element-template fields - over the real Jackson binding path, since a
 * Modeler-generated diagram carries leftover defaults for whichever source lost.
 */
class EmailRequestTest {

  private static final String SEND_EMAIL_ACTION =
      """
      "smtpActionDiscriminator": "sendEmailSmtp",
      "smtpAction": {
        "from": "from@camunda.com",
        "to": "to@camunda.com",
        "subject": "Hey",
        "contentType": "PLAIN",
        "body": "Content"
      }
      """;

  @Test
  void usesTheBoundAccountWhenOnlyOneIsProvided() {
    var request =
        bind(
            """
            {
              "protocol": "smtp",
              "configuration": {
                "username": "account-user",
                "password": "account-pass",
                "smtpHost": "localhost",
                "smtpPort": 2525,
                "smtpCryptographicProtocol": "SSL"
              },
              "data": { %s }
            }
            """
                .formatted(SEND_EMAIL_ACTION));

    assertThat(request.authentication())
        .isEqualTo(new SimpleAuthentication("account-user", "account-pass"));
    assertThat(request.getProtocolConfiguration())
        .isEqualTo(new SmtpConfig("localhost", 2525, CryptographicProtocol.SSL));
  }

  @Test
  void usesTheInlineFieldsWhenNoAccountIsBound() {
    var request =
        bind(
            """
            {
              "protocol": "smtp",
              "authentication": {
                "type": "simple",
                "username": "inline-user",
                "password": "inline-pass"
              },
              "data": {
                "smtpConfig": {
                  "smtpHost": "localhost",
                  "smtpPort": 587,
                  "smtpCryptographicProtocol": "TLS"
                },
                %s
              }
            }
            """
                .formatted(SEND_EMAIL_ACTION));

    assertThat(request.configuration()).isNull();
    assertThat(request.authentication())
        .isEqualTo(new SimpleAuthentication("inline-user", "inline-pass"));
    assertThat(request.getProtocolConfiguration())
        .isEqualTo(new SmtpConfig("localhost", 587, CryptographicProtocol.TLS));
  }

  /**
   * The shape Modeler actually generates for an account-only diagram: the inline authentication
   * discriminator and the hidden server fields are emitted unconditionally, blank, and must neither
   * fail validation nor win over the account.
   */
  @Test
  void theBoundAccountWinsOverTheLeftoverInlineFields() {
    var request =
        bind(
            """
            {
              "protocol": "smtp",
              "authentication": { "type": "simple", "username": "", "password": "" },
              "configuration": {
                "username": "account-user",
                "password": "account-pass",
                "smtpHost": "localhost",
                "smtpPort": 2525,
                "smtpCryptographicProtocol": "SSL"
              },
              "data": {
                "smtpConfig": {
                  "smtpHost": "",
                  "smtpPort": 587,
                  "smtpCryptographicProtocol": "TLS"
                },
                %s
              }
            }
            """
                .formatted(SEND_EMAIL_ACTION));

    assertThat(request.authentication())
        .isEqualTo(new SimpleAuthentication("account-user", "account-pass"));
    assertThat(request.getProtocolConfiguration())
        .isEqualTo(new SmtpConfig("localhost", 2525, CryptographicProtocol.SSL));
  }

  @Test
  void bindingFailsWhenNeitherSourceProvidesAuthentication() {
    assertThatThrownBy(
            () ->
                bind(
                    """
                    {
                      "protocol": "smtp",
                      "data": {
                        "smtpConfig": {
                          "smtpHost": "localhost",
                          "smtpPort": 587,
                          "smtpCryptographicProtocol": "TLS"
                        },
                        %s
                      }
                    }
                    """
                        .formatted(SEND_EMAIL_ACTION)))
        .hasMessageContaining(
            "No authentication provided by the credential or the element template");
  }

  @Test
  void bindingFailsWhenNeitherSourceProvidesServerSettings() {
    assertThatThrownBy(
            () ->
                bind(
                    """
                    {
                      "protocol": "smtp",
                      "authentication": {
                        "type": "simple",
                        "username": "inline-user",
                        "password": "inline-pass"
                      },
                      "data": { %s }
                    }
                    """
                        .formatted(SEND_EMAIL_ACTION)))
        .hasMessageContaining(
            "No email server settings provided by the credential or the element template");
  }

  /**
   * An account that speaks only SMTP cannot serve an IMAP task, and must say so at binding time.
   */
  @Test
  void bindingFailsWhenTheBoundAccountHasNoServerForTheChosenProtocol() {
    assertThatThrownBy(
            () ->
                bind(
                    """
                    {
                      "protocol": "imap",
                      "configuration": {
                        "username": "account-user",
                        "password": "account-pass",
                        "smtpHost": "localhost"
                      },
                      "data": {
                        "imapActionDiscriminator": "listEmailsImap",
                        "imapAction": { "maxToBeRead": 10, "sortField": "RECEIVED_DATE", "sortOrder": "ASC" }
                      }
                    }
                    """))
        .hasMessageContaining(
            "No email server settings provided by the credential or the element template");
  }

  @Test
  void bindingFailsWhenTheBoundAccountHasNoServerAtAll() {
    assertThatThrownBy(
            () ->
                bind(
                    """
                    {
                      "protocol": "smtp",
                      "configuration": { "username": "account-user", "password": "account-pass" },
                      "data": { %s }
                    }
                    """
                        .formatted(SEND_EMAIL_ACTION)))
        .hasMessageContaining("Configure at least one of the SMTP, IMAP or POP3 servers");
  }

  @Test
  void readsTheAccountsOwnServerForEachProtocol() {
    var configuration =
        new EmailAccountConfiguration(
            "u",
            "p",
            "localhost",
            587,
            CryptographicProtocol.TLS,
            "localhost",
            993,
            CryptographicProtocol.TLS,
            null,
            null,
            null);

    var request =
        bind(
            """
            {
              "protocol": "imap",
              "configuration": {
                "username": "u",
                "password": "p",
                "smtpHost": "localhost",
                "imapHost": "localhost"
              },
              "data": {
                "imapActionDiscriminator": "listEmailsImap",
                "imapAction": { "maxToBeRead": 10, "sortField": "RECEIVED_DATE", "sortOrder": "ASC" }
              }
            }
            """);

    assertThat(request.getProtocolConfiguration())
        .isEqualTo(new ImapConfig("localhost", 993, CryptographicProtocol.TLS));
    assertThat(configuration.toImapConfig())
        .isEqualTo(new ImapConfig("localhost", 993, CryptographicProtocol.TLS));
  }

  /** SMTP without authentication stays available on the inline path, where no account is bound. */
  @Test
  void keepsSupportingUnauthenticatedSmtp() {
    var request =
        bind(
            """
            {
              "protocol": "smtp",
              "authentication": { "type": "noAuth" },
              "data": {
                "smtpConfig": {
                  "smtpHost": "localhost",
                  "smtpPort": 587,
                  "smtpCryptographicProtocol": "NONE"
                },
                %s
              }
            }
            """
                .formatted(SEND_EMAIL_ACTION));

    assertThat(request.authentication()).isEqualTo(new NoAuthentication());
  }

  /** The pre-chooser Java shape keeps compiling and behaving as before. */
  @Test
  void keepsThePreChooserConstructor() {
    var authentication = new SimpleAuthentication("u", "p");
    var smtpConfig = new SmtpConfig("localhost", 587, CryptographicProtocol.TLS);
    var protocol =
        new Smtp(
            new SmtpSendEmail(
                "from@camunda.com",
                "to@camunda.com",
                null,
                null,
                null,
                "Hey",
                ContentType.PLAIN,
                "Content",
                null,
                List.of()),
            smtpConfig);

    var request = new EmailRequest(authentication, protocol);

    assertThat(request.configuration()).isNull();
    assertThat(request.authentication()).isEqualTo(authentication);
    assertThat(request.getProtocolConfiguration()).isEqualTo(smtpConfig);
  }

  /**
   * {@code includeAllValidators} is what lets these fixtures use {@code localhost}: it permits
   * loopback for the {@code @VerifiedHost} check, which otherwise denies it.
   */
  private static EmailRequest bind(String variables) {
    return OutboundConnectorContextBuilder.create()
        .includeAllValidators()
        .variables(variables)
        .build()
        .bindVariables(EmailRequest.class);
  }
}
