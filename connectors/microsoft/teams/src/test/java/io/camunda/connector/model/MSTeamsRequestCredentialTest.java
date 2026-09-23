/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.microsoft.common.auth.BearerAuthentication;
import io.camunda.connector.microsoft.common.auth.ClientCredentialsAuthentication;
import io.camunda.connector.model.request.data.GetChat;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import org.junit.jupiter.api.Test;

class MSTeamsRequestCredentialTest {

  private static final String DATA =
      "\"data\":{\"method\":\"getChat\",\"chatId\":\"chat-1\",\"expand\":\"withoutExpand\"}";

  private static MSTeamsRequest bind(String variables) {
    return OutboundConnectorContextBuilder.create()
        .variables(variables)
        .validation(new DefaultValidationProvider())
        .build()
        .bindVariables(MSTeamsRequest.class);
  }

  @Test
  void credentialSuppliesAuthenticationWhenNoInlineAuthenticationIsPresent() {
    var request =
        bind(
            """
            {"authenticationConfiguration":{"authentication":{"type":"clientCredentials",\
            "clientId":"cred-client","tenantId":"cred-tenant","clientSecret":"cred-secret"}},\
            """
                + DATA
                + "}");

    assertThat(request.authentication())
        .isInstanceOfSatisfying(
            ClientCredentialsAuthentication.class,
            auth -> assertThat(auth.clientId()).isEqualTo("cred-client"));
  }

  @Test
  void inlineAuthenticationIsUsedWhenNoCredentialIsBound() {
    var request =
        bind(
            """
            {"authentication":{"type":"clientCredentials","clientId":"inline-client",\
            "tenantId":"inline-tenant","clientSecret":"inline-secret"},\
            """
                + DATA
                + "}");

    assertThat(request.authentication())
        .isInstanceOfSatisfying(
            ClientCredentialsAuthentication.class,
            auth -> assertThat(auth.clientId()).isEqualTo("inline-client"));
  }

  @Test
  void credentialWinsWhenBothArePresent() {
    var request =
        bind(
            """
            {"authenticationConfiguration":{"authentication":{"type":"token",\
            "token":"cred-token"}},\
            "authentication":{"type":"clientCredentials","clientId":"inline-client",\
            "tenantId":"inline-tenant","clientSecret":"inline-secret"},\
            """
                + DATA
                + "}");

    assertThat(request.authentication())
        .isInstanceOfSatisfying(
            BearerAuthentication.class, auth -> assertThat(auth.token()).isEqualTo("cred-token"));
  }

  @Test
  void bindingFailsNamingBothSourcesWhenNeitherIsPresent() {
    assertThatThrownBy(() -> bind("{" + DATA + "}"))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("credential")
        .hasMessageContaining("element template");
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
                        + DATA
                        + "}"))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("tenantId");
  }

  @Test
  void leftoverInlineDiscriminatorDoesNotFailValidationWhenCredentialIsBound() {
    // Modeler leaves the inline authentication's default discriminator (and no other inline
    // fields, since they're hidden once a credential is bound) in the job input regardless of
    // which source the user picked.
    var request =
        bind(
            """
            {"authenticationConfiguration":{"authentication":{"type":"token",\
            "token":"cred-token"}},\
            "authentication":{"type":"clientCredentials"},\
            """
                + DATA
                + "}");

    assertThat(request.authentication())
        .isInstanceOfSatisfying(
            BearerAuthentication.class, auth -> assertThat(auth.token()).isEqualTo("cred-token"));
  }

  @Test
  void theLegacyTwoArgumentConstructorStillWorks() {
    var request =
        new MSTeamsRequest(
            new ClientCredentialsAuthentication("client", "tenant", "secret"),
            new GetChat("chat-1", "withoutExpand"));

    assertThat(request.authentication()).isInstanceOf(ClientCredentialsAuthentication.class);
  }
}
