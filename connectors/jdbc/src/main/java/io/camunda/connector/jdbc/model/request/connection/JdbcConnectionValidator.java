/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.request.connection;

import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import io.camunda.connector.jdbc.utils.ConnectionHelper;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbcConnectionValidator
    implements ConfigurationValidator<JdbcConnectionConfiguration> {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcConnectionValidator.class);

  private static final Duration DEFAULT_LOGIN_TIMEOUT = Duration.ofSeconds(10);

  static final String MISSING_DATABASE_MESSAGE =
      "Select a supported database for this connection, so it can be validated.";
  static final String DRIVER_MISSING_MESSAGE =
      "The JDBC driver for the selected database is not available in this runtime.";
  static final String UNAUTHORIZED_MESSAGE = "The database rejected the login (unauthorized).";
  static final String GENERIC_MESSAGE = "The JDBC connection could not be validated.";

  private static final String INVALID_AUTHORIZATION_SQL_STATE_CLASS = "28";
  private static final Set<Integer> UNAUTHORIZED_VENDOR_ERROR_CODES = Set.of(1017, 18456);

  private final Duration loginTimeout;

  public JdbcConnectionValidator() {
    this(DEFAULT_LOGIN_TIMEOUT);
  }

  JdbcConnectionValidator(Duration loginTimeout) {
    this.loginTimeout = loginTimeout;
  }

  @Override
  public ConfigurationValidationResult validate(JdbcConnectionConfiguration configuration) {
    SupportedDatabase database = configuration.database();
    if (database == null) {
      return ConfigurationValidationResult.failure(
          ErrorCode.INVALID_INPUT, MISSING_DATABASE_MESSAGE);
    }
    try (Connection ignored =
        ConnectionHelper.openConnection(
            database, configuration.toDetailedConnection(), loginTimeout)) {
      return ConfigurationValidationResult.success();
    } catch (Exception e) {
      LOG.debug(
          "JDBC connection credential validation failed for {} ({})",
          database,
          e.getClass().getName());
      return classifyFailure(e);
    }
  }

  static ConfigurationValidationResult classifyFailure(Exception e) {
    if (e instanceof ClassNotFoundException) {
      return ConfigurationValidationResult.failure(ErrorCode.ERROR, DRIVER_MISSING_MESSAGE);
    }
    return e instanceof SQLException sqlException && isLoginRejected(sqlException)
        ? ConfigurationValidationResult.failure(ErrorCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE)
        : ConfigurationValidationResult.failure(ErrorCode.ERROR, GENERIC_MESSAGE);
  }

  private static boolean isLoginRejected(SQLException e) {
    String sqlState = e.getSQLState();
    return (sqlState != null && sqlState.startsWith(INVALID_AUTHORIZATION_SQL_STATE_CLASS))
        || UNAUTHORIZED_VENDOR_ERROR_CODES.contains(e.getErrorCode());
  }
}
