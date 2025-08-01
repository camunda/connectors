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

public class ConnectionStringHelper {

  public static String buildConnectionString(
      SupportedDatabase database, DetailedConnection connection) {
    return switch (database) {
      case MARIADB, MYSQL, POSTGRESQL, ORACLE -> buildCommonConnectionString(database, connection);
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
}
