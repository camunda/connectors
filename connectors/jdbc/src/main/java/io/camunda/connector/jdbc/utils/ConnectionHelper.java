/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.utils;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.jdbc.model.request.JdbcRequest;
import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import io.camunda.connector.jdbc.model.request.connection.JdbcConnection;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionHelper {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectionHelper.class);

  public static Connection openConnection(JdbcRequest request) {
    // The effective database, not the raw field: a bound credential's database wins (see
    // JdbcRequest#database()), so the driver and URL scheme match the host and login.
    SupportedDatabase database = request.database();
    try {
      LOG.debug("Executing JDBC request: {}", request);
      return openConnection(database, resolveConnection(request));
    } catch (ClassNotFoundException e) {
      throw new ConnectorException("Cannot find class: " + database.getDriverClassName());
    } catch (SQLException e) {
      throw new ConnectorException("Cannot create the Database connection: " + e.getMessage());
    }
  }

  /**
   * Opens a connection without a surrounding request, for callers that hold a database and a
   * connection but no job to execute — out-of-band validation of a stored connection credential.
   *
   * <p>Driver failures are propagated rather than wrapped, because a caller that has to tell "the
   * database rejected this login" from "the database is unreachable" needs the {@link
   * SQLException#getSQLState() SQL state}, which the {@link ConnectorException} above discards.
   */
  public static Connection openConnection(SupportedDatabase database, JdbcConnection connection)
      throws ClassNotFoundException, SQLException {
    String driverClassName = database.getDriverClassName();
    LOG.debug("Loading JDBC driver: {}", driverClassName);
    Class.forName(driverClassName);
    Connection conn =
        DriverManager.getConnection(
            ensureMySQLCompatibleUrl(connection.getConnectionString(database), database),
            connection.getProperties());
    LOG.debug("Connection established for Database {}: {}", database, conn);
    return conn;
  }

  /**
   * Resolves the effective JDBC connection, applying the configuration/inline precedence for the
   * credentials transition (per-connector implementation of the consume-configuration capability):
   * a bound connection credential ({@code configuration}) takes precedence over the inline
   * connection fields; the inline connection is the fallback. Per-field inline override is not
   * modeled for JDBC because the connection is consumed as a whole object.
   */
  static JdbcConnection resolveConnection(JdbcRequest request) {
    if (request.configuration() != null) {
      return request.configuration().toDetailedConnection();
    }
    if (request.connection() != null) {
      return request.connection();
    }
    throw new ConnectorException(
        "No JDBC connection provided: fill in the connection fields or select a connection credential");
  }

  /**
   * Ensure MySQL compatibility as we are using MariaDB driver for MySQL.
   *
   * @return Properties with permitMysqlScheme set to true if the database is MySQL.
   * @see <a
   *     href="https://mariadb.com/kb/en/about-mariadb-connector-j/#jdbcmysql-scheme-compatibility">Compatibility
   *     details</a>
   */
  private static String ensureMySQLCompatibleUrl(String url, SupportedDatabase database) {
    if (database == SupportedDatabase.MYSQL) {
      return ConnectionParameterHelper.addQueryParameterToURL(url, "permitMysqlScheme");
    }
    return url;
  }
}
