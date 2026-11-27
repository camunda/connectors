/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.utils;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import io.camunda.connector.jdbc.model.request.connection.DetailedConnection;
import java.util.regex.Pattern;

public class ConnectionStringHelper {

  // Host names, IP literals and host lists; a SQL Server host may carry a named instance.
  private static final Pattern HOST = Pattern.compile("[A-Za-z0-9._:,\\-\\[\\]]+");
  private static final Pattern MSSQL_HOST =
      Pattern.compile(HOST.pattern() + "(\\\\[A-Za-z0-9_$#-]+)?");
  private static final Pattern PORT = Pattern.compile("[0-9]{1,5}");
  // The characters that end the database name and start driver options in a JDBC URL.
  private static final Pattern DATABASE_NAME_DELIMITER = Pattern.compile("[?&;#{}\\p{Cntrl}]");

  public static String buildConnectionString(
      SupportedDatabase database, DetailedConnection connection) {
    validate(database, connection);
    return switch (database) {
      case MARIADB, MYSQL, POSTGRESQL -> buildCommonConnectionString(database, connection);
      case MSSQL -> buildMssqlConnectionString(database, connection);
      default -> throw new ConnectorException("Unsupported database: " + database);
    };
  }

  private static String buildCommonConnectionString(
      SupportedDatabase database, DetailedConnection connection) {
    String connectionString = buildConnectionStringSegment(database, connection) + "/";
    String databaseName = connection.databaseName();
    if (databaseName != null && !databaseName.isEmpty()) {
      connectionString += databaseName;
    }
    return connectionString;
  }

  private static String buildMssqlConnectionString(
      SupportedDatabase database, DetailedConnection connection) {
    String databaseName = connection.databaseName();
    String connectionString = buildConnectionStringSegment(database, connection);
    if (databaseName != null && !databaseName.isEmpty()) {
      connectionString += ";databaseName=" + databaseName;
    }
    return connectionString;
  }

  private static String buildConnectionStringSegment(
      SupportedDatabase database, DetailedConnection connection) {
    String host = connection.host();
    String port = connection.port();
    return database.getUrlSchema() + host + ":" + port;
  }

  /**
   * The fields are concatenated into the URL, so a field must not be able to add driver options to
   * it. Options belong in the connection properties.
   */
  private static void validate(SupportedDatabase database, DetailedConnection connection) {
    Pattern hostPattern = database == SupportedDatabase.MSSQL ? MSSQL_HOST : HOST;
    if (connection.host() == null || !hostPattern.matcher(connection.host()).matches()) {
      throw new ConnectorException("Invalid host: it must be a host name or an IP address");
    }
    if (connection.port() == null || !PORT.matcher(connection.port()).matches()) {
      throw new ConnectorException("Invalid port: it must be a number");
    }
    String databaseName = connection.databaseName();
    if (databaseName != null && DATABASE_NAME_DELIMITER.matcher(databaseName).find()) {
      throw new ConnectorException(
          "Invalid database name: it must not contain any of ? & ; # { } or control characters");
    }
  }
}
