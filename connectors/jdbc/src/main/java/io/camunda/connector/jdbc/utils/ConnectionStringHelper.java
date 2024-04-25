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
      case MYSQL -> buildMySqlConnectionString(database, connection);
      case POSTGRESQL -> buildPostgresConnectionString(database, connection);
      case MSSQL -> buildMssqlConnectionString(database, connection);
      default -> throw new ConnectorException("Unsupported database: " + database);
    };
  }

  private static String buildMySqlConnectionString(
      SupportedDatabase database, DetailedConnection connection) {
    String host = connection.host();
    String port = connection.port();
    String username = connection.username();
    String password = connection.password();
    String databaseName = connection.databaseName();
    String authentication = "";
    if (username != null && !username.isEmpty()) {
      authentication += username;
      if (password != null && !password.isEmpty()) {
        authentication += ":" + password + "@";
      }
    }
    String connectionString = database.getUrlSchema() + authentication + host + ":" + port;
    if (databaseName != null && !databaseName.isEmpty()) {
      connectionString += "/" + databaseName;
    }
    return connectionString;
  }

  private static String buildPostgresConnectionString(
      SupportedDatabase database, DetailedConnection connection) {
    String host = connection.host();
    String port = connection.port();
    String username = connection.username();
    String password = connection.password();
    String databaseName = connection.databaseName();
    String connectionString = database.getUrlSchema() + host + ":" + port + "/";
    if (databaseName != null && !databaseName.isEmpty()) {
      connectionString += databaseName;
    }
    if (username != null && !username.isEmpty()) {
      connectionString += "?user=" + username;
      if (password != null && !password.isEmpty()) {
        connectionString += "&password=" + password;
      }
    }
    return connectionString;
  }

  private static String buildMssqlConnectionString(
      SupportedDatabase database, DetailedConnection connection) {
    String host = connection.host();
    String port = connection.port();
    String username = connection.username();
    String password = connection.password();
    String databaseName = connection.databaseName();
    String connectionString = database.getUrlSchema() + host + ":" + port;
    if (username != null && !username.isEmpty()) {
      connectionString += ";user=" + username;
      if (password != null && !password.isEmpty()) {
        connectionString += ";password=" + password;
      }
    }
    if (databaseName != null && !databaseName.isEmpty()) {
      connectionString += ";databaseName=" + databaseName;
    }
    return connectionString;
  }
}
