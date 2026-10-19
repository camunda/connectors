/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.microsoft.common.auth.BearerAuthentication;
import io.camunda.connector.microsoft.common.auth.ClientCredentialsAuthentication;
import io.camunda.connector.microsoft.email.model.config.MsInboundEmailProperties;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import org.junit.jupiter.api.Test;

class MsInboundEmailCredentialTest {

  private static final String REST_OF_PROPERTIES =
      """
      "pollingConfig":{"userId":"user@example.com",\
      "folder":{"folderSpecification":"byName","folderName":"inbox"},\
      "pollingInterval":"PT30S",\
      "filterCriteria":{"filterSpecification":"simple","onlyUnread":true}},\
      "operation":{"processingOperationDiscriminator":"mark-read"}\
      """;

  private static MsInboundEmailProperties bind(String properties) {
    return InboundConnectorContextBuilder.create()
        .properties(properties)
        .validation(new DefaultValidationProvider())
        .build()
        .bindProperties(MsInboundEmailProperties.class);
  }

  @Test
  void credentialSuppliesAuthenticationWhenNoInlineAuthenticationIsPresent() {
    var properties =
        bind(
            """
            {"authenticationConfiguration":{"authentication":{"type":"clientCredentials",\
            "clientId":"cred-client","tenantId":"cred-tenant","clientSecret":"cred-secret"}},\
            """
                + REST_OF_PROPERTIES
                + "}");

    assertThat(properties.authentication())
        .isInstanceOfSatisfying(
            ClientCredentialsAuthentication.class,
            auth -> assertThat(auth.clientId()).isEqualTo("cred-client"));
  }

  @Test
  void credentialSuppliesAuthenticationWhenBoundAsAFeelExpression() {
    var properties =
        bind(
            """
            {"authenticationConfiguration":"={authentication: {type: \\"clientCredentials\\",\
            clientId: \\"feel-client\\", tenantId: \\"feel-tenant\\", clientSecret: \\"feel-secret\\"}}",\
            """
                + REST_OF_PROPERTIES
                + "}");

    assertThat(properties.authentication())
        .isInstanceOfSatisfying(
            ClientCredentialsAuthentication.class,
            auth -> assertThat(auth.clientId()).isEqualTo("feel-client"));
  }

  @Test
  void inlineAuthenticationIsUsedWhenNoCredentialIsBound() {
    var properties =
        bind(
            """
            {"authentication":{"type":"clientCredentials","clientId":"inline-client",\
            "tenantId":"inline-tenant","clientSecret":"inline-secret"},\
            """
                + REST_OF_PROPERTIES
                + "}");

    assertThat(properties.authentication())
        .isInstanceOfSatisfying(
            ClientCredentialsAuthentication.class,
            auth -> assertThat(auth.clientId()).isEqualTo("inline-client"));
  }

  @Test
  void credentialWinsWhenBothArePresent() {
    var properties =
        bind(
            """
            {"authenticationConfiguration":{"authentication":{"type":"token",\
            "token":"cred-token"}},\
            "authentication":{"type":"clientCredentials","clientId":"inline-client",\
            "tenantId":"inline-tenant","clientSecret":"inline-secret"},\
            """
                + REST_OF_PROPERTIES
                + "}");

    assertThat(properties.authentication())
        .isInstanceOfSatisfying(
            BearerAuthentication.class, auth -> assertThat(auth.token()).isEqualTo("cred-token"));
  }

  @Test
  void bindingFailsNamingBothSourcesWhenNeitherIsPresent() {
    assertThatThrownBy(() -> bind("{" + REST_OF_PROPERTIES + "}"))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("credential")
        .hasMessageContaining("element template");
  }

  @Test
  void leftoverInlineDiscriminatorDoesNotFailValidationWhenCredentialIsBound() {
    // Modeler leaves the inline authentication's default discriminator (and no other inline
    // fields, since they're hidden once a credential is bound) in the job input regardless of
    // which source the user picked.
    var properties =
        bind(
            """
            {"authenticationConfiguration":{"authentication":{"type":"token",\
            "token":"cred-token"}},\
            "authentication":{"type":"clientCredentials"},\
            """
                + REST_OF_PROPERTIES
                + "}");

    assertThat(properties.authentication())
        .isInstanceOfSatisfying(
            BearerAuthentication.class, auth -> assertThat(auth.token()).isEqualTo("cred-token"));
  }

  @Test
  void validationCascadesIntoTheBoundCredential() {
    assertThatThrownBy(
            () ->
                bind(
                    """
                    {"authenticationConfiguration":{"authentication":{"type":"clientCredentials",\
                    "clientId":"cred-client","tenantId":"","clientSecret":"cred-secret"}},\
                    """
                        + REST_OF_PROPERTIES
                        + "}"))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("tenantId");
  }
}
