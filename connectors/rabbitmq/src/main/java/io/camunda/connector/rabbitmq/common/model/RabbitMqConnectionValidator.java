/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.common.model;

import com.rabbitmq.client.AuthenticationFailureException;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.rabbitmq.supplier.ConnectionFactorySupplier;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;

/**
 * Validates a stored {@link RabbitMqConnectionConfiguration} by opening and closing a connection,
 * which authenticates against the broker and opens the virtual host. It does not check exchange or
 * queue permissions.
 */
public class RabbitMqConnectionValidator
    implements ConfigurationValidator<RabbitMqConnectionConfiguration> {

  static final int TIMEOUT_MILLIS = 10_000;
  static final String INVALID_INPUT_MESSAGE = "The RabbitMQ connection configuration is invalid.";
  static final String UNAUTHORIZED_MESSAGE = "RabbitMQ rejected the connection credentials.";
  static final String ERROR_MESSAGE = "The RabbitMQ connection could not be validated.";

  private final ConnectionFactorySupplier connectionFactorySupplier;

  public RabbitMqConnectionValidator() {
    this(new ConnectionFactorySupplier());
  }

  RabbitMqConnectionValidator(ConnectionFactorySupplier connectionFactorySupplier) {
    this.connectionFactorySupplier = connectionFactorySupplier;
  }

  @Override
  public ConfigurationValidationResult validate(RabbitMqConnectionConfiguration configuration) {
    if (configuration == null
        || configuration.authentication() == null
        || !isAuthenticationComplete(configuration.authentication())
        || !configuration.isRoutingPresentWhenRequired()) {
      return ConfigurationValidationResult.failure(ErrorCode.INVALID_INPUT, INVALID_INPUT_MESSAGE);
    }

    final ConnectionFactory factory;
    try {
      factory =
          connectionFactorySupplier.createFactory(
              configuration.authentication(), configuration.routing());
    } catch (URISyntaxException | GeneralSecurityException | IllegalArgumentException e) {
      return ConfigurationValidationResult.failure(ErrorCode.INVALID_INPUT, INVALID_INPUT_MESSAGE);
    }
    factory.setAutomaticRecoveryEnabled(false);
    factory.setConnectionTimeout(TIMEOUT_MILLIS);
    factory.setHandshakeTimeout(TIMEOUT_MILLIS);

    try (Connection ignored = factory.newConnection()) {
      return ConfigurationValidationResult.success();
    } catch (AuthenticationFailureException e) {
      return ConfigurationValidationResult.failure(ErrorCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
    } catch (Exception e) {
      return ConfigurationValidationResult.failure(ErrorCode.ERROR, ERROR_MESSAGE);
    }
  }

  private static boolean isAuthenticationComplete(RabbitMqAuthentication authentication) {
    return switch (authentication) {
      case UriAuthentication uri -> uri.uri() != null && !uri.uri().isBlank();
      case CredentialsAuthentication credentials ->
          credentials.userName() != null
              && !credentials.userName().isBlank()
              && credentials.password() != null
              && !credentials.password().isBlank();
    };
  }
}
