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
    SupportedDatabase database = request.database();
    String driverClassName = database.getDriverClassName();
    try {
      LOG.debug("Executing JDBC request: {}", request);
      LOG.debug("Loading JDBC driver: {}", driverClassName);
      Class.forName(driverClassName);
      JdbcConnection connection = request.connection();
      Connection conn =
          DriverManager.getConnection(
              ensureMySQLCompatibleUrl(connection.getConnectionString(database), database),
              connection.getProperties());
      LOG.debug("Connection established for Database {}: {}", database, conn);
      return conn;
    } catch (ClassNotFoundException e) {
      throw new ConnectorException("Cannot find class: " + driverClassName);
    } catch (SQLException e) {
      throw new ConnectorException("Cannot create the Database connection: " + e.getMessage());
    }
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
