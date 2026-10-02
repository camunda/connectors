/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.model.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.automationanywhere.model.request.auth.ApiKeyAuthentication;
import io.camunda.connector.automationanywhere.model.request.auth.PasswordBasedAuthentication;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import org.junit.jupiter.api.Test;

class AutomationAnywhereRequestTest {

  private static final String OPERATION =
      """
      "operation": { "type": "listWorkItemsInQueue", "queueId": "q", "workItemId": 1 }
      """;

  private static final String CREDENTIAL =
      """
      "authenticationConfiguration": {
        "controlRoomUrl": "https://credential.example.com",
        "authentication": { "type": "apiKeyAuthentication", "username": "bot", "apiKey": "key" }
      }
      """;

  private static AutomationAnywhereRequest bind(String json) {
    return OutboundConnectorContextBuilder.create()
        .validation(new DefaultValidationProvider())
        .variables(json)
        .build()
        .bindVariables(AutomationAnywhereRequest.class);
  }

  @Test
  void credentialOnly_usesCredentialUrlAndAuthentication() {
    // Modeler still writes the timeout and the inline discriminator default once a credential is
    // bound, but none of the hidden inline fields.
    var request =
        bind(
            "{"
                + CREDENTIAL
                + ","
                + """
                "authentication": { "type": "passwordBasedAuthentication" },
                "configuration": { "controlRoomUrl": "", "connectionTimeoutInSeconds": 30 },
                """
                + OPERATION
                + "}");

    assertThat(request.configuration().controlRoomUrl())
        .isEqualTo("https://credential.example.com");
    assertThat(request.configuration().connectionTimeoutInSeconds()).isEqualTo(30);
    assertThat(request.authentication()).isEqualTo(new ApiKeyAuthentication("bot", "key"));
  }

  @Test
  void inlineOnly_usesInlineUrlAndAuthentication() {
    var request =
        bind(
            """
            {
              "authentication": {
                "type": "passwordBasedAuthentication",
                "username": "jane", "password": "pw", "multipleLogin": "false"
              },
              "configuration": { "controlRoomUrl": "https://inline.example.com" },
            """
                + OPERATION
                + "}");

    assertThat(request.configuration().controlRoomUrl()).isEqualTo("https://inline.example.com");
    assertThat(request.authentication())
        .isEqualTo(new PasswordBasedAuthentication("jane", "pw", false));
  }

  @Test
  void bothPresent_credentialWins() {
    var request =
        bind(
            "{"
                + CREDENTIAL
                + ","
                + """
                "authentication": { "type": "tokenBasedAuthentication", "token": "inline" },
                "configuration": { "controlRoomUrl": "https://inline.example.com" },
                """
                + OPERATION
                + "}");

    assertThat(request.configuration().controlRoomUrl())
        .isEqualTo("https://credential.example.com");
    assertThat(request.authentication()).isInstanceOf(ApiKeyAuthentication.class);
  }

  @Test
  void neitherPresent_failsNamingBothSources() {
    assertThatThrownBy(
            () ->
                bind(
                    """
                    { "configuration": { "controlRoomUrl": "" },
                    """
                        + OPERATION
                        + "}"))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining(
            "No Control Room URL provided by the credential or the element template")
        .hasMessageContaining(
            "No authentication provided by the credential or the element template");
  }

  @Test
  void incompleteInlineAuthentication_withoutCredential_fails() {
    assertThatThrownBy(
            () ->
                bind(
                    """
                    {
                      "authentication": { "type": "passwordBasedAuthentication" },
                      "configuration": { "controlRoomUrl": "https://inline.example.com" },
                    """
                        + OPERATION
                        + "}"))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("username");
  }

  @Test
  void incompleteCredential_fails() {
    assertThatThrownBy(
            () ->
                bind(
                    """
                    {
                      "authenticationConfiguration": {
                        "authentication": { "type": "tokenBasedAuthentication", "token": "t" }
                      },
                      "configuration": { "controlRoomUrl": "https://inline.example.com" },
                    """
                        + OPERATION
                        + "}"))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("controlRoomUrl");
  }
}
