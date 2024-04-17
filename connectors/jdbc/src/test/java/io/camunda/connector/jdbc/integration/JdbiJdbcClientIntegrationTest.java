/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.error.ConnectorException;
import java.sql.SQLException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

@ExtendWith(MockitoExtension.class)
public class JdbiJdbcClientIntegrationTest extends IntegrationBaseTest {
  public static final String PROVIDE_SQL_SERVERS_CONFIG =
      "io.camunda.connector.jdbc.integration.JdbiJdbcClientIntegrationTest#provideSqlServersConfig";

  static final MSSQLServerContainer msSqlServer = new MSSQLServerContainer().acceptLicense();
  static final MySQLContainer mySqlServer = new MySQLContainer<>();
  static final PostgreSQLContainer postgreServer = new PostgreSQLContainer();

  static Stream<IntegrationTestConfig> provideSqlServersConfig() {
    return IntegrationTestConfig.from(mySqlServer, msSqlServer, postgreServer).stream();
  }

  @BeforeAll
  public static void setUp() throws SQLException {
    // otherwise the "test" user has no permission to create a database for instance
    mySqlServer.withUsername("root");
    msSqlServer.start();
    mySqlServer.start();
    postgreServer.start();

    for (IntegrationTestConfig config :
        IntegrationTestConfig.from(mySqlServer, msSqlServer, postgreServer)) {
      createEmployeeTable(config);
    }
  }

  @AfterAll
  public static void tearDown() {
    msSqlServer.stop();
    mySqlServer.stop();
    postgreServer.stop();
  }

  @BeforeEach
  public void insertData() throws SQLException {
    for (IntegrationTestConfig config :
        IntegrationTestConfig.from(mySqlServer, msSqlServer, postgreServer)) {
      insertDefaultEmployees(config);
    }
  }

  @AfterEach
  public void cleanUp() throws SQLException {
    for (IntegrationTestConfig config :
        IntegrationTestConfig.from(mySqlServer, msSqlServer, postgreServer)) {
      cleanUp(config);
    }
  }

  @Nested
  class HappyPathTests {

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldReturnResultList_whenSelectQuery(IntegrationTestConfig config) {
      selectDataAndAssertSuccess(config);
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldInsertData_whenInsertQuery(IntegrationTestConfig config) throws SQLException {
      insertDataAndAssertSuccess(config);
      assertNewEmployeeCreated(config);
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldUpdateData_whenUpdateQuery(IntegrationTestConfig config) throws SQLException {
      updateDataAndAssertSuccess(config);
      assertEmployeeUpdated(config);
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldDeleteData_whenDeleteQuery(IntegrationTestConfig config) throws SQLException {
      deleteDataAndAssertSuccess(config);
      assertEmployeeDeleted(config);
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldCreateTable_whenCreateTableQuery(IntegrationTestConfig config)
        throws SQLException {
      createTableAndAssertSuccess(config, "TestTable", "id INT PRIMARY KEY, name VARCHAR(255)");
      selectAll(config, "TestTable");
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldCreateDatabase_whenCreateTableQuery(IntegrationTestConfig config) {
      createDatabaseAndAssertSuccess(config, "mydb");
      createTableAndAssertSuccess(
          new IntegrationTestConfig(
              config.database(),
              config.url(),
              config.host(),
              config.port(),
              config.username(),
              config.password(),
              "mydb",
              config.properties()),
          "TestTable",
          "id INT PRIMARY KEY, name VARCHAR(255)");
    }
  }

  @Nested
  class WrongModifyingParameterValueTests {

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldThrowConnectorException_whenIsModifyingIsFalseWhileSelectingInDb(
        IntegrationTestConfig config) {
      selectDataAndAssertNoResult(config);
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldThrowConnectorException_whenIsModifyingIsFalseWhileInsertingInDb(
        IntegrationTestConfig config) {
      insertDataAndAssertThrows(config);
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldThrowConnectorException_whenIsModifyingIsFalseWhileUpdatingInDb(
        IntegrationTestConfig config) {
      updateDataAndAssertThrows(config);
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldThrowConnectorException_whenIsModifyingIsFalseWhileDeletingInDb(
        IntegrationTestConfig config) {
      deleteDataAndAssertThrows(config);
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldThrowConnectorException_whenIsModifyingIsFalseWhileCreatingTableInDb(
        IntegrationTestConfig config) {
      createTableAndAssertThrows(config, "TestTable", "id INT PRIMARY KEY, name VARCHAR(255)");
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldThrowConnectorException_whenIsModifyingIsFalseWhileCreatingDatabaseInDb(
        IntegrationTestConfig config) {
      createDatabaseAndAssertThrows(config, "mydb");
    }
  }

  @Nested
  class WrongAuthenticationTests {

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldThrowConnectorException_whenWrongUriConnection(IntegrationTestConfig config) {
      ConnectorException exception =
          assertThrows(
              ConnectorException.class,
              () ->
                  selectDataAndAssertSuccess(
                      new IntegrationTestConfig(
                          config.database(),
                          config.url(),
                          config.host(),
                          config.port(),
                          config.username(),
                          config.password() + "wrong",
                          config.databaseName(),
                          config.properties())));
      assertThat(exception.getMessage()).contains("Cannot create the Database connection");
    }

    @ParameterizedTest
    @MethodSource(PROVIDE_SQL_SERVERS_CONFIG)
    public void shouldThrowConnectorException_whenWrongDetailedConnection(
        IntegrationTestConfig config) {
      ConnectorException exception =
          assertThrows(
              ConnectorException.class,
              () ->
                  selectDataAndAssertSuccess(
                      new IntegrationTestConfig(
                          config.database(),
                          config.url(),
                          config.host(),
                          config.port(),
                          config.username(),
                          config.password() + "wrong",
                          config.databaseName(),
                          config.properties())));
      assertThat(exception.getMessage()).contains("Cannot create the Database connection");
    }
  }
}
