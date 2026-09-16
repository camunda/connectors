/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.inbound.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.EvaluateExpressionCommandStep1.EvaluateExpressionCommandStep2;
import io.camunda.client.api.response.EvaluateExpressionResponse;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.email.authentication.SimpleAuthentication;
import io.camunda.connector.email.config.CryptographicProtocol;
import io.camunda.connector.email.config.ImapConfig;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImpl;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogRegistry;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.runtime.test.outbound.TestValidationProvider;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Covers both sources of the authentication and IMAP settings - a bound email account credential
 * and the inline element-template fields - over the real Jackson binding path, since a
 * Modeler-generated diagram carries leftover defaults for whichever source lost.
 */
class EmailInboundConnectorPropertiesTest {

  private static final String POLLING_CONFIG =
      """
      "pollingWaitTime": "PT20S",
      "pollingConfigDiscriminator": "unseenPollingConfig",
      "pollingConfig": { "handlingStrategy": "READ" }
      """;

  @Test
  void usesTheBoundAccountWhenOnlyOneIsProvided() {
    var properties =
        bind(
            """
            {
              "emailAccountConfiguration": {
                "username": "account-user",
                "password": "account-pass",
                "imapHost": "localhost",
                "imapPort": 1993,
                "imapCryptographicProtocol": "SSL"
              },
              "data": { %s }
            }
            """
                .formatted(POLLING_CONFIG));

    assertThat(properties.authentication())
        .isEqualTo(new SimpleAuthentication("account-user", "account-pass"));
    assertThat(properties.getImapConfiguration())
        .isEqualTo(new ImapConfig("localhost", 1993, CryptographicProtocol.SSL));
  }

  @Test
  void usesTheInlineFieldsWhenNoAccountIsBound() {
    var properties =
        bind(
            """
            {
              "authentication": {
                "type": "simple",
                "username": "inline-user",
                "password": "inline-pass"
              },
              "data": {
                "imapConfig": {
                  "imapHost": "localhost",
                  "imapPort": 1143,
                  "imapCryptographicProtocol": "NONE"
                },
                %s
              }
            }
            """
                .formatted(POLLING_CONFIG));

    assertThat(properties.emailAccountConfiguration()).isNull();
    assertThat(properties.authentication())
        .isEqualTo(new SimpleAuthentication("inline-user", "inline-pass"));
    assertThat(properties.getImapConfiguration())
        .isEqualTo(new ImapConfig("localhost", 1143, CryptographicProtocol.NONE));
  }

  /**
   * The shape Modeler actually generates for an account-only diagram: the inline authentication
   * discriminator and the hidden IMAP fields are emitted unconditionally, blank, and must neither
   * fail validation nor win over the account.
   */
  @Test
  void theBoundAccountWinsOverTheLeftoverInlineFields() {
    var properties =
        bind(
            """
            {
              "authentication": { "type": "simple", "username": "", "password": "" },
              "emailAccountConfiguration": {
                "username": "account-user",
                "password": "account-pass",
                "imapHost": "localhost",
                "imapPort": 1993,
                "imapCryptographicProtocol": "SSL"
              },
              "data": {
                "imapConfig": {
                  "imapHost": "",
                  "imapPort": 993,
                  "imapCryptographicProtocol": "TLS"
                },
                %s
              }
            }
            """
                .formatted(POLLING_CONFIG));

    assertThat(properties.authentication())
        .isEqualTo(new SimpleAuthentication("account-user", "account-pass"));
    assertThat(properties.getImapConfiguration())
        .isEqualTo(new ImapConfig("localhost", 1993, CryptographicProtocol.SSL));
  }

