/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.model;

import static org.apache.commons.lang3.StringUtils.isAnyBlank;

import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.api.validation.ConfigurationValidationResult.Status;
import io.camunda.connector.api.validation.ConfigurationValidator;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.errors.SslAuthenticationException;

/** Checks cluster connectivity and authentication, not topic or consumer-group permissions. */
public class KafkaConnectionValidator
    implements ConfigurationValidator<KafkaConnectionConfiguration> {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
  static final String INVALID_INPUT_MESSAGE = "The Kafka connection configuration is invalid.";
  static final String UNAUTHORIZED_MESSAGE = "Kafka rejected the connection credentials.";
  static final String ERROR_MESSAGE = "The Kafka connection could not be validated.";

  private final Function<Properties, Admin> adminFactory;
  private final Duration timeout;

  public KafkaConnectionValidator() {
    this(Admin::create, DEFAULT_TIMEOUT);
  }

  KafkaConnectionValidator(Function<Properties, Admin> adminFactory, Duration timeout) {
    this.adminFactory = adminFactory;
    this.timeout = timeout;
  }

  @Override
  public ConfigurationValidationResult validate(KafkaConnectionConfiguration configuration) {
    if (configuration == null
        || isAnyBlank(
            configuration.bootstrapServers(), configuration.username(), configuration.password())) {
      return ConfigurationValidationResult.failure(ErrorCode.INVALID_INPUT, INVALID_INPUT_MESSAGE);
    }

    Admin admin = null;
    ConfigurationValidationResult result = null;
    try {
      Properties properties =
          KafkaPropertiesUtil.produceAuthenticationProperties(configuration.toAuthentication());
      properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, configuration.bootstrapServers());
      int timeoutMs = Math.toIntExact(timeout.toMillis());
      properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, timeoutMs);
      properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, timeoutMs);
      admin = adminFactory.apply(properties);
      admin
          .describeCluster(
              new DescribeClusterOptions().includeAuthorizedOperations(false).timeoutMs(timeoutMs))
          .nodes()
          .get(timeoutMs, TimeUnit.MILLISECONDS);
      result = ConfigurationValidationResult.success();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      result = ConfigurationValidationResult.failure(ErrorCode.ERROR, ERROR_MESSAGE);
    } catch (ExecutionException
        | TimeoutException
        | KafkaException
        | IllegalArgumentException
        | CancellationException e) {
      result = classifyFailure(e);
    } finally {
      if (admin != null) {
        boolean interrupted = Thread.currentThread().isInterrupted();
        try {
          admin.close(timeout);
        } catch (KafkaException e) {
          var closeFailure = classifyFailure(e);
          if (result != null && result.status() == Status.SUCCESS) {
            result = closeFailure;
          }
        } finally {
          if (interrupted) {
            Thread.currentThread().interrupt();
          }
        }
      }
    }
    return result;
  }

  private static ConfigurationValidationResult classifyFailure(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof InterruptedException || cause instanceof InterruptException) {
        Thread.currentThread().interrupt();
        return ConfigurationValidationResult.failure(ErrorCode.ERROR, ERROR_MESSAGE);
      }
      if (cause instanceof SslAuthenticationException) {
        return ConfigurationValidationResult.failure(ErrorCode.ERROR, ERROR_MESSAGE);
      }
      if (cause instanceof AuthenticationException) {
        return ConfigurationValidationResult.failure(ErrorCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
      }
      if (cause instanceof ConfigException || cause instanceof IllegalArgumentException) {
        return ConfigurationValidationResult.failure(
            ErrorCode.INVALID_INPUT, INVALID_INPUT_MESSAGE);
      }
    }
    return ConfigurationValidationResult.failure(ErrorCode.ERROR, ERROR_MESSAGE);
  }
}
