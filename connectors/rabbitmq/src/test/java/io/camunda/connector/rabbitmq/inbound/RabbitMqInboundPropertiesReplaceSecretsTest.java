/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthentication;
import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthenticationType;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class RabbitMqInboundPropertiesReplaceSecretsTest extends InboundBaseTest {

  private InboundConnectorContext context;

  @ParameterizedTest
  @MethodSource("successReplaceSecretsTest")
  void replaceSecrets_shouldReplaceCommonSecrets(String input) {
    // Given request with secrets
    RabbitMqInboundProperties properties = gson.fromJson(input, RabbitMqInboundProperties.class);
    context = getContextBuilderWithSecrets().properties(properties).build();

    // When
    context.replaceSecrets(properties);

    // Then should replace secrets
    assertThat(properties.getConsumerTag()).isEqualTo(ActualValue.CONSUMER_TAG);
    assertThat(properties.getQueueName()).isEqualTo(ActualValue.QUEUE_NAME);
    assertThat(properties.getArguments().get("x-queue-type")).isEqualTo(ActualValue.QUEUE_TYPE);
  }

  @Test
  void replaceSecrets_shouldReplaceAuthSecrets_AuthTypeUri() {
    // Given request with secrets
    RabbitMqAuthentication authentication = new RabbitMqAuthentication();
    authentication.setUri(SecretsConstant.SECRETS + SecretsConstant.Authentication.URI);
    authentication.setAuthType(RabbitMqAuthenticationType.uri);
    RabbitMqInboundProperties properties = new RabbitMqInboundProperties();
    properties.setAuthentication(authentication);

    context = getContextBuilderWithSecrets().properties(properties).build();

    // When
    context.replaceSecrets(properties);

    // Then should replace secrets
    assertThat(properties.getAuthentication().getUri()).isEqualTo(ActualValue.Authentication.URI);
  }

  @Test
  void replaceSecrets_shouldReplaceAuthSecrets_AuthTypeUsernamePassword() {
    // Given request with secrets
    RabbitMqAuthentication authentication = new RabbitMqAuthentication();
    authentication.setAuthType(RabbitMqAuthenticationType.credentials);
    authentication.setUserName(SecretsConstant.SECRETS + SecretsConstant.Authentication.USERNAME);
    authentication.setPassword(SecretsConstant.SECRETS + SecretsConstant.Authentication.PASSWORD);
    RabbitMqInboundProperties properties = new RabbitMqInboundProperties();
    properties.setAuthentication(authentication);

    context = getContextBuilderWithSecrets().properties(properties).build();

    // When
    context.replaceSecrets(properties);

    // Then should replace secrets
    assertThat(properties.getAuthentication().getUserName())
        .isEqualTo(ActualValue.Authentication.USERNAME);
    assertThat(properties.getAuthentication().getPassword())
        .isEqualTo(ActualValue.Authentication.PASSWORD);
  }
}
