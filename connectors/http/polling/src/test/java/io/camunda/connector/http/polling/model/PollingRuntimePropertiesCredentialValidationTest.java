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

  /**
   * A blank credential URL normalizes to absent (see the record's compact constructor), so a
   * host-bound type still trips the requiredness check exactly as if the field were never set.
   */
  @Test
  void credentialWithBlankUrlIsRejectedForAHostBoundAuthenticationType() {
    String properties =
        """
        {
          "method": "GET",
          "authenticationConfiguration": {
            "authentication": { "type": "bearer", "token": "valid-token" },
            "url": ""
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
   * The inline URL wins over the credential's own, on any host: binding the credential and pointing
   * the polling task elsewhere are both the process author's decisions, so the override is
   * deliberately left unconstrained.
   */
  @Test
  void inlineUrlOverridesTheCredentialUrlEvenOnAnotherHost() {
    String properties =
        """
        {
          "url": "http://localhost:8085/other-path",
          "method": "GET",
          "authenticationConfiguration": {
            "authentication": { "type": "bearer", "token": "valid-token" },
            "url": "http://127.0.0.1:9999/http-endpoint"
          }
        }
        """;
    var context =
        InboundConnectorContextBuilder.create()
            .properties(properties)
            .validation(new TestValidationProvider())
            .build();

    assertThat(context.bindProperties(PollingRuntimeProperties.class).getUrl())
        .isEqualTo("http://localhost:8085/other-path");
  }

  /**
   * An OAuth credential need not carry a URL (see {@code
   * RestAuthenticationConfiguration#requiresUrl}), so binding one with neither an inline URL nor an
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

  /**
   * An OAuth credential may carry a URL - it is simply not required to. When it does and the model
   * also sets one, the inline value wins, exactly as for a host-bound credential.
   */
  @Test
  void inlineUrlOverridesAnOauthCredentialUrl() {
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
            },
            "url": "http://localhost:8085/stale-endpoint"
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
   * A blank credential URL (Modeler clearing an optional/hidden field) must not trip the
   * unconditional {@code @Pattern} shape check - it normalizes to absent, same as never having been
   * set.
   */
  @Test
  void blankCredentialUrlOnOauthDoesNotTripThePatternCheck() {
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
            },
            "url": ""
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
   * The other half of the pair above: an OAuth credential that does carry a URL supplies it to a
   * model that sets none, so the inline field may stay empty for every authentication type.
   */
  @Test
  void oauthCredentialUrlIsUsedWhenTheModelSetsNoInlineUrl() {
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
            },
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
}
