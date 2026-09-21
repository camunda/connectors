/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.EvaluateExpressionCommandStep1.EvaluateExpressionCommandStep2;
import io.camunda.client.api.response.EvaluateExpressionResponse;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.http.base.model.auth.BearerAuthentication;
import io.camunda.connector.http.base.model.auth.RestAuthenticationConfiguration;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImpl;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogRegistry;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies the per-connector consumption of a bound authentication credential (configuration). */
class PollingRuntimePropertiesTest {

  @Test
  void usesCredentialAuthenticationWhenBound() {
    var properties = new PollingRuntimeProperties();
    properties.setAuthentication(new BearerAuthentication("inline-token"));
    properties.setAuthenticationConfiguration(
        new RestAuthenticationConfiguration(
            new BearerAuthentication("credential-token"), "https://credential.example.com"));

    assertThat(properties.getAuthentication()).isInstanceOf(BearerAuthentication.class);
    assertThat(((BearerAuthentication) properties.getAuthentication()).token())
        .isEqualTo("credential-token");
  }

  @Test
  void fallsBackToInlineAuthenticationWhenNoCredential() {
    var properties = new PollingRuntimeProperties();
    properties.setAuthentication(new BearerAuthentication("inline-token"));

    assertThat(((BearerAuthentication) properties.getAuthentication()).token())
        .isEqualTo("inline-token");
  }

  @Test
  void usesCredentialUrlWhenInlineUrlIsBlank() {
    var properties = new PollingRuntimeProperties();
    properties.setAuthenticationConfiguration(
        new RestAuthenticationConfiguration(
            new BearerAuthentication("credential-token"), "https://credential.example.com"));

    assertThat(properties.getUrl()).isEqualTo("https://credential.example.com");
  }

  @Test
  void inlineUrlOverridesCredentialUrl() {
    var properties = new PollingRuntimeProperties();
    properties.setUrl("https://override.example.com");
    properties.setAuthenticationConfiguration(
        new RestAuthenticationConfiguration(
            new BearerAuthentication("credential-token"), "https://credential.example.com"));

    assertThat(properties.getUrl()).isEqualTo("https://override.example.com");
  }

  @Test
  void bindsCredentialExpressionThroughInboundContext() {
    String expression = "=camunda.vars.env.httpCredential";
    var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    var command = mock(EvaluateExpressionCommandStep2.class, RETURNS_DEEP_STUBS);
    var response = mock(EvaluateExpressionResponse.class);
    when(client.newEvaluateExpressionCommand().expression(expression)).thenReturn(command);
    when(command.send().join()).thenReturn(response);
    when(response.getResult())
        .thenReturn(
            Map.of(
                "authentication",
                Map.of("type", "bearer", "token", "credential-token"),
                "url",
                "https://credential.example.com"));
    var details = mock(ValidInboundConnectorDetails.class);
    when(details.rawPropertiesWithoutKeywords())
        .thenReturn(Map.of("authenticationConfiguration", expression, "method", "GET"));
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

    var properties = context.bindProperties(PollingRuntimeProperties.class);

    assertThat(properties.getAuthentication()).isInstanceOf(BearerAuthentication.class);
    assertThat(((BearerAuthentication) properties.getAuthentication()).token())
        .isEqualTo("credential-token");
    assertThat(properties.getUrl()).isEqualTo("https://credential.example.com");
  }

  private static class ObjectMapperBuilder extends InboundConnectorContextBuilder {
    private ObjectMapper configuredObjectMapper() {
      return objectMapper;
    }
  }
}
