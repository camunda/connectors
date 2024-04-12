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

  public static String buildConnectionString(
      SupportedDatabase database,
      String host,
      String port,
      String username,
      String password,
      String databaseName) {
    return switch (database) {
      case MYSQL -> buildMySqlConnectionString(
          database, host, port, username, password, databaseName);
      case POSTGRESQL -> buildPostgresConnectionString(
          database, host, port, username, password, databaseName);
      case MSSQL -> buildMssqlConnectionString(
          database, host, port, username, password, databaseName);
      default -> throw new ConnectorException("Unsupported database: " + database);
    };
  }

  public static Connection openConnection(JdbcRequest request) {
    try {
      LOG.debug("Executing JDBC request: {}", request);
      LOG.debug("Loading JDBC driver: {}", request.database().getDriverClassName());
      Class.forName(request.database().getDriverClassName());
      JdbcConnection connection = request.connection();
      Connection conn =
          DriverManager.getConnection(
              connection.getConnectionString(request.database()), connection.getProperties());
      LOG.debug("Connection established for Database {}: {}", request.database(), conn);
      return conn;
    } catch (ClassNotFoundException e) {
      throw new ConnectorException(
          "Cannot find class: " + request.database().getDriverClassName(), e);
    } catch (SQLException e) {
      throw new ConnectorException("Cannot create the Database connection", e);
    }
  }

  private static String buildMySqlConnectionString(
      SupportedDatabase database,
      String host,
      String port,
      String username,
      String password,
      String databaseName) {
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
      SupportedDatabase database,
      String host,
      String port,
      String username,
      String password,
      String databaseName) {
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
      SupportedDatabase database,
      String host,
      String port,
      String username,
      String password,
      String databaseName) {
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
