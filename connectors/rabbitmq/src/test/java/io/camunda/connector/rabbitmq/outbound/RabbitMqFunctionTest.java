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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.impl.AMQImpl;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.rabbitmq.common.model.FactoryRoutingData;
import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthentication;
import io.camunda.connector.rabbitmq.outbound.model.RabbitMqRequest;
import io.camunda.connector.rabbitmq.supplier.ConnectionFactorySupplier;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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
            any(RabbitMqAuthentication.class), any(FactoryRoutingData.class)))
        .thenReturn(connectionFactoryMock);
    when(connectionFactoryMock.newConnection()).thenReturn(connectionMock);
    when(connectionMock.createChannel()).thenReturn(channel);
  }

  @ParameterizedTest
  @MethodSource("successExecuteConnectorTest")
  void execute_shouldSucceedSuccessCases(final String input) throws Exception {
    // given
    OutboundConnectorContext context = getContextBuilderWithSecrets().variables(input).build();
    // when
    Object connectorResultObject = function.execute(context);

    // then expected passing all needed methods and return 'success' result
    verify(connectionFactorySupplier)
        .createFactory(any(RabbitMqAuthentication.class), any(FactoryRoutingData.class));
    verify(connectionFactoryMock, times(1)).newConnection();
    verify(connectionMock, times(1)).createChannel();
    verify(channel).confirmSelect();
    verify(channel).basicPublish(anyString(), anyString(), any(AMQP.BasicProperties.class), any());
    verify(channel).waitForConfirmsOrDie(anyLong());

    assertThat(connectorResultObject).isInstanceOf(RabbitMqResult.class);
    RabbitMqResult connectorResult = (RabbitMqResult) connectorResultObject;
    assertThat(connectorResult.getStatusResult()).isEqualTo("success");
  }

  @ParameterizedTest
  @MethodSource("successExecuteConnectorTest")
  void execute_shouldCorrectParseMessageBodyToByteArray(final String input) throws Exception {
    // given
    OutboundConnectorContext context =
        getContextBuilderWithSecrets()
            .validation(new DefaultValidationProvider())
            .variables(input)
            .build();
    // when
    function.execute(context);
    // then
    verify(channel)
        .basicPublish(
            anyString(),
            anyString(),
            any(AMQP.BasicProperties.class),
            messageInByteArrayRequest.capture());
    var message = new String(messageInByteArrayRequest.getValue());
    var body =
        MessageUtil.getBodyAsByteArray(
            context.bindVariables(RabbitMqRequest.class).message().body());
    assertThat(message).isEqualTo(new String(body));
  }

  @ParameterizedTest
  @MethodSource("successExecuteConnectorWithPlainTextTest")
  void execute_shouldCorrectParseMessageBodyToByteArrayWithPlainText(final String input)
      throws Exception {
    // given
    OutboundConnectorContext context = getContextBuilderWithSecrets().variables(input).build();
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
        .isEqualTo(context.bindVariables(RabbitMqRequest.class).message().body());
  }

  @ParameterizedTest
  @MethodSource("failExecuteConnectorWithWrongPropertiesFields")
  void execute_shouldTrowExceptionWhenPropertiesFieldUnsupported(final String input) {
    // given
    OutboundConnectorContext context = getContextBuilderWithSecrets().variables(input).build();
    // when and then
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> function.execute(context),
            "IllegalArgumentException was expected");
    assertThat(thrown).hasMessageContainingAll("Unsupported field", "for properties");
  }

  @ParameterizedTest
  @MethodSource("rejectedPublishCases")
  void execute_shouldFailWhenBrokerDoesNotConfirmPublish(
      final Exception brokerError, final String expectedMessage) throws Exception {
    // given
    String input =
        """
        {
          "authentication": {"authType": "credentials", "userName": "user", "password": "pass"},
          "routing": {
            "virtualHost": "/",
            "hostName": "localhost",
            "port": "5672",
            "exchange": "exchangeName",
            "routingKey": "routingKey"
          },
          "message": {"body": {"key": "value"}}
        }
        """;
    OutboundConnectorContext context = getContextBuilderWithSecrets().variables(input).build();
    doThrow(brokerError).when(channel).waitForConfirmsOrDie(anyLong());
    // when and then
    ConnectorException thrown =
        assertThrows(ConnectorException.class, () -> function.execute(context));
    assertThat(thrown.getErrorCode()).isEqualTo(RabbitMqFunction.PUBLISH_FAILED_ERROR_CODE);
    assertThat(thrown).hasMessageContaining(expectedMessage).hasCause(brokerError);
  }

  private static Stream<Arguments> rejectedPublishCases() {
    var channelClose =
        new AMQImpl.Channel.Close(404, "NOT_FOUND - no exchange 'missing' in vhost '/'", 60, 40);
    return Stream.of(
        Arguments.of(
            new ShutdownSignalException(false, false, channelClose, null),
            "NOT_FOUND - no exchange 'missing' in vhost '/'"),
        Arguments.of(new IOException("nacks received"), "nacks received"),
        Arguments.of(new TimeoutException(), "did not confirm the message within"));
  }
}
