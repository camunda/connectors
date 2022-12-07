/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.rabbitmq.client.AMQP;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.rabbitmq.model.RabbitMqAuthentication;
import io.camunda.connector.rabbitmq.model.RabbitMqAuthenticationType;
import io.camunda.connector.rabbitmq.model.RabbitMqRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class RabbitMqRequestReplaceSecretsTest extends BaseTest {

  private OutboundConnectorContext context;

  @Test
  void replaceSecrets_shouldReplaceAuthenticationSecretsInCredentialsAuthType() {
    // Given request with 'CREDENTIALS' authType with secrets
    RabbitMqRequest request = new RabbitMqRequest();
    RabbitMqAuthentication authentication = new RabbitMqAuthentication();
    authentication.setUri(SecretsConstant.Authentication.CREDENTIALS);
    authentication.setAuthType(RabbitMqAuthenticationType.credentials);
    authentication.setPassword(SecretsConstant.SECRETS + SecretsConstant.Authentication.PASSWORD);
    authentication.setUserName(SecretsConstant.SECRETS + SecretsConstant.Authentication.USERNAME);
    request.setAuthentication(authentication);
    context = getContextBuilderWithSecrets().variables(request).build();
    // When
    context.replaceSecrets(request);
    // Then should replace secrets
    assertThat(request.getAuthentication().getAuthType())
        .isEqualTo(RabbitMqAuthenticationType.credentials);
    assertThat(request.getAuthentication().getPassword())
        .isEqualTo(ActualValue.Authentication.PASSWORD);
    assertThat(request.getAuthentication().getUserName())
        .isEqualTo(ActualValue.Authentication.USERNAME);
  }

  @Test
  void replaceSecrets_shouldReplaceAuthenticationSecretsInUriAuthType() {
    // Given request with 'URI' authType with secrets
    RabbitMqAuthentication authentication = new RabbitMqAuthentication();
    authentication.setUri(SecretsConstant.SECRETS + SecretsConstant.Authentication.URI);
    authentication.setAuthType(RabbitMqAuthenticationType.uri);
    RabbitMqRequest request = new RabbitMqRequest();
    request.setAuthentication(authentication);
    context = getContextBuilderWithSecrets().variables(request).build();
    // When
    context.replaceSecrets(request);
    // Then should replace secrets
    assertThat(request.getAuthentication().getAuthType()).isEqualTo(RabbitMqAuthenticationType.uri);
    assertThat(request.getAuthentication().getUri()).isEqualTo(ActualValue.Authentication.URI);
  }

  @ParameterizedTest(name = "Should replace routing secrets")
  @MethodSource("successReplaceSecretsTest")
  void replaceSecrets_shouldReplaceRoutingSecrets(String input) {
    // Given request with secrets
    RabbitMqRequest request = gson.fromJson(input, RabbitMqRequest.class);
    context = getContextBuilderWithSecrets().variables(request).build();
    // When
    context.replaceSecrets(request);
    // Then should replace secrets
    assertThat(request.getRouting().getVirtualHost()).isEqualTo(ActualValue.Routing.VIRTUAL_HOST);
    assertThat(request.getRouting().getHostName()).isEqualTo(ActualValue.Routing.HOST_NAME);
    assertThat(request.getRouting().getPort()).isEqualTo(ActualValue.Routing.PORT);
    assertThat(request.getRouting().getExchange()).isEqualTo(ActualValue.Routing.EXCHANGE);
    assertThat(request.getRouting().getRoutingKey()).isEqualTo(ActualValue.Routing.ROUTING_KEY);
  }

  @ParameterizedTest(name = "Should replace routing secrets")
  @MethodSource("successReplaceSecretsTest")
  void replaceSecrets_shouldReplaceMessageBodySecrets(String input) {
    // Given request with secrets
    RabbitMqRequest request = gson.fromJson(input, RabbitMqRequest.class);
    context = getContextBuilderWithSecrets().variables(request).build();
    // When
    context.replaceSecrets(request);
    // Then should replace secrets
    JsonObject message = gson.toJsonTree(request.getMessage().getBody()).getAsJsonObject();
    assertThat(message.has(ActualValue.Message.Body.BODY_KEY)).isTrue();
    assertThat(message.get(ActualValue.Message.Body.BODY_KEY).getAsString())
        .isEqualTo(ActualValue.Message.Body.VALUE);
  }

  @ParameterizedTest(name = "Should replace message properties secrets")
  @MethodSource("successReplaceSecretsTest")
  void replaceSecrets_shouldReplaceMessagePropertiesSecrets(String input) {
    // Given request with secrets
    RabbitMqRequest request = gson.fromJson(input, RabbitMqRequest.class);
    context = getContextBuilderWithSecrets().variables(request).build();
    // When
    context.replaceSecrets(request);
    // Then should replace secrets
    String propertiesInJson = gson.toJson(request.getMessage().getProperties());
    AMQP.BasicProperties properties = gson.fromJson(propertiesInJson, AMQP.BasicProperties.class);

    assertThat(properties.getContentType()).isEqualTo(ActualValue.Message.Properties.CONTENT_TYPE);
    assertThat(properties.getContentEncoding())
        .isEqualTo(ActualValue.Message.Properties.CONTENT_ENCODING);

    Map<String, Object> headers = properties.getHeaders();
    assertThat(headers.get(ActualValue.Message.Properties.Headers.HEADER_KEY))
        .isEqualTo(ActualValue.Message.Properties.Headers.HEADER_VALUE);
  }
}
