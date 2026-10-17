/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.AuthenticationFailureException;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.rabbitmq.supplier.ConnectionFactorySupplier;
import java.io.IOException;
import java.net.URISyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RabbitMqConnectionValidatorTest {

  private static final RabbitMqConnectionConfiguration VALID =
      new RabbitMqConnectionConfiguration(
          new CredentialsAuthentication("user", "password"),
          new FactoryRoutingData("vhost", "host", "5672"));

  private final ConnectionFactorySupplier supplier = mock(ConnectionFactorySupplier.class);
  private final ConnectionFactory factory = mock(ConnectionFactory.class);
  private final RabbitMqConnectionValidator validator = new RabbitMqConnectionValidator(supplier);

  @BeforeEach
  void setUp() throws Exception {
    when(supplier.createFactory(any(), any())).thenReturn(factory);
  }

  @Test
  void succeedsAndClosesConnection() throws Exception {
    var connection = mock(Connection.class);
    when(factory.newConnection()).thenReturn(connection);

    assertThat(validator.validate(VALID)).isEqualTo(ConfigurationValidationResult.success());
    verify(connection).close();
    verify(factory).setAutomaticRecoveryEnabled(false);
    verify(factory).setConnectionTimeout(RabbitMqConnectionValidator.TIMEOUT_MILLIS);
  }

  @Test
  void reportsRejectedCredentialsAsUnauthorized() throws Exception {
    when(factory.newConnection()).thenThrow(new AuthenticationFailureException("ACCESS_REFUSED"));

    assertThat(validator.validate(VALID))
        .isEqualTo(
            ConfigurationValidationResult.failure(
                ErrorCode.UNAUTHORIZED, RabbitMqConnectionValidator.UNAUTHORIZED_MESSAGE));
  }

  @Test
  void reportsUnreachableBrokerAsError() throws Exception {
    when(factory.newConnection()).thenThrow(new IOException("connection refused"));

    assertThat(validator.validate(VALID))
        .isEqualTo(
            ConfigurationValidationResult.failure(
                ErrorCode.ERROR, RabbitMqConnectionValidator.ERROR_MESSAGE));
  }

  @Test
  void reportsMalformedUriAsInvalidInput() throws Exception {
    when(supplier.createFactory(any(), any())).thenThrow(new URISyntaxException("x", "bad"));

    assertThat(
            validator.validate(
                new RabbitMqConnectionConfiguration(new UriAuthentication("amqp://x"), null)))
        .isEqualTo(invalidInput());
  }

  @Test
  void reportsNonNumericPortAsInvalidInput() throws Exception {
    when(supplier.createFactory(any(), any())).thenThrow(new NumberFormatException("abc"));

    assertThat(validator.validate(VALID)).isEqualTo(invalidInput());
  }

  @Test
  void rejectsIncompleteConfigurationWithoutConnecting() throws Exception {
    assertThat(validator.validate(null)).isEqualTo(invalidInput());
    assertThat(validator.validate(new RabbitMqConnectionConfiguration(null, null)))
        .isEqualTo(invalidInput());
    assertThat(
            validator.validate(
                new RabbitMqConnectionConfiguration(new UriAuthentication(" "), null)))
        .isEqualTo(invalidInput());
    assertThat(
            validator.validate(
                new RabbitMqConnectionConfiguration(
                    new CredentialsAuthentication("user", "password"), null)))
        .isEqualTo(invalidInput());
    assertThat(
            validator.validate(
                new RabbitMqConnectionConfiguration(
                    new CredentialsAuthentication("user", ""),
                    new FactoryRoutingData("vhost", "host", "5672"))))
        .isEqualTo(invalidInput());
    verify(supplier, never()).createFactory(any(), any());
  }

  private static ConfigurationValidationResult invalidInput() {
    return ConfigurationValidationResult.failure(
        ErrorCode.INVALID_INPUT, RabbitMqConnectionValidator.INVALID_INPUT_MESSAGE);
  }
}
