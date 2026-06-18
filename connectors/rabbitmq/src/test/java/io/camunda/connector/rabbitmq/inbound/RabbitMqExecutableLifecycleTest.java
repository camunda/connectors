/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.inbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoveryListener;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import io.camunda.connector.rabbitmq.common.model.UriAuthentication;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundProperties;
import io.camunda.connector.rabbitmq.supplier.ConnectionFactorySupplier;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RabbitMqExecutableLifecycleTest extends InboundBaseTest {

  RabbitMqInboundProperties properties;

  ConnectionFactorySupplier connectionFactorySupplier;
  ConnectionFactory connectionFactoryMock;
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
    connectionFactoryMock = mock(ConnectionFactory.class);
    Connection connectionMock = mock(Connection.class);

    when(connectionFactorySupplier.createFactory(any(), any())).thenReturn(connectionFactoryMock);

    // lenient: tests that override newConnection() don't use these defaults
    lenient().when(connectionFactoryMock.newConnection()).thenReturn(connectionMock);
    lenient().when(connectionMock.createChannel()).thenReturn(channel);

    properties = new RabbitMqInboundProperties();
    properties.setQueueName(SecretsConstant.SECRETS + SecretsConstant.QUEUE_NAME);
    var auth = new UriAuthentication(SecretsConstant.SECRETS + SecretsConstant.Authentication.URI);
    properties.setAuthentication(auth);
    properties.setConsumerTag(SecretsConstant.SECRETS + SecretsConstant.CONSUMER_TAG);
  }

  @Test
  void executable_shouldHandleActivation() throws Exception {
    // Given
    InboundConnectorContext context =
        getContextBuilderWithSecrets()
            .validation(new DefaultValidationProvider())
            .properties(properties)
            .definition(new InboundConnectorDefinition(null, null, null, List.of()))
            .build();
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
    InboundConnectorContext context =
        getContextBuilderWithSecrets()
            .validation(new DefaultValidationProvider())
            .properties(properties)
            .definition(new InboundConnectorDefinition(null, null, null, List.of()))
            .build();
    RabbitMqExecutable executable = new RabbitMqExecutable(connectionFactorySupplier);

    // When
    executable.activate(context);
    executable.deactivate();

    // Then
    verify(channel).basicCancel(any());
  }

  @Test
  void executable_shouldRemoveRecoveryListenerOnDeactivation() throws Exception {
    // Given - a connection that also implements Recoverable (auto-recovery enabled)
    Connection recoverableConnectionMock =
        mock(Connection.class, withSettings().extraInterfaces(Recoverable.class));
    Recoverable recoverable = (Recoverable) recoverableConnectionMock;

    when(connectionFactoryMock.newConnection()).thenReturn(recoverableConnectionMock);
    when(recoverableConnectionMock.createChannel()).thenReturn(channel);

    InboundConnectorContext context =
        getContextBuilderWithSecrets()
            .validation(new DefaultValidationProvider())
            .properties(properties)
            .definition(new InboundConnectorDefinition(null, null, null, List.of()))
            .build();
    RabbitMqExecutable executable = new RabbitMqExecutable(connectionFactorySupplier);

    // When
    executable.activate(context);
    executable.deactivate();

    // Then - the same listener instance that was added must be removed to avoid retention
    verify(recoverable).addRecoveryListener(any(RecoveryListener.class));
    verify(recoverable).removeRecoveryListener(any(RecoveryListener.class));
  }
}
