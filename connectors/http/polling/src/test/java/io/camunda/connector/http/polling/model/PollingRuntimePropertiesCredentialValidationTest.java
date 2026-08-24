/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.http.base.model.auth.BearerAuthentication;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.runtime.test.outbound.TestValidationProvider;
import org.junit.jupiter.api.Test;

/**
 * Guards the {@code @Valid} cascade on the bound credential: validation must descend through {@code
 * authenticationConfiguration -> RestAuthenticationConfiguration.authentication ->
 * BearerAuthentication} so a credential carrying a blank token (which {@code @NotEmpty} forbids) is
 * rejected the same way inline authentication would be. Runs through the real runtime validation
 * path ({@code bindProperties}).
 */
class PollingRuntimePropertiesCredentialValidationTest {

  @Test
  void credentialWithBlankTokenIsRejectedByCascadingValidation() {
    String properties =
        """
        {
          "url": "http://localhost:8085/http-endpoint",
          "method": "GET",
          "authenticationConfiguration": {
            "authentication": { "type": "bearer", "token": "" },
            "url": "http://localhost:8085/http-endpoint"
          }
        }
        """;
    var context =
        InboundConnectorContextBuilder.create()
            .properties(properties)
            .validation(new TestValidationProvider())
            .build();

    assertThatThrownBy(() -> context.bindProperties(PollingRuntimeProperties.class))
        .hasMessageContaining("token");
  }

  @Test
  void credentialWithValidTokenDoesNotTripAuthValidation() {
    String properties =
        """
        {
          "method": "GET",
          "authenticationConfiguration": {
            "authentication": { "type": "bearer", "token": "valid-token" },
            "url": "http://localhost:8085/http-endpoint"
          }
        }
        """;
    var context =
        InboundConnectorContextBuilder.create()
            .properties(properties)
            .validation(new TestValidationProvider())
            .build();

    var boundProperties = context.bindProperties(PollingRuntimeProperties.class);
    assertThat(boundProperties.getAuthentication()).isInstanceOf(BearerAuthentication.class);
  }

  @Test
  void credentialUrlSatisfiesTheUrlRequirementWithoutAnInlineUrl() {
    String properties =
        """
        {
          "method": "GET",
          "authenticationConfiguration": {
            "authentication": { "type": "bearer", "token": "valid-token" },
            "url": "http://localhost:8085/http-endpoint"
          }
        }
        """;
    var context =
        InboundConnectorContextBuilder.create()
            .properties(properties)
            .validation(new TestValidationProvider())
            .build();

    assertThat(context.bindProperties(PollingRuntimeProperties.class).getUrl())
        .isEqualTo("http://localhost:8085/http-endpoint");
  }

  /**
   * The blank case, distinct from the absent one above: Modeler may write an empty property when
   * the optional override is cleared, so a blank inline URL must fall back to the credential rather
   * than trip the shape check.
   */
  @Test
  void blankInlineUrlFallsBackToCredentialUrl() {
    String properties =
        """
        {
          "method": "GET",
          "url": "",
          "authenticationConfiguration": {
            "authentication": { "type": "bearer", "token": "valid-token" },
            "url": "http://localhost:8085/http-endpoint"
          }
        }
        """;
    var context =
        InboundConnectorContextBuilder.create()
            .properties(properties)
            .validation(new TestValidationProvider())
            .build();

    assertThat(context.bindProperties(PollingRuntimeProperties.class).getUrl())
        .isEqualTo("http://localhost:8085/http-endpoint");
  }

  @Test
  void credentialWithoutUrlIsRejectedForAHostBoundAuthenticationType() {
    String properties =
        """
        {
          "method": "GET",
          "authenticationConfiguration": {
            "authentication": { "type": "bearer", "token": "valid-token" }
          }
        }
        """;
    var context =
        InboundConnectorContextBuilder.create()
            .properties(properties)
            .validation(new TestValidationProvider())
            .build();

    assertThatThrownBy(() -> context.bindProperties(PollingRuntimeProperties.class))
        .hasMessageContaining("URL is required");
  }

  @Test
  void oauthCredentialCarriesNoUrlSoTheInlineUrlIsUsed() {
    String properties =
        """
        {
          "method": "GET",
          "url": "http://localhost:8085/http-endpoint",
          "authenticationConfiguration": {
            "authentication": {
              "type": "oauth-client-credentials-flow",
              "oauthTokenEndpoint": "http://localhost:8085/token",
              "clientId": "id",
              "clientSecret": "secret",
              "clientAuthentication": "credentialsBody"
            }
          }
        }
        """;
    var context =
        InboundConnectorContextBuilder.create()
            .properties(properties)
            .validation(new TestValidationProvider())
            .build();

    assertThat(context.bindProperties(PollingRuntimeProperties.class).getUrl())
        .isEqualTo("http://localhost:8085/http-endpoint");
  }

  /**
   * A Basic/Bearer/API-key credential's secret must never risk being sent to a different host than
   * the one it was created for, so no inline URL is allowed at all once one is bound - not even one
   * that happens to match the credential's own host.
   */
  @Test
  void inlineUrlIsRejectedOnceAHostBoundCredentialIsBound() {
    String properties =
        """
        {
          "url": "http://localhost:8085/other-path",
          "method": "GET",
          "authenticationConfiguration": {
            "authentication": { "type": "bearer", "token": "valid-token" },
            "url": "http://localhost:8085/http-endpoint"
          }
        }
        """;
    var context =
        InboundConnectorContextBuilder.create()
            .properties(properties)
            .validation(new TestValidationProvider())
            .build();

    assertThatThrownBy(() -> context.bindProperties(PollingRuntimeProperties.class))
        .hasMessageContaining("not allowed once a credential provides the URL");
  }

  /**
   * An OAuth credential legitimately carries no URL (see {@code
   * RestAuthenticationConfiguration#carriesUrl}), so binding one with neither an inline URL nor an
   * override must fail with a message pointing at both possible sources, not a bare "URL is
   * required" that gives no hint where to provide it.
   */
  @Test
  void oauthCredentialWithNoUrlAndNoInlineUrlIsRejected() {
    String properties =
        """
        {
          "method": "GET",
          "authenticationConfiguration": {
            "authentication": {
              "type": "oauth-client-credentials-flow",
              "oauthTokenEndpoint": "http://localhost:8085/token",
              "clientId": "id",
              "clientSecret": "secret",
              "clientAuthentication": "credentialsBody"
            }
          }
        }
        """;
    var context =
        InboundConnectorContextBuilder.create()
            .properties(properties)
            .validation(new TestValidationProvider())
            .build();

    assertThatThrownBy(() -> context.bindProperties(PollingRuntimeProperties.class))
        .hasMessageContaining("No URL provided by the credential or the element template");
  }
}
