/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.integration;

import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import java.util.List;
import java.util.Map;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

record IntegrationTestConfig(
    SupportedDatabase database,
    String url,
    String host,
    String port,
    String rootUser,
    String username,
    String password,
    String databaseName,
    Map<String, String> properties) {

  public static List<IntegrationTestConfig> from(
      MySQLContainer mySqlServer,
      MSSQLServerContainer msSqlServer,
      PostgreSQLContainer postgreServer,
      MariaDBContainer mariaDbContainer) {
    return List.of(
        new IntegrationTestConfig(
            SupportedDatabase.MSSQL,
            msSqlServer.getJdbcUrl(),
            msSqlServer.getHost(),
            String.valueOf(msSqlServer.getMappedPort(1433)),
            null,
            msSqlServer.getUsername(),
            msSqlServer.getPassword(),
            null,
            Map.of("encrypt", "false")),
        new IntegrationTestConfig(
            SupportedDatabase.MYSQL,
            mySqlServer.getJdbcUrl(),
            mySqlServer.getHost(),
            String.valueOf(mySqlServer.getMappedPort(3306)),
            "root",
            mySqlServer.getUsername(),
            mySqlServer.getPassword(),
            mySqlServer.getDatabaseName(),
            null),
        new IntegrationTestConfig(
            SupportedDatabase.POSTGRESQL,
            postgreServer.getJdbcUrl(),
            postgreServer.getHost(),
            String.valueOf(postgreServer.getMappedPort(5432)),
            null,
            postgreServer.getUsername(),
            postgreServer.getPassword(),
            null,
            null),
        new IntegrationTestConfig(
            SupportedDatabase.MARIADB,
            mariaDbContainer.getJdbcUrl(),
            mariaDbContainer.getHost(),
            String.valueOf(mariaDbContainer.getMappedPort(3306)),
            "root",
            mariaDbContainer.getUsername(),
            mariaDbContainer.getPassword(),
            mariaDbContainer.getDatabaseName(),
            Map.of(
                "TC_INITFUNCTION", "org.testcontainers.jdbc.JDBCDriverTest::sampleInitFunction")));
  }
}
