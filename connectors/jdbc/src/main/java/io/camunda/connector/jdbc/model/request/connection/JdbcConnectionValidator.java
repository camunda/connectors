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
 * Validates a {@link JdbcConnectionConfiguration} out-of-band by opening a connection to the
 * database and closing it again. Authentication happens during connect, so a connection that opens
 * is a login that works — there is no query to run and none is run, so validating a credential
 * cannot touch data.
 *
 * <p>The connection is opened through {@link ConnectionHelper}, the same path connector execution
 * uses, and against the same database: a bound credential's database takes precedence at execution
 * time too ({@code JdbcRequest#database()}). So a credential that validates here cannot fail there
 * over a driver, a URL scheme or a property the two assembled differently.
 *
 * <p>Messages returned to the caller are static and value-free: a driver's exception text carries
 * the connection URL, the host and the login, so it is never surfaced. The full exception is
 * available at {@code DEBUG} for operators who need to diagnose a failure — enabling that level is
 * an explicit, deployment-level decision to accept those details in the logs.
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

  /**
   * Vendor error codes for a rejected login that do not reach us as SQL state class 28: Oracle's
   * ORA-01017 and SQL Server's 18456. Both report a wrong password under a SQL state that says
   * nothing about authorization, so without these two a wrong password on either product would read
   * as a generic error.
   */
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
    // The record declares the database @NotNull, but this validator is handed a deserialized
    // credential directly rather than a bean-validated one — and the record's own v1 convenience
    // constructor leaves it null — so the guard stays. Without a database there is no driver to
    // load and no URL scheme to build, and every product spells its connection string differently
    // — guessing one would validate a database the credential never named.
    SupportedDatabase database = configuration.database();
    if (database == null) {
      return ConfigurationValidationResult.failure(
          ErrorCode.INVALID_INPUT, MISSING_DATABASE_MESSAGE);
    }
    try {
      connectionCheck.run(configuration);
      return ConfigurationValidationResult.success();
    } catch (ClassNotFoundException e) {
      // Oracle's driver is not redistributable, so an Oracle credential only validates on a runtime
      // the operator added it to.
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
    try (Connection ignored =
        ConnectionHelper.openConnection(
            configuration.database(), configuration.toDetailedConnection())) {
      // Opening it is the whole check.
    }
  }
}
