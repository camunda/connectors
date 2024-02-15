/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.rabbitmq.common.model.CredentialsAuthentication;
import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthentication;
import io.camunda.connector.rabbitmq.common.model.UriAuthentication;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class RabbitMqInboundPropertiesReplaceSecretsTest extends InboundBaseTest {

  private InboundConnectorContext context;

  @ParameterizedTest
  @MethodSource("successReplaceSecretsTest")
  void replaceSecrets_shouldReplaceCommonSecrets(String input) throws JsonProcessingException {
    // Given request with secrets
    RabbitMqInboundProperties properties =
        objectMapper.readValue(input, RabbitMqInboundProperties.class);
    context = getContextBuilderWithSecrets().properties(properties).build();

    // When
    var boundProperties = context.bindProperties(RabbitMqInboundProperties.class);

    // Then should replace secrets
    assertThat(boundProperties.getConsumerTag()).isEqualTo(ActualValue.CONSUMER_TAG);
    assertThat(boundProperties.getQueueName()).isEqualTo(ActualValue.QUEUE_NAME);
    assertThat(boundProperties.getArguments().get("x-queue-type"))
        .isEqualTo(ActualValue.QUEUE_TYPE);
  }

  @Test
  void replaceSecrets_shouldReplaceAuthSecrets_AuthTypeUri() {
    // Given request with secrets
    var authentication =
        new UriAuthentication(SecretsConstant.SECRETS + SecretsConstant.Authentication.URI);
    RabbitMqInboundProperties properties = new RabbitMqInboundProperties();
    properties.setAuthentication(authentication);
    properties.setQueueName(ActualValue.QUEUE_NAME);

    context = getContextBuilderWithSecrets().properties(properties).build();

    // When
    var boundProperties = context.bindProperties(RabbitMqInboundProperties.class);

    // Then should replace secrets
    UriAuthentication uriAuthentication = (UriAuthentication) boundProperties.getAuthentication();
    assertThat(uriAuthentication.uri()).isEqualTo(ActualValue.Authentication.URI);
  }

  @Test
  void replaceSecrets_shouldReplaceAuthSecrets_AuthTypeUsernamePassword() {
    // Given request with secrets
    RabbitMqAuthentication authentication =
        new CredentialsAuthentication(
            SecretsConstant.SECRETS + SecretsConstant.Authentication.USERNAME,
            SecretsConstant.SECRETS + SecretsConstant.Authentication.PASSWORD);
    RabbitMqInboundProperties properties = new RabbitMqInboundProperties();
    properties.setQueueName(ActualValue.QUEUE_NAME);
    properties.setAuthentication(authentication);

    context = getContextBuilderWithSecrets().properties(properties).build();

    // When
    var boundProperties = context.bindProperties(RabbitMqInboundProperties.class);
    CredentialsAuthentication credentialsAuthentication =
        (CredentialsAuthentication) boundProperties.getAuthentication();
    // Then should replace secrets
    assertThat(credentialsAuthentication.userName()).isEqualTo(ActualValue.Authentication.USERNAME);
    assertThat(credentialsAuthentication.password()).isEqualTo(ActualValue.Authentication.PASSWORD);
  }
}
