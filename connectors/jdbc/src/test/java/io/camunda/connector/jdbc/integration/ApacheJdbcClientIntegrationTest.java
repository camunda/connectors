/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.integration;

import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

@ExtendWith(MockitoExtension.class)
public class ApacheJdbcClientIntegrationTest {

  @Nested
  class MsSqlApacheJdbcClientTest extends IntegrationBaseTest {
    public static final MSSQLServerContainer mssqlserver =
        new MSSQLServerContainer().acceptLicense();

    @BeforeAll
    public static void setUp() throws SQLException {
      mssqlserver.start();
      createEmployeeTable(
          mssqlserver.getJdbcUrl(), mssqlserver.getUsername(), mssqlserver.getPassword());
    }

    @AfterAll
    public static void tearDown() {
      mssqlserver.stop();
    }

    @BeforeEach
    public void insertData() throws SQLException {
      insertDefaultEmployees(
          mssqlserver.getJdbcUrl(), mssqlserver.getUsername(), mssqlserver.getPassword());
    }

    @AfterEach
    public void cleanUp() throws SQLException {
      deleteAllEmployees(
          mssqlserver.getJdbcUrl(), mssqlserver.getUsername(), mssqlserver.getPassword());
    }

    @Test
    public void shouldReturnResultList_whenSelectQuery() {
      super.shouldReturnResultList_whenSelectQuery(
          SupportedDatabase.MSSQL,
          mssqlserver.getHost(),
          String.valueOf(mssqlserver.getMappedPort(1433)),
          mssqlserver.getUsername(),
          mssqlserver.getPassword(),
          null,
          Map.of("encrypt", "false"));
    }
  }

  @Nested
  class MySqlApacheJdbcExecutorTest extends IntegrationBaseTest {
    public static final MySQLContainer mysqlserver = new MySQLContainer<>();

    @BeforeAll
    public static void setUp() throws SQLException {
      mysqlserver.start();
      createEmployeeTable(
          mysqlserver.getDatabaseName(),
          mysqlserver.getJdbcUrl(),
          mysqlserver.getUsername(),
          mysqlserver.getPassword());
    }

    @AfterAll
    public static void tearDown() {
      mysqlserver.stop();
    }

    @BeforeEach
    public void insertData() throws SQLException {
      insertDefaultEmployees(
          mysqlserver.getDatabaseName(),
          mysqlserver.getJdbcUrl(),
          mysqlserver.getUsername(),
          mysqlserver.getPassword());
    }

    @AfterEach
    public void cleanUp() throws SQLException {
      deleteAllEmployees(
          mysqlserver.getDatabaseName(),
          mysqlserver.getJdbcUrl(),
          mysqlserver.getUsername(),
          mysqlserver.getPassword());
    }

    @Test
    public void shouldReturnResultList_whenSelectQuery() {
      super.shouldReturnResultList_whenSelectQuery(
          SupportedDatabase.MYSQL,
          mysqlserver.getHost(),
          String.valueOf(mysqlserver.getMappedPort(3306)),
          mysqlserver.getUsername(),
          mysqlserver.getPassword(),
          mysqlserver.getDatabaseName(),
          null);
    }
  }

  @Nested
  class PostgreApacheJdbcExecutorTest extends IntegrationBaseTest {
    public static final PostgreSQLContainer postgreServer = new PostgreSQLContainer();

    @BeforeAll
    public static void setUp() throws SQLException {
      postgreServer.start();
      createEmployeeTable(
          postgreServer.getJdbcUrl(), postgreServer.getUsername(), postgreServer.getPassword());
    }

    @AfterAll
    public static void tearDown() {
      postgreServer.stop();
    }

    @BeforeEach
    public void insertData() throws SQLException {
      insertDefaultEmployees(
          postgreServer.getJdbcUrl(), postgreServer.getUsername(), postgreServer.getPassword());
    }

    @AfterEach
    public void cleanUp() throws SQLException {
      deleteAllEmployees(
          postgreServer.getJdbcUrl(), postgreServer.getUsername(), postgreServer.getPassword());
    }

    @Test
    public void shouldReturnResultList_whenSelectQuery() {
      super.shouldReturnResultList_whenSelectQuery(
          SupportedDatabase.POSTGRESQL,
          postgreServer.getHost(),
          String.valueOf(postgreServer.getMappedPort(5432)),
          postgreServer.getUsername(),
          postgreServer.getPassword(),
          null,
          null);
    }
  }
}