  @Test
  void bindingFailsWhenNeitherSourceProvidesAuthentication() {
    assertThatThrownBy(
            () ->
                bind(
                    """
                    {
                      "data": {
                        "imapConfig": {
                          "imapHost": "localhost",
                          "imapPort": 993,
                          "imapCryptographicProtocol": "TLS"
                        },
                        %s
                      }
                    }
                    """
                        .formatted(POLLING_CONFIG)))
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
                      "authentication": {
                        "type": "simple",
                        "username": "inline-user",
                        "password": "inline-pass"
                      },
                      "data": { %s }
                    }
                    """
                        .formatted(POLLING_CONFIG)))
        .hasMessageContaining(
            "No email server settings provided by the credential or the element template");
  }

  /**
   * {@code EmailInboundAccountConfiguration} is IMAP-only, so - unlike the outbound account, which
   * can legitimately carry no block for a given protocol - a bound account missing its IMAP host
   * fails the credential's own {@code @NotBlank}, cascaded through the chooser's {@code @Valid},
   * rather than {@link EmailInboundConnectorProperties#isImapConfigurationProvided()}.
   */
  @Test
  void bindingFailsWhenTheBoundAccountIsMissingItsImapHost() {
    assertThatThrownBy(
            () ->
                bind(
                    """
                    {
                      "emailAccountConfiguration": {
                        "username": "account-user",
                        "password": "account-pass"
                      },
                      "data": { %s }
                    }
                    """
                        .formatted(POLLING_CONFIG)))
        .hasMessageContaining("imapHost");
  }

  /**
   * A bound credential arrives at this class as a raw FEEL expression ({@code
   * =camunda.vars.env.<name>}) in the inbound {@code zeebe:property} value, not an already-expanded
   * object - unlike outbound's {@code fetchVariables} path, which resolves it before the connector
   * ever sees it. Without {@code @FEEL} on the chooser field, that expression string would be
   * handed straight to the {@code EmailInboundAccountConfiguration} deserializer instead of
   * evaluated first. Every other test in this class supplies an already-expanded JSON object and so
   * cannot catch that regression; this one goes through the real expression-eval path, mirroring
   * {@code PollingRuntimePropertiesTest#bindsCredentialExpressionThroughInboundContext}.
   */
  @Test
  void bindsCredentialExpressionThroughInboundContext() {
    String expression = "=camunda.vars.env.emailCredential";
    var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    var command = mock(EvaluateExpressionCommandStep2.class, RETURNS_DEEP_STUBS);
    var response = mock(EvaluateExpressionResponse.class);
    when(client.newEvaluateExpressionCommand().expression(expression)).thenReturn(command);
    when(command.send().join()).thenReturn(response);
    when(response.getResult())
        .thenReturn(
            Map.<String, Object>of(
                "username", "account-user",
                "password", "account-pass",
                "imapHost", "localhost",
                "imapPort", 1993,
                "imapCryptographicProtocol", "SSL"));
    var details = mock(ValidInboundConnectorDetails.class);
    when(details.rawPropertiesWithoutKeywords())
        .thenReturn(
            Map.of(
                "emailAccountConfiguration", expression,
                "data.pollingWaitTime", "PT20S",
                "data.pollingConfigDiscriminator", "unseenPollingConfig",
                "data.pollingConfig.handlingStrategy", "READ"));
    when(details.connectorElements()).thenReturn(List.of());
    var context =
        new InboundConnectorContextImpl(
            mock(SecretProvider.class),
            ignored -> {},
            details,
            null,
            ignored -> {},
            new ObjectMapperBuilder().configuredObjectMapper(),
            new ActivityLogRegistry(),
            client);

    var properties = context.bindProperties(EmailInboundConnectorProperties.class);

    assertThat(properties.authentication())
        .isEqualTo(new SimpleAuthentication("account-user", "account-pass"));
    assertThat(properties.getImapConfiguration())
        .isEqualTo(new ImapConfig("localhost", 1993, CryptographicProtocol.SSL));
  }

  private static class ObjectMapperBuilder extends InboundConnectorContextBuilder {
    private ObjectMapper configuredObjectMapper() {
      return objectMapper;
    }
  }

  /** The pre-chooser Java shape keeps compiling and behaving as before. */
  @Test
  void keepsThePreChooserConstructor() {
    var authentication = new SimpleAuthentication("u", "p");
    var imapConfig = new ImapConfig("localhost", 993, CryptographicProtocol.TLS);
    var data =
        new EmailListenerConfig(
            imapConfig, null, Duration.ofSeconds(20), new PollUnseen(HandlingStrategy.READ, null));

    var properties = new EmailInboundConnectorProperties(authentication, data);

    assertThat(properties.emailAccountConfiguration()).isNull();
    assertThat(properties.authentication()).isEqualTo(authentication);
    assertThat(properties.getImapConfiguration()).isEqualTo(imapConfig);
  }

  private static EmailInboundConnectorProperties bind(String properties) {
    return InboundConnectorContextBuilder.create()
        .properties(properties)
        .validation(new TestValidationProvider())
        .build()
        .bindProperties(EmailInboundConnectorProperties.class);
  }
}
