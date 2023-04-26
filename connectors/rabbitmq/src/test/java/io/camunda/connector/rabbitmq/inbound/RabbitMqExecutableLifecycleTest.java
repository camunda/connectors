/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.inbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthentication;
import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthenticationType;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundProperties;
import io.camunda.connector.rabbitmq.supplier.ConnectionFactorySupplier;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RabbitMqExecutableLifecycleTest extends InboundBaseTest {

  RabbitMqInboundProperties properties;

  ConnectionFactorySupplier connectionFactorySupplier;
  Channel channel;

  @BeforeEach
  void init()
      throws URISyntaxException,
          NoSuchAlgorithmException,
          KeyManagementException,
          IOException,
          TimeoutException {
    channel = mock(Channel.class);
    connectionFactorySupplier = mock(ConnectionFactorySupplier.class);
    ConnectionFactory connectionFactoryMock = mock(ConnectionFactory.class);
    Connection connectionMock = mock(Connection.class);

    when(connectionFactorySupplier.createFactory(any(), any())).thenReturn(connectionFactoryMock);

    when(connectionFactoryMock.newConnection()).thenReturn(connectionMock);
    when(connectionMock.createChannel()).thenReturn(channel);

    properties = new RabbitMqInboundProperties();
    properties.setQueueName(SecretsConstant.SECRETS + SecretsConstant.QUEUE_NAME);
    var auth = new RabbitMqAuthentication();
    auth.setAuthType(RabbitMqAuthenticationType.uri);
    auth.setUri(SecretsConstant.SECRETS + SecretsConstant.Authentication.URI);
    properties.setAuthentication(auth);
    properties.setConsumerTag(SecretsConstant.SECRETS + SecretsConstant.CONSUMER_TAG);
  }

  @Test
  void executable_shouldHandleActivation() throws Exception {
    // Given
    InboundConnectorContext context = getContextBuilderWithSecrets().properties(properties).build();
    RabbitMqExecutable executable = new RabbitMqExecutable(connectionFactorySupplier);

    // When
    executable.activate(context);

    // Then
    verify(channel)
        .basicConsume(
            eq(ActualValue.QUEUE_NAME),
            eq(false),
            eq(ActualValue.CONSUMER_TAG),
            eq(false),
            eq(properties.isExclusive()),
            eq(properties.getArguments()),
            any());
  }

  @Test
  void executable_shouldHandleDeactivation() throws Exception {
    // Given
    InboundConnectorContext context = getContextBuilderWithSecrets().properties(properties).build();
    RabbitMqExecutable executable = new RabbitMqExecutable(connectionFactorySupplier);

    // When
    executable.activate(context);
    executable.deactivate();

    // Then
    verify(channel).basicCancel(any());
  }
}
