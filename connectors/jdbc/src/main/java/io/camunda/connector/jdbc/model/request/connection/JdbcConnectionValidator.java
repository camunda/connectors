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
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates a {@link JdbcConnectionConfiguration} out-of-band by opening a connection and closing
 * it again: authentication happens during connect, and no query is ever run. Goes through {@link
 * ConnectionHelper}, the same path execution takes, so a credential that validates here cannot fail
 * there over a driver or a URL scheme.
 *
 * <p>Returned messages are static and value-free; the full exception is logged at {@code DEBUG}.
 */
public class JdbcConnectionValidator
    implements ConfigurationValidator<JdbcConnectionConfiguration> {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcConnectionValidator.class);

  static final String MISSING_DATABASE_MESSAGE =
      "Select a supported database for this connection, so it can be validated.";
  static final String DRIVER_MISSING_MESSAGE =
      "The JDBC driver for the selected database is not available in this runtime.";
  static final String UNAUTHORIZED_MESSAGE = "The database rejected the login (unauthorized).";
  static final String GENERIC_MESSAGE = "The JDBC connection could not be validated.";

  /** SQL state class 28, "invalid authorization specification" (SQL:2016). */
  private static final String INVALID_AUTHORIZATION_SQL_STATE_CLASS = "28";

  /** Rejected logins not reported as SQL state 28: Oracle ORA-01017, SQL Server 18456. */
  private static final Set<Integer> UNAUTHORIZED_VENDOR_ERROR_CODES = Set.of(1017, 18456);

  /** Seam for testing: opens and closes a connection, throwing on failure. */
  @FunctionalInterface
  interface ConnectionCheck {
    void run(JdbcConnectionConfiguration configuration) throws ClassNotFoundException, SQLException;
  }

  private final ConnectionCheck connectionCheck;

  public JdbcConnectionValidator() {
    this(JdbcConnectionValidator::openAndClose);
  }

  JdbcConnectionValidator(ConnectionCheck connectionCheck) {
    this.connectionCheck = connectionCheck;
  }

  @Override
  public ConfigurationValidationResult validate(JdbcConnectionConfiguration configuration) {
    // Guarded here: this validator gets a deserialized credential, not a bean-validated one.
    SupportedDatabase database = configuration.database();
    if (database == null) {
      return ConfigurationValidationResult.failure(
          ErrorCode.INVALID_INPUT, MISSING_DATABASE_MESSAGE);
    }
    try {
      connectionCheck.run(configuration);
      return ConfigurationValidationResult.success();
    } catch (ClassNotFoundException e) {
      // Oracle's driver is not redistributable, so it may be absent from a given runtime.
      LOG.debug("JDBC driver for {} is not on the classpath", database, e);
      return ConfigurationValidationResult.failure(ErrorCode.ERROR, DRIVER_MISSING_MESSAGE);
    } catch (SQLException e) {
      LOG.debug(
          "The database refused the connection (SQL state {}, vendor code {})",
          e.getSQLState(),
          e.getErrorCode(),
          e);
      return isLoginRejected(e)
          ? ConfigurationValidationResult.failure(ErrorCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE)
          : ConfigurationValidationResult.failure(ErrorCode.ERROR, GENERIC_MESSAGE);
    } catch (Exception e) {
      LOG.debug("JDBC connection credential validation failed", e);
      return ConfigurationValidationResult.failure(ErrorCode.ERROR, GENERIC_MESSAGE);
    }
  }

  private static boolean isLoginRejected(SQLException e) {
    String sqlState = e.getSQLState();
    return (sqlState != null && sqlState.startsWith(INVALID_AUTHORIZATION_SQL_STATE_CLASS))
        || UNAUTHORIZED_VENDOR_ERROR_CODES.contains(e.getErrorCode());
  }

  private static void openAndClose(JdbcConnectionConfiguration configuration)
      throws ClassNotFoundException, SQLException {
    // TODO: apply a finite login timeout so a black-holed host cannot block the endpoint.
    try (Connection ignored =
        ConnectionHelper.openConnection(
            configuration.database(), configuration.toDetailedConnection())) {
      // Opening it is the whole check.
    }
  }
}
