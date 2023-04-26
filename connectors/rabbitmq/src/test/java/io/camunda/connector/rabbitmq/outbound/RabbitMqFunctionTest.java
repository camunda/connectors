/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthentication;
import io.camunda.connector.rabbitmq.common.model.RabbitMqRouting;
import io.camunda.connector.rabbitmq.outbound.model.RabbitMqRequest;
import io.camunda.connector.rabbitmq.supplier.ConnectionFactorySupplier;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RabbitMqFunctionTest extends OutboundBaseTest {

  private RabbitMqFunction function;
  @Mock private ConnectionFactorySupplier connectionFactorySupplier;
  @Mock private ConnectionFactory connectionFactoryMock;
  @Mock private Connection connectionMock;
  @Mock private Channel channel;
  @Captor private ArgumentCaptor<byte[]> messageInByteArrayRequest;

  @BeforeEach
  public void init()
      throws URISyntaxException,
          NoSuchAlgorithmException,
          KeyManagementException,
          IOException,
          TimeoutException {
    function = new RabbitMqFunction(connectionFactorySupplier);
    when(connectionFactorySupplier.createFactory(
            any(RabbitMqAuthentication.class), any(RabbitMqRouting.class)))
        .thenReturn(connectionFactoryMock);
    when(connectionFactoryMock.newConnection()).thenReturn(connectionMock);
    when(connectionMock.createChannel()).thenReturn(channel);
  }

  @ParameterizedTest
  @MethodSource("successExecuteConnectorTest")
  void execute_shouldSucceedSuccessCases(final String input) throws Exception {
    // given
    RabbitMqRequest request = gson.fromJson(input, RabbitMqRequest.class);
    OutboundConnectorContext context = getContextBuilderWithSecrets().variables(request).build();
    // when
    Object connectorResultObject = function.execute(context);

    // then expected passing all needed methods and return 'success' result
    verify(connectionFactorySupplier)
        .createFactory(any(RabbitMqAuthentication.class), any(RabbitMqRouting.class));
    verify(connectionFactoryMock, times(1)).newConnection();
    verify(connectionMock, times(1)).createChannel();
    verify(channel).basicPublish(anyString(), anyString(), any(AMQP.BasicProperties.class), any());

    assertThat(connectorResultObject).isInstanceOf(RabbitMqResult.class);
    RabbitMqResult connectorResult = (RabbitMqResult) connectorResultObject;
    assertThat(connectorResult.getStatusResult()).isEqualTo("success");
  }

  @ParameterizedTest
  @MethodSource("successExecuteConnectorTest")
  void execute_shouldCorrectParseMessageBodyToByteArray(final String input) throws Exception {
    // given
    RabbitMqRequest request = gson.fromJson(input, RabbitMqRequest.class);
    OutboundConnectorContext context = getContextBuilderWithSecrets().variables(request).build();
    // when
    function.execute(context);
    // then
    verify(channel)
        .basicPublish(
            anyString(),
            anyString(),
            any(AMQP.BasicProperties.class),
            messageInByteArrayRequest.capture());
    assertThat(new String(messageInByteArrayRequest.getValue()))
        .isEqualTo(gson.toJson(request.getMessage().getBody()));
  }

  @ParameterizedTest
  @MethodSource("successExecuteConnectorWithPlainTextTest")
  void execute_shouldCorrectParseMessageBodyToByteArrayWithPlainText(final String input)
      throws Exception {
    // given
    RabbitMqRequest request = gson.fromJson(input, RabbitMqRequest.class);
    OutboundConnectorContext context = getContextBuilderWithSecrets().variables(request).build();
    // when
    function.execute(context);
    // then
    verify(channel)
        .basicPublish(
            anyString(),
            anyString(),
            any(AMQP.BasicProperties.class),
            messageInByteArrayRequest.capture());
    assertThat(new String(messageInByteArrayRequest.getValue()))
        .isEqualTo(request.getMessage().getBody());
  }

  @ParameterizedTest
  @MethodSource("failExecuteConnectorWithWrongPropertiesFields")
  void execute_shouldTrowExceptionWhenPropertiesFieldUnsupported(final String input) {
    // given
    RabbitMqRequest request = gson.fromJson(input, RabbitMqRequest.class);
    OutboundConnectorContext context = getContextBuilderWithSecrets().variables(request).build();
    // when and then
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> function.execute(context),
            "IllegalArgumentException was expected");
    assertThat(thrown).hasMessageContainingAll("Unsupported field", "for properties");
  }
}
