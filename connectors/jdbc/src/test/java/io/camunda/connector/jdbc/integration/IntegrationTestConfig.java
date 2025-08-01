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
import org.testcontainers.oracle.OracleContainer;

record IntegrationTestConfig(
    SupportedDatabase database,
    String url,
    String host,
    String port,
    String rootUser,
    String username,
    String password,
    String databaseName,
    Map<String, String> properties,
    List<String> jsonType) {

  public static List<IntegrationTestConfig> from(
      MySQLContainer mySqlServer,
      MSSQLServerContainer msSqlServer,
      PostgreSQLContainer postgreServer,
      MariaDBContainer mariaDbContainer,
      OracleContainer oracleContainer) {
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
            Map.of("encrypt", "false"),
            List.of()),
        new IntegrationTestConfig(
            SupportedDatabase.MYSQL,
            mySqlServer.getJdbcUrl(),
            mySqlServer.getHost(),
            String.valueOf(mySqlServer.getMappedPort(3306)),
            "root",
            mySqlServer.getUsername(),
            mySqlServer.getPassword(),
            mySqlServer.getDatabaseName(),
            null,
            List.of("JSON")),
        new IntegrationTestConfig(
            SupportedDatabase.POSTGRESQL,
            postgreServer.getJdbcUrl(),
            postgreServer.getHost(),
            String.valueOf(postgreServer.getMappedPort(5432)),
            null,
            postgreServer.getUsername(),
            postgreServer.getPassword(),
            null,
            null,
            List.of("JSON", "JSONB")),
        new IntegrationTestConfig(
            SupportedDatabase.MARIADB,
            mariaDbContainer.getJdbcUrl(),
            mariaDbContainer.getHost(),
            String.valueOf(mariaDbContainer.getMappedPort(3306)),
            "root",
            mariaDbContainer.getUsername(),
            mariaDbContainer.getPassword(),
            mariaDbContainer.getDatabaseName(),
            null,
            List.of("JSON")),
        new IntegrationTestConfig(
            SupportedDatabase.ORACLE,
            oracleContainer.getJdbcUrl(),
            oracleContainer.getHost(),
            String.valueOf(oracleContainer.getMappedPort(1521)),
            "root",
            oracleContainer.getUsername(),
            oracleContainer.getPassword(),
            oracleContainer.getDatabaseName(),
            null,
            List.of("CLOB", "VARCHAR2")));
  }
}
